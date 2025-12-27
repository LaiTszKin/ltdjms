package ltdjms.discord.discord.adapter;

import ltdjms.discord.discord.domain.DiscordButtonEvent;
import ltdjms.discord.discord.domain.DiscordInteraction;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;

import java.util.List;

/**
 * Button Interaction 事件適配器
 *
 * <p>此類別將 JDA 的 {@link ButtonInteractionEvent} 適配到 {@link DiscordInteraction} 抽象介面，
 * 允許業務邏輯統一處理按鈕互動，而不直接依賴 JDA。
 *
 * <h2>使用範例：</h2>
 * <pre>{@code
 * public void handle(ButtonInteractionEvent event) {
 *     DiscordInteraction interaction = new ButtonInteractionAdapter(event);
 *
 *     // 使用抽象介面處理互動
 *     long guildId = interaction.getGuildId();
 *     long userId = interaction.getUserId();
 *
 *     // 業務邏輯...
 *
 *     // 回應
 *     interaction.editEmbed(newEmbed);
 * }
 * }</pre>
 */
public class ButtonInteractionAdapter implements DiscordButtonEvent {

    private final ButtonInteractionEvent event;
    private boolean acknowledged;

    /**
     * 建立一個新的 ButtonInteractionAdapter
     *
     * @param event JDA ButtonInteractionEvent
     */
    public ButtonInteractionAdapter(ButtonInteractionEvent event) {
        this.event = event;
        this.acknowledged = false;
    }

    @Override
    public long getGuildId() {
        return event.getGuild().getIdLong();
    }

    @Override
    public long getUserId() {
        return event.getUser().getIdLong();
    }

    @Override
    public boolean isEphemeral() {
        return event.isAcknowledged(); // JDA 不直接提供 ephemeral 狀態，推斷自是否已確認
    }

    @Override
    public void reply(String message) {
        event.reply(message).queue();
        acknowledged = true;
    }

    @Override
    public void replyEmbed(MessageEmbed embed) {
        event.replyEmbeds(embed).queue();
        acknowledged = true;
    }

    @Override
    public void editEmbed(MessageEmbed embed) {
        event.editMessageEmbeds(embed).queue();
        acknowledged = true;
    }

    /**
     * 編輯訊息的元件（按鈕、選擇選單等）
     *
     * @param components ActionRow 列表
     */
    public void editComponents(List<ActionRow> components) {
        event.editComponents(components).queue();
        acknowledged = true;
    }

    @Override
    public void deferReply() {
        event.deferReply().queue();
        acknowledged = true;
    }

    @Override
    public net.dv8tion.jda.api.interactions.InteractionHook getHook() {
        return event.getHook();
    }

    @Override
    public boolean isAcknowledged() {
        return acknowledged || event.isAcknowledged();
    }

    /**
     * 取得按鈕 ID
     *
     * <p>這是 Button Interaction 特有的方法。
     *
     * @return 按鈕組件 ID
     */
    public String getButtonId() {
        return event.getComponentId();
    }

    /**
     * 取得底層的 JDA ButtonInteractionEvent
     *
     * <p>此方法僅用於需要直接存取 JDA API 的場景，
     * 一般情況下應使用抽象介面的方法。
     *
     * @return 底層的 JDA 事件
     */
    public ButtonInteractionEvent getJdaEvent() {
        return event;
    }
}
