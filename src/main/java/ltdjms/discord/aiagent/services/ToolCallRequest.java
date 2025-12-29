package ltdjms.discord.aiagent.services;

import java.util.Map;

/**
 * 工具調用請求。
 *
 * @param toolName 工具名稱
 * @param parameters 參數映射
 * @param guildId 伺服器 ID
 * @param channelId 頻道 ID
 * @param userId 觸發用戶 ID
 */
public record ToolCallRequest(
    String toolName, Map<String, Object> parameters, long guildId, long channelId, long userId) {}
