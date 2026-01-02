package ltdjms.discord.aichat.domain;

import net.dv8tion.jda.api.entities.channel.concrete.Category;

/**
 * 允許使用 AI 功能的類別值物件。
 *
 * <p>此值物件由 {@code categoryId} 與 {@code categoryName} 識別，用於記錄被授權使用 AI 功能的 Discord 類別。
 */
public record AllowedCategory(long categoryId, String categoryName) {

  /**
   * 建構式，進行驗證。
   *
   * @param categoryId 類別 ID，必須大於 0
   * @param categoryName 類別名稱，不可為 null 或空白
   * @throws IllegalArgumentException 當 categoryId <= 0 或 categoryName 為空時拋出
   */
  public AllowedCategory {
    if (categoryId <= 0) {
      throw new IllegalArgumentException("類別 ID 必須大於 0");
    }
    if (categoryName == null || categoryName.isBlank()) {
      throw new IllegalArgumentException("類別名稱不可為空");
    }
  }

  /**
   * 從 JDA Category 建立 AllowedCategory 值物件。
   *
   * @param category JDA Category 實例
   * @return AllowedCategory 值物件
   */
  public static AllowedCategory from(Category category) {
    return new AllowedCategory(category.getIdLong(), category.getName());
  }
}
