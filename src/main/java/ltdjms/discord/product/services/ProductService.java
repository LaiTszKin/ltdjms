package ltdjms.discord.product.services;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.product.domain.EscortOrderOptionCatalog;
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

  public Result<Product, DomainError> createProduct(
      long guildId,
      String name,
      String description,
      Product.RewardType rewardType,
      Long rewardAmount,
      Long currencyPrice) {
    return createProduct(guildId, name, description, rewardType, rewardAmount, currencyPrice, null);
  }

  public Result<Product, DomainError> createProduct(
      long guildId,
      String name,
      String description,
      Product.RewardType rewardType,
      Long rewardAmount,
      Long currencyPrice,
      Long fiatPriceTwd) {
    return createProduct(
        guildId,
        name,
        description,
        rewardType,
        rewardAmount,
        currencyPrice,
        fiatPriceTwd,
        null,
        false,
        null);
  }

  /**
   * Creates a new product.
   *
   * @param backendApiUrl external backend endpoint url
   * @param autoCreateEscortOrder whether to open escort order on purchase
   * @param escortOptionCode escort option code defined in EscortOrderOptionCatalog
   */
  public Result<Product, DomainError> createProduct(
      long guildId,
      String name,
      String description,
      Product.RewardType rewardType,
      Long rewardAmount,
      Long currencyPrice,
      Long fiatPriceTwd,
      String backendApiUrl,
      boolean autoCreateEscortOrder,
      String escortOptionCode) {

    Result<ValidationResult, DomainError> validationResult =
        validateInputs(
            guildId,
            null,
            name,
            rewardType,
            rewardAmount,
            currencyPrice,
            fiatPriceTwd,
            backendApiUrl,
            autoCreateEscortOrder,
            escortOptionCode);
    if (validationResult.isErr()) {
      return Result.err(validationResult.getError());
    }

    ValidationResult normalized = validationResult.getValue();
    try {
      Product product =
          Product.create(
              guildId,
              normalized.name(),
              description,
              rewardType,
              rewardAmount,
              currencyPrice,
              fiatPriceTwd,
              normalized.backendApiUrl(),
              normalized.autoCreateEscortOrder(),
              normalized.escortOptionCode());
      Product saved = productRepository.save(product);
      LOG.info(
          "Created product: guildId={}, name={}, rewardType={}, rewardAmount={}, currencyPrice={},"
              + " fiatPriceTwd={}, hasBackendApi={}, autoCreateEscortOrder={},"
              + " escortOptionCode={}",
          guildId,
          normalized.name(),
          rewardType,
          rewardAmount,
          currencyPrice,
          fiatPriceTwd,
          normalized.backendApiUrl() != null,
          normalized.autoCreateEscortOrder(),
          normalized.escortOptionCode());

      eventPublisher.publish(
          new ProductChangedEvent(guildId, saved.id(), ProductChangedEvent.OperationType.CREATED));

      return Result.ok(saved);
    } catch (Exception e) {
      LOG.error("Failed to create product for guildId={}, name={}", guildId, normalized.name(), e);
      return Result.err(DomainError.persistenceFailure("建立商品失敗", e));
    }
  }

  public Result<Product, DomainError> updateProduct(
      long productId,
      String name,
      String description,
      Product.RewardType rewardType,
      Long rewardAmount,
      Long currencyPrice) {
    Result<Unit, DomainError> nameValidationResult = validateName(name);
    if (nameValidationResult.isErr()) {
      return Result.err(nameValidationResult.getError());
    }
    Optional<Product> existingOpt = productRepository.findById(productId);
    if (existingOpt.isEmpty()) {
      return Result.err(DomainError.invalidInput("找不到商品"));
    }
    Product existing = existingOpt.get();
    return updateProductInternal(
        existing,
        name,
        description,
        rewardType,
        rewardAmount,
        currencyPrice,
        existing.fiatPriceTwd(),
        existing.backendApiUrl(),
        existing.autoCreateEscortOrder(),
        existing.escortOptionCode());
  }

  public Result<Product, DomainError> updateProduct(
      long productId,
      String name,
      String description,
      Product.RewardType rewardType,
      Long rewardAmount,
      Long currencyPrice,
      Long fiatPriceTwd) {
    Result<Unit, DomainError> nameValidationResult = validateName(name);
    if (nameValidationResult.isErr()) {
      return Result.err(nameValidationResult.getError());
    }
    Optional<Product> existingOpt = productRepository.findById(productId);
    if (existingOpt.isEmpty()) {
      return Result.err(DomainError.invalidInput("找不到商品"));
    }
    Product existing = existingOpt.get();
    return updateProductInternal(
        existing,
        name,
        description,
        rewardType,
        rewardAmount,
        currencyPrice,
        fiatPriceTwd,
        existing.backendApiUrl(),
        existing.autoCreateEscortOrder(),
        existing.escortOptionCode());
  }

  /**
   * Updates an existing product.
   *
   * @param backendApiUrl external backend endpoint url
   * @param autoCreateEscortOrder whether to open escort order on purchase
   * @param escortOptionCode escort option code defined in EscortOrderOptionCatalog
   */
  public Result<Product, DomainError> updateProduct(
      long productId,
      String name,
      String description,
      Product.RewardType rewardType,
      Long rewardAmount,
      Long currencyPrice,
      Long fiatPriceTwd,
      String backendApiUrl,
      boolean autoCreateEscortOrder,
      String escortOptionCode) {
    Result<Unit, DomainError> nameValidationResult = validateName(name);
    if (nameValidationResult.isErr()) {
      return Result.err(nameValidationResult.getError());
    }
    Optional<Product> existingOpt = productRepository.findById(productId);
    if (existingOpt.isEmpty()) {
      return Result.err(DomainError.invalidInput("找不到商品"));
    }
    return updateProductInternal(
        existingOpt.get(),
        name,
        description,
        rewardType,
        rewardAmount,
        currencyPrice,
        fiatPriceTwd,
        backendApiUrl,
        autoCreateEscortOrder,
        escortOptionCode);
  }

  private Result<Product, DomainError> updateProductInternal(
      Product existing,
      String name,
      String description,
      Product.RewardType rewardType,
      Long rewardAmount,
      Long currencyPrice,
      Long fiatPriceTwd,
      String backendApiUrl,
      boolean autoCreateEscortOrder,
      String escortOptionCode) {

    Result<ValidationResult, DomainError> validationResult =
        validateInputs(
            existing.guildId(),
            existing.id(),
            name,
            rewardType,
            rewardAmount,
            currencyPrice,
            fiatPriceTwd,
            backendApiUrl,
            autoCreateEscortOrder,
            escortOptionCode);
    if (validationResult.isErr()) {
      return Result.err(validationResult.getError());
    }

    ValidationResult normalized = validationResult.getValue();

    try {
      Product updated =
          existing.withUpdatedDetails(
              normalized.name(),
              description,
              rewardType,
              rewardAmount,
              currencyPrice,
              fiatPriceTwd,
              normalized.backendApiUrl(),
              normalized.autoCreateEscortOrder(),
              normalized.escortOptionCode());
      Product saved = productRepository.update(updated);
      LOG.info("Updated product: id={}, name={}", existing.id(), normalized.name());

      eventPublisher.publish(
          new ProductChangedEvent(
              existing.guildId(), existing.id(), ProductChangedEvent.OperationType.UPDATED));

      return Result.ok(saved);
    } catch (Exception e) {
      LOG.error("Failed to update product id={}", existing.id(), e);
      return Result.err(DomainError.persistenceFailure("更新商品失敗", e));
    }
  }

  private Result<ValidationResult, DomainError> validateInputs(
      long guildId,
      Long productId,
      String name,
      Product.RewardType rewardType,
      Long rewardAmount,
      Long currencyPrice,
      Long fiatPriceTwd,
      String backendApiUrl,
      boolean autoCreateEscortOrder,
      String escortOptionCode) {
    if (name == null || name.isBlank()) {
      return Result.err(DomainError.invalidInput("商品名稱不能為空"));
    }
    String normalizedName = name.trim();
    if (normalizedName.length() > 100) {
      return Result.err(DomainError.invalidInput("商品名稱不能超過 100 個字元"));
    }

    if (productId == null) {
      if (productRepository.existsByGuildIdAndName(guildId, normalizedName)) {
        return Result.err(DomainError.invalidInput("商品名稱已存在"));
      }
    } else if (productRepository.existsByGuildIdAndNameExcludingId(
        guildId, normalizedName, productId)) {
      return Result.err(DomainError.invalidInput("商品名稱已存在"));
    }

    if ((rewardType == null) != (rewardAmount == null)) {
      return Result.err(DomainError.invalidInput("獎勵類型和獎勵金額必須同時設定或同時為空"));
    }
    if (rewardAmount != null && rewardAmount <= 0) {
      return Result.err(DomainError.invalidInput("獎勵金額必須大於 0"));
    }

    if (currencyPrice != null && currencyPrice <= 0) {
      return Result.err(DomainError.invalidInput("貨幣價格必須大於 0"));
    }
    if (fiatPriceTwd != null && fiatPriceTwd <= 0) {
      return Result.err(DomainError.invalidInput("新台幣實際價值必須大於 0"));
    }

    Result<String, DomainError> normalizedBackendApiResult = normalizeBackendApiUrl(backendApiUrl);
    if (normalizedBackendApiResult.isErr()) {
      return Result.err(normalizedBackendApiResult.getError());
    }
    String normalizedBackendApiUrl = normalizedBackendApiResult.getValue();
    if (normalizedBackendApiUrl.isBlank()) {
      normalizedBackendApiUrl = null;
    }

    Result<String, DomainError> normalizedEscortOptionResult =
        normalizeEscortOption(autoCreateEscortOrder, escortOptionCode);
    if (normalizedEscortOptionResult.isErr()) {
      return Result.err(normalizedEscortOptionResult.getError());
    }
    String normalizedEscortOptionCode = normalizedEscortOptionResult.getValue();
    if (normalizedEscortOptionCode.isBlank()) {
      normalizedEscortOptionCode = null;
    }

    if (autoCreateEscortOrder && normalizedBackendApiUrl == null) {
      return Result.err(DomainError.invalidInput("啟用自動護航開單時，必須設定後端 API URL"));
    }

    return Result.ok(
        new ValidationResult(
            normalizedName,
            normalizedBackendApiUrl,
            autoCreateEscortOrder,
            normalizedEscortOptionCode));
  }

  private Result<String, DomainError> normalizeBackendApiUrl(String backendApiUrl) {
    if (backendApiUrl == null || backendApiUrl.isBlank()) {
      return Result.ok("");
    }
    String normalized = backendApiUrl.trim();
    if (normalized.length() > 500) {
      return Result.err(DomainError.invalidInput("後端 API URL 不能超過 500 個字元"));
    }
    try {
      URI uri = URI.create(normalized);
      String scheme = uri.getScheme();
      if (scheme == null
          || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
        return Result.err(DomainError.invalidInput("後端 API URL 必須使用 http:// 或 https://"));
      }
      if (uri.getHost() == null || uri.getHost().isBlank()) {
        return Result.err(DomainError.invalidInput("後端 API URL 格式無效"));
      }

      if (isDisallowedBackendHost(uri.getHost())) {
        return Result.err(DomainError.invalidInput("後端 API URL 不可使用 localhost 或內網位址"));
      }

      String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
      String normalizedUrl = normalizedScheme + normalized.substring(scheme.length());
      return Result.ok(normalizedUrl);
    } catch (IllegalArgumentException e) {
      return Result.err(DomainError.invalidInput("後端 API URL 格式無效"));
    }
  }

  private boolean isDisallowedBackendHost(String host) {
    String normalizedHost = host.trim().toLowerCase(Locale.ROOT);
    if (normalizedHost.equals("localhost") || normalizedHost.endsWith(".localhost")) {
      return true;
    }
    if (!looksLikeIpLiteral(normalizedHost)) {
      return false;
    }
    try {
      InetAddress address = InetAddress.getByName(normalizedHost);
      return address.isAnyLocalAddress()
          || address.isLoopbackAddress()
          || address.isLinkLocalAddress()
          || address.isSiteLocalAddress()
          || address.isMulticastAddress()
          || isIpv6UniqueLocalAddress(address);
    } catch (Exception e) {
      return true;
    }
  }

  private boolean isIpv6UniqueLocalAddress(InetAddress address) {
    if (!(address instanceof Inet6Address inet6)) {
      return false;
    }
    byte[] raw = inet6.getAddress();
    return raw.length > 0 && (raw[0] & (byte) 0xFE) == (byte) 0xFC;
  }

  private boolean looksLikeIpLiteral(String host) {
    if (host.contains(":")) {
      return true;
    }
    if (!host.contains(".")) {
      return false;
    }
    for (char ch : host.toCharArray()) {
      if (!Character.isDigit(ch) && ch != '.') {
        return false;
      }
    }
    return true;
  }

  private Result<String, DomainError> normalizeEscortOption(
      boolean autoCreateEscortOrder, String escortOptionCode) {
    if (!autoCreateEscortOrder) {
      return Result.ok("");
    }
    if (escortOptionCode == null || escortOptionCode.isBlank()) {
      return Result.err(DomainError.invalidInput("啟用自動護航開單時，必須設定護航選項代碼"));
    }
    String normalizedCode = escortOptionCode.trim().toUpperCase();
    if (!EscortOrderOptionCatalog.isSupported(normalizedCode)) {
      return Result.err(
          DomainError.invalidInput(
              "護航選項代碼無效，可用代碼：" + EscortOrderOptionCatalog.supportedCodesForDisplay()));
    }
    return Result.ok(normalizedCode);
  }

  private Result<Unit, DomainError> validateName(String name) {
    if (name == null || name.isBlank()) {
      return Result.err(DomainError.invalidInput("商品名稱不能為空"));
    }
    String normalizedName = name.trim();
    if (normalizedName.length() > 100) {
      return Result.err(DomainError.invalidInput("商品名稱不能超過 100 個字元"));
    }
    return Result.okVoid();
  }

  public Result<Unit, DomainError> deleteProduct(long productId) {
    Optional<Product> existingOpt = productRepository.findById(productId);
    if (existingOpt.isEmpty()) {
      return Result.err(DomainError.invalidInput("找不到商品"));
    }

    Product existing = existingOpt.get();

    try {
      int invalidatedCount = redemptionCodeRepository.invalidateByProductId(productId);
      if (invalidatedCount > 0) {
        LOG.info(
            "Invalidated {} redemption codes before deleting product: id={}",
            invalidatedCount,
            productId);
      }

      boolean deleted = productRepository.deleteById(productId);
      if (deleted) {
        LOG.info("Deleted product: id={}", productId);

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

  public Optional<Product> getProduct(long productId) {
    return productRepository.findById(productId);
  }

  public List<Product> getProducts(long guildId) {
    return productRepository.findByGuildId(guildId);
  }

  public long getProductCount(long guildId) {
    return productRepository.countByGuildId(guildId);
  }

  public List<Product> getProductsForPurchase(long guildId) {
    if (productRepository instanceof JdbcProductRepository jdbcRepo) {
      return jdbcRepo.findByGuildIdWithCurrencyPrice(guildId);
    }
    return productRepository.findByGuildId(guildId).stream()
        .filter(Product::hasCurrencyPrice)
        .toList();
  }

  public List<Product> getFiatOnlyProducts(long guildId) {
    if (productRepository instanceof JdbcProductRepository jdbcRepo) {
      return jdbcRepo.findFiatOnlyByGuildId(guildId);
    }
    return productRepository.findByGuildId(guildId).stream().filter(Product::isFiatOnly).toList();
  }

  private record ValidationResult(
      String name, String backendApiUrl, boolean autoCreateEscortOrder, String escortOptionCode) {}
}
