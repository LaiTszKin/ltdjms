package ltdjms.discord.markdown.services;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Discord 專用 Markdown 清理器，移除不支援語法並修正結構。 */
public final class DiscordMarkdownSanitizer {

  private static final Pattern HTML_COMMENT = Pattern.compile("<!--([\\s\\S]*?)-->");
  private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
  private static final Pattern TABLE_SEPARATOR =
      Pattern.compile("^\\s*\\|?\\s*[:\\-]+(?:\\s*\\|\\s*[:\\-]+)+\\s*\\|?\\s*$");

  public String sanitize(String markdown) {
    if (markdown == null || markdown.isBlank()) {
      return markdown;
    }

    List<String> codeBlocks = new ArrayList<>();
    String protectedContent = protectCodeBlocks(markdown, codeBlocks);

    String cleaned = removeHtml(protectedContent);
    cleaned = collapseNestedBlockquotes(cleaned);
    cleaned = convertTablesToCodeBlocks(cleaned);

    return restoreCodeBlocks(cleaned, codeBlocks);
  }

  private String removeHtml(String content) {
    String withoutComments = HTML_COMMENT.matcher(content).replaceAll("");
    return HTML_TAG.matcher(withoutComments).replaceAll("");
  }

  private String collapseNestedBlockquotes(String content) {
    String[] lines = content.split("\n", -1);
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      if (line.trim().startsWith(">")) {
        int firstNonSpace = 0;
        while (firstNonSpace < line.length()
            && Character.isWhitespace(line.charAt(firstNonSpace))) {
          firstNonSpace++;
        }
        int gtCount = 0;
        while (firstNonSpace + gtCount < line.length()
            && line.charAt(firstNonSpace + gtCount) == '>') {
          gtCount++;
        }
        if (gtCount > 1) {
          String trimmed = line.substring(firstNonSpace + gtCount).trim();
          line = line.substring(0, firstNonSpace) + "> " + trimmed;
        }
      }
      result.append(line);
      if (i < lines.length - 1) {
        result.append("\n");
      }
    }
    return result.toString();
  }

  private String convertTablesToCodeBlocks(String content) {
    String[] lines = content.split("\n", -1);
    StringBuilder result = new StringBuilder();
    int i = 0;
    while (i < lines.length) {
      String line = lines[i];
      if (isTableStart(lines, i)) {
        List<String> tableLines = new ArrayList<>();
        while (i < lines.length && lines[i].contains("|")) {
          tableLines.add(lines[i]);
          i++;
        }
        result.append("```text\n");
        for (int j = 0; j < tableLines.size(); j++) {
          result.append(tableLines.get(j));
          if (j < tableLines.size() - 1) {
            result.append("\n");
          }
        }
        result.append("\n```");
        if (i < lines.length) {
          result.append("\n");
        }
        continue;
      }

      result.append(line);
      if (i < lines.length - 1) {
        result.append("\n");
      }
      i++;
    }
    return result.toString();
  }

  private boolean isTableStart(String[] lines, int index) {
    if (index + 1 >= lines.length) {
      return false;
    }
    String header = lines[index];
    String separator = lines[index + 1];
    return header.contains("|") && TABLE_SEPARATOR.matcher(separator.trim()).matches();
  }

  private String protectCodeBlocks(String markdown, List<String> codeBlocks) {
    StringBuffer sb = new StringBuffer();
    Matcher matcher = Pattern.compile("```[\\s\\S]*?```").matcher(markdown);
    int lastEnd = 0;

    while (matcher.find()) {
      sb.append(markdown, lastEnd, matcher.start());
      codeBlocks.add(matcher.group());
      sb.append("\u0000CODE_BLOCK_").append(codeBlocks.size() - 1).append("\u0000");
      lastEnd = matcher.end();
    }

    sb.append(markdown.substring(lastEnd));
    return sb.toString();
  }

  private String restoreCodeBlocks(String markdown, List<String> codeBlocks) {
    String result = markdown;
    for (int i = 0; i < codeBlocks.size(); i++) {
      result = result.replace("\u0000CODE_BLOCK_" + i + "\u0000", codeBlocks.get(i));
    }
    return result;
  }
}
