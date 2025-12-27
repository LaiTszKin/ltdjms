package ltdjms.discord.currency.unit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.ArgumentCaptor;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;

/** Utility class for creating mocked JDA objects for unit tests. */
public final class JdaTestUtils {

  private JdaTestUtils() {
    // Utility class
  }

  /**
   * Creates a mocked SlashCommandInteractionEvent.
   *
   * @param commandName the name of the slash command
   * @param guildId the guild ID
   * @param userId the user ID
   * @param isAdmin whether the user has admin permissions
   * @return the mocked event
   */
  public static SlashCommandInteractionEvent createMockSlashEvent(
      String commandName, long guildId, long userId, boolean isAdmin) {

    SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
    Guild guild = mock(Guild.class);
    Member member = mock(Member.class);
    User user = mock(User.class);
    ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class);
    InteractionHook hook = mock(InteractionHook.class);

    // Setup guild
    when(guild.getIdLong()).thenReturn(guildId);
    when(guild.getId()).thenReturn(String.valueOf(guildId));

    // Setup user
    when(user.getIdLong()).thenReturn(userId);
    when(user.getId()).thenReturn(String.valueOf(userId));

    // Setup member
    when(member.getIdLong()).thenReturn(userId);
    when(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(isAdmin);
    when(member.getUser()).thenReturn(user);

    // Setup event
    when(event.getName()).thenReturn(commandName);
    when(event.getGuild()).thenReturn(guild);
    when(event.getMember()).thenReturn(member);
    when(event.getUser()).thenReturn(user);
    when(event.isFromGuild()).thenReturn(true);
    when(event.isAcknowledged()).thenReturn(false);
    when(event.getHook()).thenReturn(hook);

    // Setup reply chain
    when(event.reply(anyString())).thenReturn(replyAction);
    when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
    doAnswer(invocation -> null).when(replyAction).queue();
    doAnswer(invocation -> null).when(replyAction).queue(any(), any());

    return event;
  }

  /**
   * Creates a mocked SlashCommandInteractionEvent with options.
   *
   * @param commandName the name of the slash command
   * @param guildId the guild ID
   * @param userId the user ID
   * @param isAdmin whether the user has admin permissions
   * @return the mocked event builder for adding options
   */
  public static MockEventBuilder builder(
      String commandName, long guildId, long userId, boolean isAdmin) {
    return new MockEventBuilder(commandName, guildId, userId, isAdmin);
  }

  /** Builder class for creating mock events with options. */
  public static class MockEventBuilder {
    private final SlashCommandInteractionEvent event;
    private final java.util.Map<String, OptionMapping> options = new java.util.HashMap<>();

    public MockEventBuilder(String commandName, long guildId, long userId, boolean isAdmin) {
      this.event = createMockSlashEvent(commandName, guildId, userId, isAdmin);
    }

    /** Adds a string option to the mock event. */
    public MockEventBuilder withStringOption(String name, String value) {
      OptionMapping option = mock(OptionMapping.class);
      when(option.getAsString()).thenReturn(value);
      when(option.getName()).thenReturn(name);
      options.put(name, option);
      when(event.getOption(name)).thenReturn(option);
      return this;
    }

    /** Adds an integer option to the mock event. */
    public MockEventBuilder withIntOption(String name, long value) {
      OptionMapping option = mock(OptionMapping.class);
      when(option.getAsLong()).thenReturn(value);
      when(option.getAsInt()).thenReturn((int) value);
      when(option.getName()).thenReturn(name);
      options.put(name, option);
      when(event.getOption(name)).thenReturn(option);
      return this;
    }

    /** Adds a user option to the mock event. */
    public MockEventBuilder withUserOption(String name, long userId, String userName) {
      OptionMapping option = mock(OptionMapping.class);
      User user = mock(User.class);
      Member member = mock(Member.class);

      when(user.getIdLong()).thenReturn(userId);
      when(user.getId()).thenReturn(String.valueOf(userId));
      when(user.getName()).thenReturn(userName);
      when(member.getIdLong()).thenReturn(userId);
      when(member.getUser()).thenReturn(user);

      when(option.getAsUser()).thenReturn(user);
      when(option.getAsMember()).thenReturn(member);
      when(option.getName()).thenReturn(name);
      options.put(name, option);
      when(event.getOption(name)).thenReturn(option);
      return this;
    }

    /** Builds and returns the mock event. */
    public SlashCommandInteractionEvent build() {
      return event;
    }
  }

  /**
   * Captures the reply message from a mock event. Call this after the handler has processed the
   * event.
   *
   * @param event the mock event
   * @return an object that can be used to verify the reply
   */
  public static ReplyCaptor captureReply(SlashCommandInteractionEvent event) {
    return new ReplyCaptor(event);
  }

  /** Helper class to capture and verify replies. */
  public static class ReplyCaptor {
    private final SlashCommandInteractionEvent event;

    public ReplyCaptor(SlashCommandInteractionEvent event) {
      this.event = event;
    }

    /** Verifies that reply was called with a message containing the given substring. */
    public void verifyReplyContains(String substring) {
      ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
      verify(event).reply(captor.capture());
      String actual = captor.getValue();
      if (!actual.contains(substring)) {
        throw new AssertionError(
            "Expected reply to contain '" + substring + "' but was '" + actual + "'");
      }
    }

    /** Verifies that reply was called with exactly the given message. */
    public void verifyReplyEquals(String message) {
      ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
      verify(event).reply(captor.capture());
      String actual = captor.getValue();
      if (!actual.equals(message)) {
        throw new AssertionError("Expected reply '" + message + "' but was '" + actual + "'");
      }
    }

    /** Verifies that reply was never called. */
    public void verifyNoReply() {
      verify(event, never()).reply(anyString());
    }
  }
}
