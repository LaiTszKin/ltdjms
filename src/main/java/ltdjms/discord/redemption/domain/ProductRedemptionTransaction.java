package ltdjms.discord.redemption.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * 代表商品兌換交易紀錄。 記錄使用者兌換商品的所有資訊，用於使用者流水查詢與統計。
 *
 * @param id 交易紀錄唯一 ID（新建時為 null）
 * @param guildId Discord 伺服器 ID
 * @param userId Discord 使用者 ID
 * @param productId 商品 ID
 * @param productName 商品名稱（快照，以防商品被刪除後仍可顯示）
 * @param redemptionCode 使用的兌換碼
 * @param quantity 兌換的數量
 * @param rewardType 獎勵類型（CURRENCY 或 TOKEN，無自動獎勵為 null）
 * @param rewardAmount 獎勵總額（quantity × 商品單位獎勵數量）
 * @param createdAt 交易時間戳
 */
public record ProductRedemptionTransaction(
    Long id,
    long guildId,
    long userId,
    long productId,
    String productName,
    String redemptionCode,
    int quantity,
    RewardType rewardType,
    Long rewardAmount,
    Instant createdAt) {
  /** 獎勵類型。 */
  public enum RewardType {
    /** 貨幣獎勵 */
    CURRENCY("貨幣"),
    /** 遊戲代幣獎勵 */
    TOKEN("代幣");

    private final String displayName;

    RewardType(String displayName) {
      this.displayName = displayName;
    }

    /**
     * 取得獎勵類型的顯示名稱。
     *
     * @return 顯示名稱
     */
    public String getDisplayName() {
      return displayName;
    }
  }

  public ProductRedemptionTransaction {
    Objects.requireNonNull(productName, "productName must not be null");
    if (productName.isBlank()) {
      throw new IllegalArgumentException("productName must not be blank");
    }
    if (productName.length() > 100) {
      throw new IllegalArgumentException("productName must not exceed 100 characters");
    }
    Objects.requireNonNull(redemptionCode, "redemptionCode must not be null");
    if (redemptionCode.isBlank()) {
      throw new IllegalArgumentException("redemptionCode must not be blank");
    }
    if (quantity <= 0) {
      throw new IllegalArgumentException("quantity must be positive");
    }
    if (quantity > 1000) {
      throw new IllegalArgumentException("quantity must not exceed 1000");
    }
    if (rewardType != null && rewardAmount == null) {
      throw new IllegalArgumentException("rewardAmount must be specified when rewardType is set");
    }
    if (rewardType == null && rewardAmount != null) {
      throw new IllegalArgumentException("rewardType must be specified when rewardAmount is set");
    }
    if (rewardAmount != null && rewardAmount < 0) {
      throw new IllegalArgumentException("rewardAmount cannot be negative");
    }
  }

  /**
   * 建立新的商品兌換交易紀錄（ID 為 null，將由資料庫指派）。
   *
   * @param guildId Discord 伺服器 ID
   * @param userId Discord 使用者 ID
   * @param productId 商品 ID
   * @param productName 商品名稱
   * @param redemptionCode 使用的兌換碼
   * @param quantity 兌換數量
   * @param rewardType 獎勵類型（無自動獎勵為 null）
   * @param rewardAmount 獎勵總額
   * @return 新的交易紀錄
   */
  public static ProductRedemptionTransaction create(
      long guildId,
      long userId,
      long productId,
      String productName,
      String redemptionCode,
      int quantity,
      RewardType rewardType,
      Long rewardAmount) {
    return new ProductRedemptionTransaction(
        null,
        guildId,
        userId,
        productId,
        productName,
        redemptionCode,
        quantity,
        rewardType,
        rewardAmount,
        Instant.now());
  }

  /**
   * 檢查此交易是否有自動獎勵。
   *
   * @return true 如果有自動獎勵
   */
  public boolean hasReward() {
    return rewardType != null && rewardAmount != null;
  }

  /**
   * 格式化此交易紀錄用於 Discord 顯示。
   *
   * @return 格式化的字串
   */
  public String formatForDisplay() {
    StringBuilder sb = new StringBuilder();
    sb.append("**").append(productName).append("**");

    if (quantity > 1) {
      sb.append(" x").append(quantity);
    }

    if (hasReward()) {
      sb.append(" | ")
          .append(rewardType.getDisplayName())
          .append(" +")
          .append(String.format("%,d", rewardAmount));
    } else {
      sb.append(" | 無自動獎勵");
    }

    sb.append(" | `").append(getMaskedCode()).append("`");

    return sb.toString();
  }

  /**
   * 取得遮蔽後的兌換碼（只顯示前 4 碼和後 4 碼）。
   *
   * @return 遮蔽後的兌換碼
   */
  public String getMaskedCode() {
    if (redemptionCode.length() <= 8) {
      return redemptionCode;
    }
    return redemptionCode.substring(0, 4)
        + "****"
        + redemptionCode.substring(redemptionCode.length() - 4);
  }

  /**
   * 取得簡短的時間戳記字串用於顯示。
   *
   * @return 簡短時間戳記格式
   */
  public String getShortTimestamp() {
    return String.format("<t:%d:R>", createdAt.getEpochSecond());
  }
}
