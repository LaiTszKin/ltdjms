package ltdjms.discord.product.services;

import ltdjms.discord.product.domain.Product;
import ltdjms.discord.product.domain.ProductRepository;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.Unit;
import ltdjms.discord.shared.events.DomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ProductService.
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private static final long TEST_GUILD_ID = 123456789012345678L;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private DomainEventPublisher eventPublisher;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, eventPublisher);
    }

    @Nested
    @DisplayName("createProduct")
    class CreateProductTests {

        @Test
        @DisplayName("should create product without reward")
        void shouldCreateProductWithoutReward() {
            // Given
            when(productRepository.existsByGuildIdAndName(TEST_GUILD_ID, "VIP 服務")).thenReturn(false);
            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
                Product p = invocation.getArgument(0);
                return new Product(1L, p.guildId(), p.name(), p.description(),
                        p.rewardType(), p.rewardAmount(), p.createdAt(), p.updatedAt());
            });

            // When
            Result<Product, DomainError> result = productService.createProduct(
                    TEST_GUILD_ID, "VIP 服務", "專人服務", null, null);

            // Then
            assertThat(result.isOk()).isTrue();
            assertThat(result.getValue().id()).isEqualTo(1L);
            assertThat(result.getValue().name()).isEqualTo("VIP 服務");
            assertThat(result.getValue().hasReward()).isFalse();
        }

        @Test
        @DisplayName("should create product with currency reward")
        void shouldCreateProductWithCurrencyReward() {
            // Given
            when(productRepository.existsByGuildIdAndName(TEST_GUILD_ID, "新手禮包")).thenReturn(false);
            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
                Product p = invocation.getArgument(0);
                return new Product(1L, p.guildId(), p.name(), p.description(),
                        p.rewardType(), p.rewardAmount(), p.createdAt(), p.updatedAt());
            });

            // When
            Result<Product, DomainError> result = productService.createProduct(
                    TEST_GUILD_ID, "新手禮包", "歡迎", Product.RewardType.CURRENCY, 1000L);

            // Then
            assertThat(result.isOk()).isTrue();
            assertThat(result.getValue().rewardType()).isEqualTo(Product.RewardType.CURRENCY);
            assertThat(result.getValue().rewardAmount()).isEqualTo(1000L);
        }

        @Test
        @DisplayName("should reject blank name")
        void shouldRejectBlankName() {
            // When
            Result<Product, DomainError> result = productService.createProduct(
                    TEST_GUILD_ID, "   ", "desc", null, null);

            // Then
            assertThat(result.isErr()).isTrue();
            assertThat(result.getError().category()).isEqualTo(DomainError.Category.INVALID_INPUT);
            assertThat(result.getError().message()).contains("空");
        }

        @Test
        @DisplayName("should reject duplicate name")
        void shouldRejectDuplicateName() {
            // Given
            when(productRepository.existsByGuildIdAndName(TEST_GUILD_ID, "已存在")).thenReturn(true);

            // When
            Result<Product, DomainError> result = productService.createProduct(
                    TEST_GUILD_ID, "已存在", "desc", null, null);

            // Then
            assertThat(result.isErr()).isTrue();
            assertThat(result.getError().message()).contains("已存在");
        }

        @Test
        @DisplayName("should reject inconsistent reward settings")
        void shouldRejectInconsistentRewardSettings() {
            // Given
            when(productRepository.existsByGuildIdAndName(anyLong(), anyString())).thenReturn(false);

            // When - reward type without amount
            Result<Product, DomainError> result = productService.createProduct(
                    TEST_GUILD_ID, "Test", "desc", Product.RewardType.CURRENCY, null);

            // Then
            assertThat(result.isErr()).isTrue();
            assertThat(result.getError().message()).contains("同時");
        }

        @Test
        @DisplayName("should reject zero or negative reward amount")
        void shouldRejectZeroOrNegativeRewardAmount() {
            // Given
            when(productRepository.existsByGuildIdAndName(anyLong(), anyString())).thenReturn(false);

            // When
            Result<Product, DomainError> result = productService.createProduct(
                    TEST_GUILD_ID, "Test", "desc", Product.RewardType.CURRENCY, 0L);

            // Then
            assertThat(result.isErr()).isTrue();
            assertThat(result.getError().message()).contains("大於 0");
        }
    }

    @Nested
    @DisplayName("updateProduct")
    class UpdateProductTests {

        @Test
        @DisplayName("should update product successfully")
        void shouldUpdateProductSuccessfully() {
            // Given
            Instant now = Instant.now();
            Product existing = new Product(1L, TEST_GUILD_ID, "舊名稱", "舊描述",
                    null, null, now, now);

            when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(productRepository.existsByGuildIdAndNameExcludingId(TEST_GUILD_ID, "新名稱", 1L))
                    .thenReturn(false);
            when(productRepository.update(any(Product.class))).thenAnswer(i -> i.getArgument(0));

            // When
            Result<Product, DomainError> result = productService.updateProduct(
                    1L, "新名稱", "新描述", Product.RewardType.CURRENCY, 500L);

            // Then
            assertThat(result.isOk()).isTrue();
            assertThat(result.getValue().name()).isEqualTo("新名稱");
            assertThat(result.getValue().rewardAmount()).isEqualTo(500L);
        }

        @Test
        @DisplayName("should reject update for non-existent product")
        void shouldRejectUpdateForNonExistentProduct() {
            // Given
            when(productRepository.findById(999L)).thenReturn(Optional.empty());

            // When
            Result<Product, DomainError> result = productService.updateProduct(
                    999L, "Test", "desc", null, null);

            // Then
            assertThat(result.isErr()).isTrue();
            assertThat(result.getError().message()).contains("找不到");
        }

        @Test
        @DisplayName("should reject update with duplicate name")
        void shouldRejectUpdateWithDuplicateName() {
            // Given
            Instant now = Instant.now();
            Product existing = new Product(1L, TEST_GUILD_ID, "舊名稱", null,
                    null, null, now, now);

            when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(productRepository.existsByGuildIdAndNameExcludingId(TEST_GUILD_ID, "已存在", 1L))
                    .thenReturn(true);

            // When
            Result<Product, DomainError> result = productService.updateProduct(
                    1L, "已存在", null, null, null);

            // Then
            assertThat(result.isErr()).isTrue();
            assertThat(result.getError().message()).contains("已存在");
        }
    }

    @Nested
    @DisplayName("deleteProduct")
    class DeleteProductTests {

        @Test
        @DisplayName("should delete product successfully")
        void shouldDeleteProductSuccessfully() {
            // Given
            Instant now = Instant.now();
            Product existing = new Product(1L, TEST_GUILD_ID, "Test", null,
                    null, null, now, now);

            when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(productRepository.deleteById(1L)).thenReturn(true);

            // When
            Result<Unit, DomainError> result = productService.deleteProduct(1L);

            // Then
            assertThat(result.isOk()).isTrue();
            verify(productRepository).deleteById(1L);
        }

        @Test
        @DisplayName("should reject delete for non-existent product")
        void shouldRejectDeleteForNonExistentProduct() {
            // Given
            when(productRepository.findById(999L)).thenReturn(Optional.empty());

            // When
            Result<Unit, DomainError> result = productService.deleteProduct(999L);

            // Then
            assertThat(result.isErr()).isTrue();
            assertThat(result.getError().message()).contains("找不到");
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperationsTests {

        @Test
        @DisplayName("should get products for guild")
        void shouldGetProductsForGuild() {
            // Given
            Instant now = Instant.now();
            List<Product> products = List.of(
                    new Product(1L, TEST_GUILD_ID, "Product 1", null, null, null, now, now),
                    new Product(2L, TEST_GUILD_ID, "Product 2", null, null, null, now, now)
            );
            when(productRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(products);

            // When
            List<Product> result = productService.getProducts(TEST_GUILD_ID);

            // Then
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("should get product count")
        void shouldGetProductCount() {
            // Given
            when(productRepository.countByGuildId(TEST_GUILD_ID)).thenReturn(5L);

            // When
            long count = productService.getProductCount(TEST_GUILD_ID);

            // Then
            assertThat(count).isEqualTo(5L);
        }
    }
}
