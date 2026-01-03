package ltdjms.discord.markdown.validation;

/** Markdown 格式驗證器介面 用於驗證 LLM 生成的回應是否符合 Markdown 語法規範 */
public interface MarkdownValidator {

  /**
   * 驗證 Markdown 文字格式
   *
   * @param markdown 待驗證的 Markdown 文字
   * @return 驗證結果，包含成功或失敗的詳細錯誤資訊
   */
  ValidationResult validate(String markdown);

  /** 驗證結果的封閉型別 */
  sealed interface ValidationResult {
    record Valid(String markdown) implements ValidationResult {}

    record Invalid(java.util.List<MarkdownError> errors) implements ValidationResult {}
  }

  /**
   * Markdown 格式錯誤詳細資訊
   *
   * @param type 錯誤類型
   * @param lineNumber 錯誤行號（從 1 開始）
   * @param column 錯誤欄位（從 1 開始）
   * @param context 錯誤上下文
   * @param suggestion 修正建議
   */
  record MarkdownError(
      ErrorType type, int lineNumber, int column, String context, String suggestion) {}

  /** Markdown 錯誤類型枚舉 */
  enum ErrorType {
    /** 列表格式錯誤（如未使用正確的符號） */
    MALFORMED_LIST,
    /** 程式碼區塊未閉合 */
    UNCLOSED_CODE_BLOCK,
    /** 標題層級超過限制（Discord 限制為 H6） */
    HEADING_LEVEL_EXCEEDED,
    /** 標題格式錯誤（如 ###abc 缺少空格） */
    HEADING_FORMAT,
    /** 表格格式錯誤 */
    MALFORMED_TABLE,
    /** 缺少轉義字符 */
    ESCAPE_CHARACTER_MISSING,
    /** Discord 特定的渲染問題 */
    DISCORD_RENDER_ISSUE
  }
}
