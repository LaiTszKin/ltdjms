package ltdjms.discord.aichat.services;

import ltdjms.discord.aichat.domain.SystemPrompt;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;

/** 提示詞載入器介面，用於從外部檔案系統載入系統提示詞。 */
public interface PromptLoader {

  /**
   * 從 prompts 資料夾載入所有 markdown 檔案並合併為系統提示詞。
   *
   * @return 成功時回傳合併後的 {@link SystemPrompt}，失敗時回傳 {@link DomainError}
   */
  Result<SystemPrompt, DomainError> loadPrompts();
}
