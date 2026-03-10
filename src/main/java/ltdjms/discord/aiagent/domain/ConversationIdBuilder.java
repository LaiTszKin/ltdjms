package ltdjms.discord.aiagent.domain;

/**
 * 會話 ID 建構器。
 *
 * <p>根據頻道類型（一般頻道或討論串）生成對應格式的會話 ID。
 *
 * <h2>會話 ID 格式</h2>
 *
 * <ul>
 *   <li>一般頻道（訊息級別）：
 *       <pre>{@code guildId:channelId:userId:messageId}</pre>
 *       例如：{@code 123456789:987654321:111222333:444555666}
 *   <li>討論串（討論串級別）：
 *       <pre>{@code guildId:threadId:userId}</pre>
 *       例如：{@code 123456789:999888777:111222333}
 * </ul>
 */
public final class ConversationIdBuilder {

  private ConversationIdBuilder() {
    // 工具類，不允許實例化
  }

  /**
   * 建構會話 ID。
   *
   * <p>根據 {@code threadId} 參數決定使用哪種格式：
   *
   * <ul>
   *   <li>如果 {@code threadId} 不為 {@code null} 且大於 0，使用討論串級別格式
   *   <li>否則，使用訊息級別格式（一般頻道）
   * </ul>
   *
   * @param guildId 伺服器 ID
   * @param channelId 頻道 ID
   * @param threadId 討論串 ID（可為 {@code null}）
   * @param userId 用戶 ID
   * @param messageId 訊息 ID
   * @return 會話 ID 字串
   */
  public static String build(
      long guildId, long channelId, Long threadId, long userId, long messageId) {

    if (threadId != null && threadId > 0) {
      // 討論串級別：{guildId}:{threadId}:{userId}
      return "%d:%d:%d".formatted(guildId, threadId, userId);
    } else {
      // 訊息級別：{guildId}:{channelId}:{userId}:{messageId}
      return "%d:%d:%d:%d".formatted(guildId, channelId, userId, messageId);
    }
  }

  /**
   * 從會話 ID 解析策略類型。
   *
   * <p>根據會話 ID 中冒號分隔的段數判斷：
   *
   * <ul>
   *   <li>3 段 → {@link ConversationIdStrategy#THREAD_LEVEL}
   *   <li>4 段 → {@link ConversationIdStrategy#MESSAGE_LEVEL}
   * </ul>
   *
   * @param conversationId 會話 ID
   * @return 會話策略類型
   * @throws IllegalArgumentException 如果會話 ID 格式無效
   */
  public static ConversationIdStrategy parseStrategy(String conversationId) {
    if (conversationId == null || conversationId.isBlank()) {
      throw new IllegalArgumentException("會話 ID 不能為空");
    }

    String[] parts = conversationId.split(":", -1);
    for (String part : parts) {
      if (part == null || part.isBlank()) {
        throw new IllegalArgumentException("無效的會話 ID 格式: " + conversationId + "（包含空白段）");
      }
    }
    return switch (parts.length) {
      case 3 -> ConversationIdStrategy.THREAD_LEVEL;
      case 4 -> ConversationIdStrategy.MESSAGE_LEVEL;
      default ->
          throw new IllegalArgumentException("無效的會話 ID 格式: " + conversationId + "（預期 3 或 4 段）");
    };
  }

  /**
   * 檢查會話 ID 是否為討論串級別。
   *
   * @param conversationId 會話 ID
   * @return 如果是討論串級別返回 {@code true}
   */
  public static boolean isThreadLevel(String conversationId) {
    return parseStrategy(conversationId) == ConversationIdStrategy.THREAD_LEVEL;
  }
}
