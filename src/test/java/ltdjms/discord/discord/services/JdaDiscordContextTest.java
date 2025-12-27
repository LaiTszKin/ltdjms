package ltdjms.discord.discord.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ltdjms.discord.discord.domain.DiscordContext;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;

/**
 * JdaDiscordContext 實作單元測試
 *
 * <p>此測試驗證 JdaDiscordContext 的實作行為符合 DiscordContext 介面契約。
 */
class JdaDiscordContextTest {

  private SlashCommandInteractionEvent mockEvent;
  private User mockUser;

  @BeforeEach
  void setUp() {
    mockEvent = mock(SlashCommandInteractionEvent.class);
    mockUser = mock(User.class);

    // 設定基本的事件行為
    var mockGuild = mock(net.dv8tion.jda.api.entities.Guild.class);
    when(mockGuild.getIdLong()).thenReturn(123456789L);
    when(mockEvent.getGuild()).thenReturn(mockGuild);

    when(mockUser.getIdLong()).thenReturn(987654321L);
    when(mockUser.getAsMention()).thenReturn("<@987654321>");
    when(mockEvent.getUser()).thenReturn(mockUser);

    var mockChannel = mock(net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion.class);
    when(mockChannel.getIdLong()).thenReturn(111222333L);
    when(mockEvent.getChannel()).thenReturn(mockChannel);

    when(mockEvent.getOption(anyString())).thenReturn(null);
  }

  @Test
  void getGuildId_shouldReturnCorrectGuildId() {
    // Given
    var mockGuild = mock(net.dv8tion.jda.api.entities.Guild.class);
    when(mockGuild.getIdLong()).thenReturn(123456789L);
    when(mockEvent.getGuild()).thenReturn(mockGuild);

    DiscordContext context = new JdaDiscordContext(mockEvent);

    // When
    long guildId = context.getGuildId();

    // Then
    assertThat(guildId).isEqualTo(123456789L);
  }

  @Test
  void getUserId_shouldReturnCorrectUserId() {
    // Given
    when(mockUser.getIdLong()).thenReturn(987654321L);
    DiscordContext context = new JdaDiscordContext(mockEvent);

    // When
    long userId = context.getUserId();

    // Then
    assertThat(userId).isEqualTo(987654321L);
  }

  @Test
  void getChannelId_shouldReturnCorrectChannelId() {
    // Given
    var mockChannel = mock(net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion.class);
    when(mockChannel.getIdLong()).thenReturn(111222333L);
    when(mockEvent.getChannel()).thenReturn(mockChannel);

    DiscordContext context = new JdaDiscordContext(mockEvent);

    // When
    long channelId = context.getChannelId();

    // Then
    assertThat(channelId).isEqualTo(111222333L);
  }

  @Test
  void getUserMention_shouldReturnCorrectMention() {
    // Given
    when(mockUser.getAsMention()).thenReturn("<@987654321>");
    DiscordContext context = new JdaDiscordContext(mockEvent);

    // When
    String mention = context.getUserMention();

    // Then
    assertThat(mention).isEqualTo("<@987654321>");
  }

  @Test
  void getOption_shouldReturnOptionalEmptyWhenOptionNotPresent() {
    // Given
    when(mockEvent.getOption("nonexistent")).thenReturn(null);
    DiscordContext context = new JdaDiscordContext(mockEvent);

    // When
    Optional<String> result = context.getOption("nonexistent");

    // Then
    assertThat(result).isEmpty();
  }

  @Test
  void getOptionAsString_shouldReturnOptionalEmptyWhenOptionNotPresent() {
    // Given
    when(mockEvent.getOption("nonexistent")).thenReturn(null);
    DiscordContext context = new JdaDiscordContext(mockEvent);

    // When
    Optional<String> result = context.getOptionAsString("nonexistent");

    // Then
    assertThat(result).isEmpty();
  }

  @Test
  void getOptionAsLong_shouldReturnOptionalEmptyWhenOptionNotPresent() {
    // Given
    when(mockEvent.getOption("nonexistent")).thenReturn(null);
    DiscordContext context = new JdaDiscordContext(mockEvent);

    // When
    Optional<Long> result = context.getOptionAsLong("nonexistent");

    // Then
    assertThat(result).isEmpty();
  }

  @Test
  void getOptionAsUser_shouldReturnOptionalEmptyWhenOptionNotPresent() {
    // Given
    when(mockEvent.getOption("nonexistent")).thenReturn(null);
    DiscordContext context = new JdaDiscordContext(mockEvent);

    // When
    Optional<User> result = context.getOptionAsUser("nonexistent");

    // Then
    assertThat(result).isEmpty();
  }

  @Test
  void getOptionAsString_shouldReturnValueWhenOptionIsString() {
    // Given
    OptionMapping mockOption = mock(OptionMapping.class);
    when(mockOption.getType()).thenReturn(OptionType.STRING);
    when(mockOption.getAsString()).thenReturn("test value");
    when(mockEvent.getOption("test_option")).thenReturn(mockOption);

    DiscordContext context = new JdaDiscordContext(mockEvent);

    // When
    Optional<String> result = context.getOptionAsString("test_option");

    // Then
    assertThat(result).isPresent().contains("test value");
  }

  @Test
  void getOptionAsLong_shouldReturnValueWhenOptionIsLong() {
    // Given
    OptionMapping mockOption = mock(OptionMapping.class);
    when(mockOption.getType()).thenReturn(OptionType.INTEGER);
    when(mockOption.getAsLong()).thenReturn(12345L);
    when(mockEvent.getOption("amount")).thenReturn(mockOption);

    DiscordContext context = new JdaDiscordContext(mockEvent);

    // When
    Optional<Long> result = context.getOptionAsLong("amount");

    // Then
    assertThat(result).isPresent().contains(12345L);
  }

  @Test
  void getOptionAsUser_shouldReturnValueWhenOptionIsUser() {
    // Given
    User mockOptionUser = mock(User.class);
    OptionMapping mockOption = mock(OptionMapping.class);
    when(mockOption.getType()).thenReturn(OptionType.USER);
    when(mockOption.getAsUser()).thenReturn(mockOptionUser);
    when(mockEvent.getOption("target_user")).thenReturn(mockOption);

    DiscordContext context = new JdaDiscordContext(mockEvent);

    // When
    Optional<User> result = context.getOptionAsUser("target_user");

    // Then
    assertThat(result).isPresent().contains(mockOptionUser);
  }

  @Test
  void getOptionAsLong_shouldReturnEmptyWhenOptionIsNotLong() {
    // Given
    OptionMapping mockOption = mock(OptionMapping.class);
    when(mockOption.getType()).thenReturn(OptionType.STRING);
    when(mockEvent.getOption("invalid")).thenReturn(mockOption);

    DiscordContext context = new JdaDiscordContext(mockEvent);

    // When
    Optional<Long> result = context.getOptionAsLong("invalid");

    // Then
    assertThat(result).isEmpty();
  }

  @Test
  void getOptionAsUser_shouldReturnEmptyWhenOptionIsNotUser() {
    // Given
    OptionMapping mockOption = mock(OptionMapping.class);
    when(mockOption.getType()).thenReturn(OptionType.STRING);
    when(mockEvent.getOption("invalid")).thenReturn(mockOption);

    DiscordContext context = new JdaDiscordContext(mockEvent);

    // When
    Optional<User> result = context.getOptionAsUser("invalid");

    // Then
    assertThat(result).isEmpty();
  }
}
