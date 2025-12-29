package ltdjms.discord.aiagent.services;

import java.util.Map;

import ltdjms.discord.aiagent.domain.ToolExecutionResult;

/**
 * 工具實作介面。
 *
 * <p>所有工具必須實作此介面以供 ToolExecutor 調用。
 */
public interface Tool {

  /**
   * 工具名稱（必須與 ToolDefinition.name 一致）。
   *
   * @return 工具名稱
   */
  String name();

  /**
   * 執行工具。
   *
   * @param parameters 參數映射
   * @param context 執行上下文
   * @return 執行結果
   */
  ToolExecutionResult execute(Map<String, Object> parameters, ToolContext context);
}
