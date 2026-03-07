package ltdjms.discord.markdown.services;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Discord 訊息分頁器，處理長度限制與程式碼區塊跨訊息。 */
public final class DiscordMarkdownPaginator {

  private static final int MAX_MESSAGE_LENGTH = 1900;
  private static final Pattern HEADING_LINE = Pattern.compile("^#{1,6}\\s+.+");

  public List<String> paginate(String content) {
    if (content == null) {
      return List.of();
    }
    String trimmed = content.strip();
    if (trimmed.isEmpty()) {
      return List.of();
    }
    if (trimmed.length() <= MAX_MESSAGE_LENGTH) {
      return List.of(trimmed);
    }

    List<String> pages = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inCodeBlock = false;
    String codeFence = "```";

    String[] lines = content.split("\n", -1);
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      boolean hasNewline = (i < lines.length - 1);
      String lineWithNewline = hasNewline ? line + "\n" : line;

      if (!inCodeBlock && isHeadingLine(line) && current.length() > 0) {
        current = flushPage(pages, current, inCodeBlock, codeFence, false);
      }

      if (isFenceLine(line)) {
        if (current.length() + lineWithNewline.length() > MAX_MESSAGE_LENGTH) {
          current = flushPage(pages, current, inCodeBlock, codeFence, true);
        }
        current.append(lineWithNewline);
        if (inCodeBlock) {
          inCodeBlock = false;
        } else {
          inCodeBlock = true;
          codeFence = line.trim();
        }
        continue;
      }

      String remaining = lineWithNewline;
      while (!remaining.isEmpty()) {
        int available =
            MAX_MESSAGE_LENGTH - current.length() - reservedCharsForCodeFence(current, inCodeBlock);
        if (available <= 0) {
          current = flushPage(pages, current, inCodeBlock, codeFence, true);
          continue;
        }
        if (remaining.length() <= available) {
          current.append(remaining);
          remaining = "";
        } else {
          current.append(remaining, 0, available);
          remaining = remaining.substring(available);
          current = flushPage(pages, current, inCodeBlock, codeFence, true);
        }
      }
    }

    flushPage(pages, current, inCodeBlock, codeFence, false);
    return pages;
  }

  private boolean isHeadingLine(String line) {
    return HEADING_LINE.matcher(line.trim()).matches();
  }

  private boolean isFenceLine(String line) {
    return line.trim().startsWith("```");
  }

  private StringBuilder flushPage(
      List<String> pages,
      StringBuilder current,
      boolean inCodeBlock,
      String codeFence,
      boolean reopen) {
    if (current.length() == 0) {
      return current;
    }
    if (inCodeBlock) {
      ensureNewline(current);
      current.append("```");
    }
    String page = current.toString().strip();
    if (!page.isEmpty()) {
      pages.add(page);
    }
    StringBuilder next = new StringBuilder();
    if (inCodeBlock && reopen) {
      next.append(codeFence).append("\n");
    }
    return next;
  }

  private void ensureNewline(StringBuilder builder) {
    int length = builder.length();
    if (length == 0) {
      return;
    }
    if (builder.charAt(length - 1) != '\n') {
      builder.append("\n");
    }
  }

  private int reservedCharsForCodeFence(StringBuilder current, boolean inCodeBlock) {
    if (!inCodeBlock) {
      return 0;
    }
    return 4;
  }
}
