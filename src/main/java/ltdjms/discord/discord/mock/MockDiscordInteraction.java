package ltdjms.discord.discord.mock;

import java.util.ArrayList;
import java.util.List;

import ltdjms.discord.discord.domain.DiscordInteraction;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.InteractionHook;

/**
 * Discord 互動的 Mock 實作
 *
 * <p>此類別提供 {@link DiscordInteraction} 介面的測試用實作，用於單元測試中 追蹤和驗證互動行為，而不需要實際連接到 Discord API。
 *
 * <p>主要功能：
 *
 * <ul>
 *   <li>追蹤所有呼叫的 reply 訊息
 *   <li>追蹤所有呼叫的 replyEmbed
 *   <li>追蹤所有呼叫的 editEmbed
 *   <li>追蹤 deferReply 呼叫次數
 *   <li>提供方便的測試輔助方法
 * </ul>
 */
public class MockDiscordInteraction implements DiscordInteraction {

  private final long guildId;
  private final long userId;
  private final boolean ephemeral;
  private final InteractionHook hook;
  private boolean acknowledged;

  // 追蹤列表
  private final List<String> replyMessages = new ArrayList<>();
  private final List<MessageEmbed> replyEmbeds = new ArrayList<>();
  private final List<MessageEmbed> editedEmbeds = new ArrayList<>();
  private int deferReplyCount = 0;

  /**
   * 建構 Mock Discord 互動
   *
   * @param guildId Guild ID
   * @param userId 使用者 ID
   * @param hook InteractionHook（可為 null）
   */
  public MockDiscordInteraction(long guildId, long userId, InteractionHook hook) {
    this(guildId, userId, false, hook);
  }

  /**
   * 建構 Mock Discord 互動（含 ephemeral 設定）
   *
   * @param guildId Guild ID
   * @param userId 使用者 ID
   * @param ephemeral 是否為 ephemeral
   * @param hook InteractionHook（可為 null）
   */
  public MockDiscordInteraction(
      long guildId, long userId, boolean ephemeral, InteractionHook hook) {
    this.guildId = guildId;
    this.userId = userId;
    this.ephemeral = ephemeral;
    this.hook = hook;
    this.acknowledged = false;
  }

  @Override
  public long getGuildId() {
    return guildId;
  }

  @Override
  public long getUserId() {
    return userId;
  }

  @Override
  public boolean isEphemeral() {
    return ephemeral;
  }

  @Override
  public void reply(String message) {
    replyMessages.add(message);
    acknowledged = true;
  }

  @Override
  public void replyEmbed(MessageEmbed embed) {
    replyEmbeds.add(embed);
    acknowledged = true;
  }

  @Override
  public void editEmbed(MessageEmbed embed) {
    editedEmbeds.add(embed);
  }

  @Override
  public void deferReply() {
    deferReplyCount++;
    acknowledged = true;
  }

  @Override
  public InteractionHook getHook() {
    return hook;
  }

  @Override
  public boolean isAcknowledged() {
    return acknowledged;
  }

  // ========== 測試輔助方法 ==========

  /**
   * 取得所有 reply 訊息列表
   *
   * @return reply 訊息列表（不可變）
   */
  public List<String> getReplyMessages() {
    return new ArrayList<>(replyMessages);
  }

  /**
   * 取得所有 replyEmbed 列表
   *
   * @return replyEmbed 列表（不可變）
   */
  public List<MessageEmbed> getReplyEmbeds() {
    return new ArrayList<>(replyEmbeds);
  }

  /**
   * 取得所有 editEmbed 列表
   *
   * @return editEmbed 列表（不可變）
   */
  public List<MessageEmbed> getEditedEmbeds() {
    return new ArrayList<>(editedEmbeds);
  }

  /**
   * 取得最後一次的 reply 訊息
   *
   * @return 最後一則 reply 訊息，如果沒有則返回 null
   */
  public String getLastReply() {
    if (replyMessages.isEmpty()) {
      return null;
    }
    return replyMessages.get(replyMessages.size() - 1);
  }

  /**
   * 取得最後一次的 replyEmbed
   *
   * @return 最後一個 replyEmbed，如果沒有則返回 null
   */
  public MessageEmbed getLastReplyEmbed() {
    if (replyEmbeds.isEmpty()) {
      return null;
    }
    return replyEmbeds.get(replyEmbeds.size() - 1);
  }

  /**
   * 取得最後一次的 editEmbed
   *
   * @return 最後一個 editEmbed，如果沒有則返回 null
   */
  public MessageEmbed getLastEditedEmbed() {
    if (editedEmbeds.isEmpty()) {
      return null;
    }
    return editedEmbeds.get(editedEmbeds.size() - 1);
  }

  /**
   * 取得 deferReply 呼叫次數
   *
   * @return deferReply 呼叫次數
   */
  public int getDeferReplyCount() {
    return deferReplyCount;
  }

  /**
   * 取得 reply 呼叫次數
   *
   * @return reply 呼叫次數
   */
  public int getReplyCount() {
    return replyMessages.size();
  }

  /**
   * 取得 replyEmbed 呼叫次數
   *
   * @return replyEmbed 呼叫次數
   */
  public int getReplyEmbedCount() {
    return replyEmbeds.size();
  }

  /**
   * 取得 editEmbed 呼叫次數
   *
   * @return editEmbed 呼叫次數
   */
  public int getEditEmbedCount() {
    return editedEmbeds.size();
  }

  /**
   * 檢查是否有任何 reply
   *
   * @return true 如果有至少一個 reply
   */
  public boolean hasReplies() {
    return !replyMessages.isEmpty();
  }

  /**
   * 檢查是否有任何 replyEmbed
   *
   * @return true 如果有至少一個 replyEmbed
   */
  public boolean hasReplyEmbeds() {
    return !replyEmbeds.isEmpty();
  }

  /**
   * 檢查是否有任何 editEmbed
   *
   * @return true 如果有至少一個 editEmbed
   */
  public boolean hasEditedEmbeds() {
    return !editedEmbeds.isEmpty();
  }

  /**
   * 檢查是否有 deferReply 呼叫
   *
   * @return true 如果有至少一次 deferReply
   */
  public boolean hasDeferred() {
    return deferReplyCount > 0;
  }

  /**
   * 清除所有追蹤的資料
   *
   * <p>此方法會清除所有追蹤的訊息和 Embed，但會保留 Guild ID、User ID、 ephemeral 狀態和 Hook。
   */
  public void clear() {
    replyMessages.clear();
    replyEmbeds.clear();
    editedEmbeds.clear();
    deferReplyCount = 0;
    acknowledged = false;
  }
}
