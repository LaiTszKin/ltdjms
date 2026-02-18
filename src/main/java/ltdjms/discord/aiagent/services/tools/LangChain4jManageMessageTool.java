package ltdjms.discord.aiagent.services.tools;

import java.util.Locale;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import ltdjms.discord.shared.di.JDAProvider;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;

/** 管理指定訊息狀態（pin/delete/edit）工具。 */
public final class LangChain4jManageMessageTool {

  private static final Logger LOGGER = LoggerFactory.getLogger(LangChain4jManageMessageTool.class);
  private static final int MAX_MESSAGE_CONTENT_LENGTH = 2000;

  @Inject
  public LangChain4jManageMessageTool() {
    // JDA 將從 JDAProvider 延遲獲取
  }

  /**
   * 管理指定訊息狀態。
   *
   * @param messageId 目標訊息 ID
   * @param action 操作（pin、delete、edit）
   * @param channelId 頻道 ID（可選，未提供時使用當前頻道）
   * @param newContent 新內容（action=edit 時必填）
   * @param parameters 調用參數（包含 guildId、channelId、userId）
   * @return 執行結果 JSON 字串
   */
  @Tool(
      """
      管理指定訊息，可執行 pin、delete、edit 三種操作。

      使用場景：
      - pin：釘選指定訊息
      - delete：刪除指定訊息
      - edit：編輯指定訊息內容

      參數說明：
      - messageId：目標訊息 ID（必要）
      - action：操作類型，必須是 pin/delete/edit
      - channelId：訊息所在頻道 ID（可省略，省略時使用當前頻道）
      - newContent：只有 action=edit 時需要提供
      """)
  public String manageMessage(
      @P(value = "目標訊息 ID。", required = true) String messageId,
      @P(value = "操作類型，必須是 pin、delete、edit 之一。", required = true) String action,
      @P(value = "目標頻道 ID（可選，未提供時使用當前頻道）。", required = false) String channelId,
      @P(value = "新的訊息內容（僅 action=edit 時需要）。", required = false) String newContent,
      InvocationParameters parameters) {

    if (messageId == null || messageId.isBlank()) {
      return ToolJsonResponses.error("messageId 不能為空");
    }
    if (action == null || action.isBlank()) {
      return ToolJsonResponses.error("action 不能為空");
    }

    String normalizedAction = action.trim().toLowerCase(Locale.ROOT);
    if (!"pin".equals(normalizedAction)
        && !"delete".equals(normalizedAction)
        && !"edit".equals(normalizedAction)) {
      return ToolJsonResponses.error("action 必須是 pin、delete 或 edit");
    }

    Long guildId = parameters.get("guildId");
    Long currentChannelId = parameters.get("channelId");
    if (guildId == null) {
      LOGGER.error("LangChain4jManageMessageTool: guildId 未設置");
      return ToolJsonResponses.error("guildId 未設置");
    }

    Long targetChannelId = resolveChannelId(channelId, currentChannelId);
    if (targetChannelId == null) {
      return ToolJsonResponses.error("無效的 channelId，且當前頻道不可用");
    }

    Long targetMessageId = parseSnowflakeId(messageId);
    if (targetMessageId == null) {
      return ToolJsonResponses.error("無效的 messageId 格式");
    }

    Guild guild = JDAProvider.getJda().getGuildById(guildId);
    if (guild == null) {
      LOGGER.warn("LangChain4jManageMessageTool: 找不到指定的伺服器: {}", guildId);
      return ToolJsonResponses.error("找不到伺服器");
    }

    String authorizationError =
        ToolCallerAuthorizationGuard.validateAdministrator(
            parameters, guild, LOGGER, "LangChain4jManageMessageTool");
    if (authorizationError != null) {
      return ToolJsonResponses.error(authorizationError);
    }

    GuildChannel guildChannel = guild.getGuildChannelById(targetChannelId);
    if (guildChannel == null) {
      return ToolJsonResponses.error("找不到指定頻道");
    }
    if (!(guildChannel instanceof GuildMessageChannel messageChannel)) {
      return ToolJsonResponses.error("該頻道類型不支援訊息管理");
    }

    try {
      String editedContent = null;
      switch (normalizedAction) {
        case "pin" -> messageChannel.pinMessageById(targetMessageId).complete();
        case "delete" -> messageChannel.deleteMessageById(targetMessageId).complete();
        case "edit" -> {
          String normalizedContent = normalizeEditedContent(newContent);
          if (normalizedContent == null) {
            return ToolJsonResponses.error("action=edit 時，newContent 不能為空");
          }
          if (normalizedContent.length() > MAX_MESSAGE_CONTENT_LENGTH) {
            return ToolJsonResponses.error(
                String.format("newContent 長度不可超過 %d 字元", MAX_MESSAGE_CONTENT_LENGTH));
          }
          messageChannel.editMessageById(targetMessageId, normalizedContent).complete();
          editedContent = normalizedContent;
        }
        default -> {
          return ToolJsonResponses.error("不支援的 action");
        }
      }

      return buildSuccessJson(targetChannelId, targetMessageId, normalizedAction, editedContent);
    } catch (Exception e) {
      LOGGER.warn(
          "LangChain4jManageMessageTool: action={} 失敗, channelId={}, messageId={}",
          normalizedAction,
          targetChannelId,
          targetMessageId,
          e);
      return ToolJsonResponses.error("操作失敗: " + extractErrorMessage(e));
    }
  }

  private Long resolveChannelId(String explicitChannelId, Long currentChannelId) {
    if (explicitChannelId != null && !explicitChannelId.isBlank()) {
      return parseSnowflakeId(explicitChannelId);
    }
    return currentChannelId;
  }

  private Long parseSnowflakeId(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }

    String value = raw.trim();

    if (value.startsWith("<#") && value.endsWith(">")) {
      value = value.substring(2, value.length() - 1);
    } else if (value.startsWith("<") && value.endsWith(">")) {
      value = value.substring(1, value.length() - 1);
    }

    int slashIndex = value.lastIndexOf('/');
    if (slashIndex >= 0 && slashIndex < value.length() - 1) {
      value = value.substring(slashIndex + 1);
    }

    try {
      return Long.parseUnsignedLong(value);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private String normalizeEditedContent(String content) {
    if (content == null) {
      return null;
    }
    String trimmed = content.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private String extractErrorMessage(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null) {
      current = current.getCause();
    }

    String message = current.getMessage();
    if (message == null || message.isBlank()) {
      return current.getClass().getSimpleName();
    }
    return current.getClass().getSimpleName() + ": " + message;
  }

  private String buildSuccessJson(
      long channelId, long messageId, String action, String newContentForEdit) {
    StringBuilder json = new StringBuilder();
    json.append("{\n");
    json.append("  \"success\": true,\n");
    json.append("  \"message\": \"操作成功\",\n");
    json.append("  \"channelId\": \"").append(channelId).append("\",\n");
    json.append("  \"messageId\": \"").append(messageId).append("\",\n");
    json.append("  \"action\": \"").append(ToolJsonResponses.escapeJson(action)).append("\"");

    if ("edit".equals(action)) {
      json.append(",\n");
      json.append("  \"contentPreview\": \"")
          .append(ToolJsonResponses.escapeJson(trimPreview(newContentForEdit)))
          .append("\"");
    }

    json.append("\n}");
    return json.toString();
  }

  private String trimPreview(String content) {
    if (content == null) {
      return "";
    }
    String normalized = content.replace('\n', ' ').replace('\r', ' ').trim();
    if (normalized.length() <= 120) {
      return normalized;
    }
    return normalized.substring(0, 120) + "...";
  }
}
