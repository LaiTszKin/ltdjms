package ltdjms.discord.discord.adapter;

import ltdjms.discord.discord.domain.DiscordContext;
import ltdjms.discord.discord.domain.DiscordInteraction;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SlashCommandAdapter 單元測試
 */
@DisplayName("SlashCommandAdapter 測試")
class SlashCommandAdapterTest {

    @Test
    @DisplayName("fromSlashEvent 應該拋出異常當 event 為 null")
    void fromSlashEventShouldThrowExceptionWhenEventIsNull() {
        assertThatThrownBy(() -> SlashCommandAdapter.fromSlashEvent(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不能為 null");
    }

    @Test
    @DisplayName("fromGenericEvent 應該拋出異常當 event 為 null")
    void fromGenericEventShouldThrowExceptionWhenEventIsNull() {
        assertThatThrownBy(() -> SlashCommandAdapter.fromGenericEvent(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不能為 null");
    }

    @Test
    @DisplayName("toContext 應該拋出異常當 event 為 null")
    void toContextShouldThrowExceptionWhenEventIsNull() {
        assertThatThrownBy(() -> SlashCommandAdapter.toContext((SlashCommandInteractionEvent) null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不能為 null");
    }

    @Test
    @DisplayName("toContext from GenericEvent 應該拋出異常當 event 為 null")
    void toContextFromGenericEventShouldThrowExceptionWhenEventIsNull() {
        assertThatThrownBy(() -> SlashCommandAdapter.toContext((GenericInteractionCreateEvent) null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不能為 null");
    }

    @Test
    @DisplayName("fromSlashEvent 應該返回 DiscordInteraction")
    void fromSlashEventShouldReturnDiscordInteraction() {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);

        DiscordInteraction interaction = SlashCommandAdapter.fromSlashEvent(event);

        assertThat(interaction).isNotNull();
    }

    @Test
    @DisplayName("fromGenericEvent 應該返回 DiscordInteraction")
    void fromGenericEventShouldReturnDiscordInteraction() {
        // 使用 SlashCommandInteractionEvent 作為 GenericInteractionCreateEvent 的實例
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        when(event.getGuild()).thenReturn(mock(net.dv8tion.jda.api.entities.Guild.class));
        when(event.getGuild().getIdLong()).thenReturn(123L);
        when(event.getUser()).thenReturn(mock(net.dv8tion.jda.api.entities.User.class));
        when(event.getUser().getIdLong()).thenReturn(456L);

        DiscordInteraction interaction = SlashCommandAdapter.fromGenericEvent(event);

        assertThat(interaction).isNotNull();
    }

    @Test
    @DisplayName("toContext 應該返回 DiscordContext")
    void toContextShouldReturnDiscordContext() {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        when(event.getGuild()).thenReturn(mock(net.dv8tion.jda.api.entities.Guild.class));
        when(event.getGuild().getIdLong()).thenReturn(123L);
        when(event.getUser()).thenReturn(mock(net.dv8tion.jda.api.entities.User.class));
        when(event.getUser().getIdLong()).thenReturn(456L);
        when(event.getChannel()).thenReturn(mock(net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion.class));
        when(event.getChannel().getIdLong()).thenReturn(789L);
        // 確保 getOptions 不返回 null
        when(event.getOptions()).thenReturn(java.util.List.of());

        DiscordContext context = SlashCommandAdapter.toContext(event);

        assertThat(context).isNotNull();
    }

    @Test
    @DisplayName("toContext from GenericEvent 應該返回 DiscordContext")
    void toContextFromGenericEventShouldReturnDiscordContext() {
        // 使用 SlashCommandInteractionEvent 作為 GenericInteractionCreateEvent 的實例
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        when(event.getGuild()).thenReturn(mock(net.dv8tion.jda.api.entities.Guild.class));
        when(event.getGuild().getIdLong()).thenReturn(123L);
        when(event.getUser()).thenReturn(mock(net.dv8tion.jda.api.entities.User.class));
        when(event.getUser().getIdLong()).thenReturn(456L);
        when(event.getChannel()).thenReturn(mock(net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion.class));
        when(event.getChannel().getIdLong()).thenReturn(789L);
        // 確保 getOptions 不返回 null
        when(event.getOptions()).thenReturn(java.util.List.of());

        DiscordContext context = SlashCommandAdapter.toContext(event);

        assertThat(context).isNotNull();
    }

    @Test
    @DisplayName("私有建構函式應該拋出異常")
    void privateConstructorShouldThrowException() {
        assertThatThrownBy(() -> {
            // 使用反射嘗試實例化
            var constructor = SlashCommandAdapter.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
        })
            .hasCauseExactlyInstanceOf(UnsupportedOperationException.class)
            .cause()
            .hasMessageContaining("無法實例化");
    }
}
