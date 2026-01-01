package ltdjms.discord.aiagent.domain;

import java.util.Map;

/**
 * 工具調用資訊。
 *
 * <p>記錄單次工具調用的詳細資訊，用於會話歷史追蹤。
 *
 * @param toolName 工具名稱
 * @param parameters 工具參數
 * @param success 工具執行是否成功
 * @param result 工具執行結果（成功時）或錯誤訊息（失敗時）
 */
public record ToolCallInfo(
    String toolName, Map<String, Object> parameters, boolean success, String result) {}
