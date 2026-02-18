package ltdjms.discord.aiagent.services.tools;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;

/** 在指定頻道中搜尋訊息工具。 */
public final class LangChain4jSearchMessagesTool {

  private static final Logger LOGGER = LoggerFactory.getLogger(LangChain4jSearchMessagesTool.class);

  private static final int MAX_CHANNELS = 10;
  private static final int DEFAULT_RESULTS_PER_CHANNEL = 20;
  private static final int MAX_RESULTS_PER_CHANNEL = 50;
  private static final int DEFAULT_SCAN_PER_CHANNEL = 200;
  private static final int MAX_SCAN_PER_CHANNEL = 1000;
  private static final int FETCH_BATCH_SIZE = 100;
  private static final int FETCH_TIMEOUT_SECONDS = 10;
  private static final int CONTENT_SNIPPET_LENGTH = 180;

  @Inject
  public LangChain4jSearchMessagesTool() {
    // JDA 將從 JDAProvider 延遲獲取
  }

  /**
   * 在指定頻道中搜尋包含關鍵字的訊息。
   *
   * @param keywords 關鍵字（可輸入多個，以空白分隔）
   * @param channelIds 頻道 ID 列表（可選，預設當前頻道）
   * @param maxResultsPerChannel 每個頻道最多返回幾筆匹配
   * @param maxMessagesToScan 每個頻道最多掃描幾筆歷史訊息
   * @param parameters 調用參數（包含 guildId、channelId、userId）
   * @return 搜尋結果 JSON 字串
   */
  @Tool(
      """
      在一個或多個 Discord 頻道中搜尋訊息。

      使用場景：
      - 需要查找包含特定關鍵字的歷史訊息
      - 需要快速回顧多個頻道的討論內容

      參數說明：
      - keywords：搜尋關鍵字（可用空白分隔多個詞）
      - channelIds：要搜尋的頻道 ID 列表（可省略，省略時使用當前頻道）
      - maxResultsPerChannel：每個頻道回傳的最大匹配數（預設 20）
      - maxMessagesToScan：每個頻道最多掃描多少筆訊息（預設 200）
      """)
  public String searchMessages(
      String keywords,
      @P(value = "要搜尋的頻道 ID 列表，可同時指定多個。", required = false) List<String> channelIds,
      @P(value = "每個頻道最多返回幾筆匹配結果（1-50）。", required = false) Integer maxResultsPerChannel,
      @P(value = "每個頻道最多掃描幾筆歷史訊息（1-1000）。", required = false) Integer maxMessagesToScan,
      InvocationParameters parameters) {

    if (keywords == null || keywords.isBlank()) {
      return ToolJsonResponses.error("keywords 不能為空");
    }

    List<String> keywordTokens = splitKeywords(keywords);
    if (keywordTokens.isEmpty()) {
      return ToolJsonResponses.error("keywords 不能為空");
    }

    Long guildId = parameters.get("guildId");
    Long currentChannelId = parameters.get("channelId");
    if (guildId == null) {
      LOGGER.error("LangChain4jSearchMessagesTool: guildId 未設置");
      return ToolJsonResponses.error("guildId 未設置");
    }

    Guild guild = JDAProvider.getJda().getGuildById(guildId);
    if (guild == null) {
      LOGGER.warn("LangChain4jSearchMessagesTool: 找不到指定的伺服器: {}", guildId);
      return ToolJsonResponses.error("找不到伺服器");
    }

    String authorizationError =
        ToolCallerAuthorizationGuard.validateAdministrator(
            parameters, guild, LOGGER, "LangChain4jSearchMessagesTool");
    if (authorizationError != null) {
      return ToolJsonResponses.error(authorizationError);
    }

    List<String> normalizedChannelIds = normalizeChannelIds(channelIds, currentChannelId);
    if (normalizedChannelIds.isEmpty()) {
      return ToolJsonResponses.error("未提供有效的 channelIds，且當前頻道不可用");
    }
    if (normalizedChannelIds.size() > MAX_CHANNELS) {
      return ToolJsonResponses.error(String.format("一次最多可搜尋 %d 個頻道", MAX_CHANNELS));
    }

    int resultsLimit =
        clamp(maxResultsPerChannel, DEFAULT_RESULTS_PER_CHANNEL, 1, MAX_RESULTS_PER_CHANNEL);
    int scanLimit = clamp(maxMessagesToScan, DEFAULT_SCAN_PER_CHANNEL, 1, MAX_SCAN_PER_CHANNEL);

    List<ChannelSearchSummary> channelSummaries = new ArrayList<>();
    List<MessageMatch> matches = new ArrayList<>();

    for (String rawChannelId : normalizedChannelIds) {
      Long targetChannelId = parseSnowflakeId(rawChannelId);
      if (targetChannelId == null) {
        channelSummaries.add(
            new ChannelSearchSummary(rawChannelId, null, false, 0, 0, "無效的頻道 ID 格式"));
        continue;
      }

      GuildChannel guildChannel = guild.getGuildChannelById(targetChannelId);
      if (guildChannel == null) {
        channelSummaries.add(
            new ChannelSearchSummary(
                String.valueOf(targetChannelId), null, false, 0, 0, "找不到指定頻道"));
        continue;
      }

      if (!(guildChannel instanceof GuildMessageChannel messageChannel)) {
        channelSummaries.add(
            new ChannelSearchSummary(
                String.valueOf(targetChannelId),
                guildChannel.getName(),
                false,
                0,
                0,
                "該頻道類型不支援訊息搜尋"));
        continue;
      }

      try {
        List<Message> recentMessages = fetchMessages(messageChannel, scanLimit);
        int matchedCount = 0;

        for (Message message : recentMessages) {
          if (matchedCount >= resultsLimit) {
            break;
          }

          String content = message.getContentDisplay();
          if (content == null || content.isBlank()) {
            continue;
          }

          if (!containsAllKeywords(content, keywordTokens)) {
            continue;
          }

          matchedCount++;
          matches.add(
              new MessageMatch(
                  String.valueOf(targetChannelId),
                  guildChannel.getName(),
                  String.valueOf(message.getIdLong()),
                  String.valueOf(message.getAuthor().getIdLong()),
                  message.getAuthor().getName(),
                  buildSnippet(content),
                  message.getTimeCreated().toString(),
                  message.getJumpUrl()));
        }

        channelSummaries.add(
            new ChannelSearchSummary(
                String.valueOf(targetChannelId),
                guildChannel.getName(),
                true,
                recentMessages.size(),
                matchedCount,
                null));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        channelSummaries.add(
            new ChannelSearchSummary(
                String.valueOf(targetChannelId), guildChannel.getName(), false, 0, 0, "搜尋被中斷"));
      } catch (Exception e) {
        channelSummaries.add(
            new ChannelSearchSummary(
                String.valueOf(targetChannelId),
                guildChannel.getName(),
                false,
                0,
                0,
                "搜尋失敗: " + extractErrorMessage(e)));
      }
    }

    boolean hasSearchSuccess = channelSummaries.stream().anyMatch(ChannelSearchSummary::success);
    return buildResultJson(
        hasSearchSuccess,
        keywords,
        keywordTokens,
        resultsLimit,
        scanLimit,
        channelSummaries,
        matches);
  }

  private List<String> splitKeywords(String keywords) {
    String[] parts = keywords.toLowerCase(Locale.ROOT).trim().split("\\s+");
    List<String> result = new ArrayList<>();
    for (String part : parts) {
      if (!part.isBlank()) {
        result.add(part);
      }
    }
    return result;
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

  private int clamp(Integer value, int defaultValue, int min, int max) {
    if (value == null) {
      return defaultValue;
    }
    if (value < min) {
      return min;
    }
    if (value > max) {
      return max;
    }
    return value;
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

  private List<Message> fetchMessages(GuildMessageChannel channel, int scanLimit) throws Exception {
    MessageHistory history = channel.getHistory();
    List<Message> messages = new ArrayList<>();

    int remaining = scanLimit;
    while (remaining > 0) {
      int batchSize = Math.min(FETCH_BATCH_SIZE, remaining);
      List<Message> batch =
          history.retrievePast(batchSize).submit().get(FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      if (batch == null || batch.isEmpty()) {
        break;
      }

      messages.addAll(batch);
      remaining -= batch.size();

      if (batch.size() < batchSize) {
        break;
      }
    }

    return messages;
  }

  private boolean containsAllKeywords(String content, List<String> keywords) {
    String normalized = content.toLowerCase(Locale.ROOT);
    for (String keyword : keywords) {
      if (!normalized.contains(keyword)) {
        return false;
      }
    }
    return true;
  }

  private String buildSnippet(String content) {
    String normalized = content.replace('\n', ' ').replace('\r', ' ').trim();
    if (normalized.length() <= CONTENT_SNIPPET_LENGTH) {
      return normalized;
    }
    return normalized.substring(0, CONTENT_SNIPPET_LENGTH) + "...";
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
      String keywords,
      List<String> keywordTokens,
      int maxResultsPerChannel,
      int maxMessagesToScan,
      List<ChannelSearchSummary> channelSummaries,
      List<MessageMatch> matches) {

    StringBuilder json = new StringBuilder();
    json.append("{\n");
    json.append("  \"success\": ").append(success).append(",\n");
    json.append("  \"keywords\": \"")
        .append(ToolJsonResponses.escapeJson(keywords))
        .append("\",\n");
    json.append("  \"keywordTokens\": ").append(stringListToJson(keywordTokens)).append(",\n");
    json.append("  \"maxResultsPerChannel\": ").append(maxResultsPerChannel).append(",\n");
    json.append("  \"maxMessagesToScan\": ").append(maxMessagesToScan).append(",\n");
    json.append("  \"matchedCount\": ").append(matches.size()).append(",\n");
    json.append("  \"channels\": [\n");

    for (int i = 0; i < channelSummaries.size(); i++) {
      ChannelSearchSummary summary = channelSummaries.get(i);
      if (i > 0) {
        json.append(",\n");
      }

      json.append("    {\n");
      json.append("      \"channelId\": \"")
          .append(ToolJsonResponses.escapeJson(summary.channelId()))
          .append("\",\n");
      json.append("      \"channelName\": \"")
          .append(ToolJsonResponses.escapeJson(nullToEmpty(summary.channelName())))
          .append("\",\n");
      json.append("      \"success\": ").append(summary.success()).append(",\n");
      json.append("      \"scannedCount\": ").append(summary.scannedCount()).append(",\n");
      json.append("      \"matchedCount\": ").append(summary.matchedCount());
      if (!summary.success()) {
        json.append(",\n");
        json.append("      \"error\": \"")
            .append(ToolJsonResponses.escapeJson(nullToEmpty(summary.error())))
            .append("\"");
      }
      json.append("\n    }");
    }

    json.append("\n  ],\n");
    json.append("  \"matches\": [\n");

    for (int i = 0; i < matches.size(); i++) {
      MessageMatch match = matches.get(i);
      if (i > 0) {
        json.append(",\n");
      }

      json.append("    {\n");
      json.append("      \"channelId\": \"")
          .append(ToolJsonResponses.escapeJson(match.channelId()))
          .append("\",\n");
      json.append("      \"channelName\": \"")
          .append(ToolJsonResponses.escapeJson(match.channelName()))
          .append("\",\n");
      json.append("      \"messageId\": \"")
          .append(ToolJsonResponses.escapeJson(match.messageId()))
          .append("\",\n");
      json.append("      \"authorId\": \"")
          .append(ToolJsonResponses.escapeJson(match.authorId()))
          .append("\",\n");
      json.append("      \"authorName\": \"")
          .append(ToolJsonResponses.escapeJson(match.authorName()))
          .append("\",\n");
      json.append("      \"contentSnippet\": \"")
          .append(ToolJsonResponses.escapeJson(match.contentSnippet()))
          .append("\",\n");
      json.append("      \"createdAt\": \"")
          .append(ToolJsonResponses.escapeJson(match.createdAt()))
          .append("\",\n");
      json.append("      \"jumpUrl\": \"")
          .append(ToolJsonResponses.escapeJson(match.jumpUrl()))
          .append("\"\n");
      json.append("    }");
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

  private record ChannelSearchSummary(
      String channelId,
      String channelName,
      boolean success,
      int scannedCount,
      int matchedCount,
      String error) {}

  private record MessageMatch(
      String channelId,
      String channelName,
      String messageId,
      String authorId,
      String authorName,
      String contentSnippet,
      String createdAt,
      String jumpUrl) {}
}
