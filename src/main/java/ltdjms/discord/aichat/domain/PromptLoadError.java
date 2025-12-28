package ltdjms.discord.aichat.domain;

import ltdjms.discord.shared.DomainError;

/**
 * 提示詞載入錯誤工廠類別。
 *
 * <p>提供建立各種提示詞載入錯誤的靜態方法，所有錯誤都使用 {@link DomainError} 類別。
 */
public final class PromptLoadError {

  private PromptLoadError() {
    // 防止實例化
  }

  /** 建立表示資料夾不存在的錯誤。 */
  public static DomainError directoryNotFound(String dirPath) {
    return new DomainError(
        DomainError.Category.PROMPT_DIR_NOT_FOUND,
        String.format("Prompts directory not found: %s", dirPath),
        null);
  }

  /** 建立表示檔案過大的錯誤。 */
  public static DomainError fileTooLarge(String filePath, long maxSizeBytes) {
    return new DomainError(
        DomainError.Category.PROMPT_FILE_TOO_LARGE,
        String.format("Prompt file exceeds size limit: %s (max: %d bytes)", filePath, maxSizeBytes),
        null);
  }

  /** 建立表示檔案讀取失敗的錯誤。 */
  public static DomainError readFailed(String filePath, Throwable cause) {
    return new DomainError(
        DomainError.Category.PROMPT_READ_FAILED,
        String.format("Failed to read prompt file: %s", filePath),
        cause);
  }

  /** 建立表示編碼錯誤的錯誤。 */
  public static DomainError invalidEncoding(String filePath) {
    return new DomainError(
        DomainError.Category.PROMPT_INVALID_ENCODING,
        String.format("Prompt file is not valid UTF-8: %s", filePath),
        null);
  }

  /** 建立表示未知錯誤的錯誤。 */
  public static DomainError unknown(String message, Throwable cause) {
    return new DomainError(
        DomainError.Category.PROMPT_LOAD_FAILED, "Failed to load prompts: " + message, cause);
  }
}
