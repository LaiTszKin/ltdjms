package ltdjms.discord.discord.adapter;

import ltdjms.discord.discord.domain.DiscordModalEvent;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.modals.Modal;

import java.util.Optional;

/**
 * Modal Interaction 事件適配器
 *
 * <p>此類別將 JDA 的 {@link ModalInteractionEvent} 適配到 {@link DiscordModalEvent} 抽象介面，
 * 允許業務邏輯統一處理 Modal 表單提交，而不直接依賴 JDA。
 *
 * <h2>使用範例：</h2>
 * <pre>{@code
 * public void handle(ModalInteractionEvent event) {
 *     DiscordModalEvent modalEvent = new ModalInteractionAdapter(event);
 *
 *     // 使用抽象介面處理互動
 *     long guildId = modalEvent.getGuildId();
 *     long userId = modalEvent.getUserId();
 *     String modalId = modalEvent.getModalId();
 *
 *     // 取得表單欄位值
 *     Optional<String> name = modalEvent.getValueAsString("name_field");
 *     Optional<Long> amount = modalEvent.getValueAsLong("amount_field");
 *
 *     // 業務邏輯...
 *
 *     // 回應
 *     modalEvent.reply("表單已提交");
 * }
 * }</pre>
 */
public class ModalInteractionAdapter implements DiscordModalEvent {

    private final ModalInteractionEvent event;
    private boolean acknowledged;

    /**
     * 建立一個新的 ModalInteractionAdapter
     *
     * @param event JDA ModalInteractionEvent
     */
    public ModalInteractionAdapter(ModalInteractionEvent event) {
        this.event = event;
        this.acknowledged = false;
    }

    @Override
    public String getModalId() {
        return event.getModalId();
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
        event.getHook().editOriginalEmbeds(embed).queue();
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

    @Override
    public String getValue(String fieldId) {
        return event.getValue(fieldId) != null ? event.getValue(fieldId).getAsString() : null;
    }

    @Override
    public Optional<String> getValueAsString(String fieldId) {
        return Optional.ofNullable(event.getValue(fieldId))
                .map(value -> value.getAsString())
                .filter(s -> !s.isEmpty());
    }

    @Override
    public Optional<Long> getValueAsLong(String fieldId) {
        return Optional.ofNullable(event.getValue(fieldId))
                .map(value -> {
                    try {
                        return Long.parseLong(value.getAsString());
                    } catch (NumberFormatException e) {
                        return null;
                    }
                });
    }

    /**
     * 取得底層的 JDA ModalInteractionEvent
     *
     * <p>此方法僅用於需要直接存取 JDA API 的場景，
     * 一般情況下應使用抽象介面的方法。
     *
     * @return 底層的 JDA 事件
     */
    public ModalInteractionEvent getJdaEvent() {
        return event;
    }
}
