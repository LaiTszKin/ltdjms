package ltdjms.discord.discord.services;

import ltdjms.discord.discord.domain.DiscordInteraction;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JdaDiscordInteraction 實作單元測試
 *
 * <p>測試 JDA 事件包裝功能，確保 JdaDiscordInteraction 正確地：
 * <ul>
 *   <li>從 JDA 事件中提取 Guild ID 和 User ID</li>
 *   <li>委派呼叫到 InteractionHook</li>
 *   <li>追蹤互動狀態（acknowledged）</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JdaDiscordInteractionTest {

    private static final long TEST_GUILD_ID = 123456789012345678L;
    private static final long TEST_USER_ID = 987654321098765432L;
    private static final long TEST_CHANNEL_ID = 111111111111111111L;

    @Mock
    private SlashCommandInteractionEvent mockEvent;

    @Mock
    private InteractionHook mockHook;

    @Mock
    private WebhookMessageCreateAction<net.dv8tion.jda.api.entities.Message> mockMessageAction;

    @Mock
    private WebhookMessageEditAction mockEditAction;

    @Mock
    private RestAction<InteractionHook> mockHookRestAction;

    @Mock
    private User mockUser;

    private DiscordInteraction interaction;

    @BeforeEach
    void setUp() {
        // 設定 mock 事件的預設行為
        net.dv8tion.jda.api.entities.Guild mockGuild =
            org.mockito.Mockito.mock(net.dv8tion.jda.api.entities.Guild.class);
        when(mockGuild.getIdLong()).thenReturn(TEST_GUILD_ID);
        when(mockEvent.getGuild()).thenReturn(mockGuild);

        when(mockUser.getIdLong()).thenReturn(TEST_USER_ID);
        when(mockEvent.getUser()).thenReturn(mockUser);

        when(mockEvent.getHook()).thenReturn(mockHook);
        when(mockEvent.isAcknowledged()).thenReturn(false);

        // 設定 Hook mock 預設行為
        when(mockHook.sendMessage(any(String.class))).thenReturn(mockMessageAction);
        when(mockHook.sendMessageEmbeds(any(MessageEmbed.class))).thenReturn(mockMessageAction);
        when(mockHook.editOriginalEmbeds(any(MessageEmbed.class))).thenReturn(mockEditAction);

        // queue() 返回 void，不需要 mock
    }

    @Test
    @DisplayName("getGuildId 應該從 JDA 事件中取得正確的 Guild ID")
    void getGuildId_shouldReturnGuildIdFromEvent() {
        // Given
        interaction = new JdaDiscordInteraction(mockEvent);

        // When
        long guildId = interaction.getGuildId();

        // Then
        assertThat(guildId).isEqualTo(TEST_GUILD_ID);
    }

    @Test
    @DisplayName("getUserId 應該從 JDA 事件中取得正確的 User ID")
    void getUserId_shouldReturnUserIdFromEvent() {
        // Given
        interaction = new JdaDiscordInteraction(mockEvent);

        // When
        long userId = interaction.getUserId();

        // Then
        assertThat(userId).isEqualTo(TEST_USER_ID);
    }

    @Test
    @DisplayName("isEphemeral 應該返回 false（預設值）")
    void isEphemeral_shouldReturnFalseByDefault() {
        // Given
        interaction = new JdaDiscordInteraction(mockEvent);

        // When & Then
        assertThat(interaction.isEphemeral()).isFalse();
    }

    @Test
    @DisplayName("reply 應該透過 Hook 發送訊息")
    void reply_shouldSendMessageThroughHook() {
        // Given
        String message = "測試訊息";
        interaction = new JdaDiscordInteraction(mockEvent);

        // When
        interaction.reply(message);

        // Then
        verify(mockHook).sendMessage(message);
        verify(mockMessageAction).queue();
    }

    @Test
    @DisplayName("reply 應該將互動標記為已確認")
    void reply_shouldMarkInteractionAsAcknowledged() {
        // Given
        interaction = new JdaDiscordInteraction(mockEvent);
        assertThat(interaction.isAcknowledged()).isFalse();

        // When
        interaction.reply("測試");

        // Then
        assertThat(interaction.isAcknowledged()).isTrue();
    }

    @Test
    @DisplayName("replyEmbed 應該透過 Hook 發送 Embed 訊息")
    void replyEmbed_shouldSendEmbedThroughHook() {
        // Given
        MessageEmbed mockEmbed = org.mockito.Mockito.mock(MessageEmbed.class);
        interaction = new JdaDiscordInteraction(mockEvent);

        // When
        interaction.replyEmbed(mockEmbed);

        // Then
        verify(mockHook).sendMessageEmbeds(mockEmbed);
        verify(mockMessageAction).queue();
    }

    @Test
    @DisplayName("replyEmbed 應該將互動標記為已確認")
    void replyEmbed_shouldMarkInteractionAsAcknowledged() {
        // Given
        MessageEmbed mockEmbed = org.mockito.Mockito.mock(MessageEmbed.class);
        interaction = new JdaDiscordInteraction(mockEvent);
        assertThat(interaction.isAcknowledged()).isFalse();

        // When
        interaction.replyEmbed(mockEmbed);

        // Then
        assertThat(interaction.isAcknowledged()).isTrue();
    }

    @Test
    @DisplayName("editEmbed 應該透過 Hook 編輯現有訊息的 Embed")
    void editEmbed_shouldEditEmbedThroughHook() {
        // Given
        MessageEmbed mockEmbed = org.mockito.Mockito.mock(MessageEmbed.class);
        interaction = new JdaDiscordInteraction(mockEvent);

        // When
        interaction.editEmbed(mockEmbed);

        // Then
        verify(mockHook).editOriginalEmbeds(mockEmbed);
        verify(mockEditAction).queue();
    }

    @Test
    @DisplayName("editEmbed 不應該改變 acknowledged 狀態")
    void editEmbed_shouldNotChangeAcknowledgedState() {
        // Given
        MessageEmbed mockEmbed = org.mockito.Mockito.mock(MessageEmbed.class);
        interaction = new JdaDiscordInteraction(mockEvent);
        boolean initialAck = interaction.isAcknowledged();

        // When
        interaction.editEmbed(mockEmbed);

        // Then
        assertThat(interaction.isAcknowledged()).isEqualTo(initialAck);
    }

    @Test
    @DisplayName("deferReply 應該標記為已確認")
    void deferReply_shouldMarkAsAcknowledged() {
        // Given
        interaction = new JdaDiscordInteraction(mockEvent);

        // When
        interaction.deferReply();

        // Then
        assertThat(interaction.isAcknowledged()).isTrue();
    }

    @Test
    @DisplayName("getHook 應該返回底層 JDA InteractionHook")
    void getHook_shouldReturnInteractionHook() {
        // Given
        interaction = new JdaDiscordInteraction(mockEvent);

        // When
        InteractionHook hook = interaction.getHook();

        // Then
        assertThat(hook).isEqualTo(mockHook);
    }

    @Test
    @DisplayName("isAcknowledged 應該在初始狀態返回 false")
    void isAcknowledged_shouldReturnFalseInitially() {
        // Given
        interaction = new JdaDiscordInteraction(mockEvent);

        // When & Then
        assertThat(interaction.isAcknowledged()).isFalse();
    }

    @Test
    @DisplayName("isAcknowledged 應該在 reply 後返回 true")
    void isAcknowledged_shouldReturnTrueAfterReply() {
        // Given
        interaction = new JdaDiscordInteraction(mockEvent);

        // When
        interaction.reply("測試");

        // Then
        assertThat(interaction.isAcknowledged()).isTrue();
    }

    @Test
    @DisplayName("isAcknowledged 應該在 replyEmbed 後返回 true")
    void isAcknowledged_shouldReturnTrueAfterReplyEmbed() {
        // Given
        MessageEmbed mockEmbed = org.mockito.Mockito.mock(MessageEmbed.class);
        interaction = new JdaDiscordInteraction(mockEvent);

        // When
        interaction.replyEmbed(mockEmbed);

        // Then
        assertThat(interaction.isAcknowledged()).isTrue();
    }

    @Test
    @DisplayName("isAcknowledged 應該在 deferReply 後返回 true")
    void isAcknowledged_shouldReturnTrueAfterDeferReply() {
        // Given
        interaction = new JdaDiscordInteraction(mockEvent);

        // When
        interaction.deferReply();

        // Then
        assertThat(interaction.isAcknowledged()).isTrue();
    }

    @Test
    @DisplayName("應該正確處理 null Guild 的情況（DM 互動）")
    void shouldHandleNullGuildForDirectMessages() {
        // Given - 在 DM 中，guild 可能為 null
        when(mockEvent.getGuild()).thenReturn(null);
        interaction = new JdaDiscordInteraction(mockEvent);

        // When
        long guildId = interaction.getGuildId();

        // Then - 應該返回 0 或特殊值表示無 Guild
        assertThat(guildId).isEqualTo(0L);
    }

    @Test
    @DisplayName("多個 reply 呼叫應該正確處理")
    void multipleReplyCallsShouldBeHandled() {
        // Given
        interaction = new JdaDiscordInteraction(mockEvent);

        // When - 第一次 reply
        interaction.reply("第一次訊息");
        boolean firstAck = interaction.isAcknowledged();

        // When - 第二次 reply（雖然實際上不會這樣用）
        interaction.reply("第二次訊息");
        boolean secondAck = interaction.isAcknowledged();

        // Then
        assertThat(firstAck).isTrue();
        assertThat(secondAck).isTrue();
        verify(mockHook).sendMessage("第一次訊息");
        verify(mockHook).sendMessage("第二次訊息");
    }
}
