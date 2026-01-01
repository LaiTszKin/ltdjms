package ltdjms.discord.aiagent.domain;

/**
 * 會話 ID 策略。
 *
 * <p>定義兩種會話範圍：
 *
 * <ul>
 *   <li>{@link #MESSAGE_LEVEL} - 訊息級別（一般頻道），每則訊息獨立會話。 會話 ID 格式：{@code
 *       guildId:channelId:userId:messageId}
 *   <li>{@link #THREAD_LEVEL} - 討論串級別（Discord Thread），同一討論串共享上下文。 會話 ID 格式：{@code
 *       guildId:threadId:userId}
 * </ul>
 */
public enum ConversationIdStrategy {
  /** 訊息級別：每則訊息都是獨立會話（一般頻道） */
  MESSAGE_LEVEL,

  /** 討論串級別：同一討論串內的所有訊息共享上下文 */
  THREAD_LEVEL
}
