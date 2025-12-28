package ltdjms.discord.aichat.domain;

/**
 * 提示詞區間，代表單一檔案的內容與其對應的標題。
 *
 * @param title 區間標題（檔案名稱轉換後的大寫格式，如 "BOT PERSONALITY"）
 * @param content 檔案的 markdown 內容
 */
public record PromptSection(String title, String content) {

  /** 建立空的提示詞區間（用於錯誤處理）。 */
  public static PromptSection empty() {
    return new PromptSection("", "");
  }

  /** 檢查區間是否為空。 */
  public boolean isEmpty() {
    return title.isBlank() && content.isBlank();
  }

  /** 檢查內容是否為空。 */
  public boolean isContentEmpty() {
    return content.isBlank();
  }

  /**
   * 格式化為分隔線 + 標題 + 內容的字串。
   *
   * <p>格式範例：
   *
   * <pre>
   * === BOT PERSONALITY ===
   * 這是機器人的人格描述...
   * </pre>
   */
  public String toFormattedString() {
    if (isEmpty()) {
      return "";
    }
    // 如果內容為空但標題不為空，只返回標題
    if (content.isBlank()) {
      return String.format("=== %s ===", title);
    }
    return String.format("=== %s ===%n%s", title, content);
  }
}
