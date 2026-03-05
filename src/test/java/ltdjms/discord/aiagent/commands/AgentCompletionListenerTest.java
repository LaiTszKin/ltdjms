package ltdjms.discord.aiagent.commands;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import ltdjms.discord.shared.di.JDAProvider;
import ltdjms.discord.shared.events.AgentCompletedEvent;
import ltdjms.discord.shared.events.AgentFailedEvent;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;

/** Unit tests for AgentCompletionListener. */
@DisplayName("AgentCompletionListener")
class AgentCompletionListenerTest {

  private AgentCompletionListener listener;
  private JDA jda;
  private Guild guild;
  private ThreadChannel threadChannel;
  private MessageCreateAction messageCreateAction;

  @BeforeEach
  void setUp() {
    listener = new AgentCompletionListener();

    jda = mock(JDA.class);
    guild = mock(Guild.class);
    threadChannel = mock(ThreadChannel.class);
    messageCreateAction = mock(MessageCreateAction.class);

    // Setup JDAProvider mock
    JDAProvider.setJda(jda);

    // Mock sendMessage behavior
    when(threadChannel.sendMessage(any(CharSequence.class))).thenReturn(messageCreateAction);
    doNothing().when(messageCreateAction).queue();
  }

  @Nested
  @DisplayName("accept")
  class AcceptTests {

    @Test
    @DisplayName("should handle AgentCompletedEvent")
    void shouldHandleAgentCompletedEvent() {
      // Given
      when(jda.getGuildById(anyLong())).thenReturn(guild);
      when(guild.getThreadChannelById(anyLong())).thenReturn(threadChannel);

      AgentCompletedEvent event =
          new AgentCompletedEvent(
              123L, "456", "789", "conv-123", "Hello, world!", List.of(), Instant.now());

      // When
      listener.accept(event);

      // Then
      verify(threadChannel).sendMessage(any(CharSequence.class));
    }

    @Test
    @DisplayName("should handle AgentFailedEvent")
    void shouldHandleAgentFailedEvent() {
      // Given
      when(jda.getGuildById(anyLong())).thenReturn(guild);
      when(guild.getThreadChannelById(anyLong())).thenReturn(threadChannel);

      AgentFailedEvent event =
          new AgentFailedEvent(
              123L, "456", "789", "conv-123", "Something went wrong", Instant.now());

      // When
      listener.accept(event);

      // Then
      verify(threadChannel).sendMessage(any(CharSequence.class));
    }

    @Test
    @DisplayName("should ignore null event gracefully")
    void shouldIgnoreNullEventGracefully() {
      // When
      listener.accept(null);

      // Then - should not throw exception
      verify(threadChannel, never()).sendMessage(any(CharSequence.class));
    }
  }

  @Nested
  @DisplayName("handleAgentCompleted")
  class HandleAgentCompletedTests {

    @Test
    @DisplayName("should send message when thread channel is resolved")
    void shouldSendMessageWhenThreadChannelResolved() {
      // Given
      when(jda.getGuildById(123L)).thenReturn(guild);
      when(guild.getThreadChannelById(456L)).thenReturn(threadChannel);

      AgentCompletedEvent event =
          new AgentCompletedEvent(
              123L, "456", "789", "conv-123", "Test response", List.of(), Instant.now());

      // When
      listener.accept(event);

      // Then
      verify(threadChannel).sendMessage("Test response");
    }

    @Test
    @DisplayName("should not send message when guild is not found")
    void shouldNotSendMessageWhenGuildNotFound() {
      // Given
      when(jda.getGuildById(123L)).thenReturn(null);

      AgentCompletedEvent event =
          new AgentCompletedEvent(
              123L, "456", "789", "conv-123", "Test response", List.of(), Instant.now());

      // When
      listener.accept(event);

      // Then
      verify(threadChannel, never()).sendMessage(any(CharSequence.class));
    }

    @Test
    @DisplayName("should not send message when channel is not found")
    void shouldNotSendMessageWhenChannelNotFound() {
      // Given
      when(jda.getGuildById(123L)).thenReturn(guild);
      when(guild.getThreadChannelById(456L)).thenReturn(null);

      AgentCompletedEvent event =
          new AgentCompletedEvent(
              123L, "456", "789", "conv-123", "Test response", List.of(), Instant.now());

      // When
      listener.accept(event);

      // Then
      verify(threadChannel, never()).sendMessage(any(CharSequence.class));
    }

    @Test
    @DisplayName("should not send message when channelId is invalid")
    void shouldNotSendMessageWhenChannelIdInvalid() {
      // Given
      when(jda.getGuildById(123L)).thenReturn(guild);

      AgentCompletedEvent event =
          new AgentCompletedEvent(
              123L, "invalid", "789", "conv-123", "Test response", List.of(), Instant.now());

      // When
      listener.accept(event);

      // Then
      verify(threadChannel, never()).sendMessage(any(CharSequence.class));
    }

    @Test
    @DisplayName("should send fallback message when final response is blank")
    void shouldSendFallbackMessageWhenFinalResponseIsBlank() {
      // Given
      when(jda.getGuildById(123L)).thenReturn(guild);
      when(guild.getThreadChannelById(456L)).thenReturn(threadChannel);

      AgentCompletedEvent event =
          new AgentCompletedEvent(123L, "456", "789", "conv-123", "   ", List.of(), Instant.now());

      // When
      listener.accept(event);

      // Then
      verify(threadChannel).sendMessage(":question: AI 沒有產生回應");
    }
  }

  @Nested
  @DisplayName("handleAgentFailed")
  class HandleAgentFailedTests {

    @Test
    @DisplayName("should send error message when thread channel is resolved")
    void shouldSendErrorMessageWhenThreadChannelResolved() {
      // Given
      when(jda.getGuildById(123L)).thenReturn(guild);
      when(guild.getThreadChannelById(456L)).thenReturn(threadChannel);

      AgentFailedEvent event =
          new AgentFailedEvent(123L, "456", "789", "conv-123", "API error", Instant.now());

      // When
      listener.accept(event);

      // Then
      verify(threadChannel).sendMessage("❌ API error");
    }

    @Test
    @DisplayName("should not send message when guild is not found")
    void shouldNotSendMessageWhenGuildNotFound() {
      // Given
      when(jda.getGuildById(123L)).thenReturn(null);

      AgentFailedEvent event =
          new AgentFailedEvent(123L, "456", "789", "conv-123", "API error", Instant.now());

      // When
      listener.accept(event);

      // Then
      verify(threadChannel, never()).sendMessage(any(CharSequence.class));
    }
  }
}
