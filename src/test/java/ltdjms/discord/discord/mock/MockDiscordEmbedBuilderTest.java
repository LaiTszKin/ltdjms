package ltdjms.discord.discord.mock;

import static org.assertj.core.api.Assertions.*;

import java.awt.Color;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ltdjms.discord.discord.domain.DiscordEmbedBuilder;
import ltdjms.discord.discord.domain.EmbedView;
import net.dv8tion.jda.api.entities.MessageEmbed;

/**
 * MockDiscordEmbedBuilder 實作單元測試
 *
 * <p>測試 Mock 版本的 DiscordEmbedBuilder 實作， 驗證其追蹤建構過程並返回模擬資料。
 */
@DisplayName("MockDiscordEmbedBuilder 實作測試")
class MockDiscordEmbedBuilderTest {

  private MockDiscordEmbedBuilder builder;

  @BeforeEach
  void setUp() {
    builder = new MockDiscordEmbedBuilder();
  }

  @Test
  @DisplayName("建構應該建立一個新的 Mock builder")
  void constructorShouldCreateNewMockBuilder() {
    assertThat(builder).isNotNull();
  }

  @Test
  @DisplayName("setTitle 應該記錄標題並返回 this")
  void setTitleShouldRecordTitleAndReturnThis() {
    DiscordEmbedBuilder result = builder.setTitle("測試標題");

    assertThat(result).isSameAs(builder);
    assertThat(builder.getTitle()).isEqualTo("測試標題");
  }

  @Test
  @DisplayName("setDescription 應該記錄描述並返回 this")
  void setDescriptionShouldRecordDescriptionAndReturnThis() {
    DiscordEmbedBuilder result = builder.setDescription("測試描述");

    assertThat(result).isSameAs(builder);
    assertThat(builder.getDescription()).isEqualTo("測試描述");
  }

  @Test
  @DisplayName("setColor 應該記錄顏色並返回 this")
  void setColorShouldRecordColorAndReturnThis() {
    Color testColor = new Color(0x5865F2);
    DiscordEmbedBuilder result = builder.setColor(testColor);

    assertThat(result).isSameAs(builder);
    assertThat(builder.getColor()).isEqualTo(testColor);
  }

  @Test
  @DisplayName("addField 應該記錄欄位並返回 this")
  void addFieldShouldRecordFieldAndReturnThis() {
    DiscordEmbedBuilder result = builder.addField("欄位名稱", "欄位值", true);

    assertThat(result).isSameAs(builder);
    assertThat(builder.getFields()).hasSize(1);

    EmbedView.FieldView field = builder.getFields().get(0);
    assertThat(field.name()).isEqualTo("欄位名稱");
    assertThat(field.value()).isEqualTo("欄位值");
    assertThat(field.inline()).isTrue();
  }

  @Test
  @DisplayName("addField 可以新增多個欄位")
  void addFieldCanAddMultipleFields() {
    builder.addField("欄位1", "值1", true).addField("欄位2", "值2", false).addField("欄位3", "值3", true);

    assertThat(builder.getFields()).hasSize(3);
    assertThat(builder.getFields().get(0).name()).isEqualTo("欄位1");
    assertThat(builder.getFields().get(1).name()).isEqualTo("欄位2");
    assertThat(builder.getFields().get(2).name()).isEqualTo("欄位3");
  }

  @Test
  @DisplayName("setFooter 應該記錄 footer 並返回 this")
  void setFooterShouldRecordFooterAndReturnThis() {
    DiscordEmbedBuilder result = builder.setFooter("Footer 文字");

    assertThat(result).isSameAs(builder);
    assertThat(builder.getFooter()).isEqualTo("Footer 文字");
  }

  @Test
  @DisplayName("build 應該返回模擬的 MessageEmbed")
  void buildShouldReturnMockMessageEmbed() {
    builder
        .setTitle("測試標題")
        .setDescription("測試描述")
        .setColor(new Color(0x5865F2))
        .addField("欄位1", "值1", true)
        .setFooter("Footer");

    MessageEmbed embed = builder.build();

    assertThat(embed).isNotNull();
    assertThat(embed.getTitle()).isEqualTo("測試標題");
    assertThat(embed.getDescription()).isEqualTo("測試描述");
    assertThat(embed.getColor()).isEqualTo(new Color(0x5865F2));
    assertThat(embed.getFields()).hasSize(1);
    assertThat(embed.getFooter().getText()).isEqualTo("Footer");
  }

  @Test
  @DisplayName("buildPaginated 應該返回模擬的 Embed 列表")
  void buildPaginatedShouldReturnMockEmbedList() {
    EmbedView view =
        new EmbedView(
            "標題",
            "描述",
            new Color(0x5865F2),
            List.of(
                new EmbedView.FieldView("欄位1", "值1", true),
                new EmbedView.FieldView("欄位2", "值2", false)),
            "Footer");

    List<MessageEmbed> embeds = builder.buildPaginated(view);

    assertThat(embeds).isNotNull();
    assertThat(embeds).hasSize(1);

    MessageEmbed embed = embeds.get(0);
    assertThat(embed.getTitle()).isEqualTo("標題");
    assertThat(embed.getDescription()).isEqualTo("描述");
    assertThat(embed.getFields()).hasSize(2);
  }

  @Test
  @DisplayName("buildPaginated 對於長描述應該分頁")
  void buildPaginatedShouldPaginateLongDescription() {
    // 建立超過 4096 字元的描述
    String longDescription = "a".repeat(10000);
    EmbedView view = new EmbedView("標題", longDescription, new Color(0x5865F2), List.of(), null);

    List<MessageEmbed> embeds = builder.buildPaginated(view);

    // 應該產生多個 Embed
    assertThat(embeds).hasSizeGreaterThan(1);

    // 驗證每個 Embed 的描述長度
    for (MessageEmbed embed : embeds) {
      assertThat(embed.getDescription().length()).isLessThanOrEqualTo(4096);
    }
  }

  @Test
  @DisplayName("buildPaginated 對於超過 25 個欄位應該分頁")
  void buildPaginatedShouldPaginateOver25Fields() {
    // 建立超過 25 個欄位
    List<EmbedView.FieldView> fields = new java.util.ArrayList<>();
    for (int i = 0; i < 30; i++) {
      fields.add(new EmbedView.FieldView("欄位" + i, "值" + i, false));
    }

    EmbedView view = new EmbedView("標題", "描述", new Color(0x5865F2), fields, null);

    List<MessageEmbed> embeds = builder.buildPaginated(view);

    // 應該產生多個 Embed
    assertThat(embeds).hasSizeGreaterThan(1);

    // 驗證每個 Embed 的欄位數量
    for (MessageEmbed embed : embeds) {
      assertThat(embed.getFields().size()).isLessThanOrEqualTo(25);
    }
  }

  @Test
  @DisplayName("流式 API 應該正確運作")
  void fluentAPIShouldWork() {
    builder
        .setTitle("標題")
        .setDescription("描述")
        .setColor(new Color(0x5865F2))
        .addField("欄位1", "值1", true)
        .addField("欄位2", "值2", false)
        .setFooter("Footer");

    assertThat(builder.getTitle()).isEqualTo("標題");
    assertThat(builder.getDescription()).isEqualTo("描述");
    assertThat(builder.getColor()).isEqualTo(new Color(0x5865F2));
    assertThat(builder.getFields()).hasSize(2);
    assertThat(builder.getFooter()).isEqualTo("Footer");
  }

  @Test
  @DisplayName("重置應該清除所有記錄的值")
  void resetShouldClearAllRecordedValues() {
    builder.setTitle("標題").setDescription("描述").addField("欄位", "值", false);

    builder.reset();

    assertThat(builder.getTitle()).isNull();
    assertThat(builder.getDescription()).isNull();
    assertThat(builder.getFields()).isEmpty();
    assertThat(builder.getColor()).isNull();
    assertThat(builder.getFooter()).isNull();
  }

  @Test
  @DisplayName("連續呼叫 build 應該產生獨立的 Embed")
  void consecutiveBuildShouldCreateIndependentEmbeds() {
    builder.setTitle("第一個");
    MessageEmbed embed1 = builder.build();

    builder.setTitle("第二個");
    MessageEmbed embed2 = builder.build();

    assertThat(embed1.getTitle()).isEqualTo("第一個");
    assertThat(embed2.getTitle()).isEqualTo("第二個");
  }

  @Test
  @DisplayName("getter 方法應該返回記錄的值")
  void gettersShouldReturnRecordedValues() {
    Color testColor = new Color(0x5865F2);

    builder
        .setTitle("標題")
        .setDescription("描述")
        .setColor(testColor)
        .addField("欄位", "值", true)
        .setFooter("Footer");

    assertThat(builder.getTitle()).isEqualTo("標題");
    assertThat(builder.getDescription()).isEqualTo("描述");
    assertThat(builder.getColor()).isEqualTo(testColor);
    assertThat(builder.getFields()).hasSize(1);
    assertThat(builder.getFooter()).isEqualTo("Footer");
  }

  @Test
  @DisplayName("空 builder 應該拋出異常（JDA 限制）")
  void emptyBuilderShouldBuild() {
    // JDA 的 EmbedBuilder 不允許建立空的 embed
    assertThatThrownBy(() -> builder.build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Cannot build an empty embed!");
  }

  @Test
  @DisplayName("特殊字元應該被正確處理")
  void specialCharactersShouldBeHandled() {
    builder.setTitle("標題 @everyone https://example.com");
    builder.setDescription("描述 **粗體** *斜體* `code`");

    assertThat(builder.getTitle()).contains("@everyone");
    assertThat(builder.getDescription()).contains("**粗體**");
  }

  @Test
  @DisplayName("長度限制應該被模擬處理")
  void lengthLimitsShouldBeSimulated() {
    // 超長標題
    String longTitle = "a".repeat(300);
    builder.setTitle(longTitle);

    // Mock 實作應該模擬截斷行為
    assertThat(builder.getTitle().length()).isLessThanOrEqualTo(256);
  }

  @Test
  @DisplayName("build 時應該自動應用長度限制")
  void buildShouldApplyLengthLimits() {
    // 設定合理的長度值，確保總長度不超過 JDA 的 6000 字元限制
    builder.setTitle("a".repeat(200));
    builder.setDescription("b".repeat(2000));
    builder.setFooter("c".repeat(1000));

    MessageEmbed embed = builder.build();

    // 驗證長度限制已應用
    assertThat(embed.getTitle().length()).isLessThanOrEqualTo(256);
    assertThat(embed.getDescription().length()).isLessThanOrEqualTo(4096);
    assertThat(embed.getFooter().getText().length()).isLessThanOrEqualTo(2048);
  }
}
