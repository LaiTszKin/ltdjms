package ltdjms.discord.markdown.validation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

/** 使用 CommonMark Java 實作的 Markdown 驗證器 檢測語法錯誤並提供結構化錯誤報告 */
public final class CommonMarkValidator implements MarkdownValidator {

  private final Parser parser;
  private final HtmlRenderer renderer;

  public CommonMarkValidator() {
    this.parser =
        Parser.builder()
            .extensions(List.of(TablesExtension.create(), TaskListItemsExtension.create()))
            .build();

    this.renderer =
        HtmlRenderer.builder()
            .extensions(List.of(TablesExtension.create(), TaskListItemsExtension.create()))
            .build();
  }

  // 用於依賴注入的建構函式
  public CommonMarkValidator(Parser parser, HtmlRenderer renderer) {
    this.parser = parser;
    this.renderer = renderer;
  }

  @Override
  public ValidationResult validate(String markdown) {
    if (markdown == null || markdown.isBlank()) {
      return new ValidationResult.Valid(markdown);
    }

    List<MarkdownError> errors = new ArrayList<>();

    // 1. 檢查 Discord 不支援的語法
    checkDiscordUnsupportedSyntax(markdown, errors);

    // 2. 檢查標題層級
    checkHeadingLevels(markdown, errors);
    checkInlineHeadings(markdown, errors);

    // 3. 檢查列表格式
    checkListFormat(markdown, errors);
    checkInlineListItems(markdown, errors);

    // 4. 解析階段 - 檢測語法錯誤
    try {
      Node document = parser.parse(markdown);
      checkCodeBlocks(document, markdown, errors);
      checkDiscordUnsupportedFeatures(document, markdown, errors);
    } catch (Exception e) {
      // 解析失敗視為格式錯誤
      errors.add(
          new MarkdownError(
              ErrorType.MALFORMED_LIST,
              1,
              1,
              markdown.substring(0, Math.min(50, markdown.length())),
              "檢查 Markdown 語法是否正確"));
    }

    if (errors.isEmpty()) {
      return new ValidationResult.Valid(markdown);
    } else {
      return new ValidationResult.Invalid(errors);
    }
  }

  /** 檢查標題層級是否超過 Discord 限制（H6）以及標題格式是否正確 */
  private void checkHeadingLevels(String markdown, List<MarkdownError> errors) {
    String[] lines = markdown.split("\n");

    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      String trimmedLine = line.trim();

      if (trimmedLine.startsWith("#")) {
        int count = 0;
        for (char c : trimmedLine.toCharArray()) {
          if (c == '#') {
            count++;
          } else {
            break;
          }
        }

        // 只有 # 符號時視為合法（CommonMark 允許空標題）
        if (count < trimmedLine.length()) {
          // 檢查 # 後面是否有空格（CommonMark 規範要求）
          char charAfterHash = trimmedLine.charAt(count);
          if (charAfterHash != ' ' && charAfterHash != '\t') {
            errors.add(
                new MarkdownError(
                    ErrorType.HEADING_FORMAT,
                    i + 1,
                    count + 1,
                    line.length() > 50 ? line.substring(0, 50) + "..." : line,
                    "在 # 符號後面加入空格，例如 ### " + trimmedLine.substring(count)));
          } else {
            // 檢查標題文字中是否包含列表標記
            String headingText = trimmedLine.substring(count + 1).trim();
            checkListMarkersInHeading(headingText, i + 1, line, errors);
            checkInlineListMarkersInHeading(headingText, i + 1, line, errors);
          }
        }

        if (count > 6) {
          errors.add(
              new MarkdownError(
                  ErrorType.HEADING_LEVEL_EXCEEDED, i + 1, 1, line, "減少標題層級到 ###### 或以下"));
        }
      }
    }
  }

  /** 檢查標題文字中是否包含列表標記（如 -、*、+ 或 數字.） */
  private void checkListMarkersInHeading(
      String headingText, int lineNum, String originalLine, List<MarkdownError> errors) {
    // 檢查無序列表標記（-、*、+ 開頭）
    // 即使後面跟著空格，在標題中也不應該出現列表標記
    if (headingText.matches("^[-*+]\\s.*")) {
      errors.add(
          new MarkdownError(
              ErrorType.HEADING_CONTAINS_LIST_MARKER,
              lineNum,
              1,
              originalLine.length() > 50 ? originalLine.substring(0, 50) + "..." : originalLine,
              "標題文字中不應包含列表標記 '" + headingText.charAt(0) + "'"));
    }

    // 檢查有序列表標記（數字. 開頭）
    if (headingText.matches("^\\d+\\.\\s.*")) {
      errors.add(
          new MarkdownError(
              ErrorType.HEADING_CONTAINS_LIST_MARKER,
              lineNum,
              1,
              originalLine.length() > 50 ? originalLine.substring(0, 50) + "..." : originalLine,
              "標題文字中不應包含列表標記（如 '1.'）"));
    }
  }

  /** 檢查標題文字內部的列表標記（例如 "標題- item"） */
  private void checkInlineListMarkersInHeading(
      String headingText, int lineNum, String originalLine, List<MarkdownError> errors) {
    if (headingText.matches(".*[^\\s][-*+]\\s+.*")) {
      errors.add(
          new MarkdownError(
              ErrorType.HEADING_CONTAINS_LIST_MARKER,
              lineNum,
              1,
              originalLine.length() > 50 ? originalLine.substring(0, 50) + "..." : originalLine,
              "標題後請換行再開始列表（例如使用 \\n- 開頭）"));
    }
  }

  /** 檢查行內標題（標題標記未獨立成行） */
  private void checkInlineHeadings(String markdown, List<MarkdownError> errors) {
    String[] lines = markdown.split("\n");
    boolean inCodeBlock = false;

    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      String trimmed = line.trim();

      if (trimmed.startsWith("```")) {
        inCodeBlock = !inCodeBlock;
        continue;
      }
      if (inCodeBlock) {
        continue;
      }

      if (trimmed.startsWith("#")) {
        continue;
      }

      int headingIndex = line.indexOf("##");
      if (headingIndex > 0) {
        errors.add(
            new MarkdownError(
                ErrorType.HEADING_FORMAT,
                i + 1,
                headingIndex + 1,
                line.length() > 50 ? line.substring(0, 50) + "..." : line,
                "標題需獨立成行，並在 # 後加空格"));
      }
    }
  }

  /** 檢查列表格式是否正確（標記後面應有空格）以及嵌套列表縮排 */
  private void checkListFormat(String markdown, List<MarkdownError> errors) {
    String[] lines = markdown.split("\n");
    boolean inCodeBlock = false;
    // 堆疊追蹤每層列表的縮排深度
    Deque<Integer> indentStack = new ArrayDeque<>();

    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];

      // 追蹤程式碼區塊狀態（避免檢查程式碼中的內容）
      if (line.trim().startsWith("```")) {
        inCodeBlock = !inCodeBlock;
        continue;
      }
      if (inCodeBlock) {
        continue;
      }

      // 計算縮排深度
      int leadingSpaces = 0;
      for (int j = 0; j < line.length(); j++) {
        char c = line.charAt(j);
        if (c == ' ') {
          leadingSpaces++;
        } else if (c == '\t') {
          leadingSpaces += 4; // tab 視為 4 個空格
        } else {
          break;
        }
      }

      String trimmed = line.trim();

      // 空行：重置堆疊（列表結束）
      if (trimmed.isEmpty()) {
        indentStack.clear();
        continue;
      }

      // 檢查是否為列表行
      boolean isUnorderedList = trimmed.matches("^[-*+].*") && !isLikelyEmphasisLine(trimmed);
      boolean isOrderedList = trimmed.matches("^\\d+\\..*");
      boolean isListLine = isUnorderedList || isOrderedList;

      if (isListLine) {
        // 檢查列表標記後是否有空格
        if (isUnorderedList) {
          if (trimmed.matches("^[-*+][^\\s].*")
              && !isHorizontalRule(trimmed)
              && !isLikelyEmphasisLine(trimmed)) {
            errors.add(
                new MarkdownError(
                    ErrorType.MALFORMED_LIST,
                    i + 1,
                    leadingSpaces + 1,
                    line.length() > 50 ? line.substring(0, 50) + "..." : line,
                    "在列表標記後面加入空格，例如 " + trimmed.charAt(0) + " "));
            continue; // 已經有錯誤，跳過嵌套檢查
          }
        } else if (isOrderedList) {
          if (trimmed.matches("^\\d+\\.[^\\s].*")) {
            errors.add(
                new MarkdownError(
                    ErrorType.MALFORMED_LIST,
                    i + 1,
                    leadingSpaces + 1,
                    line.length() > 50 ? line.substring(0, 50) + "..." : line,
                    "在數字後面加入空格，例如 1. "));
            continue; // 已經有錯誤，跳過嵌套檢查
          }
        }

        // 先處理堆疊更新（縮排減少時彈出堆疊）
        while (!indentStack.isEmpty() && indentStack.peek() > leadingSpaces) {
          indentStack.pop();
        }

        // 檢查嵌套層級是否正確（只對新的更深層級進行檢查）
        if (!indentStack.isEmpty() && leadingSpaces > indentStack.peek()) {
          int parentIndent = indentStack.peek();
          int expectedIndent = parentIndent + 4;
          // 每一層固定 4 個空格（或 1 個 tab）
          if (leadingSpaces != expectedIndent) {
            errors.add(
                new MarkdownError(
                    ErrorType.MALFORMED_NESTED_LIST,
                    i + 1,
                    leadingSpaces + 1,
                    line.length() > 50 ? line.substring(0, 50) + "..." : line,
                    "巢狀列表每層需縮排 4 個空格，此行應縮排到 " + expectedIndent + " 個空格"));
          }
        }

        // 更新堆疊（添加新的更深層級）
        if (indentStack.isEmpty() || leadingSpaces > indentStack.peek()) {
          if (indentStack.isEmpty() || leadingSpaces != indentStack.peek()) {
            indentStack.push(leadingSpaces);
          }
        }
        // 注意：縮排相同或較小的情况不需要改變堆疊（已在前面處理過彈出）
      } else {
        // 非列表行：如果縮排減少，可能表示列表結束
        while (!indentStack.isEmpty() && indentStack.peek() > leadingSpaces) {
          indentStack.pop();
        }
      }
    }
  }

  /**
   * 檢查同一行中是否出現多個列表標記（例如 "- item1- item2" 或 "1. item1 2. item2"）。
   *
   * <p>Discord 會將這種格式渲染成單一列表項，容易導致格式不符合預期。
   */
  private void checkInlineListItems(String markdown, List<MarkdownError> errors) {
    String[] lines = markdown.split("\n");
    boolean inCodeBlock = false;
    Pattern markerPattern = Pattern.compile("[-*+]\\s+|\\d+\\.\\s+");

    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      String trimmed = line.trim();

      if (trimmed.startsWith("```")) {
        inCodeBlock = !inCodeBlock;
        continue;
      }
      if (inCodeBlock || trimmed.isEmpty()) {
        continue;
      }

      boolean isListLine = trimmed.matches("^[-*+]\\s+.*") || trimmed.matches("^\\d+\\.\\s+.*");
      if (!isListLine || isHorizontalRule(trimmed)) {
        continue;
      }

      Matcher matcher = markerPattern.matcher(line);
      int count = 0;
      int secondMarkerIndex = -1;
      while (matcher.find()) {
        count++;
        if (count == 2) {
          secondMarkerIndex = matcher.start();
          break;
        }
      }

      if (count > 1) {
        errors.add(
            new MarkdownError(
                ErrorType.MALFORMED_LIST,
                i + 1,
                Math.max(1, secondMarkerIndex + 1),
                line.length() > 50 ? line.substring(0, 50) + "..." : line,
                "列表項需分行，請在每個列表標記前換行"));
      }
    }
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

  /** 判斷一行是否為分隔線（---、***、___） */
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

  /** 檢查程式碼區塊是否正確閉合 */
  private void checkCodeBlocks(Node node, String fullMarkdown, List<MarkdownError> errors) {
    String[] lines = fullMarkdown.split("\n");
    boolean inCodeBlock = false;
    int codeBlockStartLine = -1;

    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      if (line.trim().startsWith("```")) {
        if (!inCodeBlock) {
          // 開始一個新的程式碼區塊
          inCodeBlock = true;
          codeBlockStartLine = i;
        } else {
          // 結束當前程式碼區塊
          inCodeBlock = false;
          codeBlockStartLine = -1;
        }
      }
    }

    // 如果仍在程式碼區塊中，表示未閉合
    if (inCodeBlock) {
      String line = lines[codeBlockStartLine];
      errors.add(
          new MarkdownError(
              ErrorType.UNCLOSED_CODE_BLOCK,
              codeBlockStartLine + 1,
              1,
              line.length() > 50 ? line.substring(0, 50) + "..." : line,
              "在程式碼區塊結束處添加 ```"));
    }
  }

  /**
   * 檢查 Discord 不支援的 Markdown 語法（字串層級檢查）。
   *
   * <p>Discord 不支援以下 Markdown 語法：
   *
   * <ul>
   *   <li>水平分隔線（---、***、___）
   *   <li>粗體使用底線（__text__）
   *   <li>Task List（- [x] 或 - [ ]）
   * </ul>
   *
   * @param markdown 待檢查的 Markdown 文字
   * @param errors 錯誤列表
   */
  private void checkDiscordUnsupportedSyntax(String markdown, List<MarkdownError> errors) {
    String[] lines = markdown.split("\n");
    boolean inCodeBlock = false;

    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      String trimmed = line.trim();

      // 追蹤程式碼區塊狀態
      if (trimmed.startsWith("```")) {
        inCodeBlock = !inCodeBlock;
        continue;
      }
      if (inCodeBlock) {
        continue;
      }

      // 檢查水平分隔線（Discord 不支援）
      if (isHorizontalRule(trimmed)) {
        errors.add(
            new MarkdownError(
                ErrorType.DISCORD_RENDER_ISSUE,
                i + 1,
                1,
                line.length() > 50 ? line.substring(0, 50) + "..." : line,
                "Discord 不支援水平分隔線（---、***、___），請移除或改用粗體分隔"));
        continue;
      }

      // 檢查粗體使用底線（Discord 僅支援星號）
      if (trimmed.matches(".*__.+__.*")) {
        // 確保不是誤判（如 __variable_name__ 在程式碼中）
        if (!trimmed.startsWith("```")) {
          errors.add(
              new MarkdownError(
                  ErrorType.DISCORD_RENDER_ISSUE,
                  i + 1,
                  1,
                  line.length() > 50 ? line.substring(0, 50) + "..." : line,
                  "Discord 粗體應使用雙星號（**text**），而非底線（__text__）"));
        }
      }

      // 檢查 Task List（Discord 不支援）
      if (trimmed.matches("^[-*+]\\s+\\[([ xX])\\]\\s+.*")) {
        errors.add(
            new MarkdownError(
                ErrorType.DISCORD_RENDER_ISSUE,
                i + 1,
                1,
                line.length() > 50 ? line.substring(0, 50) + "..." : line,
                "Discord 不支援 Task List（- [ ] 或 - [x]），請改用普通列表"));
      }
    }
  }

  /**
   * 檢查 Discord 不支援的 Markdown 功能（解析後檢查）。
   *
   * <p>Discord 不支援以下 Markdown 功能：
   *
   * <ul>
   *   <li>表格
   * </ul>
   *
   * @param document 解析後的文件節點
   * @param fullMarkdown 完整的 Markdown 文字（用於定位錯誤行號）
   * @param errors 錯誤列表
   */
  private void checkDiscordUnsupportedFeatures(
      Node document, String fullMarkdown, List<MarkdownError> errors) {
    // 遞迴遍歷 AST，檢測表格
    checkForTables(document, fullMarkdown, errors);
  }

  /**
   * 遞迴檢查節點樹中的表格。
   *
   * @param node 當前節點
   * @param fullMarkdown 完整的 Markdown 文字
   * @param errors 錯誤列表
   */
  private void checkForTables(Node node, String fullMarkdown, List<MarkdownError> errors) {
    if (node == null) {
      return;
    }

    // 檢查是否為表格節點
    if (node instanceof TableBlock) {
      int lineNumber = findLineNumber(fullMarkdown, node);
      String tableLine =
          lineNumber > 0 && lineNumber <= fullMarkdown.split("\n").length
              ? fullMarkdown.split("\n")[lineNumber - 1]
              : "| 表格內容 |";

      errors.add(
          new MarkdownError(
              ErrorType.DISCORD_RENDER_ISSUE,
              lineNumber,
              1,
              tableLine.length() > 50 ? tableLine.substring(0, 50) + "..." : tableLine,
              "Discord 不支援表格，請改用列表或其他格式"));
      return; // 找到表格後不需要再檢查子節點
    }

    // 遞迴檢查子節點
    Node child = node.getFirstChild();
    while (child != null) {
      checkForTables(child, fullMarkdown, errors);
      child = child.getNext();
    }
  }

  /**
   * 查找節點在原始 Markdown 中的行號。
   *
   * @param fullMarkdown 完整的 Markdown 文字
   * @param node 要查找的節點
   * @return 行號（從 1 開始），如果找不到返回 1
   */
  private int findLineNumber(String fullMarkdown, Node node) {
    // CommonMark 不提供直接的行號資訊
    // 這裡使用簡單的啟發式方法：查找包含 | 的行
    String[] lines = fullMarkdown.split("\n");
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i].trim();
      if (line.contains("|") && line.startsWith("|")) {
        return i + 1;
      }
    }
    return 1;
  }
}
