package ltdjms.discord.discord.mock;

import ltdjms.discord.discord.domain.DiscordContext;
import net.dv8tion.jda.api.entities.User;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MockDiscordContext 實作單元測試
 *
 * <p>此測試驗證 MockDiscordContext 的實作行為符合 DiscordContext 介面契約。
 */
class MockDiscordContextTest {

    @Test
    void constructor_shouldInitializeWithBasicFields() {
        // Given
        long guildId = 123456789L;
        long userId = 987654321L;
        long channelId = 111222333L;
        String userMention = "<@987654321>";

        // When
        DiscordContext context = new MockDiscordContext(guildId, userId, channelId, userMention);

        // Then
        assertThat(context.getGuildId()).isEqualTo(guildId);
        assertThat(context.getUserId()).isEqualTo(userId);
        assertThat(context.getChannelId()).isEqualTo(channelId);
        assertThat(context.getUserMention()).isEqualTo(userMention);
    }

    @Test
    void getOption_shouldReturnEmptyByDefault() {
        // Given
        DiscordContext context = new MockDiscordContext(123L, 456L, 789L, "<@456>");

        // When
        Optional<String> result = context.getOption("test");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void getOptionAsString_shouldReturnEmptyByDefault() {
        // Given
        DiscordContext context = new MockDiscordContext(123L, 456L, 789L, "<@456>");

        // When
        Optional<String> result = context.getOptionAsString("test");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void getOptionAsLong_shouldReturnEmptyByDefault() {
        // Given
        DiscordContext context = new MockDiscordContext(123L, 456L, 789L, "<@456>");

        // When
        Optional<Long> result = context.getOptionAsLong("test");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void getOptionAsUser_shouldReturnEmptyByDefault() {
        // Given
        DiscordContext context = new MockDiscordContext(123L, 456L, 789L, "<@456>");

        // When
        Optional<User> result = context.getOptionAsUser("test");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void setOptionAsString_shouldStoreStringValue() {
        // Given
        DiscordContext context = new MockDiscordContext(123L, 456L, 789L, "<@456>");
        ((MockDiscordContext) context).setOption("test", "value");

        // When
        Optional<String> result = context.getOption("test");

        // Then
        assertThat(result).isPresent().contains("value");
    }

    @Test
    void setOptionAsLong_shouldStoreLongValue() {
        // Given
        DiscordContext context = new MockDiscordContext(123L, 456L, 789L, "<@456>");
        ((MockDiscordContext) context).setOption("amount", 12345L);

        // When
        Optional<Long> result = context.getOptionAsLong("amount");

        // Then
        assertThat(result).isPresent().contains(12345L);
    }

    @Test
    void setOptionAsUser_shouldStoreUserValue() {
        // Given
        User mockUser = org.mockito.Mockito.mock(User.class);
        DiscordContext context = new MockDiscordContext(123L, 456L, 789L, "<@456>");
        ((MockDiscordContext) context).setOption("target_user", mockUser);

        // When
        Optional<User> result = context.getOptionAsUser("target_user");

        // Then
        assertThat(result).isPresent().contains(mockUser);
    }

    @Test
    void getOption_shouldReturnStoredValueRegardlessOfType() {
        // Given
        DiscordContext context = new MockDiscordContext(123L, 456L, 789L, "<@456>");
        ((MockDiscordContext) context).setOption("test", 12345L);

        // When
        Optional<String> result = context.getOption("test");

        // Then
        assertThat(result).isPresent();
    }

    @Test
    void clearOption_shouldRemoveStoredValue() {
        // Given
        DiscordContext context = new MockDiscordContext(123L, 456L, 789L, "<@456>");
        ((MockDiscordContext) context).setOption("test", "value");

        // When
        ((MockDiscordContext) context).clearOption("test");
        Optional<String> result = context.getOption("test");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void clearAllOptions_shouldRemoveAllStoredValues() {
        // Given
        DiscordContext context = new MockDiscordContext(123L, 456L, 789L, "<@456>");
        ((MockDiscordContext) context).setOption("test1", "value1");
        ((MockDiscordContext) context).setOption("test2", "value2");

        // When
        ((MockDiscordContext) context).clearAllOptions();

        // Then
        assertThat(context.getOption("test1")).isEmpty();
        assertThat(context.getOption("test2")).isEmpty();
    }
}
