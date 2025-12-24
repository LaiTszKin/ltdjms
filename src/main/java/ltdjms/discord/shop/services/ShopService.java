package ltdjms.discord.shop.services;

import ltdjms.discord.product.domain.Product;
import ltdjms.discord.product.domain.ProductRepository;
import ltdjms.discord.shop.services.ShopService.ShopPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Service for shop-related operations.
 */
public class ShopService {

    private static final Logger LOG = LoggerFactory.getLogger(ShopService.class);

    private final ProductRepository productRepository;
    private final int pageSize;

    public ShopService(ProductRepository productRepository) {
        this(productRepository, ShopView.getPageSize());
    }

    public ShopService(ProductRepository productRepository, int pageSize) {
        this.productRepository = productRepository;
        this.pageSize = pageSize;
    }

    /**
     * Gets a page of products for the shop.
     *
     * @param guildId the Discord guild ID
     * @param page    zero-based page number
     * @return the shop page containing products and pagination info
     */
    public ShopPage getShopPage(long guildId, int page) {
        LOG.debug("Getting shop page for guildId={}, page={}, pageSize={}", guildId, page, pageSize);

        long totalCount = productRepository.countByGuildId(guildId);
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);

        // Ensure page is within valid range
        int validPage = Math.max(0, Math.min(page, totalPages - 1));

        List<Product> products = productRepository.findByGuildIdPaginated(guildId, validPage, pageSize);

        LOG.debug("Shop page {}: found {} products, totalPages={}", validPage, products.size(), totalPages);

        return new ShopPage(products, validPage + 1, totalPages);
    }

    /**
     * Gets the total number of products for a guild.
     */
    public long getProductCount(long guildId) {
        return productRepository.countByGuildId(guildId);
    }

    /**
     * Checks if the shop has any products.
     */
    public boolean hasProducts(long guildId) {
        return productRepository.countByGuildId(guildId) > 0;
    }

    /**
     * Represents a page of shop products.
     */
    public record ShopPage(
            List<Product> products,
            int currentPage,
            int totalPages
    ) {
        public boolean isEmpty() {
            return products.isEmpty();
        }

        public boolean hasPreviousPage() {
            return currentPage > 1;
        }

        public boolean hasNextPage() {
            return currentPage < totalPages;
        }

        public String formatPageIndicator() {
            if (totalPages <= 1) {
                return "共 " + products.size() + " 個商品";
            }
            return "第 " + currentPage + " / " + totalPages + " 頁";
        }
    }
}
