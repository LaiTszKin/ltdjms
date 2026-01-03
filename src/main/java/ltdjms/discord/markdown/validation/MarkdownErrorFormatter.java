package ltdjms.discord.markdown.validation;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 將 Markdown 驗證錯誤格式化為結構化報告 提供給 LLM 進行自我修正 */
public final class MarkdownErrorFormatter {

  private static final int MAX_CONTEXT_LENGTH = 60;

  /**
   * 格式化錯誤報告
   *
   * @param originalPrompt 原始用戶提示詞
   * @param errors 驗證錯誤列表
   * @param attempt 當前重試次數
   * @param fullResponse 完整的問題回應
   * @return 格式化的錯誤報告
   */
  public String formatErrorReport(
      String originalPrompt,
      List<MarkdownValidator.MarkdownError> errors,
      int attempt,
      String fullResponse) {

    StringBuilder report = new StringBuilder();

    // 概述區段
    report.append("## Markdown 格式驗證失敗\n\n");
    report.append("**重試次數: ").append(attempt).append("**\n");
    report.append("**錯誤總數: ").append(errors.size()).append("**\n\n");

    // 錯誤明細區段（按類型分組）
    Map<MarkdownValidator.ErrorType, List<MarkdownValidator.MarkdownError>> groupedErrors =
        errors.stream().collect(Collectors.groupingBy(MarkdownValidator.MarkdownError::type));

    report.append("### 錯誤明細\n\n");

    for (Map.Entry<MarkdownValidator.ErrorType, List<MarkdownValidator.MarkdownError>> entry :
        groupedErrors.entrySet()) {
      MarkdownValidator.ErrorType type = entry.getKey();
      List<MarkdownValidator.MarkdownError> typeErrors = entry.getValue();

      report.append("#### ").append(getErrorTypeDisplayName(type)).append("\n");

      for (MarkdownValidator.MarkdownError error : typeErrors) {
        report
            .append("- **行 ")
            .append(error.lineNumber())
            .append(", 欄 ")
            .append(error.column())
            .append("**: ")
            .append(error.suggestion())
            .append("\n");

        // 添加上下文（截斷過長的內容）
        String context = error.context();
        if (context.length() > MAX_CONTEXT_LENGTH) {
          context = context.substring(0, MAX_CONTEXT_LENGTH) + "...";
        }
        report.append("  - 上下文: `").append(context).append("`").append("\n");
      }
      report.append("\n");
    }

    return report.toString();
  }

  /** 取得錯誤類型的中文顯示名稱 */
  private String getErrorTypeDisplayName(MarkdownValidator.ErrorType type) {
    return switch (type) {
      case MALFORMED_LIST -> "列表格式錯誤";
      case UNCLOSED_CODE_BLOCK -> "程式碼區塊未閉合";
      case HEADING_LEVEL_EXCEEDED -> "標題層級超限";
      case HEADING_FORMAT -> "標題格式錯誤";
      case MALFORMED_TABLE -> "表格格式錯誤";
      case ESCAPE_CHARACTER_MISSING -> "缺少轉義字符";
      case DISCORD_RENDER_ISSUE -> "Discord 渲染問題";
    };
  }
}
