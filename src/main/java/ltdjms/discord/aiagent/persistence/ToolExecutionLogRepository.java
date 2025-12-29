package ltdjms.discord.aiagent.persistence;

import java.time.LocalDateTime;
import java.util.List;

import ltdjms.discord.aiagent.domain.ToolExecutionLog;
import ltdjms.discord.shared.Result;

/**
 * 工具執行日誌 Repository。
 *
 * <p>提供工具執行日誌的資料存取操作。
 */
public interface ToolExecutionLogRepository {

  /**
   * 儲存日誌。
   *
   * @param log 日誌實體
   * @return 儲存結果，包含更新後的日誌或錯誤
   */
  Result<ToolExecutionLog, Exception> save(ToolExecutionLog log);

  /**
   * 查詢指定頻道的執行歷史。
   *
   * @param channelId 頻道 ID
   * @param limit 限制返回數量
   * @return 查詢結果，包含日誌列表或錯誤
   */
  Result<List<ToolExecutionLog>, Exception> findByChannelId(long channelId, int limit);

  /**
   * 查詢指定時間範圍的日誌。
   *
   * @param guildId 伺服器 ID
   * @param start 開始時間
   * @param end 結束時間
   * @return 查詢結果，包含日誌列表或錯誤
   */
  Result<List<ToolExecutionLog>, Exception> findByTimeRange(
      long guildId, LocalDateTime start, LocalDateTime end);

  /**
   * 刪除指定日期之前的日誌。
   *
   * @param cutoff 截止日期
   * @return 刪除結果，包含刪除的數量或錯誤
   */
  Result<Integer, Exception> deleteOlderThan(LocalDateTime cutoff);
}
