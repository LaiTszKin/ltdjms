package ltdjms.discord.redemption.domain;

import java.util.List;

/** 商品兌換交易紀錄的 Repository 介面。 提供儲存和查詢交易歷史的方法。 */
public interface ProductRedemptionTransactionRepository {

  /**
   * 儲存新的交易紀錄到資料庫。
   *
   * @param transaction 要儲存的交易紀錄
   * @return 儲存後的交易紀錄（包含生成的 ID）
   */
  ProductRedemptionTransaction save(ProductRedemptionTransaction transaction);

  /**
   * 查詢特定使用者在伺服器中的交易紀錄，按時間倒序排列。
   *
   * @param guildId Discord 伺服器 ID
   * @param userId Discord 使用者 ID
   * @param limit 最多返回的交易筆數
   * @param offset 跳過的交易筆數（用於分頁）
   * @return 交易紀錄列表，按 created_at DESC 排序
   */
  List<ProductRedemptionTransaction> findByGuildIdAndUserId(
      long guildId, long userId, int limit, int offset);

  /**
   * 統計特定使用者在伺服器中的交易總筆數。
   *
   * @param guildId Discord 伺服器 ID
   * @param userId Discord 使用者 ID
   * @return 交易總筆數
   */
  long countByGuildIdAndUserId(long guildId, long userId);

  /**
   * 刪除特定使用者在伺服器中的所有交易紀錄。 主要用於測試或帳號清理。
   *
   * @param guildId Discord 伺服器 ID
   * @param userId Discord 使用者 ID
   * @return 刪除的交易筆數
   */
  int deleteByGuildIdAndUserId(long guildId, long userId);
}
