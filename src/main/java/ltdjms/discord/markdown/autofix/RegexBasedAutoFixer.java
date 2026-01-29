package ltdjms.discord.markdown.autofix;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基於正規表達式的 Markdown 自動修復器。
 *
 * <p>此實作使用正規表達式來識別和修復常見的 Markdown 格式錯誤。
 */
public class RegexBasedAutoFixer implements MarkdownAutoFixer {

  private static final Logger log = LoggerFactory.getLogger(RegexBasedAutoFixer.class);

  /** 正規表達式：匹配行首的連續 # 符號後直接跟隨非空白字符 例如: "#Heading" 或 "##Subheading" */
  private static final Pattern HEADING_WITHOUT_SPACE =
      Pattern.compile("^(#{1,6})([^\\s#].*)$", Pattern.MULTILINE);

  /** 正規表達式：匹配行首的無序列表標記後直接跟隨非空白字符（排除分隔線） 例如: "-item" */
  private static final Pattern UNORDERED_LIST_WITHOUT_SPACE =
      Pattern.compile("^(\\s*)([-*+])([^\\s].*)$", Pattern.MULTILINE);

  /** 正規表達式：匹配行首的有序列表標記後直接跟隨非空白字符 例如: "1.item" */
  private static final Pattern ORDERED_LIST_WITHOUT_SPACE =
      Pattern.compile("^(\\s*)(\\d+\\.)([^\\s].*)$", Pattern.MULTILINE);

  /** 正規表達式：匹配非行首的標題標記（避免標題與前文黏在同一行） */
  private static final Pattern INLINE_HEADING_MARKER = Pattern.compile("([^\\n])(?=[ \\t]*#{2,6})");

  /** 正規表達式：匹配標題行中緊貼的列表標記（例如 "## 標題- item"） */
  private static final Pattern HEADING_INLINE_LIST_MARKER =
      Pattern.compile("(?m)^(#{1,6}\\s+[^\\n]*?)([-*+]\\s+)");

  @Override
  public String autoFix(String markdown) {
    if (markdown == null || markdown.isEmpty()) {
      return markdown;
    }

    String result = markdown;

    // 應用修復（順序很重要）
    result = fixUnclosedCodeBlocks(result);
    result = fixHeadingLevelExceeded(result);
    result = fixInlineHeadings(result);
    result = fixHeadingFormat(result);
    result = fixHeadingContainsListMarker(result);
    result = fixHeadingInlineListItems(result);
    result = fixEmbeddedLists(result);
    result = fixInlineListMarkersInListLines(result);
    result = fixListFormat(result);
    result = normalizeUnorderedListMarkers(result);
    result = fixNestedListIndentation(result);
    result = fixDiscordUnderlineBold(result);
    result = fixTaskList(result);
    // Discord 不支援水平分隔線，這裡直接移除以避免驗證失敗
    result = fixHorizontalRules(result);

    return result;
  }

  /**
   * 修復未閉合的程式碼區塊。
   *
   * <p>計算程式碼區塊的開啟和閉合標記數量，如果不匹配則在文末添加閉合標記。
   *
   * <p>此外，會偵測程式碼區塊內的明顯非程式碼內容（如普通英文句子），並在該內容之前閉合程式碼區塊。
   *
   * @param markdown 原始 Markdown
   * @return 修復後的 Markdown
   */
  private String fixUnclosedCodeBlocks(String markdown) {
    // 使用狀態機追蹤程式碼區塊
    StringBuilder result = new StringBuilder();
    boolean inCodeBlock = false;
    int codeBlockStart = -1;
    StringBuilder currentLine = new StringBuilder();

    for (int i = 0; i < markdown.length(); i++) {
      char c = markdown.charAt(i);

      // 檢查是否是程式碼區塊標記 (```)
      if (c == '`' && i + 2 < markdown.length()) {
        if (markdown.charAt(i + 1) == '`' && markdown.charAt(i + 2) == '`') {
          // 確保不是四個連續的 `
          boolean isQuad = i + 3 < markdown.length() && markdown.charAt(i + 3) == '`';
          if (!isQuad) {
            inCodeBlock = !inCodeBlock;
            if (inCodeBlock) {
              codeBlockStart = i;
              currentLine.setLength(0);
            } else {
              currentLine.setLength(0);
            }
            result.append("```");
            i += 2; // 跳過後兩個 `
            continue;
          }
        }
      }

      // 檢查是否遇到換行符
      if (c == '\n') {
        // 如果在程式碼區塊中，檢查當前行是否像非程式碼內容
        if (inCodeBlock && currentLine.length() > 0) {
          String lineContent = currentLine.toString().trim();
          // 如果這行看起來像普通英文句子
          if (looksLikePlainText(lineContent)) {
            log.debug("偵測到程式碼區塊中的普通文本：{}，提前閉合程式碼區塊", lineContent);
            // 移除最後一行（包括前面的換行符）
            int lineStart = result.length() - currentLine.length() - 1; // -1 for the newline
            result.setLength(lineStart);
            // 添加閉合標記（不包含額外的換行符，因為後面會添加）
            result.append("```");
            // 將當前行加入結果（保留原始格式）
            result.append("\n").append(lineContent);
            inCodeBlock = false;
            // 清空 currentLine 並繼續
            currentLine.setLength(0);
            // 不要在這裡返回，讓後面的邏輯處理換行符
          }
        }
        currentLine.setLength(0);
        result.append(c);
      } else {
        currentLine.append(c);
        result.append(c);
      }
    }

    // 處理最後一行
    if (inCodeBlock && currentLine.length() > 0) {
      String lineContent = currentLine.toString().trim();
      if (looksLikePlainText(lineContent)) {
        log.debug("偵測到程式碼區塊中的普通文本：{}，提前閉合程式碼區塊", lineContent);
        // 移除最後一行（因為它不是程式碼）
        int lineStart = result.length() - lineContent.length();
        result.setLength(lineStart);
        // 添加閉合標記和當前行
        result.append("```\n").append(lineContent);
        inCodeBlock = false;
      }
    }

    // 如果結束時仍在程式碼區塊中，添加閉合標記
    if (inCodeBlock) {
      log.debug("偵測到未閉合的程式碼區塊（位置: {}），自動閉合", codeBlockStart);
      result.append("\n```");
    }

    return result.toString();
  }

  /**
   * 判斷一行文字是否像普通文本而非程式碼。
   *
   * <p>簡單的啟發式規則：
   *
   * <ul>
   *   <li>包含常見的英文單詞（完整單詞匹配）
   *   <li>且不包含明顯的程式碼特徵
   *   <li>不是以 # 開頭的行（避免影響標題）
   *   <li>不包含列表標記（避免誤判列表）
   * </ul>
   */
  private boolean looksLikePlainText(String line) {
    if (line.isEmpty()) {
      return false;
    }

    // 不處理以 # 開頭的行（可能是標題）
    if (line.trim().startsWith("#")) {
      return false;
    }

    // 檢查是否包含列表標記（有序列表如 1. 2.，無序列表如 - * +）
    if (line.matches(".*\\d+\\.\\s+.*") || line.matches(".*[-*+]\\s+.*")) {
      return false; // 像列表項
    }

    // 檢查是否包含明顯的程式碼特徵 - 優先檢查這些
    if (line.contains("()") || line.contains(";")) {
      return false; // 像函數調用或語句
    }
    if (line.contains("{") || line.contains("}")) {
      return false; // 像程式碼區塊
    }

    // 檢查是否包含程式語言關鍵字（這些通常表示程式碼）
    String[] codeKeywords = {
      "class ",
      "interface ",
      "public ",
      "private ",
      "protected ",
      "static ",
      "void ",
      "int ",
      "String ",
      "return ",
      "import ",
      "package ",
      "extends ",
      "implements "
    };
    for (String keyword : codeKeywords) {
      if (line.contains(keyword)) {
        return false;
      }
    }

    // 檢查是否包含常見的英文小詞（完整單詞匹配，避免 "block1" 被誤判）
    String[] commonWords = {
      " some ", " text ", " after ", " the ", " and ", " this ", " that ", " with ", "some ",
      "text ", "after ", "the ", "and ", "this ", "that ", "with "
    };
    String lowerLine = " " + line.toLowerCase() + " "; // 添加空格以便匹配完整單詞
    for (String word : commonWords) {
      if (lowerLine.contains(word)) {
        return true;
      }
    }

    return false;
  }

  /**
   * 修復行內標題（標題標記與前文黏在同一行）。
   *
   * <p>將 "文字## 標題" 轉為 "文字\n## 標題"。
   */
  private String fixInlineHeadings(String markdown) {
    List<String> codeBlocks = new ArrayList<>();
    String protectedContent = protectCodeBlocks(markdown, codeBlocks);
    String[] lines = protectedContent.split("\n", -1);
    StringBuilder result = new StringBuilder();

    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      String trimmed = line.trim();

      if (!trimmed.startsWith("#")) {
        line = INLINE_HEADING_MARKER.matcher(line).replaceAll("$1\n");
      } else {
        line = splitInlineHeadingInHeadingLine(line);
      }

      result.append(line);
      if (i < lines.length - 1) {
        result.append("\n");
      }
    }

    return restoreCodeBlocks(result.toString(), codeBlocks);
  }

  private String splitInlineHeadingInHeadingLine(String line) {
    int firstRun = 0;
    while (firstRun < line.length() && line.charAt(firstRun) == '#') {
      firstRun++;
    }
    if (firstRun == 0 || firstRun >= line.length()) {
      return line;
    }
    int nextHeadingIndex = line.indexOf("##", firstRun);
    if (nextHeadingIndex <= 0) {
      return line;
    }
    return line.substring(0, nextHeadingIndex) + "\n" + line.substring(nextHeadingIndex);
  }

  /**
   * 修復標題格式錯誤。
   *
   * <p>在 # 符號和標題文字之間插入空格。
   *
   * @param markdown 原始 Markdown
   * @return 修復後的 Markdown
   */
  private String fixHeadingFormat(String markdown) {
    // 先保護程式碼區塊
    List<String> codeBlocks = new ArrayList<>();
    String protectedContent = protectCodeBlocks(markdown, codeBlocks);

    // 修復非程式碼區塊中的標題
    String fixedContent = HEADING_WITHOUT_SPACE.matcher(protectedContent).replaceAll("$1 $2");

    // 還原程式碼區塊
    return restoreCodeBlocks(fixedContent, codeBlocks);
  }

  /**
   * 修復標題行內的列表標記。
   *
   * <p>將 "## 標題- item" 轉為 "## 標題\n- item"。
   */
  private String fixHeadingInlineListItems(String markdown) {
    List<String> codeBlocks = new ArrayList<>();
    String protectedContent = protectCodeBlocks(markdown, codeBlocks);

    String fixedContent = HEADING_INLINE_LIST_MARKER.matcher(protectedContent).replaceAll("$1\n$2");

    return restoreCodeBlocks(fixedContent, codeBlocks);
  }

  /**
   * 修復列表格式錯誤。
   *
   * <p>在列表標記（-、*、+ 或數字+句號）後面插入空格。
   *
   * <p>注意：此方法不會修改分隔線（---、***、___）。
   *
   * @param markdown 原始 Markdown
   * @return 修復後的 Markdown
   */
  private String fixListFormat(String markdown) {
    // 先保護程式碼區塊
    List<String> codeBlocks = new ArrayList<>();
    String protectedContent = protectCodeBlocks(markdown, codeBlocks);

    // 修復無序列表（-、*、+），但跳過分隔線
    String fixedContent =
        UNORDERED_LIST_WITHOUT_SPACE
            .matcher(protectedContent)
            .replaceAll(
                mr -> {
                  String indent = mr.group(1);
                  String marker = mr.group(2);
                  String content = mr.group(3);

                  // 檢查是否為分隔線（只有破折號/星號且沒有其他內容，或只有相同的字符）
                  if (isHorizontalRule(marker, content)) {
                    return mr.group(0); // 保持原樣
                  }

                  // 避免把純強調語法（*bold* / **bold**）當成列表
                  if ("*".equals(marker) && isLikelyEmphasisLine(mr.group(0).trim())) {
                    return mr.group(0);
                  }

                  return indent + marker + " " + content;
                });

    // 修復有序列表（1.、2. 等）
    fixedContent = ORDERED_LIST_WITHOUT_SPACE.matcher(fixedContent).replaceAll("$1$2 $3");

    // 還原程式碼區塊
    return restoreCodeBlocks(fixedContent, codeBlocks);
  }

  /**
   * 修復列表行中黏在一起的多個列表項。
   *
   * <p>將 "- item1- item2" 轉為 "- item1\n- item2"（保留縮排）。
   */
  private String fixInlineListMarkersInListLines(String markdown) {
    List<String> codeBlocks = new ArrayList<>();
    String protectedContent = protectCodeBlocks(markdown, codeBlocks);

    String[] lines = protectedContent.split("\n", -1);
    StringBuilder result = new StringBuilder();
    Pattern inlineUnorderedMarker = Pattern.compile("([^\\s])(-\\s+)");
    Pattern inlineOrderedMarker = Pattern.compile("([^\\s])(\\d+\\.\\s+)");
    Pattern inlineUnorderedMarkerWithLeadingSpace = Pattern.compile("(?<=\\S)\\s+(-\\s+)");
    Pattern inlineOrderedMarkerWithLeadingSpace = Pattern.compile("(?<=\\S)\\s+(\\d+\\.\\s+)");
    Pattern inlineUnorderedMarkerMissingSpace = Pattern.compile("(?<=\\S)\\s+(-)(?=\\S)");
    Pattern inlineUnorderedMarkerNoSpaceCjk =
        Pattern.compile(
            "([\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}])(-)(?=[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}])");
    // 有序列表缺少空格時可能誤判版本號（例如 1.2.3），避免在此處處理

    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      String trimmed = line.trim();

      if (trimmed.startsWith("- ")
          || trimmed.startsWith("* ")
          || trimmed.startsWith("+ ")
          || trimmed.matches("^\\d+\\.\\s+.*")) {
        int firstNonSpace = line.indexOf(trimmed);
        String indent = firstNonSpace > 0 ? line.substring(0, firstNonSpace) : "";

        line = splitInlineListMarkers(line, indent, inlineUnorderedMarker);
        line = splitInlineListMarkers(line, indent, inlineOrderedMarker);
        line =
            splitInlineListMarkersWithLeadingSpace(
                line, indent, inlineUnorderedMarkerWithLeadingSpace, false);
        line =
            splitInlineListMarkersWithLeadingSpace(
                line, indent, inlineOrderedMarkerWithLeadingSpace, false);
        line =
            splitInlineListMarkersWithLeadingSpace(
                line, indent, inlineUnorderedMarkerMissingSpace, true);
        line = splitInlineListMarkersNoSpaceCjk(line, indent, inlineUnorderedMarkerNoSpaceCjk);
      }

      result.append(line);
      if (i < lines.length - 1) {
        result.append("\n");
      }
    }

    return restoreCodeBlocks(result.toString(), codeBlocks);
  }

  private String splitInlineListMarkers(String line, String indent, Pattern pattern) {
    Matcher matcher = pattern.matcher(line);
    StringBuffer sb = new StringBuffer();
    while (matcher.find()) {
      String replacement = matcher.group(1) + "\n" + indent + matcher.group(2);
      matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(sb);
    return sb.toString();
  }

  private String splitInlineListMarkersWithLeadingSpace(
      String line, String indent, Pattern pattern, boolean ensureSpaceAfter) {
    Matcher matcher = pattern.matcher(line);
    StringBuffer sb = new StringBuffer();
    while (matcher.find()) {
      String marker = matcher.group(1);
      String replacement = "\n" + indent + marker;
      if (ensureSpaceAfter && !marker.endsWith(" ")) {
        replacement += " ";
      }
      matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(sb);
    return sb.toString();
  }

  private String splitInlineListMarkersNoSpaceCjk(String line, String indent, Pattern pattern) {
    Matcher matcher = pattern.matcher(line);
    StringBuffer sb = new StringBuffer();
    while (matcher.find()) {
      String replacement = matcher.group(1) + "\n" + indent + matcher.group(2) + " ";
      matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(sb);
    return sb.toString();
  }

  /**
   * 檢查一行是否為水平分隔線。
   *
   * <p>水平分隔線是連續的 -、* 或 _ 字符（至少 3 個）。
   *
   * @param line 要檢查的行
   * @return 如果是水平分隔線返回 true
   */
  private boolean isHorizontalRule(String line) {
    if (line.isEmpty()) {
      return false;
    }
    // 移除空白字符
    String trimmed = line.replaceAll("\\s", "");
    // 分隔線至少需要 3 個相同字符
    if (trimmed.length() < 3) {
      return false;
    }
    // 全部是 - 或 * 或 _
    return trimmed.chars().allMatch(c -> c == '-' || c == '*' || c == '_')
        && (trimmed.chars().distinct().count() == 1);
  }

  /**
   * 檢查是否為分隔線（用於列表處理）。
   *
   * <p>分隔線是連續的 -、* 或 _ 字符（至少 3 個），可選前面有空格。
   *
   * @param marker 列表標記（-、* 或 +）
   * @param content 標記後的內容
   * @return 如果是分隔線返回 true
   */
  private boolean isHorizontalRule(String marker, String content) {
    // + 號不會形成分隔線
    if ("+".equals(marker)) {
      return false;
    }

    // 檢查內容是否只有相同字符（全部是 - 或全部是 *）
    if (content.isEmpty()) {
      return false;
    }

    // 移除內容中的所有空白字符
    String trimmed = content.replaceAll("\\s", "");

    // 如果內容為空，或者內容只包含與標記相同的字符
    if (trimmed.isEmpty()) {
      return false;
    }

    // 檢查是否只包含標記字符（- 或 *）
    if ("-".equals(marker)) {
      return trimmed.chars().allMatch(c -> c == '-');
    } else if ("*".equals(marker)) {
      return trimmed.chars().allMatch(c -> c == '*');
    }

    return false;
  }

  /**
   * 判斷一行是否是純強調語法（避免把 *bold* 或 **bold** 當成列表）。
   *
   * <p>只在行首是 * 或 _ 時檢查，且限制在 1~3 個連續符號。
   */
  private boolean isLikelyEmphasisLine(String trimmedLine) {
    if (trimmedLine == null || trimmedLine.isEmpty()) {
      return false;
    }

    if (trimmedLine.startsWith("*")) {
      return isWrappedByMarker(trimmedLine, '*');
    }
    if (trimmedLine.startsWith("_")) {
      return isWrappedByMarker(trimmedLine, '_');
    }

    return false;
  }

  private boolean isWrappedByMarker(String trimmedLine, char marker) {
    int run = countLeadingMarkers(trimmedLine, marker);
    if (run < 1 || run > 3) {
      return false;
    }
    int coreEnd = trimTrailingPunctuationAndSpaces(trimmedLine);
    if (coreEnd <= run * 2) {
      return false;
    }
    if (Character.isWhitespace(trimmedLine.charAt(run))) {
      return false;
    }
    String suffix = String.valueOf(marker).repeat(run);
    return trimmedLine.substring(0, coreEnd).endsWith(suffix);
  }

  private int countLeadingMarkers(String text, char marker) {
    int count = 0;
    while (count < text.length() && text.charAt(count) == marker) {
      count++;
    }
    return count;
  }

  private int trimTrailingPunctuationAndSpaces(String text) {
    int end = text.length();
    while (end > 0 && isTrailingPunctuationOrSpace(text.charAt(end - 1))) {
      end--;
    }
    return end;
  }

  private boolean isTrailingPunctuationOrSpace(char c) {
    if (Character.isWhitespace(c)) {
      return true;
    }
    return "：:，,。.!！？?;；、".indexOf(c) >= 0;
  }

  private String normalizeUnorderedListMarkers(String markdown) {
    List<String> codeBlocks = new ArrayList<>();
    String protectedContent = protectCodeBlocks(markdown, codeBlocks);

    String[] lines = protectedContent.split("\n", -1);
    StringBuilder result = new StringBuilder();
    Pattern normalizePattern = Pattern.compile("^(\\s*)[+*]\\s+(.*)$");

    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      String fixedLine = normalizePattern.matcher(line).replaceAll("$1- $2");
      result.append(fixedLine);
      if (i < lines.length - 1) {
        result.append("\n");
      }
    }

    return restoreCodeBlocks(result.toString(), codeBlocks);
  }

  /** 保護程式碼區塊，替換為佔位符。 */
  private String protectCodeBlocks(String markdown, List<String> codeBlocks) {
    StringBuffer sb = new StringBuffer();
    Matcher matcher = Pattern.compile("```[\\s\\S]*?```").matcher(markdown);
    int lastEnd = 0;

    while (matcher.find()) {
      // 添加程式碼區塊之前的內容
      sb.append(markdown, lastEnd, matcher.start());
      // 儲存程式碼區塊
      codeBlocks.add(matcher.group());
      // 添加佔位符
      sb.append("\u0000CODE_BLOCK_").append(codeBlocks.size() - 1).append("\u0000");
      lastEnd = matcher.end();
    }

    sb.append(markdown.substring(lastEnd));
    return sb.toString();
  }

  /** 還原程式碼區塊。 */
  private String restoreCodeBlocks(String markdown, List<String> codeBlocks) {
    String result = markdown;
    for (int i = 0; i < codeBlocks.size(); i++) {
      result = result.replace("\u0000CODE_BLOCK_" + i + "\u0000", codeBlocks.get(i));
    }
    return result;
  }

  /**
   * 修復正文中嵌入的列表項。
   *
   * <p>識別並轉換正文中嵌入的列表項為正確的 markdown 列表格式。
   *
   * <p>例如：
   *
   * <ul>
   *   <li>{@code text 1. item1 2. item2} → {@code text\n1. item1\n2. item2}
   *   <li>{@code text - item1 - item2} → {@code text\n- item1\n- item2}
   * </ul>
   *
   * @param markdown 原始 Markdown
   * @return 修復後的 Markdown
   */
  private String fixEmbeddedLists(String markdown) {
    // 先保護程式碼區塊
    List<String> codeBlocks = new ArrayList<>();
    String protectedContent = protectCodeBlocks(markdown, codeBlocks);

    // 修復有序列表
    String fixedContent = fixEmbeddedOrderedList(protectedContent);

    // 修復無序列表
    fixedContent = fixEmbeddedUnorderedList(fixedContent);

    // 還原程式碼區塊
    return restoreCodeBlocks(fixedContent, codeBlocks);
  }

  /**
   * 修復正文中嵌入的有序列表。
   *
   * <p>識別類似 {@code text 1. item 2. item} 的模式，轉換為正確的列表格式。
   */
  private String fixEmbeddedOrderedList(String markdown) {
    // 使用 replaceAll 一次性將所有 " 空格+數字. " 替換為 "\n數字. "
    // 使用環視確保前面不是換行符（避免影響已經正確格式的列表）
    return markdown.replaceAll("([^\\n])(\\s+)(\\d+\\.\\s+)", "$1\n$3");
  }

  /**
   * 修復正文中嵌入的無序列表。
   *
   * <p>識別類似 {@code text - item - item} 的模式，轉換為正確的列表格式。
   */
  private String fixEmbeddedUnorderedList(String markdown) {
    String[] lines = markdown.split("\n", -1);
    StringBuilder result = new StringBuilder();
    Pattern markerPattern = Pattern.compile("\\s+-\\s+");

    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      String trimmed = line.trim();

      if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
        result.append(line);
      } else {
        Matcher matcher = markerPattern.matcher(line);
        int count = 0;
        while (matcher.find()) {
          count++;
          if (count >= 2) {
            break;
          }
        }
        if (count >= 2) {
          int leadingSpaces = countLeadingSpaces(line);
          String indent = leadingSpaces > 0 ? line.substring(0, leadingSpaces) : "";
          String replaced = line.replaceAll("\\s+-\\s+", "\n" + indent + "- ");
          result.append(replaced);
        } else {
          result.append(line);
        }
      }

      if (i < lines.length - 1) {
        result.append("\n");
      }
    }

    return result.toString();
  }

  /**
   * 修復超過 H6 層級的標題。
   *
   * <p>將超過 6 個 # 符號的標題截斷為 6 個（Discord 限制）。
   *
   * @param markdown 原始 Markdown
   * @return 修復後的 Markdown
   */
  private String fixHeadingLevelExceeded(String markdown) {
    // 先保護程式碼區塊
    List<String> codeBlocks = new ArrayList<>();
    String protectedContent = protectCodeBlocks(markdown, codeBlocks);

    // 匹配行首超過 6 個的 # 符號，截斷為 6 個
    String fixedContent =
        Pattern.compile("^(#{7,})(\\s?.*)$", Pattern.MULTILINE)
            .matcher(protectedContent)
            .replaceAll("######$2");

    // 還原程式碼區塊
    return restoreCodeBlocks(fixedContent, codeBlocks);
  }

  /**
   * 修復標題中包含的列表標記。
   *
   * <p>移除標題開頭的列表標記（如 `### - 標題` → `### 標題`）。
   *
   * @param markdown 原始 Markdown
   * @return 修復後的 Markdown
   */
  private String fixHeadingContainsListMarker(String markdown) {
    // 先保護程式碼區塊
    List<String> codeBlocks = new ArrayList<>();
    String protectedContent = protectCodeBlocks(markdown, codeBlocks);

    // 修復標題中的無序列表標記（-、*、+ 開頭，後面跟空格）
    // ### - 標題 → ### 標題
    String fixedContent =
        Pattern.compile("^(#{1,6}\\s+)[-*+]\\s+(.*)$", Pattern.MULTILINE)
            .matcher(protectedContent)
            .replaceAll("$1$2");

    // 修復標題中的有序列表標記（數字. 開頭，後面跟空格）
    // ### 1. 標題 → ### 標題
    fixedContent =
        Pattern.compile("^(#{1,6}\\s+)\\d+\\.\\s+(.*)$", Pattern.MULTILINE)
            .matcher(fixedContent)
            .replaceAll("$1$2");

    // 還原程式碼區塊
    return restoreCodeBlocks(fixedContent, codeBlocks);
  }

  /**
   * 修復嵌套列表縮排不足的問題。
   *
   * <p>自動將巢狀列表縮排校正為每層固定 4 個空格。
   *
   * @param markdown 原始 Markdown
   * @return 修復後的 Markdown
   */
  private String fixNestedListIndentation(String markdown) {
    // 先保護程式碼區塊
    List<String> codeBlocks = new ArrayList<>();
    String protectedContent = protectCodeBlocks(markdown, codeBlocks);

    String[] lines = protectedContent.split("\n", -1);
    StringBuilder result = new StringBuilder();
    Deque<Integer> rawIndentStack = new ArrayDeque<>();
    Deque<Integer> normalizedIndentStack = new ArrayDeque<>();

    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      String trimmed = line.trim();

      // 空行：重置縮排堆疊
      if (trimmed.isEmpty()) {
        rawIndentStack.clear();
        normalizedIndentStack.clear();
        result.append(line);
        if (i < lines.length - 1) {
          result.append("\n");
        }
        continue;
      }

      int leadingSpaces = countLeadingSpaces(line);

      // 檢查是否為列表行
      boolean isUnorderedList =
          trimmed.matches("^[-*+]\\s+.*")
              && !isLikelyEmphasisLine(trimmed)
              && !isHorizontalRule(trimmed);
      boolean isOrderedList = trimmed.matches("^\\d+\\.\\s+.*");
      boolean isListLine = isUnorderedList || isOrderedList;

      if (isListLine) {
        while (!rawIndentStack.isEmpty() && leadingSpaces < rawIndentStack.peek()) {
          rawIndentStack.pop();
          normalizedIndentStack.pop();
        }

        int targetIndent = leadingSpaces;
        if (rawIndentStack.isEmpty()) {
          targetIndent = leadingSpaces;
          rawIndentStack.push(leadingSpaces);
          normalizedIndentStack.push(targetIndent);
        } else {
          int parentRawIndent = rawIndentStack.peek();
          int parentNormalizedIndent = normalizedIndentStack.peek();
          if (leadingSpaces > parentRawIndent) {
            targetIndent = parentNormalizedIndent + 4;
            rawIndentStack.push(leadingSpaces);
            normalizedIndentStack.push(targetIndent);
          } else {
            targetIndent = parentNormalizedIndent;
          }
        }

        String content = line.substring(firstNonWhitespaceIndex(line));
        String fixedLine = " ".repeat(targetIndent) + content;
        result.append(fixedLine);
      } else {
        // 非列表行：如果縮排減少，重置父級縮排
        while (!rawIndentStack.isEmpty() && leadingSpaces < rawIndentStack.peek()) {
          rawIndentStack.pop();
          normalizedIndentStack.pop();
        }
        result.append(line);
      }

      if (i < lines.length - 1) {
        result.append("\n");
      }
    }

    // 還原程式碼區塊
    return restoreCodeBlocks(result.toString(), codeBlocks);
  }

  private int firstNonWhitespaceIndex(String line) {
    for (int i = 0; i < line.length(); i++) {
      if (!Character.isWhitespace(line.charAt(i))) {
        return i;
      }
    }
    return line.length();
  }

  /** 計算行首的空格數量。 */
  private int countLeadingSpaces(String line) {
    int count = 0;
    for (char c : line.toCharArray()) {
      if (c == ' ') {
        count++;
      } else if (c == '\t') {
        count += 4; // tab 視為 4 個空格
      } else {
        break;
      }
    }
    return count;
  }

  /**
   * 修復 Discord 不支援的底線粗體格式。
   *
   * <p>將 `__text__` 轉換為 `**text**`（Discord 只支援星號粗體）。
   *
   * @param markdown 原始 Markdown
   * @return 修復後的 Markdown
   */
  private String fixDiscordUnderlineBold(String markdown) {
    // 先保護程式碼區塊
    List<String> codeBlocks = new ArrayList<>();
    String protectedContent = protectCodeBlocks(markdown, codeBlocks);

    // 將 __text__ 轉換為 **text**
    // 使用非貪婪匹配，避免過度匹配
    String fixedContent =
        Pattern.compile("__(.+?)__").matcher(protectedContent).replaceAll("**$1**");

    // 還原程式碼區塊
    return restoreCodeBlocks(fixedContent, codeBlocks);
  }

  /**
   * 修復 Discord 不支援的 Task List 格式。
   *
   * <p>將 `- [x]` 或 `- [ ]` 轉換為普通列表項 `-`。
   *
   * @param markdown 原始 Markdown
   * @return 修復後的 Markdown
   */
  private String fixTaskList(String markdown) {
    // 先保護程式碼區塊
    List<String> codeBlocks = new ArrayList<>();
    String protectedContent = protectCodeBlocks(markdown, codeBlocks);

    // 移除 Task List 的方括號部分
    // - [x] item → - item
    // - [ ] item → - item
    String fixedContent =
        Pattern.compile("^([ \\t]*[-*+]\\s+)\\[([ xX])\\]\\s+(.*)$", Pattern.MULTILINE)
            .matcher(protectedContent)
            .replaceAll("$1$3");

    // 還原程式碼區塊
    return restoreCodeBlocks(fixedContent, codeBlocks);
  }

  /**
   * 移除 Discord 不支援的水平分隔線。
   *
   * <p>移除 `---`、`***`、`___` 等水平分隔線（Discord 無法正確渲染）。
   *
   * @param markdown 原始 Markdown
   * @return 修復後的 Markdown
   */
  private String fixHorizontalRules(String markdown) {
    // 先保護程式碼區塊
    List<String> codeBlocks = new ArrayList<>();
    String protectedContent = protectCodeBlocks(markdown, codeBlocks);

    String[] lines = protectedContent.split("\n", -1);
    StringBuilder result = new StringBuilder();
    boolean removed = false;

    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      String trimmed = line.trim();

      // 檢查是否為水平分隔線
      if (isHorizontalRule(trimmed)) {
        // 跳過此行（移除水平分隔線）
        removed = true;
        continue;
      }

      result.append(line);
      if (i < lines.length - 1) {
        result.append("\n");
      }
    }

    // 還原程式碼區塊
    if (!removed) {
      return markdown;
    }

    return restoreCodeBlocks(result.toString(), codeBlocks);
  }
}
