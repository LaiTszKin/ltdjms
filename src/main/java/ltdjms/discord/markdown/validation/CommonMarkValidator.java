package ltdjms.discord.markdown.validation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

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

    // 1. 檢查標題層級
    checkHeadingLevels(markdown, errors);

    // 2. 檢查列表格式
    checkListFormat(markdown, errors);

    // 3. 解析階段 - 檢測語法錯誤
    try {
      Node document = parser.parse(markdown);
      checkCodeBlocks(document, markdown, errors);
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
      boolean isUnorderedList = trimmed.matches("^[-*+].*");
      boolean isOrderedList = trimmed.matches("^\\d+\\..*");
      boolean isListLine = isUnorderedList || isOrderedList;

      if (isListLine) {
        // 檢查列表標記後是否有空格
        if (isUnorderedList) {
          if (trimmed.matches("^[-*+][^\\s].*") && !isHorizontalRule(trimmed)) {
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
          // CommonMark 規則：嵌套列表必須縮排至少 4 個空格（或 1 個 tab）
          if (leadingSpaces < parentIndent + 4) {
            errors.add(
                new MarkdownError(
                    ErrorType.MALFORMED_NESTED_LIST,
                    i + 1,
                    leadingSpaces + 1,
                    line.length() > 50 ? line.substring(0, 50) + "..." : line,
                    "嵌套列表縮排不足，需要至少 " + (parentIndent + 4) + " 個空格"));
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
}
