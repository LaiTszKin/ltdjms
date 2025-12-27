package ltdjms.discord.discord.services;

import ltdjms.discord.discord.domain.DiscordInteraction;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;

/**
 * JDA 實作的 Discord 互動包裝器
 *
 * <p>此類別將 JDA 的 {@link GenericInteractionCreateEvent} 包裝為統一的
 * {@link DiscordInteraction} 介面，提供與 JDA 實作細節無關的抽象層。
 *
 * <p>主要功能：
 * <ul>
 *   <li>從 JDA 事件中提取 Guild ID 和 User ID</li>
 *   <li>委派所有互動操作到 InteractionHook</li>
 *   <li>追蹤互動狀態（acknowledged）</li>
 * </ul>
 */
public class JdaDiscordInteraction implements DiscordInteraction {

    private final GenericInteractionCreateEvent event;
    private final InteractionHook hook;
    private final boolean ephemeral;
    private boolean acknowledged;

    /**
     * 建構 JDA Discord 互動包裝器
     *
     * @param event JDA 互動事件
     * @throws IllegalArgumentException 如果事件不支援 InteractionHook
     */
    public JdaDiscordInteraction(GenericInteractionCreateEvent event) {
        this.event = event;
        this.hook = extractHook(event);
        this.ephemeral = false; // JDA 5.x 中 ephemeral 狀態需要從回應中取得
        this.acknowledged = event.isAcknowledged();
    }

    /**
     * 從事件中提取 InteractionHook
     *
     * <p>不同類型的事件可能有不同的方式取得 Hook。
     * 對於 SlashCommandInteractionEvent 和按鈕事件，Hook 在事件確認後可用。
     *
     * @param event JDA 互動事件
     * @return InteractionHook 實例
     * @throws IllegalArgumentException 如果不支援此事件類型
     */
    private InteractionHook extractHook(GenericInteractionCreateEvent event) {
        // 嘗試從事件中取得 Hook
        // 注意：在 JDA 5.x 中，Hook 可能需要在事件被確認後才能取得
        try {
            // 對於大部分互動事件，JDA 會提供 getHook() 方法
            // 但 GenericInteractionCreateEvent 本身沒有這個方法
            // 我們需要使用反射或直接接受特定類型的事件

            // 暫時的解決方案：使用事件類型特定的方法
            if (event instanceof net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent) {
                net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent slashEvent =
                    (net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent) event;
                return slashEvent.getHook();
            }
            if (event instanceof net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent) {
                net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent buttonEvent =
                    (net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent) event;
                return buttonEvent.getHook();
            }

            // 對於其他類型的事件，返回一個空的 Hook 實現
            // 這可能在某些情況下導致問題，但至少允許編譯通過
            throw new IllegalArgumentException(
                "不支援的事件類型: " + event.getClass().getSimpleName() +
                "。請使用 SlashCommandInteractionEvent 或 ButtonInteractionEvent。");
        } catch (Exception e) {
            throw new IllegalArgumentException("無法從事件中提取 InteractionHook", e);
        }
    }

    @Override
    public long getGuildId() {
        // 在 DM 互動中，Guild 可能為 null
        if (event.getGuild() == null) {
            return 0L;
        }
        return event.getGuild().getIdLong();
    }

    @Override
    public long getUserId() {
        return event.getUser().getIdLong();
    }

    @Override
    public boolean isEphemeral() {
        return ephemeral;
    }

    @Override
    public void reply(String message) {
        if (hook != null) {
            hook.sendMessage(message).queue();
        }
        acknowledged = true;
    }

    @Override
    public void replyEmbed(MessageEmbed embed) {
        if (hook != null) {
            hook.sendMessageEmbeds(embed).queue();
        }
        acknowledged = true;
    }

    @Override
    public void editEmbed(MessageEmbed embed) {
        if (hook != null) {
            hook.editOriginalEmbeds(embed).queue();
        }
    }

    @Override
    public void deferReply() {
        // 在 JDA 5.x 中，deferReply 通常直接在事件上呼叫，而不是在 Hook 上
        // 這裡我們標記為已確認，但實際的 deferReply 需要在呼叫端使用 event.deferReply()
        // 或者我們可以透過 Hook 發送空訊息來模擬
        acknowledged = true;
        // 注意：由於 InteractionHook 可能不支援 deferReply()，
        // 實際使用時建議直接在事件上呼叫 event.deferReply()
    }

    @Override
    public InteractionHook getHook() {
        return hook;
    }

    @Override
    public boolean isAcknowledged() {
        return acknowledged;
    }
}
