package ltdjms.discord.aiagent.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;

/**
 * 記憶體中的工具調用歷史管理器。
 *
 * <p>維護 Thread 級別的工具調用歷史，用於保留可審計的工具執行記錄，並只向後續對話 暴露記憶安全的摘要。
 *
 * <h2>設計原則</h2>
 *
 * <ul>
 *   <li>記憶體存儲：使用 ConcurrentHashMap 存儲，支援並發訪問
 *   <li>數量限制：每個 threadId:userId 會話最多保留 50 條工具調用記錄
 *   <li>生命週期：與應用程式生命週期綁定，重啟後清空
 * </ul>
 */
public final class InMemoryToolCallHistory {

  /** 會話 key（threadId:userId）→ 工具調用歷史列表 */
  private final ConcurrentHashMap<String, List<ToolCallEntry>> historyMap;

  /** 最大保留歷史記錄數（每個 threadId:userId 會話） */
  private static final int MAX_HISTORY_PER_CONVERSATION = 50;

  /** 建立新的工具調用歷史管理器。 */
  public InMemoryToolCallHistory() {
    this.historyMap = new ConcurrentHashMap<>();
  }

  /**
   * 工具調用條目。
   *
   * @param timestamp 調用時間戳
   * @param toolName 工具名稱
   * @param parameters 調用參數
   * @param success 是否成功
   * @param memorySummary 可安全注入 chat memory 的摘要
   * @param redactionMode 摘要紅線化狀態
   */
  public record ToolCallEntry(
      Instant timestamp,
      String toolName,
      Map<String, Object> parameters,
      boolean success,
      String memorySummary,
      RedactionMode redactionMode) {}

  /** 工具結果在跨回合記憶中的紅線化模式。 */
  public enum RedactionMode {
    NONE,
    REDACTED,
    OMITTED
  }

  /**
   * 添加工具調用記錄。
   *
   * @param threadId Discord Thread ID
   * @param entry 工具調用條目
   */
  public void addToolCall(long threadId, ToolCallEntry entry) {
    addToolCall(threadId, 0L, entry);
  }

  /**
   * 添加工具調用記錄（使用者隔離）。
   *
   * @param threadId Discord Thread ID
   * @param userId 發起工具調用的使用者 ID
   * @param entry 工具調用條目
   */
  public void addToolCall(long threadId, long userId, ToolCallEntry entry) {
    String conversationKey = buildKey(threadId, userId);
    historyMap.compute(
        conversationKey,
        (ignoredKey, existingEntries) -> {
          List<ToolCallEntry> updatedEntries =
              existingEntries == null ? new ArrayList<>() : existingEntries;
          updatedEntries.add(entry);

          // 超過限制時移除最舊的記錄
          if (updatedEntries.size() > MAX_HISTORY_PER_CONVERSATION) {
            updatedEntries.remove(0);
          }

          return updatedEntries;
        });
  }

  /**
   * 獲取 Thread 的工具調用歷史（轉換為 ChatMessage 格式）。
   *
   * @param threadId Discord Thread ID
   * @return 工具調用訊息列表
   */
  public List<ChatMessage> getToolCallMessages(long threadId) {
    return getToolCallMessages(threadId, 0L);
  }

  /**
   * 獲取 Thread + 使用者 的工具調用歷史（轉換為 ChatMessage 格式）。
   *
   * @param threadId Discord Thread ID
   * @param userId 發起工具調用的使用者 ID
   * @return 工具調用訊息列表
   */
  public List<ChatMessage> getToolCallMessages(long threadId, long userId) {
    List<ToolCallEntry> history = getAuditEntries(threadId, userId);
    List<ChatMessage> messages = new ArrayList<>();

    for (ToolCallEntry entry : history) {
      if (entry.memorySummary() == null || entry.memorySummary().isBlank()) {
        continue;
      }
      messages.add(AiMessage.from(entry.memorySummary()));
    }

    return messages;
  }

  /**
   * 獲取 Thread + 使用者 的原始審計條目（不供模型直接使用）。
   *
   * @param threadId Discord Thread ID
   * @param userId 發起工具調用的使用者 ID
   * @return 審計條目快照
   */
  public List<ToolCallEntry> getAuditEntries(long threadId, long userId) {
    return List.copyOf(historyMap.getOrDefault(buildKey(threadId, userId), List.of()));
  }

  /**
   * 清理 Thread 的歷史記錄。
   *
   * @param threadId Discord Thread ID
   */
  public void clearHistory(long threadId) {
    clearHistory(threadId, 0L);
  }

  /**
   * 清理 Thread + 使用者 的歷史記錄。
   *
   * @param threadId Discord Thread ID
   * @param userId 發起工具調用的使用者 ID
   */
  public void clearHistory(long threadId, long userId) {
    historyMap.remove(buildKey(threadId, userId));
  }

  /**
   * 獲取指定 Thread 的歷史記錄數量。
   *
   * @param threadId Discord Thread ID
   * @return 歷史記錄數量
   */
  public int getHistorySize(long threadId) {
    return getHistorySize(threadId, 0L);
  }

  /**
   * 獲取指定 Thread + 使用者 的歷史記錄數量。
   *
   * @param threadId Discord Thread ID
   * @param userId 發起工具調用的使用者 ID
   * @return 歷史記錄數量
   */
  public int getHistorySize(long threadId, long userId) {
    List<ToolCallEntry> history = historyMap.get(buildKey(threadId, userId));
    return history == null ? 0 : history.size();
  }

  /** 清空所有歷史記錄（主要用於測試）。 */
  public void clearAll() {
    historyMap.clear();
  }

  private String buildKey(long threadId, long userId) {
    return threadId + ":" + userId;
  }
}
