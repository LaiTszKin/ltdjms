package ltdjms.discord.discord.domain;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.InteractionHook;

/**
 * Discord 互動回應的統一抽象介面
 *
 * <p>此介面提供與 Discord 互動的所有必要操作，包括：
 * <ul>
 *   <li>發送訊息回應</li>
 *   <li>發送 Embed 訊息</li>
 *   <li>編輯現有訊息</li>
 *   <li>延遲回應（defer reply）</li>
 * </ul>
 *
 * <p>實作類別應將 JDA 特定的細節封裝，提供統一的介面給業務邏輯使用。
 */
public interface DiscordInteraction {

    /**
     * 取得 Guild ID
     *
     * @return Guild ID
     */
    long getGuildId();

    /**
     * 取得使用者 ID
     *
     * @return 使用者 ID
     */
    long getUserId();

    /**
     * 檢查此互動是否為 ephemeral（僅使用者可見）
     *
     * @return true 如果是 ephemeral
     */
    boolean isEphemeral();

    /**
     * 回覆純文字訊息
     *
     * @param message 訊息內容
     */
    void reply(String message);

    /**
     * 回覆 Embed 訊息
     *
     * @param embed Embed 物件
     */
    void replyEmbed(MessageEmbed embed);

    /**
     * 編輯現有訊息的 Embed
     *
     * @param embed 新的 Embed 物件
     */
    void editEmbed(MessageEmbed embed);

    /**
     * 延遲回應（告知 Discord 將稍後回應）
     */
    void deferReply();

    /**
     * 取得底層 JDA InteractionHook
     *
     * @return InteractionHook 物件
     */
    InteractionHook getHook();

    /**
     * 檢查互動是否已被確認
     *
     * @return true 如果已被確認
     */
    boolean isAcknowledged();
}
