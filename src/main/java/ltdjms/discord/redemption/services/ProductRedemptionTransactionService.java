package ltdjms.discord.redemption.services;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.product.domain.Product;
import ltdjms.discord.redemption.domain.ProductRedemptionTransaction;
import ltdjms.discord.redemption.domain.ProductRedemptionTransactionRepository;
import ltdjms.discord.redemption.domain.RedemptionCode;

/** 商品兌換交易紀錄的服務層。 提供記錄交易和查詢使用者交易歷史的功能。 */
public class ProductRedemptionTransactionService {

  private static final Logger LOG =
      LoggerFactory.getLogger(ProductRedemptionTransactionService.class);

  /** 預設每頁顯示的交易筆數。 */
  public static final int DEFAULT_PAGE_SIZE = 10;

  private final ProductRedemptionTransactionRepository transactionRepository;

  public ProductRedemptionTransactionService(
      ProductRedemptionTransactionRepository transactionRepository) {
    this.transactionRepository = transactionRepository;
  }

  /**
   * 記錄一筆商品兌換交易。
   *
   * @param guildId Discord 伺服器 ID
   * @param userId Discord 使用者 ID
   * @param product 兌換的商品
   * @param redemptionCode 使用的兌換碼
   * @return 儲存後的交易紀錄
   */
  public ProductRedemptionTransaction recordTransaction(
      long guildId, long userId, Product product, RedemptionCode redemptionCode) {
    // 計算總獎勵數量
    Long rewardAmount = null;
    ProductRedemptionTransaction.RewardType rewardType = null;

    if (product.hasReward()) {
      rewardAmount = product.rewardAmount() * redemptionCode.quantity();
      rewardType =
          switch (product.rewardType()) {
            case CURRENCY -> ProductRedemptionTransaction.RewardType.CURRENCY;
            case TOKEN -> ProductRedemptionTransaction.RewardType.TOKEN;
          };
    }

    ProductRedemptionTransaction transaction =
        ProductRedemptionTransaction.create(
            guildId,
            userId,
            product.id(),
            product.name(),
            redemptionCode.code(),
            redemptionCode.quantity(),
            rewardType,
            rewardAmount);

    ProductRedemptionTransaction saved = transactionRepository.save(transaction);
    LOG.info(
        "Recorded product redemption transaction: guildId={}, userId={}, product={}, quantity={}",
        guildId,
        userId,
        product.name(),
        redemptionCode.quantity());
    return saved;
  }

  /**
   * 取得使用者的商品兌換交易分頁紀錄。
   *
   * @param guildId Discord 伺服器 ID
   * @param userId Discord 使用者 ID
   * @param page 頁碼（從 1 開始）
   * @param pageSize 每頁筆數
   * @return 交易分頁結果
   */
  public TransactionPage getTransactionPage(long guildId, long userId, int page, int pageSize) {
    if (page < 1) {
      page = 1;
    }
    if (pageSize < 1) {
      pageSize = DEFAULT_PAGE_SIZE;
    }

    long totalCount = transactionRepository.countByGuildIdAndUserId(guildId, userId);

    int totalPages = (int) Math.ceil((double) totalCount / pageSize);
    if (totalPages < 1) {
      totalPages = 1;
    }
    if (page > totalPages) {
      page = totalPages;
    }

    int offset = (page - 1) * pageSize;
    List<ProductRedemptionTransaction> transactions =
        transactionRepository.findByGuildIdAndUserId(guildId, userId, pageSize, offset);

    LOG.debug(
        "Retrieved product redemption transaction page: guildId={}, userId={}, page={}/{},"
            + " count={}",
        guildId,
        userId,
        page,
        totalPages,
        transactions.size());

    return new TransactionPage(transactions, page, totalPages, totalCount, pageSize);
  }

  /**
   * 取得使用者的第一頁商品兌換交易紀錄（使用預設頁面大小）。
   *
   * @param guildId Discord 伺服器 ID
   * @param userId Discord 使用者 ID
   * @return 第一頁交易紀錄
   */
  public TransactionPage getTransactionPage(long guildId, long userId) {
    return getTransactionPage(guildId, userId, 1, DEFAULT_PAGE_SIZE);
  }

  /** 代表交易紀錄的分頁結果。 */
  public record TransactionPage(
      List<ProductRedemptionTransaction> transactions,
      int currentPage,
      int totalPages,
      long totalCount,
      int pageSize) {
    /**
     * 檢查是否有下一頁。
     *
     * @return true 如果有下一頁
     */
    public boolean hasNextPage() {
      return currentPage < totalPages;
    }

    /**
     * 檢查是否有上一頁。
     *
     * @return true 如果有上一頁
     */
    public boolean hasPreviousPage() {
      return currentPage > 1;
    }

    /**
     * 檢查此頁是否為空。
     *
     * @return true 如果沒有交易紀錄
     */
    public boolean isEmpty() {
      return transactions.isEmpty();
    }

    /**
     * 取得格式化的分頁指示器文字。
     *
     * @return 格式如「第 1/5 頁（共 50 筆）」的字串
     */
    public String formatPageIndicator() {
      return String.format("第 %d/%d 頁（共 %d 筆）", currentPage, totalPages, totalCount);
    }
  }
}
