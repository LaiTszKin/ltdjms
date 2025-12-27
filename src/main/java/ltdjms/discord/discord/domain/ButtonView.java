package ltdjms.discord.discord.domain;

import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;

/**
 * 按鈕視圖的不可變資料結構
 *
 * <p>此 record 用於傳遞按鈕的資料，提供與 JDA Button 的解耦。
 *
 * @param id 按鈕識別碼
 * @param label 標籤文字（最多 80 字元）
 * @param style 按鈕樣式
 * @param disabled 是否停用
 */
public record ButtonView(
        String id,
        String label,
        ButtonStyle style,
        boolean disabled
) {
    public ButtonView {
        // 驗證長度限制
        if (id != null && id.length() > 100) {
            throw new IllegalArgumentException("button id 不可超過 100 字元");
        }
        if (label != null && label.length() > 80) {
            throw new IllegalArgumentException("button label 不可超過 80 字元");
        }
    }

    /**
     * 轉換為 JDA Button
     *
     * @return Button 物件
     */
    public net.dv8tion.jda.api.interactions.components.buttons.Button toJdaButton() {
        return net.dv8tion.jda.api.interactions.components.buttons.Button
                .of(style, id, label)
                .withDisabled(disabled);
    }
}
