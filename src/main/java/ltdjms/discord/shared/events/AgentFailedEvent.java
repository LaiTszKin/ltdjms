package ltdjms.discord.shared.events;

import java.time.Instant;

/**
 * Agent 會話失敗事件。
 *
 * <p>當多輪工具調用過程中發生錯誤或達到最大迭代次數時發布。
 *
 * @param guildId 伺服器 ID
 * @param channelId 頻道 ID
 * @param userId 用戶 ID
 * @param conversationId 會話 ID
 * @param reason 失敗原因
 * @param timestamp 事件時間戳
 */
public record AgentFailedEvent(
    long guildId,
    String channelId,
    String userId,
    String conversationId,
    String reason,
    Instant timestamp)
    implements DomainEvent {}
