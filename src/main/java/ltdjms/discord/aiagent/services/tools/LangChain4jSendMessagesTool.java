package ltdjms.discord.aiagent.services.tools;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import ltdjms.discord.shared.di.JDAProvider;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;

/** 發送訊息到指定頻道工具。 */
public final class LangChain4jSendMessagesTool {

  private static final Logger LOGGER = LoggerFactory.getLogger(LangChain4jSendMessagesTool.class);

  private static final int MAX_CHANNELS = 10;
  private static final int MAX_MESSAGES = 10;
  private static final int MAX_MESSAGE_LENGTH = 2000;
  private static final int ACTION_TIMEOUT_SECONDS = 10;

  @Inject
  public LangChain4jSendMessagesTool() {
    // JDA 將從 JDAProvider 延遲獲取
  }

  /**
   * 發送單則或多則訊息到指定的 Discord 頻道（支援一次多個頻道）。
   *
   * @param channelIds 目標頻道 ID 列表（可選，未提供時預設使用當前頻道）
   * @param message 單則訊息（可選）
   * @param messages 多則訊息（可選）
   * @param parameters 調用參數（包含 guildId、channelId、userId）
   * @return 執行結果 JSON 字串
   */
  @Tool(
      """
      發送訊息到一個或多個 Discord 頻道。

      使用場景：
      - 需要同時通知多個頻道時
      - 需要在指定頻道批次發送訊息時

      參數說明：
      - channelIds：目標頻道 ID 列表（可省略，省略時使用當前頻道）
      - message：單則訊息內容
      - messages：多則訊息內容列表

      規則：
      - 至少需要提供 message 或 messages 其中之一
      - 若 message 與 messages 同時提供，會合併後依序發送
      - 會將每則訊息發送到每個目標頻道
      """)
  public String sendMessages(
      @P(
              value =
                  """
                  目標頻道 ID 列表。

                  可同時指定多個頻道。
                  支援格式：
                  - 純 ID："123456789012345678"
                  - 頻道提及："<#123456789012345678>"

                  若不提供，會使用當前對話頻道。
                  """,
              required = false)
          List<String> channelIds,
      @P(value = "單則要發送的訊息內容。", required = false) String message,
      @P(value = "多則要發送的訊息內容列表。", required = false) List<String> messages,
      InvocationParameters parameters) {

    Long guildId = parameters.get("guildId");
    Long currentChannelId = parameters.get("channelId");
    if (guildId == null) {
      LOGGER.error("LangChain4jSendMessagesTool: guildId 未設置");
      return ToolJsonResponses.error("guildId 未設置");
    }

    Guild guild = JDAProvider.getJda().getGuildById(guildId);
    if (guild == null) {
      LOGGER.warn("LangChain4jSendMessagesTool: 找不到指定的伺服器: {}", guildId);
      return ToolJsonResponses.error("找不到伺服器");
    }

    String authorizationError =
        ToolCallerAuthorizationGuard.validateAdministrator(
            parameters, guild, LOGGER, "LangChain4jSendMessagesTool");
    if (authorizationError != null) {
      return ToolJsonResponses.error(authorizationError);
    }

    List<String> normalizedChannelIds = normalizeChannelIds(channelIds, currentChannelId);
    if (normalizedChannelIds.isEmpty()) {
      return ToolJsonResponses.error("未提供有效的 channelIds，且當前頻道不可用");
    }
    if (normalizedChannelIds.size() > MAX_CHANNELS) {
      return ToolJsonResponses.error(String.format("一次最多可指定 %d 個頻道", MAX_CHANNELS));
    }

    List<String> normalizedMessages = normalizeMessages(message, messages);
    if (normalizedMessages.isEmpty()) {
      return ToolJsonResponses.error("請至少提供一則非空白訊息（message 或 messages）");
    }
    if (normalizedMessages.size() > MAX_MESSAGES) {
      return ToolJsonResponses.error(String.format("一次最多可發送 %d 則訊息", MAX_MESSAGES));
    }

    for (String content : normalizedMessages) {
      if (content.length() > MAX_MESSAGE_LENGTH) {
        return ToolJsonResponses.error(String.format("訊息長度不可超過 %d 字元", MAX_MESSAGE_LENGTH));
      }
    }

    List<ChannelSendResult> results = new ArrayList<>();
    int totalMessagesSent = 0;

    for (String rawChannelId : normalizedChannelIds) {
      Long targetChannelId = parseSnowflakeId(rawChannelId);
      if (targetChannelId == null) {
        results.add(new ChannelSendResult(rawChannelId, null, false, List.of(), "無效的頻道 ID 格式"));
        continue;
      }

      GuildChannel guildChannel = guild.getGuildChannelById(targetChannelId);
      if (guildChannel == null) {
        results.add(
            new ChannelSendResult(
                String.valueOf(targetChannelId), null, false, List.of(), "找不到指定頻道"));
        continue;
      }

      if (!(guildChannel instanceof GuildMessageChannel messageChannel)) {
        results.add(
            new ChannelSendResult(
                String.valueOf(targetChannelId),
                guildChannel.getName(),
                false,
                List.of(),
                "該頻道類型不支援發送訊息"));
        continue;
      }

      try {
        List<String> sentMessageIds = new ArrayList<>();
        for (String content : normalizedMessages) {
          Message sent =
              messageChannel
                  .sendMessage(content)
                  .submit()
                  .get(ACTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
          sentMessageIds.add(String.valueOf(sent.getIdLong()));
        }

        totalMessagesSent += sentMessageIds.size();
        results.add(
            new ChannelSendResult(
                String.valueOf(targetChannelId),
                guildChannel.getName(),
                true,
                sentMessageIds,
                null));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        results.add(
            new ChannelSendResult(
                String.valueOf(targetChannelId),
                guildChannel.getName(),
                false,
                List.of(),
                "發送訊息被中斷"));
      } catch (Exception e) {
        results.add(
            new ChannelSendResult(
                String.valueOf(targetChannelId),
                guildChannel.getName(),
                false,
                List.of(),
                "發送失敗: " + extractErrorMessage(e)));
      }
    }

    boolean hasSuccess = results.stream().anyMatch(ChannelSendResult::success);
    return buildResultJson(
        hasSuccess,
        normalizedChannelIds.size(),
        normalizedMessages.size(),
        totalMessagesSent,
        results);
  }

  private List<String> normalizeChannelIds(List<String> channelIds, Long currentChannelId) {
    Set<String> deduplicated = new LinkedHashSet<>();

    if (channelIds != null) {
      for (String channelId : channelIds) {
        if (channelId == null) {
          continue;
        }
        String trimmed = channelId.trim();
        if (!trimmed.isEmpty()) {
          deduplicated.add(trimmed);
        }
      }
    }

    if (deduplicated.isEmpty() && currentChannelId != null) {
      deduplicated.add(String.valueOf(currentChannelId));
    }

    return new ArrayList<>(deduplicated);
  }

  private List<String> normalizeMessages(String message, List<String> messages) {
    List<String> merged = new ArrayList<>();

    if (message != null && !message.isBlank()) {
      merged.add(message.trim());
    }

    if (messages != null) {
      for (String item : messages) {
        if (item == null || item.isBlank()) {
          continue;
        }
        merged.add(item.trim());
      }
    }

    return merged;
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

  private String buildResultJson(
      boolean success,
      int requestedChannels,
      int requestedMessages,
      int totalMessagesSent,
      List<ChannelSendResult> results) {

    int successfulChannels = 0;
    for (ChannelSendResult result : results) {
      if (result.success()) {
        successfulChannels++;
      }
    }

    StringBuilder json = new StringBuilder();
    json.append("{\n");
    json.append("  \"success\": ").append(success).append(",\n");
    json.append("  \"requestedChannels\": ").append(requestedChannels).append(",\n");
    json.append("  \"requestedMessages\": ").append(requestedMessages).append(",\n");
    json.append("  \"successfulChannels\": ").append(successfulChannels).append(",\n");
    json.append("  \"failedChannels\": ").append(results.size() - successfulChannels).append(",\n");
    json.append("  \"totalMessagesSent\": ").append(totalMessagesSent).append(",\n");
    json.append("  \"results\": [\n");

    for (int i = 0; i < results.size(); i++) {
      ChannelSendResult result = results.get(i);
      if (i > 0) {
        json.append(",\n");
      }

      json.append("    {\n");
      json.append("      \"channelId\": \"")
          .append(ToolJsonResponses.escapeJson(result.channelId()))
          .append("\",\n");
      json.append("      \"channelName\": \"")
          .append(ToolJsonResponses.escapeJson(nullToEmpty(result.channelName())))
          .append("\",\n");
      json.append("      \"success\": ").append(result.success());

      if (result.success()) {
        json.append(",\n");
        json.append("      \"sentMessageIds\": ").append(stringListToJson(result.sentMessageIds()));
      } else {
        json.append(",\n");
        json.append("      \"error\": \"")
            .append(ToolJsonResponses.escapeJson(nullToEmpty(result.error())))
            .append("\"");
      }

      json.append("\n    }");
    }

    json.append("\n  ]\n");
    json.append("}");
    return json.toString();
  }

  private String stringListToJson(List<String> values) {
    if (values == null || values.isEmpty()) {
      return "[]";
    }

    StringBuilder json = new StringBuilder("[");
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) {
        json.append(", ");
      }
      json.append("\"").append(ToolJsonResponses.escapeJson(values.get(i))).append("\"");
    }
    json.append("]");
    return json.toString();
  }

  private String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private record ChannelSendResult(
      String channelId,
      String channelName,
      boolean success,
      List<String> sentMessageIds,
      String error) {}
}
