package ltdjms.discord.shop.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.membership.services.EscortPriceQuote;
import ltdjms.discord.membership.services.MembershipPricingService;
import ltdjms.discord.product.domain.EscortProductRules;
import ltdjms.discord.product.domain.Product;
import ltdjms.discord.product.domain.ProductRepository;
import ltdjms.discord.shop.services.ShopService.ShopPage;

/** Service for shop-related operations. */
public class ShopService {

  private static final Logger LOG = LoggerFactory.getLogger(ShopService.class);

  private final ProductRepository productRepository;
  private final MembershipPricingService membershipPricingService;
  private final int pageSize;

  public ShopService(ProductRepository productRepository) {
    this(productRepository, null, ShopView.getPageSize());
  }

  public ShopService(ProductRepository productRepository, int pageSize) {
    this(productRepository, null, pageSize);
  }

  public ShopService(
      ProductRepository productRepository, MembershipPricingService membershipPricingService) {
    this(productRepository, membershipPricingService, ShopView.getPageSize());
  }

  public ShopService(
      ProductRepository productRepository,
      MembershipPricingService membershipPricingService,
      int pageSize) {
    this.productRepository = productRepository;
    this.membershipPricingService = membershipPricingService;
    this.pageSize = pageSize;
  }

  /** Quotes escort pricing with membership discounts for shop UI flows. */
  public EscortPriceQuote quoteEscortPrice(long userId, Product product, long guildId) {
    if (membershipPricingService == null) {
      throw new IllegalStateException("MembershipPricingService is not configured for ShopService");
    }
    return membershipPricingService.quoteEscortPrice(userId, product, guildId);
  }

  /**
   * Batch-quotes escort-linked products for shop list and menu rendering.
   *
   * @return map keyed by product id; non-escort products omitted; empty when pricing unavailable
   */
  public Map<Long, EscortPriceQuote> quoteEscortPrices(
      long userId, List<Product> products, long guildId) {
    if (membershipPricingService == null || products == null || products.isEmpty()) {
      return Map.of();
    }

    Map<Long, EscortPriceQuote> quotes = new HashMap<>();
    for (Product product : products) {
      if (!EscortProductRules.isEscortLinked(product)) {
        continue;
      }
      try {
        quotes.put(product.id(), quoteEscortPrice(userId, product, guildId));
      } catch (Exception e) {
        LOG.warn(
            "Failed to quote escort price for productId={}, userId={}: {}",
            product.id(),
            userId,
            e.getMessage());
      }
    }
    return quotes;
  }

  /**
   * Gets a page of products for the shop.
   *
   * @param guildId the Discord guild ID
   * @param page zero-based page number
   * @return the shop page containing products and pagination info
   */
  public ShopPage getShopPage(long guildId, int page) {
    LOG.debug("Getting shop page for guildId={}, page={}, pageSize={}", guildId, page, pageSize);

    long totalCount = productRepository.countByGuildId(guildId);
    int totalPages = (int) Math.ceil((double) totalCount / pageSize);

    // Ensure page is within valid range
    int validPage = Math.max(0, Math.min(page, totalPages - 1));

    List<Product> products = productRepository.findByGuildIdPaginated(guildId, validPage, pageSize);

    LOG.debug(
        "Shop page {}: found {} products, totalPages={}", validPage, products.size(), totalPages);

    return new ShopPage(products, validPage + 1, totalPages);
  }

  /**
   * Searches products by name keyword with pagination.
   *
   * @param guildId the Discord guild ID
   * @param keyword the search keyword
   * @param page zero-based page number
   * @return the search result page containing matching products and pagination info
   */
  public ShopPage searchProducts(long guildId, String keyword, int page) {
    if (keyword == null || keyword.isBlank()) {
      LOG.debug("Search called with empty keyword for guildId={}", guildId);
      return new ShopPage(List.of(), 1, 0);
    }

    LOG.debug(
        "Searching products for guildId={}, keyword={}, page={}, pageSize={}",
        guildId,
        keyword,
        page,
        pageSize);

    long totalCount = productRepository.countByGuildIdAndNameContaining(guildId, keyword);
    int totalPages = (int) Math.ceil((double) totalCount / pageSize);

    // Ensure page is within valid range
    int validPage = Math.max(0, Math.min(page, totalPages - 1));

    List<Product> products =
        productRepository.findByGuildIdAndNameContaining(guildId, keyword, validPage, pageSize);

    LOG.debug(
        "Search page {}: found {} products, totalPages={}", validPage, products.size(), totalPages);

    return new ShopPage(products, validPage + 1, totalPages);
  }

  /** Gets the total number of products for a guild. */
  public long getProductCount(long guildId) {
    return productRepository.countByGuildId(guildId);
  }

  /** Checks if the shop has any products. */
  public boolean hasProducts(long guildId) {
    return productRepository.countByGuildId(guildId) > 0;
  }

  /** Represents a page of shop products. */
  public record ShopPage(List<Product> products, int currentPage, int totalPages) {
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
