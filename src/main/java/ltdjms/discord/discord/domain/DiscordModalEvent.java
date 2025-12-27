package ltdjms.discord.discord.domain;

import java.util.Optional;

/**
 * Discord Modal 互動事件抽象
 *
 * <p>此介面擴展 {@link DiscordInteraction}，增加 Modal 特定的操作：
 * <ul>
 *   <li>取得 Modal ID</li>
 *   <li>取得指定欄位的值</li>
 *   <li>取得欄位值作為特定類型</li>
 * </ul>
 *
 * <h2>使用範例：</h2>
 * <pre>{@code
 * public void handleModalInteraction(ModalInteractionEvent event) {
 *     DiscordModalEvent modalEvent = new ModalInteractionAdapter(event);
 *
 *     String modalId = modalEvent.getModalId();
 *     long guildId = modalEvent.getGuildId();
 *     long userId = modalEvent.getUserId();
 *
 *     // 取得表單欄位值
 *     Optional<String> name = modalEvent.getValueAsString("name_field");
 *     Optional<String> amount = modalEvent.getValueAsString("amount_field");
 *
 *     // 業務邏輯...
 *
 *     // 回應使用者
 *     modalEvent.reply("表單已提交");
 * }
 * }</pre>
 *
 * @see DiscordInteraction
 * @see DiscordButtonEvent
 */
public interface DiscordModalEvent extends DiscordInteraction {

    /**
     * 取得 Modal ID
     *
     * <p>Modal ID 是在建立 Modal 時指定的唯一識別碼，
     * 用於識別使用者提交了哪個表單。
     *
     * @return Modal 識別碼
     */
    String getModalId();

    /**
     * 取得指定欄位的值
     *
     * <p>欄位 ID 是在建立 Modal 時為每個 TextInput 指定的識別碼。
     *
     * @param fieldId 欄位 ID
     * @return 欄位值，如果欄位不存在則返回 null
     */
    String getValue(String fieldId);

    /**
     * 取得指定欄位的值作為 String
     *
     * <p>這是 {@link #getValue(String)} 的安全版本，返回 Optional。
     *
     * @param fieldId 欄位 ID
     * @return Optional 包含字串值，如果欄位不存在或為空則為空
     */
    Optional<String> getValueAsString(String fieldId);

    /**
     * 取得指定欄位的值作為 Long
     *
     * <p>這會嘗試將欄位值解析為 Long，適合用於數字輸入欄位。
     *
     * @param fieldId 欄位 ID
     * @return Optional 包含 long 值，如果欄位不存在、為空或無法解析則為空
     */
    Optional<Long> getValueAsLong(String fieldId);
}
