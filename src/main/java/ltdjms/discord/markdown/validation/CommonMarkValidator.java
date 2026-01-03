package ltdjms.discord.markdown.validation;

import java.util.ArrayList;
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

    // 2. 解析階段 - 檢測語法錯誤
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
