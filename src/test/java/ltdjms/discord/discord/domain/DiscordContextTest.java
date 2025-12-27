package ltdjms.discord.discord.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import net.dv8tion.jda.api.entities.User;

/**
 * DiscordContext 介面契約測試
 *
 * <p>此測試定義 DiscordContext 介面的契約，確保所有實作都符合預期行為。
 */
class DiscordContextTest {

  /** 測試實作：用於驗證介面契約的簡單實作 */
  private static class TestDiscordContext implements DiscordContext {
    private final long guildId;
    private final long userId;
    private final long channelId;
    private final String userMention;

    public TestDiscordContext(long guildId, long userId, long channelId, String userMention) {
      this.guildId = guildId;
      this.userId = userId;
      this.channelId = channelId;
      this.userMention = userMention;
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
    public long getChannelId() {
      return channelId;
    }

    @Override
    public String getUserMention() {
      return userMention;
    }

    @Override
    public Optional<String> getOption(String name) {
      return Optional.empty();
    }

    @Override
    public Optional<String> getOptionAsString(String name) {
      return Optional.empty();
    }

    @Override
    public Optional<Long> getOptionAsLong(String name) {
      return Optional.empty();
    }

    @Override
    public Optional<User> getOptionAsUser(String name) {
      return Optional.empty();
    }
  }

  @Test
  void getGuildId_shouldReturnCorrectGuildId() {
    // Given
    long expectedGuildId = 123456789L;
    DiscordContext context =
        new TestDiscordContext(expectedGuildId, 987654321L, 111222333L, "<@123>");

    // When
    long actualGuildId = context.getGuildId();

    // Then
    assertThat(actualGuildId).isEqualTo(expectedGuildId);
  }

  @Test
  void getUserId_shouldReturnCorrectUserId() {
    // Given
    long expectedUserId = 987654321L;
    DiscordContext context =
        new TestDiscordContext(123456789L, expectedUserId, 111222333L, "<@123>");

    // When
    long actualUserId = context.getUserId();

    // Then
    assertThat(actualUserId).isEqualTo(expectedUserId);
  }

  @Test
  void getChannelId_shouldReturnCorrectChannelId() {
    // Given
    long expectedChannelId = 111222333L;
    DiscordContext context =
        new TestDiscordContext(123456789L, 987654321L, expectedChannelId, "<@123>");

    // When
    long actualChannelId = context.getChannelId();

    // Then
    assertThat(actualChannelId).isEqualTo(expectedChannelId);
  }

  @Test
  void getUserMention_shouldReturnCorrectMention() {
    // Given
    String expectedMention = "<@987654321>";
    DiscordContext context =
        new TestDiscordContext(123456789L, 987654321L, 111222333L, expectedMention);

    // When
    String actualMention = context.getUserMention();

    // Then
    assertThat(actualMention).isEqualTo(expectedMention);
  }

  @Test
  void getOption_shouldReturnOptional() {
    // Given
    DiscordContext context = new TestDiscordContext(123456789L, 987654321L, 111222333L, "<@123>");

    // When
    Optional<String> result = context.getOption("test");

    // Then
    assertThat(result).isNotNull();
    assertThat(result).isEmpty();
  }

  @Test
  void getOptionAsString_shouldReturnOptional() {
    // Given
    DiscordContext context = new TestDiscordContext(123456789L, 987654321L, 111222333L, "<@123>");

    // When
    Optional<String> result = context.getOptionAsString("test");

    // Then
    assertThat(result).isNotNull();
    assertThat(result).isEmpty();
  }

  @Test
  void getOptionAsLong_shouldReturnOptional() {
    // Given
    DiscordContext context = new TestDiscordContext(123456789L, 987654321L, 111222333L, "<@123>");

    // When
    Optional<Long> result = context.getOptionAsLong("test");

    // Then
    assertThat(result).isNotNull();
    assertThat(result).isEmpty();
  }

  @Test
  void getOptionAsUser_shouldReturnOptional() {
    // Given
    DiscordContext context = new TestDiscordContext(123456789L, 987654321L, 111222333L, "<@123>");

    // When
    Optional<User> result = context.getOptionAsUser("test");

    // Then
    assertThat(result).isNotNull();
    assertThat(result).isEmpty();
  }

  @Test
  void getGuildId_shouldBePositive() {
    // Given
    DiscordContext context = new TestDiscordContext(123456789L, 987654321L, 111222333L, "<@123>");

    // When
    long guildId = context.getGuildId();

    // Then
    assertThat(guildId).isPositive();
  }

  @Test
  void getUserId_shouldBePositive() {
    // Given
    DiscordContext context = new TestDiscordContext(123456789L, 987654321L, 111222333L, "<@123>");

    // When
    long userId = context.getUserId();

    // Then
    assertThat(userId).isPositive();
  }

  @Test
  void getChannelId_shouldBePositive() {
    // Given
    DiscordContext context = new TestDiscordContext(123456789L, 987654321L, 111222333L, "<@123>");

    // When
    long channelId = context.getChannelId();

    // Then
    assertThat(channelId).isPositive();
  }

  @Test
  void getUserMention_shouldNotBeNull() {
    // Given
    DiscordContext context = new TestDiscordContext(123456789L, 987654321L, 111222333L, "<@123>");

    // When
    String mention = context.getUserMention();

    // Then
    assertThat(mention).isNotNull();
    assertThat(mention).isNotEmpty();
  }
}
