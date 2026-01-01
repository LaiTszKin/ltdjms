package ltdjms.discord.aiagent.services;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;

/**
 * Discord Thread 歷史訊息提供者。
 *
 * <p>從 Discord Thread 獲取歷史訊息並轉換為 LangChain4J ChatMessage 格式。
 *
 * <h2>功能特性</h2>
 *
 * <ul>
 *   <li>動態獲取：使用 JDA API 即時獲取 Thread 歷史
 *   <li>雙向保留：同時保留用戶訊息和 AI 回應
 *   <li>過濾機器人：排除機器人自己的訊息
 *   <li>Token 限制：使用 TokenEstimator 裁剪訊息
 * </ul>
 */
public final class DiscordThreadHistoryProvider {

  private static final Logger LOG = LoggerFactory.getLogger(DiscordThreadHistoryProvider.class);

  private final int maxMessages;
  private final TokenEstimator tokenEstimator;

  /**
   * 建立 Discord Thread 歷史訊息提供者。
   *
   * <p>JDA 實例會在使用時從 JDAProvider 延遲獲取，避免在 Dagger 初始化時就要求 JDA 實例存在。
   *
   * @param maxMessages 最大獲取訊息數量
   * @param tokenEstimator Token 估算器
   */
  public DiscordThreadHistoryProvider(int maxMessages, TokenEstimator tokenEstimator) {
    this.maxMessages = maxMessages;
    this.tokenEstimator = tokenEstimator;
  }

  /**
   * 獲取 JDA 實例。
   *
   * @return JDA 實例
   */
  private JDA getJda() {
    return ltdjms.discord.shared.di.JDAProvider.getJda();
  }

  /**
   * 獲取 Discord Thread 的歷史訊息。
   *
   * @param guildId Discord 伺服器 ID
   * @param threadId Discord Thread ID
   * @param botUserId 機器人用戶 ID（用於過濾）
   * @return ChatMessage 列表（按時間排序，從舊到新）
   */
  public List<ChatMessage> getThreadHistory(long guildId, long threadId, long botUserId) {
    Guild guild = getJda().getGuildById(guildId);
    if (guild == null) {
      LOG.warn("找不到伺服器: guildId={}", guildId);
      return List.of();
    }

    ThreadChannel threadChannel = guild.getThreadChannelById(threadId);
    if (threadChannel == null) {
      LOG.warn("找不到 Thread: guildId={}, threadId={}", guildId, threadId);
      return List.of();
    }

    try {
      // 使用 JDA API 獲取歷史訊息（避免在 callback thread 調用 complete()）
      List<Message> discordMessages =
          threadChannel.getHistory().retrievePast(maxMessages).submit().get(5, TimeUnit.SECONDS);

      LOG.debug("從 Thread {} 獲取 {} 則訊息", threadId, discordMessages.size());

      // 轉換為 ChatMessage 格式
      List<ChatMessage> chatMessages = convertToChatMessages(discordMessages, botUserId);

      // 使用 TokenEstimator 裁剪
      List<ChatMessage> trimmed = trimByTokens(chatMessages);

      LOG.debug(
          "Thread {} 最終保留 {} 則訊息（過濾後 {}, 裁剪後 {}）",
          threadId,
          trimmed.size(),
          chatMessages.size(),
          trimmed.size());

      return trimmed;

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.error("獲取 Thread 歷史被中斷: guildId={}, threadId={}", guildId, threadId, e);
      return List.of();
    } catch (Exception e) {
      LOG.error("獲取 Thread 歷史失敗: guildId={}, threadId={}", guildId, threadId, e);
      return List.of();
    }
  }

  /**
   * 將 Discord 訊息轉換為 ChatMessage 格式。
   *
   * @param discordMessages Discord 訊息列表
   * @param botUserId 機器人用戶 ID
   * @return ChatMessage 列表
   */
  private List<ChatMessage> convertToChatMessages(List<Message> discordMessages, long botUserId) {
    List<ChatMessage> chatMessages = new ArrayList<>();

    for (Message msg : discordMessages) {
      // 過濾非預設類型的訊息（只保留一般訊息）
      if (msg.getType() != MessageType.DEFAULT) {
        continue;
      }

      String content = msg.getContentDisplay();
      if (content.isBlank()) {
        continue;
      }

      // 根據作者類型決定訊息類型
      if (msg.getAuthor().isBot()) {
        // AI 回應（其他機器人的訊息）
        chatMessages.add(AiMessage.from(content));
      } else {
        // 用戶訊息
        chatMessages.add(UserMessage.from(content));
      }
    }

    return chatMessages;
  }

  /**
   * 根據 Token 限制裁剪訊息。
   *
   * <p>策略：從最新訊息開始保留，確保多輪對話的連續性。
   *
   * @param messages 訊息列表
   * @return 裁剪後的訊息列表
   */
  private List<ChatMessage> trimByTokens(List<ChatMessage> messages) {
    int maxTokens = tokenEstimator.getMaxTokens();
    int currentTokens = 0;
    List<ChatMessage> result = new ArrayList<>();

    // 從最新訊息開始保留
    for (int i = messages.size() - 1; i >= 0; i--) {
      ChatMessage msg = messages.get(i);
      int msgTokens = estimateTokens(msg);

      if (currentTokens + msgTokens > maxTokens && !result.isEmpty()) {
        break;
      }

      result.add(0, msg);
      currentTokens += msgTokens;
    }

    return result;
  }

  /**
   * 估算訊息的 token 數量。
   *
   * @param message ChatMessage
   * @return 估算的 token 數量
   */
  private int estimateTokens(ChatMessage message) {
    // 根據訊息類型獲取文字內容
    String text;
    if (message instanceof UserMessage userMsg) {
      text = userMsg.singleText();
    } else if (message instanceof AiMessage aiMsg) {
      text = aiMsg.text();
    } else {
      text = "";
    }

    // 簡單估算：字元數 ÷ 4 + 開銷
    int contentTokens = (text.length() + 3) / 4;

    // 根據訊息類型添加開銷
    if (message instanceof UserMessage) {
      return contentTokens + 10;
    } else if (message instanceof AiMessage) {
      return contentTokens + 10;
    } else {
      return contentTokens + 20;
    }
  }
}
