package ltdjms.discord.shared.events;

import java.time.Instant;
import java.util.List;

import ltdjms.discord.aiagent.domain.ConversationMessage;

/**
 * Agent 會話完成事件。
 *
 * <p>當 AI 完成多輪工具調用並不再需要調用更多工具時發布。
 *
 * @param guildId 伺服器 ID
 * @param channelId 頻道 ID
 * @param userId 用戶 ID
 * @param conversationId 會話 ID
 * @param finalResponse AI 最終回應內容
 * @param fullHistory 完整對話歷史
 * @param timestamp 事件時間戳
 */
public record AgentCompletedEvent(
    long guildId,
    String channelId,
    String userId,
    String conversationId,
    String finalResponse,
    List<ConversationMessage> fullHistory,
    Instant timestamp)
    implements DomainEvent {}
