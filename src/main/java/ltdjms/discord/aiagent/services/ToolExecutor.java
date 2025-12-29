package ltdjms.discord.aiagent.services;

import java.util.concurrent.CompletableFuture;

import ltdjms.discord.aiagent.domain.ToolExecutionResult;

/**
 * 工具執行器。
 *
 * <p>執行 AI 請求的工具調用，使用 FIFO 佇列序列化處理。
 */
public interface ToolExecutor {

  /**
   * 提交工具調用請求。
   *
   * @param request 工具調用請求
   * @return CompletableFuture，完成時返回執行結果
   */
  CompletableFuture<ToolExecutionResult> submit(ToolCallRequest request);

  /**
   * 同步執行工具調用（僅用於測試）。
   *
   * @param request 工具調用請求
   * @return 執行結果
   */
  ToolExecutionResult executeSync(ToolCallRequest request);

  /**
   * 獲取佇列大小。
   *
   * @return 當前等待處理的請求數量
   */
  int getQueueSize();
}
