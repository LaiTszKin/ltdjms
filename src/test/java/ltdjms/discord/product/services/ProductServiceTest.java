package ltdjms.discord.product.services;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ltdjms.discord.product.domain.Product;
import ltdjms.discord.product.domain.ProductRepository;
import ltdjms.discord.redemption.domain.RedemptionCodeRepository;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.Unit;
import ltdjms.discord.shared.events.DomainEventPublisher;

/** Unit tests for ProductService. */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;

  @Mock private ProductRepository productRepository;

  @Mock private RedemptionCodeRepository redemptionCodeRepository;

  @Mock private DomainEventPublisher eventPublisher;

  private ProductService productService;

  @BeforeEach
  void setUp() {
    productService =
        new ProductService(productRepository, redemptionCodeRepository, eventPublisher);
  }

  @Nested
  @DisplayName("createProduct")
  class CreateProductTests {

    @Test
    @DisplayName("should create product without reward")
    void shouldCreateProductWithoutReward() {
      // Given
      when(productRepository.existsByGuildIdAndName(TEST_GUILD_ID, "VIP 服務")).thenReturn(false);
      when(productRepository.save(any(Product.class)))
          .thenAnswer(
              invocation -> {
                Product p = invocation.getArgument(0);
                return new Product(
                    1L,
                    p.guildId(),
                    p.name(),
                    p.description(),
                    p.rewardType(),
                    p.rewardAmount(),
                    p.currencyPrice(),
                    p.createdAt(),
                    p.updatedAt());
              });

      // When
      Result<Product, DomainError> result =
          productService.createProduct(TEST_GUILD_ID, "VIP 服務", "專人服務", null, null, null);

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
      when(productRepository.save(any(Product.class)))
          .thenAnswer(
              invocation -> {
                Product p = invocation.getArgument(0);
                return new Product(
                    1L,
                    p.guildId(),
                    p.name(),
                    p.description(),
                    p.rewardType(),
                    p.rewardAmount(),
                    p.currencyPrice(),
                    p.createdAt(),
                    p.updatedAt());
              });

      // When
      Result<Product, DomainError> result =
          productService.createProduct(
              TEST_GUILD_ID, "新手禮包", "歡迎", Product.RewardType.CURRENCY, 1000L, null);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().rewardType()).isEqualTo(Product.RewardType.CURRENCY);
      assertThat(result.getValue().rewardAmount()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("should reject blank name")
    void shouldRejectBlankName() {
      // When
      Result<Product, DomainError> result =
          productService.createProduct(TEST_GUILD_ID, "   ", "desc", null, null, null);

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
      Result<Product, DomainError> result =
          productService.createProduct(TEST_GUILD_ID, "已存在", "desc", null, null, null);

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
      Result<Product, DomainError> result =
          productService.createProduct(
              TEST_GUILD_ID, "Test", "desc", Product.RewardType.CURRENCY, null, null);

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
      Result<Product, DomainError> result =
          productService.createProduct(
              TEST_GUILD_ID, "Test", "desc", Product.RewardType.CURRENCY, 0L, null);

      // Then
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().message()).contains("大於 0");
    }

    @Test
    @DisplayName("should reject name exceeding 100 characters")
    void shouldRejectNameExceeding100Characters() {
      // When
      String longName = "a".repeat(101);
      Result<Product, DomainError> result =
          productService.createProduct(TEST_GUILD_ID, longName, "desc", null, null, null);

      // Then
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().message()).contains("不能超過 100 個字元");
    }

    @Test
    @DisplayName("should reject zero or negative currency price")
    void shouldRejectZeroOrNegativeCurrencyPrice() {
      // Given
      when(productRepository.existsByGuildIdAndName(anyLong(), anyString())).thenReturn(false);

      // When
      Result<Product, DomainError> result =
          productService.createProduct(TEST_GUILD_ID, "Test", "desc", null, null, 0L);

      // Then
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().message()).contains("貨幣價格必須大於 0");
    }

    @Test
    @DisplayName("should create product with currency price")
    void shouldCreateProductWithCurrencyPrice() {
      // Given
      when(productRepository.existsByGuildIdAndName(TEST_GUILD_ID, "Premium Product"))
          .thenReturn(false);
      when(productRepository.save(any(Product.class)))
          .thenAnswer(
              invocation -> {
                Product p = invocation.getArgument(0);
                return new Product(
                    1L,
                    p.guildId(),
                    p.name(),
                    p.description(),
                    p.rewardType(),
                    p.rewardAmount(),
                    p.currencyPrice(),
                    p.createdAt(),
                    p.updatedAt());
              });

      // When
      Result<Product, DomainError> result =
          productService.createProduct(
              TEST_GUILD_ID, "Premium Product", "Premium", null, null, 500L);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().currencyPrice()).isEqualTo(500L);
      assertThat(result.getValue().hasCurrencyPrice()).isTrue();
    }

    @Test
    @DisplayName("should normalize uppercase backend API URL scheme")
    void shouldNormalizeUppercaseBackendApiUrlScheme() {
      when(productRepository.existsByGuildIdAndName(TEST_GUILD_ID, "Backend Product"))
          .thenReturn(false);
      when(productRepository.save(any(Product.class)))
          .thenAnswer(
              invocation -> {
                Product p = invocation.getArgument(0);
                return new Product(
                    1L,
                    p.guildId(),
                    p.name(),
                    p.description(),
                    p.rewardType(),
                    p.rewardAmount(),
                    p.currencyPrice(),
                    p.fiatPriceTwd(),
                    p.backendApiUrl(),
                    p.autoCreateEscortOrder(),
                    p.escortOptionCode(),
                    p.createdAt(),
                    p.updatedAt());
              });

      Result<Product, DomainError> result =
          productService.createProduct(
              TEST_GUILD_ID,
              "Backend Product",
              "desc",
              null,
              null,
              300L,
              null,
              "HTTPS://backend.example.com/fulfill",
              false,
              null);

      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().backendApiUrl())
          .isEqualTo("https://backend.example.com/fulfill");
    }

    @Test
    @DisplayName("should reject localhost and private backend API target")
    void shouldRejectLocalOrPrivateBackendApiUrl() {
      when(productRepository.existsByGuildIdAndName(TEST_GUILD_ID, "Unsafe Backend"))
          .thenReturn(false);

      Result<Product, DomainError> result =
          productService.createProduct(
              TEST_GUILD_ID,
              "Unsafe Backend",
              "desc",
              null,
              null,
              300L,
              null,
              "http://127.0.0.1/internal",
              false,
              null);

      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().message()).contains("localhost 或內網位址");
    }

    @Test
    @DisplayName("should reject IPv6 ULA backend API target")
    void shouldRejectIpv6UniqueLocalBackendApiUrl() {
      when(productRepository.existsByGuildIdAndName(TEST_GUILD_ID, "Unsafe IPv6 Backend"))
          .thenReturn(false);

      Result<Product, DomainError> result =
          productService.createProduct(
              TEST_GUILD_ID,
              "Unsafe IPv6 Backend",
              "desc",
              null,
              null,
              300L,
              null,
              "http://[fc00::1]/internal",
              false,
              null);

      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().message()).contains("localhost 或內網位址");
    }

    @Test
    @DisplayName("should handle persistence failure on create")
    void shouldHandlePersistenceFailureOnCreate() {
      // Given
      when(productRepository.existsByGuildIdAndName(anyLong(), anyString())).thenReturn(false);
      when(productRepository.save(any(Product.class)))
          .thenThrow(new RuntimeException("Database error"));

      // When
      Result<Product, DomainError> result =
          productService.createProduct(TEST_GUILD_ID, "Test", "desc", null, null, null);

      // Then
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().category()).isEqualTo(DomainError.Category.PERSISTENCE_FAILURE);
    }

    @Test
    @DisplayName("should publish event on successful create")
    void shouldPublishEventOnSuccessfulCreate() {
      // Given
      when(productRepository.existsByGuildIdAndName(TEST_GUILD_ID, "Test Product"))
          .thenReturn(false);
      when(productRepository.save(any(Product.class)))
          .thenAnswer(
              invocation -> {
                Product p = invocation.getArgument(0);
                return new Product(
                    1L,
                    p.guildId(),
                    p.name(),
                    p.description(),
                    p.rewardType(),
                    p.rewardAmount(),
                    p.currencyPrice(),
                    p.createdAt(),
                    p.updatedAt());
              });

      // When
      productService.createProduct(TEST_GUILD_ID, "Test Product", "desc", null, null, null);

      // Then
      verify(eventPublisher).publish(any(ltdjms.discord.shared.events.ProductChangedEvent.class));
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
      Product existing = new Product(1L, TEST_GUILD_ID, "舊名稱", "舊描述", null, null, null, now, now);

      when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
      when(productRepository.existsByGuildIdAndNameExcludingId(TEST_GUILD_ID, "新名稱", 1L))
          .thenReturn(false);
      when(productRepository.update(any(Product.class))).thenAnswer(i -> i.getArgument(0));

      // When
      Result<Product, DomainError> result =
          productService.updateProduct(1L, "新名稱", "新描述", Product.RewardType.CURRENCY, 500L, null);

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
      Result<Product, DomainError> result =
          productService.updateProduct(999L, "Test", "desc", null, null, null);

      // Then
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().message()).contains("找不到");
    }

    @Test
    @DisplayName("should reject update with duplicate name")
    void shouldRejectUpdateWithDuplicateName() {
      // Given
      Instant now = Instant.now();
      Product existing = new Product(1L, TEST_GUILD_ID, "舊名稱", null, null, null, null, now, now);

      when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
      when(productRepository.existsByGuildIdAndNameExcludingId(TEST_GUILD_ID, "已存在", 1L))
          .thenReturn(true);

      // When
      Result<Product, DomainError> result =
          productService.updateProduct(1L, "已存在", null, null, null, null);

      // Then
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().message()).contains("已存在");
    }

    @Test
    @DisplayName("should reject update with blank name")
    void shouldRejectUpdateWithBlankName() {
      // When
      Result<Product, DomainError> result =
          productService.updateProduct(1L, "   ", "desc", null, null, null);

      // Then
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().message()).contains("空");
    }

    @Test
    @DisplayName("should reject update with name exceeding 100 characters")
    void shouldRejectUpdateWithNameExceeding100Characters() {
      // When - name length check happens before repository lookup
      String longName = "a".repeat(101);
      Result<Product, DomainError> result =
          productService.updateProduct(1L, longName, "desc", null, null, null);

      // Then
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().message()).contains("不能超過 100 個字元");
    }

    @Test
    @DisplayName("should reject update with inconsistent reward settings")
    void shouldRejectUpdateWithInconsistentRewardSettings() {
      // Given
      Instant now = Instant.now();
      Product existing = new Product(1L, TEST_GUILD_ID, "Old", null, null, null, null, now, now);

      when(productRepository.findById(1L)).thenReturn(Optional.of(existing));

      // When
      Result<Product, DomainError> result =
          productService.updateProduct(1L, "New", "desc", Product.RewardType.CURRENCY, null, null);

      // Then
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().message()).contains("同時");
    }

    @Test
    @DisplayName("should reject update with zero or negative currency price")
    void shouldRejectUpdateWithZeroOrNegativeCurrencyPrice() {
      // Given
      Instant now = Instant.now();
      Product existing = new Product(1L, TEST_GUILD_ID, "Old", null, null, null, null, now, now);

      when(productRepository.findById(1L)).thenReturn(Optional.of(existing));

      // When
      Result<Product, DomainError> result =
          productService.updateProduct(1L, "New", "desc", null, null, 0L);

      // Then
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().message()).contains("貨幣價格必須大於 0");
    }

    @Test
    @DisplayName("should handle persistence failure on update")
    void shouldHandlePersistenceFailureOnUpdate() {
      // Given
      Instant now = Instant.now();
      Product existing = new Product(1L, TEST_GUILD_ID, "Old", null, null, null, null, now, now);

      when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
      when(productRepository.existsByGuildIdAndNameExcludingId(anyLong(), anyString(), anyLong()))
          .thenReturn(false);
      when(productRepository.update(any(Product.class)))
          .thenThrow(new RuntimeException("Database error"));

      // When
      Result<Product, DomainError> result =
          productService.updateProduct(1L, "New", "desc", null, null, null);

      // Then
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().category()).isEqualTo(DomainError.Category.PERSISTENCE_FAILURE);
    }

    @Test
    @DisplayName("should publish event on successful update")
    void shouldPublishEventOnSuccessfulUpdate() {
      // Given
      Instant now = Instant.now();
      Product existing = new Product(1L, TEST_GUILD_ID, "Old", null, null, null, null, now, now);

      when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
      when(productRepository.existsByGuildIdAndNameExcludingId(TEST_GUILD_ID, "New", 1L))
          .thenReturn(false);
      when(productRepository.update(any(Product.class))).thenAnswer(i -> i.getArgument(0));

      // When
      productService.updateProduct(1L, "New", "desc", null, null, null);

      // Then
      verify(eventPublisher).publish(any(ltdjms.discord.shared.events.ProductChangedEvent.class));
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
      Product existing = new Product(1L, TEST_GUILD_ID, "Test", null, null, null, null, now, now);

      when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
      when(redemptionCodeRepository.invalidateByProductId(1L)).thenReturn(0);
      when(productRepository.deleteById(1L)).thenReturn(true);

      // When
      Result<Unit, DomainError> result = productService.deleteProduct(1L);

      // Then
      assertThat(result.isOk()).isTrue();
      verify(redemptionCodeRepository).invalidateByProductId(1L);
      verify(productRepository).deleteById(1L);
    }

    @Test
    @DisplayName("should invalidate associated codes before deleting product")
    void shouldInvalidateAssociatedCodesBeforeDeletingProduct() {
      // Given
      Instant now = Instant.now();
      Product existing = new Product(1L, TEST_GUILD_ID, "Test", null, null, null, null, now, now);

      when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
      when(redemptionCodeRepository.invalidateByProductId(1L)).thenReturn(5);
      when(productRepository.deleteById(1L)).thenReturn(true);

      // When
      Result<Unit, DomainError> result = productService.deleteProduct(1L);

      // Then
      assertThat(result.isOk()).isTrue();
      verify(redemptionCodeRepository).invalidateByProductId(1L);
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
      verify(redemptionCodeRepository, never()).invalidateByProductId(anyLong());
    }

    @Test
    @DisplayName("should handle delete when repository returns false")
    void shouldHandleDeleteWhenRepositoryReturnsFalse() {
      // Given
      Instant now = Instant.now();
      Product existing = new Product(1L, TEST_GUILD_ID, "Test", null, null, null, null, now, now);

      when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
      when(redemptionCodeRepository.invalidateByProductId(1L)).thenReturn(0);
      when(productRepository.deleteById(1L)).thenReturn(false);

      // When
      Result<Unit, DomainError> result = productService.deleteProduct(1L);

      // Then
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().message()).contains("刪除商品失敗");
    }

    @Test
    @DisplayName("should handle persistence failure on delete")
    void shouldHandlePersistenceFailureOnDelete() {
      // Given
      Instant now = Instant.now();
      Product existing = new Product(1L, TEST_GUILD_ID, "Test", null, null, null, null, now, now);

      when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
      when(redemptionCodeRepository.invalidateByProductId(1L))
          .thenThrow(new RuntimeException("Database error"));

      // When
      Result<Unit, DomainError> result = productService.deleteProduct(1L);

      // Then
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().category()).isEqualTo(DomainError.Category.PERSISTENCE_FAILURE);
    }

    @Test
    @DisplayName("should publish event on successful delete")
    void shouldPublishEventOnSuccessfulDelete() {
      // Given
      Instant now = Instant.now();
      Product existing = new Product(1L, TEST_GUILD_ID, "Test", null, null, null, null, now, now);

      when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
      when(redemptionCodeRepository.invalidateByProductId(1L)).thenReturn(0);
      when(productRepository.deleteById(1L)).thenReturn(true);

      // When
      productService.deleteProduct(1L);

      // Then
      verify(eventPublisher).publish(any(ltdjms.discord.shared.events.ProductChangedEvent.class));
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
      List<Product> products =
          List.of(
              new Product(1L, TEST_GUILD_ID, "Product 1", null, null, null, null, now, now),
              new Product(2L, TEST_GUILD_ID, "Product 2", null, null, null, null, now, now));
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

    @Test
    @DisplayName("should get product by ID when exists")
    void shouldGetProductByIdWhenExists() {
      // Given
      Instant now = Instant.now();
      Product product = new Product(1L, TEST_GUILD_ID, "Test", null, null, null, null, now, now);
      when(productRepository.findById(1L)).thenReturn(Optional.of(product));

      // When
      Optional<Product> result = productService.getProduct(1L);

      // Then
      assertThat(result).isPresent();
      assertThat(result.get().id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("should return empty when product not found")
    void shouldReturnEmptyWhenProductNotFound() {
      // Given
      when(productRepository.findById(999L)).thenReturn(Optional.empty());

      // When
      Optional<Product> result = productService.getProduct(999L);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should get products for purchase filtering by currency price")
    void shouldGetProductsForPurchaseFilteringByCurrencyPrice() {
      // Given
      Instant now = Instant.now();
      Product productWithPrice =
          new Product(1L, TEST_GUILD_ID, "Paid", null, null, null, 500L, now, now);
      Product productWithoutPrice =
          new Product(2L, TEST_GUILD_ID, "Free", null, null, null, null, now, now);

      when(productRepository.findByGuildId(TEST_GUILD_ID))
          .thenReturn(List.of(productWithPrice, productWithoutPrice));

      // When
      List<Product> result = productService.getProductsForPurchase(TEST_GUILD_ID);

      // Then
      assertThat(result).hasSize(1);
      assertThat(result.get(0).id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("should return empty list when no products have currency price")
    void shouldReturnEmptyListWhenNoProductsHaveCurrencyPrice() {
      // Given
      Instant now = Instant.now();
      Product product = new Product(1L, TEST_GUILD_ID, "Free", null, null, null, null, now, now);

      when(productRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(List.of(product));

      // When
      List<Product> result = productService.getProductsForPurchase(TEST_GUILD_ID);

      // Then
      assertThat(result).isEmpty();
    }
  }
}
