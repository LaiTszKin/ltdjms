package ltdjms.discord.aiagent.persistence;

import java.time.Instant;
import java.util.Optional;

import ltdjms.discord.aiagent.domain.AgentConversation;

/**
 * 會話 Repository 介面。
 *
 * <p>定義會話持久化的操作介面，用於存儲和檢索 AI Agent 對話會話。
 */
public interface ConversationRepository {

  /**
   * 根據 ID 查找會話。
   *
   * @param conversationId 會話 ID
   * @return 會話實例，如果不存在則返回空
   */
  Optional<AgentConversation> findById(String conversationId);

  /**
   * 保存新會話或更新現有會話。
   *
   * <p>使用 UPSERT 語義：如果會話已存在則更新，否則插入新記錄。
   *
   * @param conversation 會話實體
   * @return 保存後的會話
   */
  AgentConversation save(AgentConversation conversation);

  /**
   * 更新會話活動時間。
   *
   * <p>每次會話有新活動時調用，同時增加迭代次數。
   *
   * @param conversationId 會話 ID
   * @param lastActivity 活動時間
   */
  void updateActivity(String conversationId, Instant lastActivity);

  /**
   * 刪除會話。
   *
   * <p>會連帶刪除該會話的所有訊息（CASCADE）。
   *
   * @param conversationId 會話 ID
   */
  void deleteById(String conversationId);
}
