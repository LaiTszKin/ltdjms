package ltdjms.discord.discord.domain;

import net.dv8tion.jda.api.entities.MessageEmbed;

import java.awt.Color;
import java.util.List;

/**
 * Embed 視圖的不可變資料結構
 *
 * <p>此 record 用於傳遞 Embed 的資料，提供與 JDA MessageEmbed 的解耦。
 *
 * <p>長度限制驗證由 {@link DiscordEmbedBuilder#buildPaginated(EmbedView)} 負責，
 * 以支援自動分頁功能。
 *
 * @param title 標題
 * @param description 描述
 * @param color 顏色
 * @param fields 欄位列表
 * @param footer Footer 文字
 */
public record EmbedView(
        String title,
        String description,
        Color color,
        List<FieldView> fields,
        String footer
) {
    /**
     * 欄位視圖
     *
     * @param name 名稱
     * @param value 值
     * @param inline 是否內聯顯示
     */
    public record FieldView(
            String name,
            String value,
            boolean inline
    ) {

        /**
         * 轉換為 JDA MessageEmbed.Field
         *
         * @return MessageEmbed.Field 物件
         */
        public MessageEmbed.Field toJdaField() {
            return new MessageEmbed.Field(name, value, inline);
        }
    }
}
