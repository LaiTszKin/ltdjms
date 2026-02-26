package ltdjms.discord.aiagent.services.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

/** 管理指定訊息狀態（pin/delete/edit）工具。 */
public final class LangChain4jManageMessageTool {

  private static final Logger LOGGER = LoggerFactory.getLogger(LangChain4jManageMessageTool.class);
  private static final int MAX_MESSAGE_CONTENT_LENGTH = 2000;
  private static final Pattern DIFF_HUNK_HEADER_PATTERN =
      Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*$");

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
      - editMode：僅 action=edit 時可選，replace（覆寫）、append（附加到原文後）、prepend（附加到原文前）、diff（套用 unified diff）
      """)
  public String manageMessage(
      @P(value = "目標訊息 ID。", required = true) String messageId,
      @P(value = "操作類型，必須是 pin、delete、edit 之一。", required = true) String action,
      @P(value = "目標頻道 ID（可選，未提供時使用當前頻道）。", required = false) String channelId,
      @P(value = "新的訊息內容（僅 action=edit 時需要）。", required = false) String newContent,
      @P(
              value =
                  "編輯模式（僅 action=edit 時可選）：replace=覆寫全文，append=附加到原文後，prepend=附加到原文前，diff=套用"
                      + " unified diff。",
              required = false)
          String editMode,
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
      String appliedEditMode = null;
      switch (normalizedAction) {
        case "pin" -> messageChannel.pinMessageById(targetMessageId).complete();
        case "delete" -> messageChannel.deleteMessageById(targetMessageId).complete();
        case "edit" -> {
          EditMode resolvedEditMode = resolveEditMode(editMode, newContent);
          if (resolvedEditMode == null) {
            return ToolJsonResponses.error("editMode 必須是 replace、append、prepend 或 diff");
          }

          String normalizedContent = normalizeEditedContent(newContent, resolvedEditMode);
          if (normalizedContent == null) {
            return ToolJsonResponses.error("action=edit 時，newContent 不能為空");
          }
          if (resolvedEditMode != EditMode.DIFF
              && normalizedContent.length() > MAX_MESSAGE_CONTENT_LENGTH) {
            return ToolJsonResponses.error(
                String.format("newContent 長度不可超過 %d 字元", MAX_MESSAGE_CONTENT_LENGTH));
          }

          String finalContent;
          try {
            finalContent =
                buildFinalEditedContent(
                    messageChannel, targetMessageId, normalizedContent, resolvedEditMode);
          } catch (IllegalArgumentException validationError) {
            return ToolJsonResponses.error(validationError.getMessage());
          }
          if (finalContent.length() > MAX_MESSAGE_CONTENT_LENGTH) {
            return ToolJsonResponses.error(
                String.format(
                    "編輯後內容長度不可超過 %d 字元（目前 %d 字元）",
                    MAX_MESSAGE_CONTENT_LENGTH, finalContent.length()));
          }

          messageChannel.editMessageById(targetMessageId, finalContent).complete();
          editedContent = finalContent;
          appliedEditMode = resolvedEditMode.value;
        }
        default -> {
          return ToolJsonResponses.error("不支援的 action");
        }
      }

      return buildSuccessJson(
          targetChannelId, targetMessageId, normalizedAction, editedContent, appliedEditMode);
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

  private String normalizeEditedContent(String content, EditMode editMode) {
    if (content == null) {
      return null;
    }

    if (editMode == EditMode.DIFF) {
      return content.trim().isEmpty() ? null : content;
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
      long channelId,
      long messageId,
      String action,
      String newContentForEdit,
      String editModeForEdit) {
    StringBuilder json = new StringBuilder();
    json.append("{\n");
    json.append("  \"success\": true,\n");
    json.append("  \"message\": \"操作成功\",\n");
    json.append("  \"channelId\": \"").append(channelId).append("\",\n");
    json.append("  \"messageId\": \"").append(messageId).append("\",\n");
    json.append("  \"action\": \"").append(ToolJsonResponses.escapeJson(action)).append("\"");

    if ("edit".equals(action)) {
      json.append(",\n");
      json.append("  \"editMode\": \"")
          .append(ToolJsonResponses.escapeJson(nullToEmpty(editModeForEdit)))
          .append("\",\n");
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

  private EditMode resolveEditMode(String rawEditMode, String rawContent) {
    if (rawEditMode != null && !rawEditMode.isBlank()) {
      String candidate = rawEditMode.trim().toLowerCase(Locale.ROOT);
      if ("replace".equals(candidate)) {
        return EditMode.REPLACE;
      }
      if ("append".equals(candidate)) {
        return EditMode.APPEND;
      }
      if ("prepend".equals(candidate)) {
        return EditMode.PREPEND;
      }
      if ("diff".equals(candidate)) {
        return EditMode.DIFF;
      }
      return null;
    }

    if (isLikelyUnifiedDiff(rawContent)) {
      return EditMode.DIFF;
    }

    String normalizedContent = normalizeEditedContent(rawContent, EditMode.REPLACE);
    return isLikelyIncrementalEdit(normalizedContent) ? EditMode.APPEND : EditMode.REPLACE;
  }

  private boolean isLikelyUnifiedDiff(String content) {
    if (content == null || content.isBlank()) {
      return false;
    }

    boolean hasHunkHeader = false;
    boolean hasPatchOperation = false;
    for (String line : splitLines(content)) {
      if (DIFF_HUNK_HEADER_PATTERN.matcher(line).matches()) {
        hasHunkHeader = true;
        continue;
      }
      if (line.startsWith("+++ ") || line.startsWith("--- ")) {
        continue;
      }
      if (!line.isEmpty()) {
        char prefix = line.charAt(0);
        if (prefix == '+' || prefix == '-') {
          hasPatchOperation = true;
        }
      }
    }
    return hasHunkHeader && hasPatchOperation;
  }

  private boolean isLikelyIncrementalEdit(String normalizedContent) {
    if (normalizedContent == null) {
      return false;
    }
    return !normalizedContent.contains("\n") && normalizedContent.length() <= 20;
  }

  private String buildFinalEditedContent(
      GuildMessageChannel messageChannel,
      long messageId,
      String normalizedContent,
      EditMode editMode) {
    if (editMode == EditMode.REPLACE) {
      return normalizedContent;
    }

    Message originalMessage = messageChannel.retrieveMessageById(messageId).complete();
    String originalContent = originalMessage.getContentRaw();
    if (originalContent == null || originalContent.isBlank()) {
      return normalizedContent;
    }

    return switch (editMode) {
      case APPEND -> joinWithNewline(originalContent, normalizedContent);
      case PREPEND -> joinWithNewline(normalizedContent, originalContent);
      case DIFF -> applyUnifiedDiff(originalContent, normalizedContent);
      case REPLACE -> normalizedContent;
    };
  }

  private String joinWithNewline(String first, String second) {
    if (first.endsWith("\n")) {
      return first + second;
    }
    return first + "\n" + second;
  }

  private String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private String applyUnifiedDiff(String originalContent, String diffContent) {
    List<DiffHunk> hunks = parseUnifiedDiff(diffContent);
    List<String> originalLines = splitLines(originalContent);
    List<String> patchedLines = new ArrayList<>();

    int originalIndex = 0;
    for (DiffHunk hunk : hunks) {
      int targetIndex = hunk.oldStart() == 0 ? 0 : hunk.oldStart() - 1;
      if (targetIndex < originalIndex || targetIndex > originalLines.size()) {
        throw new IllegalArgumentException("diff 套用失敗：hunk 位置超出原始訊息範圍");
      }
      int expectedPatchedIndex = hunk.newStart() == 0 ? 0 : hunk.newStart() - 1;
      if (patchedLines.size() > expectedPatchedIndex) {
        throw new IllegalArgumentException("diff 套用失敗：hunk 區段重疊或順序錯誤");
      }

      while (originalIndex < targetIndex) {
        patchedLines.add(originalLines.get(originalIndex));
        originalIndex++;
      }
      if (patchedLines.size() != expectedPatchedIndex) {
        throw new IllegalArgumentException("diff 格式無效：hunk newStart 與內容位置不一致");
      }

      int consumedOldLines = 0;
      int producedNewLines = 0;
      for (String hunkLine : hunk.lines()) {
        char prefix = hunkLine.charAt(0);
        String payload = hunkLine.length() > 1 ? hunkLine.substring(1) : "";
        switch (prefix) {
          case ' ' -> {
            assertOriginalLineMatches(originalLines, originalIndex, payload);
            patchedLines.add(payload);
            originalIndex++;
            consumedOldLines++;
            producedNewLines++;
          }
          case '-' -> {
            assertOriginalLineMatches(originalLines, originalIndex, payload);
            originalIndex++;
            consumedOldLines++;
          }
          case '+' -> {
            patchedLines.add(payload);
            producedNewLines++;
          }
          case '\\' -> {
            // 忽略 "\ No newline at end of file" 標記，不影響 Discord 文字內容
          }
          default -> throw new IllegalArgumentException("diff 格式無效：hunk 內容前綴不合法");
        }
      }

      if (consumedOldLines != hunk.oldCount()) {
        throw new IllegalArgumentException(
            String.format("diff 格式無效：hunk 宣告移除 %d 行，但實際為 %d 行", hunk.oldCount(), consumedOldLines));
      }
      if (producedNewLines != hunk.newCount()) {
        throw new IllegalArgumentException(
            String.format("diff 格式無效：hunk 宣告新增 %d 行，但實際為 %d 行", hunk.newCount(), producedNewLines));
      }
    }

    while (originalIndex < originalLines.size()) {
      patchedLines.add(originalLines.get(originalIndex));
      originalIndex++;
    }

    return String.join("\n", patchedLines);
  }

  private List<DiffHunk> parseUnifiedDiff(String diffContent) {
    List<String> lines = splitLines(diffContent);
    List<DiffHunk> hunks = new ArrayList<>();
    int index = 0;

    while (index < lines.size()) {
      String line = lines.get(index);
      Matcher headerMatcher = DIFF_HUNK_HEADER_PATTERN.matcher(line);
      if (!headerMatcher.matches()) {
        if (line.isBlank() || isDiffMetadataLine(line)) {
          index++;
          continue;
        }
        throw new IllegalArgumentException("diff 格式無效：找不到 hunk 標頭（@@ ... @@）");
      }

      int oldStart = Integer.parseInt(headerMatcher.group(1));
      int oldCount = parseHunkCount(headerMatcher.group(2));
      int newStart = Integer.parseInt(headerMatcher.group(3));
      int newCount = parseHunkCount(headerMatcher.group(4));
      index++;

      List<String> hunkLines = new ArrayList<>();
      while (index < lines.size()) {
        String hunkLine = lines.get(index);
        if (DIFF_HUNK_HEADER_PATTERN.matcher(hunkLine).matches()) {
          break;
        }
        if (isDiffMetadataLine(hunkLine)) {
          break;
        }
        if (hunkLine.isEmpty()) {
          if (hasOnlyBlankOrMetadataLinesRemaining(lines, index)) {
            index = lines.size();
            break;
          }
          throw new IllegalArgumentException("diff 格式無效：hunk 內容不得為空行");
        }
        char prefix = hunkLine.charAt(0);
        if (prefix != ' ' && prefix != '+' && prefix != '-' && prefix != '\\') {
          throw new IllegalArgumentException("diff 格式無效：hunk 內容需以空白、+、- 開頭");
        }
        hunkLines.add(hunkLine);
        index++;
      }

      if (hunkLines.isEmpty()) {
        throw new IllegalArgumentException("diff 格式無效：hunk 不可為空");
      }
      hunks.add(new DiffHunk(oldStart, oldCount, newStart, newCount, hunkLines));
    }

    if (hunks.isEmpty()) {
      throw new IllegalArgumentException("diff 格式無效：至少需要一個 hunk（@@ ... @@）");
    }
    return hunks;
  }

  private void assertOriginalLineMatches(
      List<String> originalLines, int index, String expectedLine) {
    if (index >= originalLines.size()) {
      throw new IllegalArgumentException("diff 套用失敗：原始訊息行數不足");
    }
    String actualLine = originalLines.get(index);
    if (!actualLine.equals(expectedLine)) {
      throw new IllegalArgumentException(
          String.format(
              "diff 套用失敗：第 %d 行內容不符（預期：%s，實際：%s）",
              index + 1, trimPreview(expectedLine), trimPreview(actualLine)));
    }
  }

  private boolean isDiffMetadataLine(String line) {
    return line.startsWith("diff ")
        || line.startsWith("index ")
        || line.startsWith("--- ")
        || line.startsWith("+++ ")
        || line.startsWith("old mode ")
        || line.startsWith("new mode ")
        || line.startsWith("```");
  }

  private boolean hasOnlyBlankOrMetadataLinesRemaining(List<String> lines, int startIndex) {
    for (int i = startIndex; i < lines.size(); i++) {
      String line = lines.get(i);
      if (!line.isBlank() && !isDiffMetadataLine(line)) {
        return false;
      }
    }
    return true;
  }

  private int parseHunkCount(String rawCount) {
    if (rawCount == null) {
      return 1;
    }
    return Integer.parseInt(rawCount);
  }

  private List<String> splitLines(String content) {
    if (content == null || content.isEmpty()) {
      return new ArrayList<>();
    }
    String normalized = content.replace("\r\n", "\n");
    String[] segments = normalized.split("\n", -1);
    List<String> lines = new ArrayList<>(segments.length);
    for (String segment : segments) {
      if (segment.endsWith("\r")) {
        lines.add(segment.substring(0, segment.length() - 1));
      } else {
        lines.add(segment);
      }
    }
    return lines;
  }

  private enum EditMode {
    REPLACE("replace"),
    APPEND("append"),
    PREPEND("prepend"),
    DIFF("diff");

    private final String value;

    EditMode(String value) {
      this.value = value;
    }
  }

  private record DiffHunk(
      int oldStart, int oldCount, int newStart, int newCount, List<String> lines) {}
}
