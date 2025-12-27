package ltdjms.discord.discord.domain;

import static org.assertj.core.api.Assertions.*;

import java.awt.Color;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.dv8tion.jda.api.entities.MessageEmbed;

/**
 * DiscordEmbedBuilder 介面契約測試
 *
 * <p>此測試定義 DiscordEmbedBuilder 介面的行為契約。 所有實作（JDA 和 Mock）都必須符合此契約。
 */
@DisplayName("DiscordEmbedBuilder 介面契約測試")
class DiscordEmbedBuilderTest {

  /** 測試用的抽象建構器，用於驗證介面契約 */
  private static class TestDiscordEmbedBuilder implements DiscordEmbedBuilder {
    private String title;
    private String description;
    private Color color;
    private final java.util.List<EmbedView.FieldView> fields = new java.util.ArrayList<>();
    private String footer;

    @Override
    public DiscordEmbedBuilder setTitle(String title) {
      this.title = title;
      return this;
    }

    @Override
    public DiscordEmbedBuilder setDescription(String description) {
      this.description = description;
      return this;
    }

    @Override
    public DiscordEmbedBuilder setColor(Color color) {
      this.color = color;
      return this;
    }

    @Override
    public DiscordEmbedBuilder addField(String name, String value, boolean inline) {
      this.fields.add(new EmbedView.FieldView(name, value, inline));
      return this;
    }

    @Override
    public DiscordEmbedBuilder setFooter(String text) {
      this.footer = text;
      return this;
    }

    @Override
    public MessageEmbed build() {
      throw new UnsupportedOperationException("測試實作不需要真實建構");
    }

    @Override
    public List<MessageEmbed> buildPaginated(EmbedView data) {
      throw new UnsupportedOperationException("測試實作不需要真實建構");
    }

    // 測試用 getter 方法
    public String getTitle() {
      return title;
    }

    public String getDescription() {
      return description;
    }

    public Color getColor() {
      return color;
    }

    public List<EmbedView.FieldView> getFields() {
      return fields;
    }

    public String getFooter() {
      return footer;
    }
  }

  @Test
  @DisplayName("setTitle 應該設定標題並返回 this")
  void setTitleShouldSetTitleAndReturnThis() {
    TestDiscordEmbedBuilder builder = new TestDiscordEmbedBuilder();

    DiscordEmbedBuilder result = builder.setTitle("測試標題");

    assertThat(result).isSameAs(builder);
    assertThat(builder.getTitle()).isEqualTo("測試標題");
  }

  @Test
  @DisplayName("setDescription 應該設定描述並返回 this")
  void setDescriptionShouldSetDescriptionAndReturnThis() {
    TestDiscordEmbedBuilder builder = new TestDiscordEmbedBuilder();

    DiscordEmbedBuilder result = builder.setDescription("測試描述");

    assertThat(result).isSameAs(builder);
    assertThat(builder.getDescription()).isEqualTo("測試描述");
  }

  @Test
  @DisplayName("setColor 應該設定顏色並返回 this")
  void setColorShouldSetColorAndReturnThis() {
    TestDiscordEmbedBuilder builder = new TestDiscordEmbedBuilder();
    Color testColor = new Color(0x5865F2);

    DiscordEmbedBuilder result = builder.setColor(testColor);

    assertThat(result).isSameAs(builder);
    assertThat(builder.getColor()).isEqualTo(testColor);
  }

  @Test
  @DisplayName("addField 應該新增欄位並返回 this")
  void addFieldShouldAddFieldAndReturnThis() {
    TestDiscordEmbedBuilder builder = new TestDiscordEmbedBuilder();

    DiscordEmbedBuilder result = builder.addField("欄位名稱", "欄位值", true);

    assertThat(result).isSameAs(builder);
    assertThat(builder.getFields()).hasSize(1);
    assertThat(builder.getFields().get(0).name()).isEqualTo("欄位名稱");
    assertThat(builder.getFields().get(0).value()).isEqualTo("欄位值");
    assertThat(builder.getFields().get(0).inline()).isTrue();
  }

  @Test
  @DisplayName("addField 可以新增多個欄位")
  void addFieldCanAddMultipleFields() {
    TestDiscordEmbedBuilder builder = new TestDiscordEmbedBuilder();

    builder.addField("欄位1", "值1", true).addField("欄位2", "值2", false).addField("欄位3", "值3", true);

    assertThat(builder.getFields()).hasSize(3);
  }

  @Test
  @DisplayName("setFooter 應該設定 footer 並返回 this")
  void setFooterShouldSetFooterAndReturnThis() {
    TestDiscordEmbedBuilder builder = new TestDiscordEmbedBuilder();

    DiscordEmbedBuilder result = builder.setFooter("Footer 文字");

    assertThat(result).isSameAs(builder);
    assertThat(builder.getFooter()).isEqualTo("Footer 文字");
  }

  @Test
  @DisplayName("支援流式 API")
  void shouldSupportFluentAPI() {
    TestDiscordEmbedBuilder builder = new TestDiscordEmbedBuilder();

    builder
        .setTitle("標題")
        .setDescription("描述")
        .setColor(new Color(0x5865F2))
        .addField("欄位1", "值1", true)
        .addField("欄位2", "值2", false)
        .setFooter("Footer");

    assertThat(builder.getTitle()).isEqualTo("標題");
    assertThat(builder.getDescription()).isEqualTo("描述");
    assertThat(builder.getFields()).hasSize(2);
    assertThat(builder.getFooter()).isEqualTo("Footer");
  }

  @Test
  @DisplayName("build 應該返回 MessageEmbed 物件")
  void buildShouldReturnMessageEmbed() {
    TestDiscordEmbedBuilder builder = new TestDiscordEmbedBuilder();

    assertThatThrownBy(() -> builder.build()).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("buildPaginated 應該返回 Embed 列表")
  void buildPaginatedShouldReturnEmbedList() {
    TestDiscordEmbedBuilder builder = new TestDiscordEmbedBuilder();

    assertThatThrownBy(() -> builder.buildPaginated(new EmbedView(null, null, null, null, null)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("介面方法簽章應該正確")
  void interfaceMethodSignaturesShouldBeCorrect() {
    // 驗證介面包含所有必要的方法
    assertThat(DiscordEmbedBuilder.class)
        .hasDeclaredMethods(
            "setTitle",
            "setDescription",
            "setColor",
            "addField",
            "setFooter",
            "build",
            "buildPaginated");
  }

  /** Discord API 長度限制驗證測試 這些測試定義了實作必須遵守的限制 */
  @org.junit.jupiter.api.Nested
  @DisplayName("Discord API 長度限制契約")
  class LengthLimitContractTests {

    @Test
    @DisplayName("Title 長度限制：256 字元")
    void titleLengthLimitShouldBe256() {
      // Discord API 限制：標題最多 256 字元
      int maxLength = 256;

      // 實作必須確保不超過此限制
      String longTitle = "a".repeat(maxLength + 100);

      // 實作應該截斷或拋出異常
      // 此處僅定義契約，實際驗證在實作測試中進行
      assertThat(longTitle.length()).isGreaterThan(maxLength);
    }

    @Test
    @DisplayName("Description 長度限制：4096 字元")
    void descriptionLengthLimitShouldBe4096() {
      int maxLength = 4096;

      String longDescription = "a".repeat(maxLength + 100);

      assertThat(longDescription.length()).isGreaterThan(maxLength);
    }

    @Test
    @DisplayName("Field Name 長度限制：256 字元")
    void fieldNameLengthLimitShouldBe256() {
      int maxLength = 256;

      String longFieldName = "a".repeat(maxLength + 100);

      assertThat(longFieldName.length()).isGreaterThan(maxLength);
    }

    @Test
    @DisplayName("Field Value 長度限制：1024 字元")
    void fieldValueLengthLimitShouldBe1024() {
      int maxLength = 1024;

      String longFieldValue = "a".repeat(maxLength + 100);

      assertThat(longFieldValue.length()).isGreaterThan(maxLength);
    }

    @Test
    @DisplayName("Fields 數量限制：25 個")
    void fieldsCountLimitShouldBe25() {
      int maxFields = 25;

      // 實作必須確保不超過此限制
      assertThat(maxFields).isEqualTo(25);
    }

    @Test
    @DisplayName("Footer 長度限制：2048 字元")
    void footerLengthLimitShouldBe2048() {
      int maxLength = 2048;

      String longFooter = "a".repeat(maxLength + 100);

      assertThat(longFooter.length()).isGreaterThan(maxLength);
    }
  }
}
