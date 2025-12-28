package ltdjms.discord.aichat.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.aichat.services.AIChatService;
import ltdjms.discord.shared.DomainError;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/** AI 聊天提及監聽器，處理使用者提及機器人的訊息。 */
public class AIChatMentionListener extends ListenerAdapter {

  private static final Logger LOGGER = LoggerFactory.getLogger(AIChatMentionListener.class);

  private final AIChatService aiChatService;

  /**
   * 創建 AIChatMentionListener。
   *
   * @param aiChatService AI 聊天服務
   */
  public AIChatMentionListener(AIChatService aiChatService) {
    this.aiChatService = aiChatService;
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

    String channelId = event.getChannel().getId();
    String userId = event.getAuthor().getId();
    var channel = event.getChannel();

    LOGGER.info("Bot mentioned by user {} in channel {}: {}", userId, channelId, userMessage);

    // Generate AI streaming response
    // 先發送初始提示訊息
    channel
        .sendMessage(":thought_balloon: AI 正在思考...")
        .queue(
            thinkingMessage -> {
              // 使用陣列來追蹤是否為第一個片段
              boolean[] isFirstChunk = {true};

              aiChatService.generateStreamingResponse(
                  channelId,
                  userId,
                  userMessage,
                  (chunk, isComplete, error) -> {
                    if (error != null) {
                      // 編輯初始訊息為錯誤訊息
                      thinkingMessage.editMessage(getErrorMessage(error)).queue();
                      LOGGER.warn("AI streaming error: {} - {}", error.category(), error.message());
                      return;
                    }

                    if (chunk != null && !chunk.isEmpty()) {
                      if (isFirstChunk[0]) {
                        // 編輯初始訊息為第一個片段
                        thinkingMessage.editMessage(chunk).queue();
                        isFirstChunk[0] = false;
                      } else {
                        // 發送新片段
                        channel.sendMessage(chunk).queue();
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
}
