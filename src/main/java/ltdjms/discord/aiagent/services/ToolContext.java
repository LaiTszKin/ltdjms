package ltdjms.discord.aiagent.services;

import net.dv8tion.jda.api.JDA;

/**
 * 工具執行上下文。
 *
 * @param guildId 伺服器 ID
 * @param channelId 頻道 ID
 * @param userId 觸發用戶 ID
 * @param jda JDA 實例（用於 Discord API 操作）
 */
public record ToolContext(long guildId, long channelId, long userId, JDA jda) {}
