package ltdjms.discord.redemption.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.currency.domain.CurrencyTransaction;
import ltdjms.discord.currency.services.BalanceAdjustmentService;
import ltdjms.discord.currency.services.CurrencyTransactionService;
import ltdjms.discord.gametoken.domain.GameTokenTransaction;
import ltdjms.discord.gametoken.services.GameTokenService;
import ltdjms.discord.gametoken.services.GameTokenTransactionService;
import ltdjms.discord.product.domain.Product;
import ltdjms.discord.product.domain.ProductRepository;
import ltdjms.discord.redemption.domain.ProductRedemptionTransaction;
import ltdjms.discord.redemption.domain.RedemptionCode;
import ltdjms.discord.redemption.domain.RedemptionCodeRepository;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.events.DomainEventPublisher;
import ltdjms.discord.shared.events.ProductRedemptionCompletedEvent;
import ltdjms.discord.shared.events.RedemptionCodesGeneratedEvent;

/** Service for redemption code operations including generation and redemption. */
public class RedemptionService {

  private static final Logger LOG = LoggerFactory.getLogger(RedemptionService.class);

  /** Maximum number of codes that can be generated in a single batch. */
  public static final int MAX_BATCH_SIZE = 100;

  private final RedemptionCodeRepository codeRepository;
  private final ProductRepository productRepository;
  private final RedemptionCodeGenerator codeGenerator;
  private final BalanceAdjustmentService balanceAdjustmentService;
  private final GameTokenService gameTokenService;
  private final CurrencyTransactionService currencyTransactionService;
  private final GameTokenTransactionService gameTokenTransactionService;
  private final ProductRedemptionTransactionService productRedemptionTransactionService;
  private final DomainEventPublisher eventPublisher;

  public RedemptionService(
      RedemptionCodeRepository codeRepository,
      ProductRepository productRepository,
      RedemptionCodeGenerator codeGenerator,
      BalanceAdjustmentService balanceAdjustmentService,
      GameTokenService gameTokenService,
      CurrencyTransactionService currencyTransactionService,
      GameTokenTransactionService gameTokenTransactionService,
      ProductRedemptionTransactionService productRedemptionTransactionService,
      DomainEventPublisher eventPublisher) {
    this.codeRepository = codeRepository;
    this.productRepository = productRepository;
    this.codeGenerator = codeGenerator;
    this.balanceAdjustmentService = balanceAdjustmentService;
    this.gameTokenService = gameTokenService;
    this.currencyTransactionService = currencyTransactionService;
    this.gameTokenTransactionService = gameTokenTransactionService;
    this.productRedemptionTransactionService = productRedemptionTransactionService;
    this.eventPublisher = eventPublisher;
  }

  /**
   * Generates redemption codes for a product.
   *
   * @param productId the product ID
   * @param count the number of codes to generate
   * @param expiresAt the expiration time (can be null for no expiration)
   * @return Result containing the generated codes or an error
   */
  public Result<List<RedemptionCode>, DomainError> generateCodes(
      long productId, int count, Instant expiresAt) {
    return generateCodes(productId, count, expiresAt, 1);
  }

  /**
   * Generates redemption codes for a product with specified quantity per code.
   *
   * @param productId the product ID
   * @param count the number of codes to generate
   * @param expiresAt the expiration time (can be null for no expiration)
   * @param quantity the quantity of products each code redeems
   * @return Result containing the generated codes or an error
   */
  public Result<List<RedemptionCode>, DomainError> generateCodes(
      long productId, int count, Instant expiresAt, int quantity) {

    // Validate count
    if (count <= 0) {
      return Result.err(DomainError.invalidInput("生成數量必須大於 0"));
    }
    if (count > MAX_BATCH_SIZE) {
      return Result.err(DomainError.invalidInput(String.format("單次最多生成 %d 個兌換碼", MAX_BATCH_SIZE)));
    }

    // Validate quantity
    if (quantity <= 0) {
      return Result.err(DomainError.invalidInput("兌換數量必須大於 0"));
    }
    if (quantity > 1000) {
      return Result.err(DomainError.invalidInput("單個兌換碼最多可兌換 1000 個商品"));
    }

    // Find product
    Optional<Product> productOpt = productRepository.findById(productId);
    if (productOpt.isEmpty()) {
      return Result.err(DomainError.invalidInput("找不到商品"));
    }

    Product product = productOpt.get();

    try {
      List<RedemptionCode> codes = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
        String codeStr = generateUniqueCode();
        RedemptionCode code =
            RedemptionCode.create(codeStr, productId, product.guildId(), expiresAt, quantity);
        codes.add(code);
      }

      List<RedemptionCode> savedCodes = codeRepository.saveAll(codes);
      LOG.info(
          "Generated {} redemption codes for productId={} with quantity={}",
          savedCodes.size(),
          productId,
          quantity);

      // 發布事件以便面板即時刷新
      eventPublisher.publish(
          new RedemptionCodesGeneratedEvent(product.guildId(), productId, savedCodes.size()));
      return Result.ok(savedCodes);

    } catch (Exception e) {
      LOG.error("Failed to generate codes for productId={}", productId, e);
      return Result.err(DomainError.persistenceFailure("生成兌換碼失敗", e));
    }
  }

  /**
   * Redeems a code for a user.
   *
   * @param codeStr the redemption code string
   * @param guildId the Discord guild ID
   * @param userId the Discord user ID
   * @return Result containing the redemption result or an error
   */
  public Result<RedemptionResult, DomainError> redeemCode(
      String codeStr, long guildId, long userId) {
    if (codeStr == null || codeStr.isBlank()) {
      return Result.err(DomainError.invalidInput("兌換碼無效"));
    }

    codeStr = codeStr.trim().toUpperCase();

    // Find code
    Optional<RedemptionCode> codeOpt = codeRepository.findByCode(codeStr);
    if (codeOpt.isEmpty()) {
      LOG.debug("Redemption code not found: {}", codeStr);
      return Result.err(DomainError.invalidInput("兌換碼無效"));
    }

    RedemptionCode code = codeOpt.get();

    // Check if code belongs to this guild (don't reveal cross-guild info)
    if (!code.belongsToGuild(guildId)) {
      LOG.debug("Redemption code {} does not belong to guild {}", codeStr, guildId);
      return Result.err(DomainError.invalidInput("兌換碼無效"));
    }

    // Check if code has been invalidated
    if (code.isInvalidated()) {
      LOG.debug("Redemption code {} has been invalidated", codeStr);
      return Result.err(DomainError.invalidInput("此兌換碼已失效"));
    }

    // Check if already redeemed
    if (code.isRedeemed()) {
      LOG.debug("Redemption code {} already redeemed", codeStr);
      return Result.err(DomainError.invalidInput("此兌換碼已被使用"));
    }

    // Check if expired
    if (code.isExpired()) {
      LOG.debug("Redemption code {} has expired", codeStr);
      return Result.err(DomainError.invalidInput("此兌換碼已過期"));
    }

    // Find product
    // If productId is null (product was deleted), treat as invalid code
    if (code.productId() == null) {
      LOG.error("Product ID is null for redemption code: codeId={}", code.id());
      return Result.err(DomainError.invalidInput("此兌換碼已失效"));
    }

    Optional<Product> productOpt = productRepository.findById(code.productId());
    if (productOpt.isEmpty()) {
      LOG.error("Product not found for redemption code: productId={}", code.productId());
      return Result.err(DomainError.unexpectedFailure("商品資料異常", null));
    }

    Product product = productOpt.get();

    try {
      // Mark code as redeemed
      if (code.id() == null) {
        LOG.error("Redemption code ID is null during redeem: code={}", code.getMaskedCode());
        return Result.err(DomainError.unexpectedFailure("兌換碼資料異常", null));
      }

      RedemptionCode redeemedCode = code.withRedeemed(userId);
      boolean marked =
          codeRepository.markAsRedeemedIfAvailable(
              redeemedCode.id(), userId, redeemedCode.redeemedAt());
      if (!marked) {
        LOG.warn(
            "Redemption code became unavailable during redeem attempt: code={}, userId={}",
            code.getMaskedCode(),
            userId);
        return Result.err(DomainError.invalidInput("此兌換碼已被使用或不可用"));
      }

      // Grant reward if applicable
      Long rewardedAmount = null;
      if (product.hasReward()) {
        Result<Long, DomainError> rewardResult = grantReward(guildId, userId, product, code);
        if (rewardResult.isErr()) {
          // Rollback the redemption would be complex, log and continue
          LOG.error(
              "Failed to grant reward for code {}: {}",
              code.code(),
              rewardResult.getError().message());
        } else {
          rewardedAmount = rewardResult.getValue();
        }
      }

      // Record the product redemption transaction
      ProductRedemptionTransaction transaction =
          productRedemptionTransactionService.recordTransaction(
              guildId, userId, product, redeemedCode);

      // Publish event for panel updates
      eventPublisher.publish(
          new ProductRedemptionCompletedEvent(guildId, userId, transaction, Instant.now()));

      RedemptionResult result = new RedemptionResult(redeemedCode, product, rewardedAmount);

      LOG.info(
          "Successfully redeemed code {} by user {} for product {}",
          code.getMaskedCode(),
          userId,
          product.name());
      return Result.ok(result);

    } catch (Exception e) {
      LOG.error("Failed to redeem code {}", code.getMaskedCode(), e);
      return Result.err(DomainError.persistenceFailure("兌換失敗", e));
    }
  }

  /**
   * Finds a redemption code by its code string.
   *
   * @param codeStr the code string
   * @return Optional containing the code if found
   */
  public Optional<RedemptionCode> findByCode(String codeStr) {
    if (codeStr == null || codeStr.isBlank()) {
      return Optional.empty();
    }
    return codeRepository.findByCode(codeStr.trim().toUpperCase());
  }

  /**
   * Gets codes for a product with pagination.
   *
   * @param productId the product ID
   * @param page the page number (1-based)
   * @param pageSize the page size
   * @return a page of codes
   */
  public CodePage getCodePage(long productId, int page, int pageSize) {
    if (page < 1) page = 1;
    if (pageSize < 1) pageSize = 10;

    long totalCount = codeRepository.countByProductId(productId);

    int totalPages = (int) Math.ceil((double) totalCount / pageSize);
    if (totalPages < 1) totalPages = 1;
    if (page > totalPages) {
      page = totalPages;
    }

    int offset = (page - 1) * pageSize;
    List<RedemptionCode> codes = codeRepository.findByProductId(productId, pageSize, offset);

    return new CodePage(codes, page, totalPages, totalCount, pageSize);
  }

  /**
   * Gets statistics for a product's redemption codes.
   *
   * @param productId the product ID
   * @return code statistics
   */
  public RedemptionCodeRepository.CodeStats getCodeStats(long productId) {
    return codeRepository.getStatsByProductId(productId);
  }

  /** Generates a unique code, checking against the database. */
  private String generateUniqueCode() {
    int maxAttempts = 10;
    for (int i = 0; i < maxAttempts; i++) {
      String code = codeGenerator.generate();
      if (!codeRepository.existsByCode(code)) {
        return code;
      }
      LOG.debug("Generated duplicate code, retrying: attempt {}", i + 1);
    }
    throw new IllegalStateException(
        "Failed to generate unique code after " + maxAttempts + " attempts");
  }

  /** Grants the reward for a product. */
  private Result<Long, DomainError> grantReward(
      long guildId, long userId, Product product, RedemptionCode code) {

    if (!product.hasReward()) {
      return Result.ok(null);
    }

    // Calculate total reward based on quantity
    long totalAmount = product.rewardAmount() * code.quantity();
    String description =
        String.format("兌換碼: %s (%s) x%d", code.getMaskedCode(), product.name(), code.quantity());

    return switch (product.rewardType()) {
      case CURRENCY -> {
        var adjustResult = balanceAdjustmentService.tryAdjustBalance(guildId, userId, totalAmount);
        if (adjustResult.isOk()) {
          // Record the transaction with redemption source
          currencyTransactionService.recordTransaction(
              guildId,
              userId,
              totalAmount,
              adjustResult.getValue().newBalance(),
              CurrencyTransaction.Source.REDEMPTION_CODE,
              description);
          yield Result.ok(totalAmount);
        }
        yield Result.err(adjustResult.getError());
      }
      case TOKEN -> {
        var tokenResult = gameTokenService.tryAdjustTokens(guildId, userId, totalAmount);
        if (tokenResult.isOk()) {
          // Record the transaction with redemption source
          gameTokenTransactionService.recordTransaction(
              guildId,
              userId,
              totalAmount,
              tokenResult.getValue().newTokens(),
              GameTokenTransaction.Source.REDEMPTION_CODE,
              description);
          yield Result.ok(totalAmount);
        }
        yield Result.err(tokenResult.getError());
      }
    };
  }

  /** Result of a successful redemption. */
  public record RedemptionResult(RedemptionCode code, Product product, Long rewardedAmount) {
    /** Formats a success message for the user. */
    public String formatSuccessMessage() {
      StringBuilder sb = new StringBuilder();
      sb.append("你已成功兌換「").append(product.name()).append("」");

      if (product.description() != null && !product.description().isBlank()) {
        sb.append("\n").append(product.description());
      }

      if (rewardedAmount != null && product.hasReward()) {
        sb.append("\n\n已發放獎勵：").append(product.formatReward());
      }

      return sb.toString();
    }
  }

  /** Page of redemption codes. */
  public record CodePage(
      List<RedemptionCode> codes, int currentPage, int totalPages, long totalCount, int pageSize) {
    public boolean hasNextPage() {
      return currentPage < totalPages;
    }

    public boolean hasPreviousPage() {
      return currentPage > 1;
    }

    public boolean isEmpty() {
      return codes.isEmpty();
    }

    public String formatPageIndicator() {
      return String.format("第 %d/%d 頁（共 %d 個）", currentPage, totalPages, totalCount);
    }
  }
}
