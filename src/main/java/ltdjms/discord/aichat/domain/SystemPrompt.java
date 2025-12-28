package ltdjms.discord.aichat.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 系統提示詞，由多個提示詞區間組成的完整內容。
 *
 * <p>此物件用於合併多個 markdown 檔案的內容，生成單一的 system prompt 字串。
 *
 * @param sections 提示詞區間列表（按字母順序排列）
 */
public record SystemPrompt(List<PromptSection> sections) {

  /** 建立空的系統提示詞。 */
  public static SystemPrompt empty() {
    return new SystemPrompt(List.of());
  }

  /** 建立包含單一區間的系統提示詞。 */
  public static SystemPrompt of(PromptSection section) {
    return new SystemPrompt(List.of(Objects.requireNonNull(section)));
  }

  /** 建立包含多個區間的系統提示詞。 */
  public static SystemPrompt of(List<PromptSection> sections) {
    return new SystemPrompt(new ArrayList<>(Objects.requireNonNull(sections)));
  }

  /** 檢查是否為空提示詞。 */
  public boolean isEmpty() {
    return sections.isEmpty();
  }

  /** 獲取區間數量。 */
  public int sectionCount() {
    return sections.size();
  }

  /**
   * 合併所有區間為單一字串，用於傳送至 AI API。
   *
   * <p>每個區間之間以換行符號分隔。
   *
   * @return 合併後的完整提示詞字串（空提示詞回傳空字串）
   */
  public String toCombinedString() {
    if (isEmpty()) {
      return "";
    }

    StringBuilder sb = new StringBuilder();
    for (PromptSection section : sections) {
      if (!section.isEmpty()) {
        sb.append(section.toFormattedString()).append("\n\n");
      }
    }

    // 移除末尾的換行符號
    if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
      sb.setLength(sb.length() - 1);
    }
    if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
      sb.setLength(sb.length() - 1);
    }

    return sb.toString();
  }

  /** 獲取不可變的區間列表（防止外部修改）。 */
  @Override
  public List<PromptSection> sections() {
    return Collections.unmodifiableList(sections);
  }
}
