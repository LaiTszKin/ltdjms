package ltdjms.discord.shop.services;

import ltdjms.discord.product.domain.Product;
import ltdjms.discord.product.domain.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ShopService.
 */
@ExtendWith(MockitoExtension.class)
class ShopServiceTest {

    private static final long TEST_GUILD_ID = 123456789012345678L;
    private static final int PAGE_SIZE = 5;

    @Mock
    private ProductRepository productRepository;

    private ShopService shopService;

    @BeforeEach
    void setUp() {
        shopService = new ShopService(productRepository, PAGE_SIZE);
    }

    private Product createProduct(long id, String name, String description, Product.RewardType rewardType, Long rewardAmount) {
        Instant now = Instant.now();
        return new Product(id, TEST_GUILD_ID, name, description, rewardType, rewardAmount, now, now);
    }

    @Nested
    @DisplayName("getShopPage")
    class GetShopPageTests {

        @Test
        @DisplayName("should return first page with products")
        void shouldReturnFirstPageWithProducts() {
            // Given
            List<Product> products = List.of(
                    createProduct(1L, "Product 1", "Desc 1", Product.RewardType.CURRENCY, 100L),
                    createProduct(2L, "Product 2", "Desc 2", Product.RewardType.TOKEN, 50L)
            );
            when(productRepository.countByGuildId(TEST_GUILD_ID)).thenReturn(2L);
            when(productRepository.findByGuildIdPaginated(TEST_GUILD_ID, 0, PAGE_SIZE)).thenReturn(products);

            // When
            ShopService.ShopPage result = shopService.getShopPage(TEST_GUILD_ID, 0);

            // Then
            assertThat(result.isEmpty()).isFalse();
            assertThat(result.products()).hasSize(2);
            assertThat(result.currentPage()).isEqualTo(1);
            assertThat(result.totalPages()).isEqualTo(1);
        }

        @Test
        @DisplayName("should return empty page when no products")
        void shouldReturnEmptyPageWhenNoProducts() {
            // Given
            when(productRepository.countByGuildId(TEST_GUILD_ID)).thenReturn(0L);
            when(productRepository.findByGuildIdPaginated(TEST_GUILD_ID, 0, PAGE_SIZE)).thenReturn(List.of());

            // When
            ShopService.ShopPage result = shopService.getShopPage(TEST_GUILD_ID, 0);

            // Then
            assertThat(result.isEmpty()).isTrue();
            assertThat(result.products()).isEmpty();
            assertThat(result.currentPage()).isEqualTo(1);
            assertThat(result.totalPages()).isEqualTo(0);
        }

        @Test
        @DisplayName("should calculate correct pagination for many products")
        void shouldCalculateCorrectPaginationForManyProducts() {
            // Given - 20 products, page size 5 = 4 pages
            when(productRepository.countByGuildId(TEST_GUILD_ID)).thenReturn(20L);
            when(productRepository.findByGuildIdPaginated(TEST_GUILD_ID, 0, PAGE_SIZE))
                    .thenReturn(List.of(createProduct(1L, "P1", null, null, null)));

            // When
            ShopService.ShopPage result = shopService.getShopPage(TEST_GUILD_ID, 0);

            // Then
            assertThat(result.totalPages()).isEqualTo(4);
            assertThat(result.currentPage()).isEqualTo(1);
            assertThat(result.hasNextPage()).isTrue();
            assertThat(result.hasPreviousPage()).isFalse();
        }

        @Test
        @DisplayName("should return last page when page number exceeds total pages")
        void shouldReturnLastPageWhenPageNumberExceedsTotalPages() {
            // Given - 3 products, page size 5 = 1 page
            when(productRepository.countByGuildId(TEST_GUILD_ID)).thenReturn(3L);
            when(productRepository.findByGuildIdPaginated(TEST_GUILD_ID, 0, PAGE_SIZE))
                    .thenReturn(List.of(
                            createProduct(1L, "P1", null, null, null),
                            createProduct(2L, "P2", null, null, null),
                            createProduct(3L, "P3", null, null, null)
                    ));

            // When - requesting page 5 (beyond actual pages)
            ShopService.ShopPage result = shopService.getShopPage(TEST_GUILD_ID, 4);

            // Then - should return page 1 (clamped to valid range)
            assertThat(result.currentPage()).isEqualTo(1);
            assertThat(result.totalPages()).isEqualTo(1);
        }

        @Test
        @DisplayName("should return specific page correctly")
        void shouldReturnSpecificPageCorrectly() {
            // Given - page 2 of 4 pages
            when(productRepository.countByGuildId(TEST_GUILD_ID)).thenReturn(20L);
            when(productRepository.findByGuildIdPaginated(TEST_GUILD_ID, 1, PAGE_SIZE))
                    .thenReturn(List.of(
                            createProduct(6L, "Product 6", null, null, null),
                            createProduct(7L, "Product 7", null, null, null)
                    ));

            // When - requesting page 2 (1-based), internally converted to 0-based
            ShopService.ShopPage result = shopService.getShopPage(TEST_GUILD_ID, 1);

            // Then
            assertThat(result.currentPage()).isEqualTo(2);
            assertThat(result.totalPages()).isEqualTo(4);
            assertThat(result.hasPreviousPage()).isTrue();
            assertThat(result.hasNextPage()).isTrue();
            verify(productRepository).findByGuildIdPaginated(TEST_GUILD_ID, 1, PAGE_SIZE);
        }
    }

    @Nested
    @DisplayName("hasProducts")
    class HasProductsTests {

        @Test
        @DisplayName("should return true when products exist")
        void shouldReturnTrueWhenProductsExist() {
            // Given
            when(productRepository.countByGuildId(TEST_GUILD_ID)).thenReturn(5L);

            // When
            boolean result = shopService.hasProducts(TEST_GUILD_ID);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when no products")
        void shouldReturnFalseWhenNoProducts() {
            // Given
            when(productRepository.countByGuildId(TEST_GUILD_ID)).thenReturn(0L);

            // When
            boolean result = shopService.hasProducts(TEST_GUILD_ID);

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("getProductCount")
    class GetProductCountTests {

        @Test
        @DisplayName("should return product count from repository")
        void shouldReturnProductCountFromRepository() {
            // Given
            when(productRepository.countByGuildId(TEST_GUILD_ID)).thenReturn(10L);

            // When
            long result = shopService.getProductCount(TEST_GUILD_ID);

            // Then
            assertThat(result).isEqualTo(10L);
        }
    }

    @Nested
    @DisplayName("ShopPage")
    class ShopPageTests {

        @Test
        @DisplayName("should format page indicator for single page")
        void shouldFormatPageIndicatorForSinglePage() {
            // Given
            ShopService.ShopPage page = new ShopService.ShopPage(
                    List.of(createProduct(1L, "P1", null, null, null)),
                    1,
                    1
            );

            // Then
            assertThat(page.formatPageIndicator()).isEqualTo("共 1 個商品");
        }

        @Test
        @DisplayName("should format page indicator for multiple pages")
        void shouldFormatPageIndicatorForMultiplePages() {
            // Given
            ShopService.ShopPage page = new ShopService.ShopPage(
                    List.of(createProduct(1L, "P1", null, null, null)),
                    2,
                    4
            );

            // Then
            assertThat(page.formatPageIndicator()).isEqualTo("第 2 / 4 頁");
        }

        @Test
        @DisplayName("should correctly identify page boundaries")
        void shouldCorrectlyIdentifyPageBoundaries() {
            // First page
            ShopService.ShopPage firstPage = new ShopService.ShopPage(List.of(), 1, 4);
            assertThat(firstPage.hasPreviousPage()).isFalse();
            assertThat(firstPage.hasNextPage()).isTrue();

            // Middle page
            ShopService.ShopPage middlePage = new ShopService.ShopPage(List.of(), 2, 4);
            assertThat(middlePage.hasPreviousPage()).isTrue();
            assertThat(middlePage.hasNextPage()).isTrue();

            // Last page
            ShopService.ShopPage lastPage = new ShopService.ShopPage(List.of(), 4, 4);
            assertThat(lastPage.hasPreviousPage()).isTrue();
            assertThat(lastPage.hasNextPage()).isFalse();
        }
    }
}
