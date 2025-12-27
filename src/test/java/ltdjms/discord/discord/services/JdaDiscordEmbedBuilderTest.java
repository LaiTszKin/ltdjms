package ltdjms.discord.discord.services;

import ltdjms.discord.discord.domain.DiscordEmbedBuilder;
import ltdjms.discord.discord.domain.EmbedView;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.awt.Color;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * JdaDiscordEmbedBuilder 實作單元測試
 *
 * <p>測試 JDA 版本的 DiscordEmbedBuilder 實作，
 * 驗證其正確包裝 JDA EmbedBuilder 並處理長度限制。
 */
@DisplayName("JdaDiscordEmbedBuilder 實作測試")
class JdaDiscordEmbedBuilderTest {

    private DiscordEmbedBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new JdaDiscordEmbedBuilder();
    }

    @Test
    @DisplayName("建構應該建立一個新的 builder")
    void constructorShouldCreateNewBuilder() {
        assertThat(builder).isNotNull();
    }

    @Test
    @DisplayName("setTitle 應該正確設定標題")
    void setTitleShouldSetTitle() {
        builder.setTitle("測試標題");
        MessageEmbed embed = builder.build();

        assertThat(embed.getTitle()).isEqualTo("測試標題");
    }

    @Test
    @DisplayName("setTitle 應該自動截斷超過 256 字元的標題")
    void setTitleShouldTruncateOver256Characters() {
        String longTitle = "a".repeat(300);
        builder.setTitle(longTitle);
        MessageEmbed embed = builder.build();

        // 標題應該被截斷為 256 字元（或加上省略號）
        assertThat(embed.getTitle().length()).isLessThanOrEqualTo(256);
    }

    @Test
    @DisplayName("setTitle 截斷時應該加上省略號")
    void setTitleTruncationShouldAddEllipsis() {
        String longTitle = "a".repeat(260);
        builder.setTitle(longTitle);
        MessageEmbed embed = builder.build();

        // 被截斷的標題應該以 "..." 結尾
        if (longTitle.length() > 256) {
            assertThat(embed.getTitle()).endsWith("...");
        }
    }

    @Test
    @DisplayName("setDescription 應該正確設定描述")
    void setDescriptionShouldSetDescription() {
        builder.setDescription("測試描述");
        MessageEmbed embed = builder.build();

        assertThat(embed.getDescription()).isEqualTo("測試描述");
    }

    @Test
    @DisplayName("setDescription 應該自動截斷超過 4096 字元的描述")
    void setDescriptionShouldTruncateOver4096Characters() {
        String longDescription = "a".repeat(5000);
        builder.setDescription(longDescription);
        MessageEmbed embed = builder.build();

        assertThat(embed.getDescription().length()).isLessThanOrEqualTo(4096);
    }

    @Test
    @DisplayName("setColor 應該正確設定顏色")
    void setColorShouldSetColor() {
        Color testColor = new Color(0x5865F2);
        builder.setColor(testColor);
        MessageEmbed embed = builder.build();

        assertThat(embed.getColor()).isEqualTo(testColor);
    }

    @Test
    @DisplayName("addField 應該正確新增欄位")
    void addFieldShouldAddField() {
        builder.addField("欄位名稱", "欄位值", false);
        MessageEmbed embed = builder.build();

        assertThat(embed.getFields()).hasSize(1);
        MessageEmbed.Field field = embed.getFields().get(0);
        assertThat(field.getName()).isEqualTo("欄位名稱");
        assertThat(field.getValue()).isEqualTo("欄位值");
        assertThat(field.isInline()).isFalse();
    }

    @Test
    @DisplayName("addField 應該自動截斷超過 256 字元的欄位名稱")
    void addFieldShouldTruncateFieldNameOver256Characters() {
        String longFieldName = "a".repeat(300);
        builder.addField(longFieldName, "值", false);
        MessageEmbed embed = builder.build();

        MessageEmbed.Field field = embed.getFields().get(0);
        assertThat(field.getName().length()).isLessThanOrEqualTo(256);
    }

    @Test
    @DisplayName("addField 應該自動截斷超過 1024 字元的欄位值")
    void addFieldShouldTruncateFieldValueOver1024Characters() {
        String longFieldValue = "a".repeat(1500);
        builder.addField("名稱", longFieldValue, false);
        MessageEmbed embed = builder.build();

        MessageEmbed.Field field = embed.getFields().get(0);
        assertThat(field.getValue().length()).isLessThanOrEqualTo(1024);
    }

    @Test
    @DisplayName("addField 應該限制最多 25 個欄位")
    void addFieldShouldLimitTo25Fields() {
        // 嘗試新增 30 個欄位
        for (int i = 0; i < 30; i++) {
            builder.addField("欄位" + i, "值" + i, false);
        }
        MessageEmbed embed = builder.build();

        // 應該只保留前 25 個
        assertThat(embed.getFields()).hasSize(25);
    }

    @Test
    @DisplayName("setFooter 應該正確設定 footer")
    void setFooterShouldSetFooter() {
        builder.setFooter("Footer 文字");
        MessageEmbed embed = builder.build();

        assertThat(embed.getFooter().getText()).isEqualTo("Footer 文字");
    }

    @Test
    @DisplayName("setFooter 應該自動截斷超過 2048 字元的 footer")
    void setFooterShouldTruncateOver2048Characters() {
        String longFooter = "a".repeat(3000);
        builder.setFooter(longFooter);
        MessageEmbed embed = builder.build();

        assertThat(embed.getFooter().getText().length()).isLessThanOrEqualTo(2048);
    }

    @Test
    @DisplayName("流式 API 應該正確運作")
    void fluentAPIShouldWork() {
        MessageEmbed embed = builder
            .setTitle("標題")
            .setDescription("描述")
            .setColor(new Color(0x5865F2))
            .addField("欄位1", "值1", true)
            .addField("欄位2", "值2", false)
            .setFooter("Footer")
            .build();

        assertThat(embed.getTitle()).isEqualTo("標題");
        assertThat(embed.getDescription()).isEqualTo("描述");
        assertThat(embed.getFields()).hasSize(2);
        assertThat(embed.getFooter().getText()).isEqualTo("Footer");
    }

    @Test
    @DisplayName("空 Embed 應該拋出異常（JDA 限制）")
    void emptyEmbedShouldBuild() {
        // JDA 的 EmbedBuilder 不允許建立空的 embed
        assertThatThrownBy(() -> builder.build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot build an empty embed!");
    }

    @Test
    @DisplayName("buildPaginated 應該將長描述分頁")
    void buildPaginatedShouldPaginateLongDescription() {
        // 建立一個超過 4096 字元的描述
        String longDescription = "a".repeat(10000);
        EmbedView view = new EmbedView(
            "標題",
            longDescription,
            new Color(0x5865F2),
            List.of(),
            null
        );

        List<MessageEmbed> embeds = builder.buildPaginated(view);

        // 應該產生多個 Embed
        assertThat(embeds).hasSizeGreaterThan(1);

        // 每個 Embed 的描述都不應超過 4096 字元
        for (MessageEmbed embed : embeds) {
            assertThat(embed.getDescription().length()).isLessThanOrEqualTo(4096);
        }
    }

    @Test
    @DisplayName("buildPaginated 對於短描述應該返回單一 Embed")
    void buildPaginatedShouldReturnSingleEmbedForShortDescription() {
        EmbedView view = new EmbedView(
            "標題",
            "短描述",
            new Color(0x5865F2),
            List.of(),
            null
        );

        List<MessageEmbed> embeds = builder.buildPaginated(view);

        assertThat(embeds).hasSize(1);
        assertThat(embeds.get(0).getDescription()).isEqualTo("短描述");
    }

    @Test
    @DisplayName("buildPaginated 應該處理超過 25 個欄位的情況")
    void buildPaginatedShouldHandleOver25Fields() {
        // 建立超過 25 個欄位
        List<EmbedView.FieldView> fields = new java.util.ArrayList<>();
        for (int i = 0; i < 30; i++) {
            fields.add(new EmbedView.FieldView("欄位" + i, "值" + i, false));
        }

        EmbedView view = new EmbedView(
            "標題",
            "描述",
            new Color(0x5865F2),
            fields,
            null
        );

        List<MessageEmbed> embeds = builder.buildPaginated(view);

        // 應該產生多個 Embed 來容納所有欄位
        assertThat(embeds).hasSizeGreaterThan(1);

        // 每個 Embed 最多 25 個欄位
        for (MessageEmbed embed : embeds) {
            assertThat(embed.getFields().size()).isLessThanOrEqualTo(25);
        }
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
    @DisplayName("特殊字元應該被正確處理")
    void specialCharactersShouldBeHandled() {
        builder.setTitle("標題 @everyone https://example.com");
        builder.setDescription("描述 **粗體** *斜體* `code`");
        MessageEmbed embed = builder.build();

        assertThat(embed.getTitle()).contains("@everyone");
        assertThat(embed.getDescription()).contains("**粗體**");
    }
}
