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
 * <p>維護 Thread 級別的工具調用歷史，用於在對話上下文中保留工具執行記錄。
 *
 * <h2>設計原則</h2>
 *
 * <ul>
 *   <li>記憶體存儲：使用 ConcurrentHashMap 存儲，支援並發訪問
 *   <li>數量限制：每個 Thread 最多保留 50 條工具調用記錄
 *   <li>生命週期：與應用程式生命週期綁定，重啟後清空
 * </ul>
 */
public final class InMemoryToolCallHistory {

  /** Thread ID → 工具調用歷史列表 */
  private final ConcurrentHashMap<Long, List<ToolCallEntry>> historyMap;

  /** 最大保留歷史記錄數（每個 Thread） */
  private static final int MAX_HISTORY_PER_THREAD = 50;

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
   * @param result 執行結果
   */
  public record ToolCallEntry(
      Instant timestamp,
      String toolName,
      Map<String, Object> parameters,
      boolean success,
      String result) {}

  /**
   * 添加工具調用記錄。
   *
   * @param threadId Discord Thread ID
   * @param entry 工具調用條目
   */
  public void addToolCall(long threadId, ToolCallEntry entry) {
    historyMap.compute(
        threadId,
        (key, list) -> {
          List<ToolCallEntry> newList = list == null ? new ArrayList<>() : list;
          newList.add(entry);

          // 超過限制時移除最舊的記錄
          if (newList.size() > MAX_HISTORY_PER_THREAD) {
            newList.remove(0);
          }

          return newList;
        });
  }

  /**
   * 獲取 Thread 的工具調用歷史（轉換為 ChatMessage 格式）。
   *
   * @param threadId Discord Thread ID
   * @return 工具調用訊息列表
   */
  public List<ChatMessage> getToolCallMessages(long threadId) {
    List<ToolCallEntry> history = historyMap.getOrDefault(threadId, List.of());
    List<ChatMessage> messages = new ArrayList<>();

    for (ToolCallEntry entry : history) {
      String toolResult = entry.success() ? "✅ " + entry.result() : "❌ " + entry.result();
      String summary = "工具「" + entry.toolName() + "」執行結果：" + toolResult;
      messages.add(AiMessage.from(summary));
    }

    return messages;
  }

  /**
   * 清理 Thread 的歷史記錄。
   *
   * @param threadId Discord Thread ID
   */
  public void clearHistory(long threadId) {
    historyMap.remove(threadId);
  }

  /**
   * 獲取指定 Thread 的歷史記錄數量。
   *
   * @param threadId Discord Thread ID
   * @return 歷史記錄數量
   */
  public int getHistorySize(long threadId) {
    List<ToolCallEntry> history = historyMap.get(threadId);
    return history == null ? 0 : history.size();
  }

  /** 清空所有歷史記錄（主要用於測試）。 */
  public void clearAll() {
    historyMap.clear();
  }
}
