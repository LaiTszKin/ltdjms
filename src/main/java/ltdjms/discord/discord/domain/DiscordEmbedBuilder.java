package ltdjms.discord.discord.domain;

import net.dv8tion.jda.api.entities.MessageEmbed;

import java.awt.Color;
import java.util.List;

/**
 * Discord Embed 建構器抽象介面
 *
 * <p>此介面提供流式 API 來建構 Discord Embed 訊息，
 * 並自動處理 Discord API 的長度限制。
 *
 * <h2>Discord API 長度限制：</h2>
 * <ul>
 *   <li>Title: 256 字元</li>
 *   <li>Description: 4096 字元</li>
 *   <li>Field Name: 256 字元</li>
 *   <li>Field Value: 1024 字元</li>
 *   <li>Fields: 25 個</li>
 *   <li>Footer: 2048 字元</li>
 * </ul>
 *
 * <h2>使用範例：</h2>
 * <pre>{@code
 * DiscordEmbedBuilder builder = ...;
 * MessageEmbed embed = builder
 *     .setTitle("標題")
 *     .setDescription("描述")
 *     .setColor(new Color(0x5865F2))
 *     .addField("欄位名稱", "欄位值", true)
 *     .setFooter("Footer 文字")
 *     .build();
 * }</pre>
 */
public interface DiscordEmbedBuilder {

    /**
     * Discord API 長度限制常數
     */
    int MAX_TITLE_LENGTH = 256;
    int MAX_DESCRIPTION_LENGTH = 4096;
    int MAX_FIELD_NAME_LENGTH = 256;
    int MAX_FIELD_VALUE_LENGTH = 1024;
    int MAX_FIELDS = 25;
    int MAX_FOOTER_LENGTH = 2048;
    String ELLIPSIS = "...";

    /**
     * 設定 Embed 標題
     *
     * <p>如果標題超過 256 字元，將自動截斷並附加 "..."
     *
     * @param title 標題（最多 256 字元）
     * @return this 建構器
     */
    DiscordEmbedBuilder setTitle(String title);

    /**
     * 設定 Embed 描述
     *
     * <p>如果描述超過 4096 字元，將自動截斷或使用分頁
     *
     * @param description 描述（最多 4096 字元）
     * @return this 建構器
     */
    DiscordEmbedBuilder setDescription(String description);

    /**
     * 設定 Embed 顏色
     *
     * @param color 顏色物件
     * @return this 建構器
     */
    DiscordEmbedBuilder setColor(Color color);

    /**
     * 新增一個欄位
     *
     * <p>欄位名稱超過 256 字元或值超過 1024 字元時將自動截斷。
     * 如果已達 25 個欄位上限，將忽略此呼叫。
     *
     * @param name 欄位名稱（最多 256 字元）
     * @param value 欄位值（最多 1024 字元）
     * @param inline 是否為內聯顯示
     * @return this 建構器
     */
    DiscordEmbedBuilder addField(String name, String value, boolean inline);

    /**
     * 設定 Footer
     *
     * <p>Footer 文字超過 2048 字元時將自動截斷。
     *
     * @param text Footer 文字（最多 2048 字元）
     * @return this 建構器
     */
    DiscordEmbedBuilder setFooter(String text);

    /**
     * 建構最終的 MessageEmbed 物件
     *
     * <p>此方法會應用所有長度限制，確保生成的 Embed 符合 Discord API 規範。
     *
     * @return MessageEmbed 物件
     */
    MessageEmbed build();

    /**
     * 建構多個 Embed（用於分頁）
     *
     * <p>當資料量超過單個 Embed 限制時，此方法會自動分頁。
     *
     * @param data 視圖資料
     * @return Embed 列表
     */
    List<MessageEmbed> buildPaginated(EmbedView data);
}
