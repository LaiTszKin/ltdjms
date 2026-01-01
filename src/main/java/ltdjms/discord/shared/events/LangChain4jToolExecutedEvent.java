package ltdjms.discord.shared.events;

import java.time.Instant;

/**
 * LangChain4J 工具執行完成事件。
 *
 * <p>當 LangChain4J 工具執行完成後發布，用於審計和 UI 通知。
 *
 * <p>此事件與舊的 {@code ToolResultEvent} 不同，它專為 LangChain4J 框架設計， 不依賴 {@code ToolCallRequest} 和 {@code
 * ToolExecutionResult} 類別。
 *
 * @param guildId 伺服器 ID
 * @param channelId 頻道 ID
 * @param userId 用戶 ID
 * @param toolName 工具名稱
 * @param result 工具執行結果
 * @param success 是否成功
 * @param timestamp 事件時間戳
 */
public record LangChain4jToolExecutedEvent(
    long guildId,
    long channelId,
    long userId,
    String toolName,
    String result,
    boolean success,
    Instant timestamp)
    implements DomainEvent {}
