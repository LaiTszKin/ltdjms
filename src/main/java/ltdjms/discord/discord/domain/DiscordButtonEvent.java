package ltdjms.discord.discord.domain;

import java.util.List;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.components.ActionRow;

/**
 * Discord 按鈕互動事件抽象
 *
 * <p>此介面擴展 {@link DiscordInteraction}，增加按鈕特定的操作：
 *
 * <ul>
 *   <li>取得按鈕 ID
 *   <li>編輯訊息的 Embed
 *   <li>編輯訊息的元件（按鈕、選擇選單等）
 * </ul>
 *
 * <h2>使用範例：</h2>
 *
 * <pre>{@code
 * public void handleButtonInteraction(ButtonInteractionEvent event) {
 *     DiscordButtonEvent buttonEvent = new ButtonInteractionAdapter(event);
 *
 *     String buttonId = buttonEvent.getButtonId();
 *     long guildId = buttonEvent.getGuildId();
 *     long userId = buttonEvent.getUserId();
 *
 *     // 業務邏輯...
 *
 *     // 編輯 Embed
 *     buttonEvent.editEmbed(newEmbed);
 * }
 * }</pre>
 *
 * @see DiscordInteraction
 * @see DiscordModalEvent
 */
public interface DiscordButtonEvent extends DiscordInteraction {

  /**
   * 取得按鈕 ID
   *
   * <p>按鈕 ID 是在建立按鈕時指定的唯一識別碼， 用於識別使用者點擊了哪個按鈕。
   *
   * @return 按鈕組件 ID
   */
  String getButtonId();

  /**
   * 編輯訊息的 Embed
   *
   * <p>此方法會更新原有訊息的 Embed，而不是發送新訊息。 適合用於更新面板、表單等需要多次變更的場景。
   *
   * @param embed 新的 Embed 物件
   */
  void editEmbed(MessageEmbed embed);

  /**
   * 編輯訊息的元件（按鈕、選擇選單等）
   *
   * <p>此方法會更新原有訊息的元件，例如：
   *
   * <ul>
   *   <li>停用/啟用按鈕
   *   <li>更換按鈕標籤
   *   <li>更新選擇選單選項
   * </ul>
   *
   * @param components ActionRow 列表
   */
  void editComponents(List<ActionRow> components);
}
