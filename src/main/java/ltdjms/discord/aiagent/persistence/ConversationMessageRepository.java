package ltdjms.discord.aiagent.persistence;

import java.util.List;

import ltdjms.discord.aiagent.domain.ConversationMessage;

/**
 * 會話訊息 Repository 介面。
 *
 * <p>定義會話訊息持久化的操作介面，用於存儲和檢索對話訊息。
 */
public interface ConversationMessageRepository {

  /**
   * 根據會話 ID 查找訊息列表。
   *
   * <p>訊息會根據 token 限制自動截斷，只保留最新的訊息。
   *
   * @param conversationId 會話 ID
   * @param maxTokens 最大 token 限制（用於截斷）
   * @return 訊息列表（已按時間排序）
   */
  List<ConversationMessage> findByConversationId(String conversationId, int maxTokens);

  /**
   * 保存訊息。
   *
   * @param conversationId 會話 ID
   * @param message 訊息實體
   * @return 保存後的訊息（包含生成的 ID）
   */
  ConversationMessage save(String conversationId, ConversationMessage message);
}
