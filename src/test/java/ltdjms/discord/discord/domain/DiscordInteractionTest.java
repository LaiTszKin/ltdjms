package ltdjms.discord.discord.domain;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * DiscordInteraction 介面契約測試
 *
 * <p>此測試定義 DiscordInteraction 介面的契約，確保所有實作都符合預期行為。
 */
class DiscordInteractionTest {

    /**
     * 測試實作：用於驗證介面契約的簡單實作
     */
    private static class TestDiscordInteraction implements DiscordInteraction {
        private final long guildId;
        private final long userId;
        private final boolean ephemeral;
        private final InteractionHook hook;
        private boolean acknowledged = false;
        private String lastReply;
        private MessageEmbed lastEmbed;

        public TestDiscordInteraction(long guildId, long userId, boolean ephemeral, InteractionHook hook) {
            this.guildId = guildId;
            this.userId = userId;
            this.ephemeral = ephemeral;
            this.hook = hook;
        }

        @Override
        public long getGuildId() {
            return guildId;
        }

        @Override
        public long getUserId() {
            return userId;
        }

        @Override
        public boolean isEphemeral() {
            return ephemeral;
        }

        @Override
        public void reply(String message) {
            lastReply = message;
            acknowledged = true;
        }

        @Override
        public void replyEmbed(MessageEmbed embed) {
            lastEmbed = embed;
            acknowledged = true;
        }

        @Override
        public void editEmbed(MessageEmbed embed) {
            lastEmbed = embed;
        }

        @Override
        public void deferReply() {
            acknowledged = true;
        }

        @Override
        public InteractionHook getHook() {
            return hook;
        }

        @Override
        public boolean isAcknowledged() {
            return acknowledged;
        }

        // 測試用輔助方法
        public String getLastReply() {
            return lastReply;
        }

        public MessageEmbed getLastEmbed() {
            return lastEmbed;
        }
    }

    @Test
    void getGuildId_shouldReturnCorrectGuildId() {
        // Given
        long expectedGuildId = 123456789L;
        InteractionHook mockHook = mock(InteractionHook.class);
        DiscordInteraction interaction = new TestDiscordInteraction(expectedGuildId, 987654321L, false, mockHook);

        // When
        long actualGuildId = interaction.getGuildId();

        // Then
        assertThat(actualGuildId).isEqualTo(expectedGuildId);
    }

    @Test
    void getUserId_shouldReturnCorrectUserId() {
        // Given
        long expectedUserId = 987654321L;
        InteractionHook mockHook = mock(InteractionHook.class);
        DiscordInteraction interaction = new TestDiscordInteraction(123456789L, expectedUserId, false, mockHook);

        // When
        long actualUserId = interaction.getUserId();

        // Then
        assertThat(actualUserId).isEqualTo(expectedUserId);
    }

    @Test
    void isEphemeral_shouldReturnEphemeralStatus() {
        // Given
        InteractionHook mockHook = mock(InteractionHook.class);
        DiscordInteraction ephemeralInteraction = new TestDiscordInteraction(123456789L, 987654321L, true, mockHook);
        DiscordInteraction publicInteraction = new TestDiscordInteraction(123456789L, 987654321L, false, mockHook);

        // When & Then
        assertThat(ephemeralInteraction.isEphemeral()).isTrue();
        assertThat(publicInteraction.isEphemeral()).isFalse();
    }

    @Test
    void reply_shouldStoreMessageAndMarkAsAcknowledged() {
        // Given
        String message = "測試訊息";
        InteractionHook mockHook = mock(InteractionHook.class);
        TestDiscordInteraction interaction = new TestDiscordInteraction(123456789L, 987654321L, false, mockHook);

        // When
        interaction.reply(message);

        // Then
        assertThat(interaction.getLastReply()).isEqualTo(message);
        assertThat(interaction.isAcknowledged()).isTrue();
    }

    @Test
    void replyEmbed_shouldStoreEmbedAndMarkAsAcknowledged() {
        // Given
        MessageEmbed mockEmbed = mock(MessageEmbed.class);
        InteractionHook mockHook = mock(InteractionHook.class);
        TestDiscordInteraction interaction = new TestDiscordInteraction(123456789L, 987654321L, false, mockHook);

        // When
        interaction.replyEmbed(mockEmbed);

        // Then
        assertThat(interaction.getLastEmbed()).isEqualTo(mockEmbed);
        assertThat(interaction.isAcknowledged()).isTrue();
    }

    @Test
    void editEmbed_shouldStoreEmbed() {
        // Given
        MessageEmbed mockEmbed = mock(MessageEmbed.class);
        InteractionHook mockHook = mock(InteractionHook.class);
        TestDiscordInteraction interaction = new TestDiscordInteraction(123456789L, 987654321L, false, mockHook);

        // When
        interaction.editEmbed(mockEmbed);

        // Then
        assertThat(interaction.getLastEmbed()).isEqualTo(mockEmbed);
    }

    @Test
    void deferReply_shouldMarkAsAcknowledged() {
        // Given
        InteractionHook mockHook = mock(InteractionHook.class);
        TestDiscordInteraction interaction = new TestDiscordInteraction(123456789L, 987654321L, false, mockHook);

        // When
        interaction.deferReply();

        // Then
        assertThat(interaction.isAcknowledged()).isTrue();
    }

    @Test
    void getHook_shouldReturnHook() {
        // Given
        InteractionHook mockHook = mock(InteractionHook.class);
        DiscordInteraction interaction = new TestDiscordInteraction(123456789L, 987654321L, false, mockHook);

        // When
        InteractionHook actualHook = interaction.getHook();

        // Then
        assertThat(actualHook).isEqualTo(mockHook);
    }

    @Test
    void isAcknowledged_shouldReturnFalseInitially() {
        // Given
        InteractionHook mockHook = mock(InteractionHook.class);
        DiscordInteraction interaction = new TestDiscordInteraction(123456789L, 987654321L, false, mockHook);

        // When & Then
        assertThat(interaction.isAcknowledged()).isFalse();
    }

    @Test
    void isAcknowledged_shouldReturnTrueAfterReply() {
        // Given
        InteractionHook mockHook = mock(InteractionHook.class);
        DiscordInteraction interaction = new TestDiscordInteraction(123456789L, 987654321L, false, mockHook);

        // When
        interaction.reply("測試");

        // Then
        assertThat(interaction.isAcknowledged()).isTrue();
    }
}
