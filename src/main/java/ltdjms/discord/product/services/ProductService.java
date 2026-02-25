package ltdjms.discord.product.services;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.product.domain.Product;
import ltdjms.discord.product.domain.ProductRepository;
import ltdjms.discord.product.persistence.JdbcProductRepository;
import ltdjms.discord.redemption.domain.RedemptionCodeRepository;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.Unit;
import ltdjms.discord.shared.events.DomainEventPublisher;
import ltdjms.discord.shared.events.ProductChangedEvent;

/** Service layer for product management operations. */
public class ProductService {

  private static final Logger LOG = LoggerFactory.getLogger(ProductService.class);

  private final ProductRepository productRepository;
  private final RedemptionCodeRepository redemptionCodeRepository;
  private final DomainEventPublisher eventPublisher;

  public ProductService(
      ProductRepository productRepository,
      RedemptionCodeRepository redemptionCodeRepository,
      DomainEventPublisher eventPublisher) {
    this.productRepository = productRepository;
    this.redemptionCodeRepository = redemptionCodeRepository;
    this.eventPublisher = eventPublisher;
  }

  /**
   * Creates a new product.
   *
   * @param guildId the Discord guild ID
   * @param name the product name
   * @param description the product description (can be null)
   * @param rewardType the type of reward (can be null for no automatic reward)
   * @param rewardAmount the reward amount (can be null for no automatic reward)
   * @param currencyPrice the currency price for direct purchase (can be null for currency purchase
   *     not available)
   * @param fiatPriceTwd the product actual value in TWD (can be null for not configured)
   * @return Result containing the created product or an error
   */
  public Result<Product, DomainError> createProduct(
      long guildId,
      String name,
      String description,
      Product.RewardType rewardType,
      Long rewardAmount,
      Long currencyPrice) {
    return createProduct(guildId, name, description, rewardType, rewardAmount, currencyPrice, null);
  }

  /**
   * Creates a new product.
   *
   * @param guildId the Discord guild ID
   * @param name the product name
   * @param description the product description (can be null)
   * @param rewardType the type of reward (can be null for no automatic reward)
   * @param rewardAmount the reward amount (can be null for no automatic reward)
   * @param currencyPrice the currency price for direct purchase (can be null for currency purchase
   *     not available)
   * @param fiatPriceTwd the product actual value in TWD (can be null for not configured)
   * @return Result containing the created product or an error
   */
  public Result<Product, DomainError> createProduct(
      long guildId,
      String name,
      String description,
      Product.RewardType rewardType,
      Long rewardAmount,
      Long currencyPrice,
      Long fiatPriceTwd) {

    // Validate name
    if (name == null || name.isBlank()) {
      return Result.err(DomainError.invalidInput("商品名稱不能為空"));
    }

    name = name.trim();
    if (name.length() > 100) {
      return Result.err(DomainError.invalidInput("商品名稱不能超過 100 個字元"));
    }

    // Check for duplicate name
    if (productRepository.existsByGuildIdAndName(guildId, name)) {
      return Result.err(DomainError.invalidInput("商品名稱已存在"));
    }

    // Validate reward consistency
    if ((rewardType == null) != (rewardAmount == null)) {
      return Result.err(DomainError.invalidInput("獎勵類型和獎勵金額必須同時設定或同時為空"));
    }

    if (rewardAmount != null && rewardAmount <= 0) {
      return Result.err(DomainError.invalidInput("獎勵金額必須大於 0"));
    }

    // Validate currency price
    if (currencyPrice != null && currencyPrice <= 0) {
      return Result.err(DomainError.invalidInput("貨幣價格必須大於 0"));
    }
    if (fiatPriceTwd != null && fiatPriceTwd <= 0) {
      return Result.err(DomainError.invalidInput("新台幣實際價值必須大於 0"));
    }

    try {
      Product product =
          Product.create(
              guildId, name, description, rewardType, rewardAmount, currencyPrice, fiatPriceTwd);
      Product saved = productRepository.save(product);
      LOG.info(
          "Created product: guildId={}, name={}, rewardType={}, rewardAmount={}, currencyPrice={},"
              + " fiatPriceTwd={}",
          guildId,
          name,
          rewardType,
          rewardAmount,
          currencyPrice,
          fiatPriceTwd);

      // Publish event after successful save
      eventPublisher.publish(
          new ProductChangedEvent(guildId, saved.id(), ProductChangedEvent.OperationType.CREATED));

      return Result.ok(saved);
    } catch (Exception e) {
      LOG.error("Failed to create product for guildId={}, name={}", guildId, name, e);
      return Result.err(DomainError.persistenceFailure("建立商品失敗", e));
    }
  }

  /**
   * Updates an existing product.
   *
   * @param productId the product ID
   * @param name the new product name
   * @param description the new description
   * @param rewardType the new reward type
   * @param rewardAmount the new reward amount
   * @param currencyPrice the new currency price
   * @param fiatPriceTwd the new fiat value in TWD
   * @return Result containing the updated product or an error
   */
  public Result<Product, DomainError> updateProduct(
      long productId,
      String name,
      String description,
      Product.RewardType rewardType,
      Long rewardAmount,
      Long currencyPrice) {
    Long fiatPriceTwd =
        productRepository.findById(productId).map(Product::fiatPriceTwd).orElse(null);
    return updateProduct(
        productId, name, description, rewardType, rewardAmount, currencyPrice, fiatPriceTwd);
  }

  /**
   * Updates an existing product.
   *
   * @param productId the product ID
   * @param name the new product name
   * @param description the new description
   * @param rewardType the new reward type
   * @param rewardAmount the new reward amount
   * @param currencyPrice the new currency price
   * @param fiatPriceTwd the new fiat value in TWD
   * @return Result containing the updated product or an error
   */
  public Result<Product, DomainError> updateProduct(
      long productId,
      String name,
      String description,
      Product.RewardType rewardType,
      Long rewardAmount,
      Long currencyPrice,
      Long fiatPriceTwd) {

    // Validate name
    if (name == null || name.isBlank()) {
      return Result.err(DomainError.invalidInput("商品名稱不能為空"));
    }

    name = name.trim();
    if (name.length() > 100) {
      return Result.err(DomainError.invalidInput("商品名稱不能超過 100 個字元"));
    }

    // Find existing product
    Optional<Product> existingOpt = productRepository.findById(productId);
    if (existingOpt.isEmpty()) {
      return Result.err(DomainError.invalidInput("找不到商品"));
    }

    Product existing = existingOpt.get();

    // Check for duplicate name (excluding current product)
    if (productRepository.existsByGuildIdAndNameExcludingId(existing.guildId(), name, productId)) {
      return Result.err(DomainError.invalidInput("商品名稱已存在"));
    }

    // Validate reward consistency
    if ((rewardType == null) != (rewardAmount == null)) {
      return Result.err(DomainError.invalidInput("獎勵類型和獎勵金額必須同時設定或同時為空"));
    }

    if (rewardAmount != null && rewardAmount <= 0) {
      return Result.err(DomainError.invalidInput("獎勵金額必須大於 0"));
    }

    // Validate currency price
    if (currencyPrice != null && currencyPrice <= 0) {
      return Result.err(DomainError.invalidInput("貨幣價格必須大於 0"));
    }
    if (fiatPriceTwd != null && fiatPriceTwd <= 0) {
      return Result.err(DomainError.invalidInput("新台幣實際價值必須大於 0"));
    }

    try {
      Product updated =
          existing.withUpdatedDetails(
              name, description, rewardType, rewardAmount, currencyPrice, fiatPriceTwd);
      Product saved = productRepository.update(updated);
      LOG.info("Updated product: id={}, name={}", productId, name);

      // Publish event after successful update
      eventPublisher.publish(
          new ProductChangedEvent(
              existing.guildId(), productId, ProductChangedEvent.OperationType.UPDATED));

      return Result.ok(saved);
    } catch (Exception e) {
      LOG.error("Failed to update product id={}", productId, e);
      return Result.err(DomainError.persistenceFailure("更新商品失敗", e));
    }
  }

  /**
   * Deletes a product by ID. Associated redemption codes will be marked as invalidated and
   * preserved.
   *
   * @param productId the product ID
   * @return Result indicating success or an error
   */
  public Result<Unit, DomainError> deleteProduct(long productId) {
    Optional<Product> existingOpt = productRepository.findById(productId);
    if (existingOpt.isEmpty()) {
      return Result.err(DomainError.invalidInput("找不到商品"));
    }

    Product existing = existingOpt.get();

    try {
      // First, invalidate all associated redemption codes
      int invalidatedCount = redemptionCodeRepository.invalidateByProductId(productId);
      if (invalidatedCount > 0) {
        LOG.info(
            "Invalidated {} redemption codes before deleting product: id={}",
            invalidatedCount,
            productId);
      }

      // Then delete the product
      boolean deleted = productRepository.deleteById(productId);
      if (deleted) {
        LOG.info("Deleted product: id={}", productId);

        // Publish event after successful delete
        eventPublisher.publish(
            new ProductChangedEvent(
                existing.guildId(), productId, ProductChangedEvent.OperationType.DELETED));

        return Result.okVoid();
      } else {
        return Result.err(DomainError.invalidInput("刪除商品失敗"));
      }
    } catch (Exception e) {
      LOG.error("Failed to delete product id={}", productId, e);
      return Result.err(DomainError.persistenceFailure("刪除商品失敗", e));
    }
  }

  /**
   * Gets a product by ID.
   *
   * @param productId the product ID
   * @return Optional containing the product if found
   */
  public Optional<Product> getProduct(long productId) {
    return productRepository.findById(productId);
  }

  /**
   * Gets all products for a guild.
   *
   * @param guildId the Discord guild ID
   * @return a list of products
   */
  public List<Product> getProducts(long guildId) {
    return productRepository.findByGuildId(guildId);
  }

  /**
   * Gets the count of products for a guild.
   *
   * @param guildId the Discord guild ID
   * @return the number of products
   */
  public long getProductCount(long guildId) {
    return productRepository.countByGuildId(guildId);
  }

  /**
   * Gets products available for purchase with currency. Only products with a currency price set
   * (greater than 0) are returned.
   *
   * @param guildId the Discord guild ID
   * @return a list of products that can be purchased with currency
   */
  public List<Product> getProductsForPurchase(long guildId) {
    if (productRepository instanceof JdbcProductRepository jdbcRepo) {
      return jdbcRepo.findByGuildIdWithCurrencyPrice(guildId);
    }
    // Fallback to filtering all products if not using JdbcProductRepository
    return productRepository.findByGuildId(guildId).stream()
        .filter(Product::hasCurrencyPrice)
        .toList();
  }

  /**
   * Gets products that are limited to fiat payment only.
   *
   * @param guildId the Discord guild ID
   * @return a list of fiat-only products
   */
  public List<Product> getFiatOnlyProducts(long guildId) {
    if (productRepository instanceof JdbcProductRepository jdbcRepo) {
      return jdbcRepo.findFiatOnlyByGuildId(guildId);
    }
    return productRepository.findByGuildId(guildId).stream().filter(Product::isFiatOnly).toList();
  }
}
