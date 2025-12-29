package ltdjms.discord.aichat.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.aichat.services.AIChannelRestrictionService;
import ltdjms.discord.aichat.services.AIChatService;
import ltdjms.discord.aichat.services.StreamingResponseHandler;
import ltdjms.discord.shared.DomainError;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/** AI 聊天提及監聽器，處理使用者提及機器人的訊息。 */
public class AIChatMentionListener extends ListenerAdapter {

  private static final Logger LOGGER = LoggerFactory.getLogger(AIChatMentionListener.class);
  private static final String SPOILER_PREFIX = "-# ";

  /** Reasoning 訊息追蹤器，用於追蹤並刪除所有 reasoning 訊息。 */
  static class ReasoningMessageTracker {
    private Message initialMessage; // 初始「思考中」訊息（可能被編輯為 reasoning）
    private final List<Message> reasoningMessages = new ArrayList<>();
    private final AtomicBoolean deletionRequested = new AtomicBoolean(false);

    void setInitialMessage(Message message) {
      if (message == null) {
        return;
      }
      if (deletionRequested.get()) {
        deleteMessage(message, null);
        return;
      }
      this.initialMessage = message;
    }

    void addReasoningMessage(Message message) {
      if (message == null) {
        return;
      }
      if (deletionRequested.get()) {
        deleteMessage(message, null);
        return;
      }
      reasoningMessages.add(message);
    }

    void deleteAll(Runnable completionCallback) {
      deletionRequested.set(true);
      List<Message> allMessages = new ArrayList<>();
      if (initialMessage != null) {
        allMessages.add(initialMessage);
      }
      allMessages.addAll(reasoningMessages);
      reasoningMessages.clear();
      initialMessage = null;

      if (allMessages.isEmpty()) {
        completionCallback.run();
        return;
      }

      AtomicInteger deletedCount = new AtomicInteger(0);
      int totalMessages = allMessages.size();

      for (Message message : allMessages) {
        deleteMessage(
            message, () -> checkCompletion(deletedCount, totalMessages, completionCallback));
      }
    }

    private void deleteMessage(Message message, Runnable completionCallback) {
      message
          .delete()
          .queue(
              (v) -> {
                if (completionCallback != null) {
                  completionCallback.run();
                }
              },
              (e) -> {
                LOGGER.warn("刪除 reasoning 訊息失敗: {}", e.getMessage());
                if (completionCallback != null) {
                  completionCallback.run();
                }
              });
    }

    private void checkCompletion(AtomicInteger count, int total, Runnable callback) {
      if (count.incrementAndGet() == total) {
        callback.run();
      }
    }
  }

  private final AIChatService aiChatService;
  private final AIChannelRestrictionService channelRestrictionService;

  /**
   * 創建 AIChatMentionListener。
   *
   * @param aiChatService AI 聊天服務
   * @param channelRestrictionService AI 頻道限制服務
   */
  public AIChatMentionListener(
      AIChatService aiChatService, AIChannelRestrictionService channelRestrictionService) {
    this.aiChatService = aiChatService;
    this.channelRestrictionService = channelRestrictionService;
  }

  @Override
  public void onMessageReceived(MessageReceivedEvent event) {
    // Ignore bot messages
    if (event.getAuthor().isBot()) {
      return;
    }

    // Ignore DMs
    if (!event.isFromGuild()) {
      return;
    }

    // Early channel check: silently ignore if channel not allowed
    long guildId = event.getGuild().getIdLong();
    long channelId = event.getChannel().getIdLong();
    if (!channelRestrictionService.isChannelAllowed(guildId, channelId)) {
      LOGGER.debug(
          "Channel {} not in allowed list for guild {}, ignoring mention", channelId, guildId);
      return;
    }

    String message = event.getMessage().getContentRaw();
    String botId = event.getJDA().getSelfUser().getId();
    String botMention = "<@" + botId + ">";
    String botNicknameMention = "<@!" + botId + ">";

    // Check if bot is mentioned
    if (!message.contains(botMention) && !message.contains(botNicknameMention)) {
      return;
    }

    // Remove mention and extract user message
    String originalUserMessage =
        message.replace(botMention, "").replace(botNicknameMention, "").trim();

    // If message is empty, use default greeting
    String userMessage = (originalUserMessage.isBlank()) ? "你好" : originalUserMessage;

    String channelIdStr = event.getChannel().getId();
    String userId = event.getAuthor().getId();
    var channel = event.getChannel();

    LOGGER.info("Bot mentioned by user {} in channel {}: {}", userId, channelIdStr, userMessage);

    // Generate AI streaming response
    // 先發送初始提示訊息
    channel
        .sendMessage(":thought_balloon: AI 正在思考...")
        .queue(
            thinkingMessage -> {
              // 新增追蹤器和狀態變數
              final ReasoningMessageTracker reasoningTracker = new ReasoningMessageTracker();
              reasoningTracker.setInitialMessage(thinkingMessage);

              final boolean[] isFirstChunk = {true};
              final boolean[] hasReasoning = {false};
              final AtomicBoolean reasoningDeleted = new AtomicBoolean(false);

              aiChatService.generateStreamingResponse(
                  channelIdStr,
                  userId,
                  userMessage,
                  (chunk, isComplete, error, type) -> {
                    if (error != null) {
                      // 編輯初始訊息為錯誤訊息
                      thinkingMessage.editMessage(getErrorMessage(error)).queue();
                      LOGGER.warn("AI streaming error: {} - {}", error.category(), error.message());
                      return;
                    }

                    if (chunk != null && !chunk.isEmpty()) {
                      String formattedChunk = chunk;

                      // 處理 CONTENT 類型片段
                      if (type == StreamingResponseHandler.ChunkType.CONTENT) {
                        // 首次收到 CONTENT 時刪除所有 reasoning
                        if (hasReasoning[0] && reasoningDeleted.compareAndSet(false, true)) {
                          reasoningTracker.deleteAll(
                              () -> {
                                LOGGER.debug("已刪除所有 reasoning 訊息");
                              });
                        }

                        if (isFirstChunk[0]) {
                          thinkingMessage.editMessage(formattedChunk).queue();
                          isFirstChunk[0] = false;
                        } else {
                          channel.sendMessage(formattedChunk).queue();
                        }
                        return;
                      }

                      // 處理 REASONING 類型片段
                      if (type == StreamingResponseHandler.ChunkType.REASONING) {
                        formattedChunk = formatAsSpoiler(chunk);
                        hasReasoning[0] = true;

                        if (isFirstChunk[0]) {
                          // 編輯初始訊息為第一個 reasoning 片段
                          thinkingMessage.editMessage(formattedChunk).queue();
                          isFirstChunk[0] = false;
                        } else {
                          // 發送新的 reasoning 訊息並追蹤
                          channel
                              .sendMessage(formattedChunk)
                              .queue(
                                  sentMessage -> reasoningTracker.addReasoningMessage(sentMessage));
                        }
                      }
                    }

                    if (isComplete) {
                      LOGGER.info("AI streaming completed");
                    }
                  });
            });
  }

  private String getErrorMessage(DomainError error) {
    return switch (error.category()) {
      case AI_SERVICE_AUTH_FAILED -> ":x: AI 服務認證失敗，請聯絡管理員";
      case AI_SERVICE_RATE_LIMITED -> ":timer: AI 服務暫時忙碌，請稍後再試";
      case AI_SERVICE_TIMEOUT -> ":hourglass: AI 服務連線逾時，請稍後再試";
      case AI_SERVICE_UNAVAILABLE -> ":warning: AI 服務暫時無法使用";
      case AI_RESPONSE_EMPTY -> ":question: AI 沒有產生回應";
      case AI_RESPONSE_INVALID -> ":warning: AI 回應格式錯誤";
      default -> ":warning: 發生錯誤：" + error.message();
    };
  }

  /**
   * 將內容格式化為 Discord 小字體（spoiler）。
   *
   * @param content 原始內容
   * @return 格式化後的內容（帶 `-# ` 前綴）
   */
  private String formatAsSpoiler(String content) {
    if (content == null || content.isEmpty()) {
      return content;
    }
    // 防止重複添加前綴
    if (content.startsWith(SPOILER_PREFIX)) {
      return content;
    }
    return SPOILER_PREFIX + content;
  }
}
