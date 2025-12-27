package ltdjms.discord.discord.mock;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import ltdjms.discord.discord.domain.DiscordEmbedBuilder;
import ltdjms.discord.discord.domain.EmbedView;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

/**
 * Mock 版本的 DiscordEmbedBuilder 實作
 *
 * <p>此實作用於測試，記錄所有建構過程並返回模擬的 MessageEmbed 物件。
 */
public class MockDiscordEmbedBuilder implements DiscordEmbedBuilder {

  private String title;
  private String description;
  private Color color;
  private final List<EmbedView.FieldView> fields = new ArrayList<>();
  private String footer;

  /** 建立一個新的 MockDiscordEmbedBuilder */
  public MockDiscordEmbedBuilder() {}

  @Override
  public DiscordEmbedBuilder setTitle(String title) {
    if (title != null && title.length() > MAX_TITLE_LENGTH) {
      this.title = truncateWithEllipsis(title, MAX_TITLE_LENGTH);
    } else {
      this.title = title;
    }
    return this;
  }

  @Override
  public DiscordEmbedBuilder setDescription(String description) {
    if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
      this.description = description.substring(0, MAX_DESCRIPTION_LENGTH);
    } else {
      this.description = description;
    }
    return this;
  }

  @Override
  public DiscordEmbedBuilder setColor(Color color) {
    this.color = color;
    return this;
  }

  @Override
  public DiscordEmbedBuilder addField(String name, String value, boolean inline) {
    // 檢查欄位數量上限
    if (fields.size() >= MAX_FIELDS) {
      return this; // 已達上限，忽略此欄位
    }

    // 截斷欄位名稱和值
    String truncatedName = name;
    String truncatedValue = value;

    if (name != null && name.length() > MAX_FIELD_NAME_LENGTH) {
      truncatedName = truncateWithEllipsis(name, MAX_FIELD_NAME_LENGTH);
    }
    if (value != null && value.length() > MAX_FIELD_VALUE_LENGTH) {
      truncatedValue = truncateWithEllipsis(value, MAX_FIELD_VALUE_LENGTH);
    }

    fields.add(new EmbedView.FieldView(truncatedName, truncatedValue, inline));
    return this;
  }

  @Override
  public DiscordEmbedBuilder setFooter(String text) {
    if (text != null && text.length() > MAX_FOOTER_LENGTH) {
      this.footer = truncateWithEllipsis(text, MAX_FOOTER_LENGTH);
    } else {
      this.footer = text;
    }
    return this;
  }

  @Override
  public MessageEmbed build() {
    // 使用 JDA EmbedBuilder 建立真實的 MessageEmbed
    EmbedBuilder jdaBuilder = new EmbedBuilder();

    if (title != null) {
      jdaBuilder.setTitle(title);
    }
    if (description != null) {
      jdaBuilder.setDescription(description);
    }
    if (color != null) {
      jdaBuilder.setColor(color);
    }
    if (footer != null) {
      jdaBuilder.setFooter(footer);
    }

    for (EmbedView.FieldView field : fields) {
      jdaBuilder.addField(field.toJdaField());
    }

    return jdaBuilder.build();
  }

  @Override
  public List<MessageEmbed> buildPaginated(EmbedView data) {
    List<MessageEmbed> embeds = new ArrayList<>();

    // 處理標題、顏色、Footer（所有頁面共用）
    String title = data.title();
    if (title != null && title.length() > MAX_TITLE_LENGTH) {
      title = truncateWithEllipsis(title, MAX_TITLE_LENGTH);
    }

    Color color = data.color();
    String footer = data.footer();
    if (footer != null && footer.length() > MAX_FOOTER_LENGTH) {
      footer = truncateWithEllipsis(footer, MAX_FOOTER_LENGTH);
    }

    // 處理描述（可能需要分頁）
    String description = data.description();
    if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
      // 描述過長，需要分頁
      int totalPages = (int) Math.ceil((double) description.length() / MAX_DESCRIPTION_LENGTH);

      for (int i = 0; i < totalPages; i++) {
        int start = i * MAX_DESCRIPTION_LENGTH;
        int end = Math.min(start + MAX_DESCRIPTION_LENGTH, description.length());
        String pageDescription = description.substring(start, end);

        EmbedBuilder pageBuilder =
            new EmbedBuilder()
                .setTitle(title + (totalPages > 1 ? " (" + (i + 1) + "/" + totalPages + ")" : ""))
                .setDescription(pageDescription)
                .setColor(color);

        if (footer != null) {
          pageBuilder.setFooter(footer);
        }

        embeds.add(pageBuilder.build());
      }
    } else {
      // 描述不需要分頁，檢查欄位是否需要分頁
      List<EmbedView.FieldView> fields = data.fields();
      if (fields == null || fields.isEmpty()) {
        // 無欄位，建立單一 Embed
        EmbedBuilder pageBuilder =
            new EmbedBuilder().setTitle(title).setDescription(description).setColor(color);

        if (footer != null) {
          pageBuilder.setFooter(footer);
        }

        embeds.add(pageBuilder.build());
      } else {
        // 欄位可能需要分頁
        int fieldPageIndex = 0;
        EmbedBuilder currentBuilder =
            new EmbedBuilder().setTitle(title).setDescription(description).setColor(color);

        if (footer != null) {
          currentBuilder.setFooter(footer);
        }

        for (EmbedView.FieldView field : fields) {
          if (currentBuilder.getFields().size() >= MAX_FIELDS) {
            // 當前 Embed 已滿，儲存並建立新的
            embeds.add(currentBuilder.build());
            fieldPageIndex++;
            currentBuilder =
                new EmbedBuilder()
                    .setTitle(
                        title
                            + (fields.size() > MAX_FIELDS ? " (" + (fieldPageIndex + 1) + ")" : ""))
                    .setColor(color);

            if (footer != null) {
              currentBuilder.setFooter(footer);
            }
          }

          String fieldName = field.name();
          String fieldValue = field.value();

          if (fieldName != null && fieldName.length() > MAX_FIELD_NAME_LENGTH) {
            fieldName = truncateWithEllipsis(fieldName, MAX_FIELD_NAME_LENGTH);
          }
          if (fieldValue != null && fieldValue.length() > MAX_FIELD_VALUE_LENGTH) {
            fieldValue = truncateWithEllipsis(fieldValue, MAX_FIELD_VALUE_LENGTH);
          }

          currentBuilder.addField(fieldName, fieldValue, field.inline());
        }

        // 加入最後一頁
        embeds.add(currentBuilder.build());
      }
    }

    return embeds;
  }

  /**
   * 截斷字串並附加省略號
   *
   * @param str 原始字串
   * @param maxLength 最大長度
   * @return 截斷後的字串
   */
  private String truncateWithEllipsis(String str, int maxLength) {
    if (str == null) {
      return null;
    }
    if (str.length() <= maxLength) {
      return str;
    }
    // 保留空間給省略號
    return str.substring(0, maxLength - ELLIPSIS.length()) + ELLIPSIS;
  }

  // Getter 方法用於測試驗證

  /** 取得記錄的標題 */
  public String getTitle() {
    return title;
  }

  /** 取得記錄的描述 */
  public String getDescription() {
    return description;
  }

  /** 取得記錄的顏色 */
  public Color getColor() {
    return color;
  }

  /** 取得記錄的欄位列表 */
  public List<EmbedView.FieldView> getFields() {
    return new ArrayList<>(fields);
  }

  /** 取得記錄的 Footer */
  public String getFooter() {
    return footer;
  }

  /** 重置所有記錄的值 */
  public void reset() {
    title = null;
    description = null;
    color = null;
    fields.clear();
    footer = null;
  }
}
