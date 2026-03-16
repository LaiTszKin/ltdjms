package ltdjms.discord.aichat.domain;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import ltdjms.discord.aiagent.domain.ConversationMessage;
import ltdjms.discord.aiagent.domain.MessageRole;
import ltdjms.discord.shared.DomainError;
import net.dv8tion.jda.api.entities.channel.concrete.Category;

/** Unit tests for aichat.domain package. */
class AIChatDomainTest {

  @Nested
  @DisplayName("PromptLoadError")
  class PromptLoadErrorTests {

    @Test
    @DisplayName("should create directoryNotFound error")
    void shouldCreateDirectoryNotFoundError() {
      // When
      DomainError error = PromptLoadError.directoryNotFound("/prompts");

      // Then
      assertThat(error.category()).isEqualTo(DomainError.Category.PROMPT_DIR_NOT_FOUND);
      assertThat(error.message()).contains("Prompts directory not found");
      assertThat(error.message()).contains("/prompts");
      assertThat(error.cause()).isNull();
    }

    @Test
    @DisplayName("should create fileTooLarge error")
    void shouldCreateFileTooLargeError() {
      // When
      DomainError error = PromptLoadError.fileTooLarge("/prompts/system.txt", 1024000L);

      // Then
      assertThat(error.category()).isEqualTo(DomainError.Category.PROMPT_FILE_TOO_LARGE);
      assertThat(error.message()).contains("Prompt file exceeds size limit");
      assertThat(error.message()).contains("/prompts/system.txt");
      assertThat(error.message()).contains("1024000");
    }

    @Test
    @DisplayName("should create readFailed error")
    void shouldCreateReadFailedError() {
      // Given
      Throwable cause = new RuntimeException("IO error");

      // When
      DomainError error = PromptLoadError.readFailed("/prompts/system.txt", cause);

      // Then
      assertThat(error.category()).isEqualTo(DomainError.Category.PROMPT_READ_FAILED);
      assertThat(error.message()).contains("Failed to read prompt file");
      assertThat(error.message()).contains("/prompts/system.txt");
      assertThat(error.cause()).isSameAs(cause);
    }

    @Test
    @DisplayName("should create invalidEncoding error")
    void shouldCreateInvalidEncodingError() {
      // When
      DomainError error = PromptLoadError.invalidEncoding("/prompts/system.txt");

      // Then
      assertThat(error.category()).isEqualTo(DomainError.Category.PROMPT_INVALID_ENCODING);
      assertThat(error.message()).contains("Prompt file is not valid UTF-8");
      assertThat(error.message()).contains("/prompts/system.txt");
    }

    @Test
    @DisplayName("should create unknown error")
    void shouldCreateUnknownError() {
      // Given
      Throwable cause = new RuntimeException("Unknown error");

      // When
      DomainError error = PromptLoadError.unknown("Failed to load", cause);

      // Then
      assertThat(error.category()).isEqualTo(DomainError.Category.PROMPT_LOAD_FAILED);
      assertThat(error.message()).contains("Failed to load prompts: Failed to load");
      assertThat(error.cause()).isSameAs(cause);
    }
  }

  @Nested
  @DisplayName("AllowedCategory")
  class AllowedCategoryTests {

    @Test
    @DisplayName("should create AllowedCategory successfully")
    void shouldCreateAllowedCategory() {
      // When
      AllowedCategory category = new AllowedCategory(123L, "Gaming");

      // Then
      assertThat(category.categoryId()).isEqualTo(123L);
      assertThat(category.categoryName()).isEqualTo("Gaming");
    }

    @Test
    @DisplayName("should create AllowedCategory from JDA Category")
    void shouldCreateFromJDACategory() {
      // Given
      Category mockCategory = mock(Category.class);
      when(mockCategory.getIdLong()).thenReturn(456L);
      when(mockCategory.getName()).thenReturn("Music");

      // When
      AllowedCategory category = AllowedCategory.from(mockCategory);

      // Then
      assertThat(category.categoryId()).isEqualTo(456L);
      assertThat(category.categoryName()).isEqualTo("Music");
    }

    @Test
    @DisplayName("should throw exception when categoryId <= 0")
    void shouldThrowExceptionWhenCategoryIdInvalid() {
      // When/Then
      assertThatThrownBy(() -> new AllowedCategory(0L, "Test"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("類別 ID 必須大於 0");

      assertThatThrownBy(() -> new AllowedCategory(-1L, "Test"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("類別 ID 必須大於 0");
    }

    @Test
    @DisplayName("should throw exception when categoryName is null")
    void shouldThrowExceptionWhenCategoryNameNull() {
      // When/Then
      assertThatThrownBy(() -> new AllowedCategory(123L, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("類別名稱不可為空");
    }

    @Test
    @DisplayName("should throw exception when categoryName is blank")
    void shouldThrowExceptionWhenCategoryNameBlank() {
      // When/Then
      assertThatThrownBy(() -> new AllowedCategory(123L, "  "))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("類別名稱不可為空");
    }
  }

  @Nested
  @DisplayName("AICategoryRestrictionChangedEvent")
  class AICategoryRestrictionChangedEventTests {

    @Test
    @DisplayName("should create event successfully")
    void shouldCreateEvent() {
      // When
      AICategoryRestrictionChangedEvent event =
          new AICategoryRestrictionChangedEvent(123L, 456L, true, Instant.now());

      // Then
      assertThat(event.guildId()).isEqualTo(123L);
      assertThat(event.categoryId()).isEqualTo(456L);
      assertThat(event.added()).isTrue();
    }

    @Test
    @DisplayName("should create categoryAdded event")
    void shouldCreateCategoryAddedEvent() {
      // When
      AICategoryRestrictionChangedEvent event =
          AICategoryRestrictionChangedEvent.categoryAdded(123L, 456L);

      // Then
      assertThat(event.guildId()).isEqualTo(123L);
      assertThat(event.categoryId()).isEqualTo(456L);
      assertThat(event.added()).isTrue();
      assertThat(event.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("should create categoryRemoved event")
    void shouldCreateCategoryRemovedEvent() {
      // When
      AICategoryRestrictionChangedEvent event =
          AICategoryRestrictionChangedEvent.categoryRemoved(123L, 456L);

      // Then
      assertThat(event.guildId()).isEqualTo(123L);
      assertThat(event.categoryId()).isEqualTo(456L);
      assertThat(event.added()).isFalse();
      assertThat(event.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("should use current time when timestamp is null")
    void shouldUseCurrentTimeWhenTimestampNull() {
      // When
      AICategoryRestrictionChangedEvent event =
          new AICategoryRestrictionChangedEvent(123L, 456L, false, null);

      // Then
      assertThat(event.timestamp()).isNotNull();
      assertThat(event.timestamp()).isBefore(Instant.now().plusSeconds(1));
    }

    @Test
    @DisplayName("should throw exception when categoryId <= 0")
    void shouldThrowExceptionWhenCategoryIdInvalid() {
      // When/Then
      assertThatThrownBy(() -> new AICategoryRestrictionChangedEvent(123L, 0L, true, Instant.now()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("類別 ID 必須大於 0");

      assertThatThrownBy(
              () -> new AICategoryRestrictionChangedEvent(123L, -1L, true, Instant.now()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("類別 ID 必須大於 0");
    }
  }

  @Nested
  @DisplayName("AIChannelRestrictionChangedEvent")
  class AIChannelRestrictionChangedEventTests {

    @Test
    @DisplayName("should create event successfully")
    void shouldCreateEvent() {
      // Given
      AllowedChannel channel1 = new AllowedChannel(123L, "general");
      AllowedChannel channel2 = new AllowedChannel(456L, "random");
      Set<AllowedChannel> channels = Set.of(channel1, channel2);

      // When
      AIChannelRestrictionChangedEvent event =
          new AIChannelRestrictionChangedEvent(789L, channels, Instant.now());

      // Then
      assertThat(event.guildId()).isEqualTo(789L);
      assertThat(event.allowedChannels()).hasSize(2);
      assertThat(event.allowedChannels()).contains(channel1);
      assertThat(event.allowedChannels()).contains(channel2);
    }

    @Test
    @DisplayName("should create event without timestamp")
    void shouldCreateEventWithoutTimestamp() {
      // Given
      AllowedChannel channel = new AllowedChannel(123L, "general");
      Set<AllowedChannel> channels = Set.of(channel);

      // When
      AIChannelRestrictionChangedEvent event =
          new AIChannelRestrictionChangedEvent(789L, channels, null);

      // Then
      assertThat(event.timestamp()).isNotNull();
      assertThat(event.timestamp()).isBefore(Instant.now().plusSeconds(1));
    }

    @Test
    @DisplayName("should create event using constructor without timestamp")
    void shouldCreateEventUsingConstructorWithoutTimestamp() {
      // Given
      AllowedChannel channel = new AllowedChannel(123L, "general");
      Set<AllowedChannel> channels = Set.of(channel);

      // When
      AIChannelRestrictionChangedEvent event = new AIChannelRestrictionChangedEvent(789L, channels);

      // Then
      assertThat(event.timestamp()).isNotNull();
      assertThat(event.guildId()).isEqualTo(789L);
      assertThat(event.allowedChannels()).hasSize(1);
    }

    @Test
    @DisplayName("should throw exception when allowedChannels is null")
    void shouldThrowExceptionWhenAllowedChannelsNull() {
      // When/Then
      assertThatThrownBy(
              () -> new AIChannelRestrictionChangedEvent(789L, (Set<AllowedChannel>) null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("允許頻道集合不可為 null");
    }

    @Test
    @DisplayName("should return true when unrestricted")
    void shouldReturnTrueWhenUnrestricted() {
      // Given
      AIChannelRestrictionChangedEvent event = new AIChannelRestrictionChangedEvent(789L, Set.of());

      // When
      boolean result = event.isUnrestricted();

      // Then
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("should return false when restricted")
    void shouldReturnFalseWhenRestricted() {
      // Given
      AllowedChannel channel = new AllowedChannel(123L, "general");
      AIChannelRestrictionChangedEvent event =
          new AIChannelRestrictionChangedEvent(789L, Set.of(channel));

      // When
      boolean result = event.isUnrestricted();

      // Then
      assertThat(result).isFalse();
    }
  }

  @Nested
  @DisplayName("AIChatRequest")
  class AIChatRequestTests {

    private static final AIServiceConfig CONFIG =
        new AIServiceConfig(
            "https://api.example.com", "test-key", "gpt-4", 0.7, 30, false, false, false, 3, false);

    @Test
    @DisplayName("should create user message without system prompt")
    void shouldCreateUserMessageWithoutSystemPrompt() {
      // When
      AIChatRequest request = AIChatRequest.createUserMessage("Hello", CONFIG);

      // Then
      assertThat(request.model()).isEqualTo("gpt-4");
      assertThat(request.temperature()).isEqualTo(0.7);
      assertThat(request.stream()).isFalse();
      assertThat(request.messages()).hasSize(1);
      assertThat(request.messages().get(0).role()).isEqualTo("user");
      assertThat(request.messages().get(0).content()).isEqualTo("Hello");
    }

    @Test
    @DisplayName("should use default greeting when content is blank")
    void shouldUseDefaultGreetingWhenContentBlank() {
      // When
      AIChatRequest request = AIChatRequest.createUserMessage("", CONFIG);

      // Then
      assertThat(request.messages()).hasSize(1);
      assertThat(request.messages().get(0).content()).isEqualTo("你好");
    }

    @Test
    @DisplayName("should use default greeting when content is null")
    void shouldUseDefaultGreetingWhenContentNull() {
      // When
      AIChatRequest request = AIChatRequest.createUserMessage(null, CONFIG);

      // Then
      assertThat(request.messages()).hasSize(1);
      assertThat(request.messages().get(0).content()).isEqualTo("你好");
    }

    @Test
    @DisplayName("should create streaming user message")
    void shouldCreateStreamingUserMessage() {
      // When
      AIChatRequest request = AIChatRequest.createStreamingUserMessage("Hello", CONFIG);

      // Then
      assertThat(request.stream()).isTrue();
      assertThat(request.messages()).hasSize(1);
      assertThat(request.messages().get(0).role()).isEqualTo("user");
    }

    @Test
    @DisplayName("should create request with system prompt")
    void shouldCreateRequestWithSystemPrompt() {
      // Given
      SystemPrompt systemPrompt = SystemPrompt.of(List.of(new PromptSection("test", "content")));

      // When
      AIChatRequest request = AIChatRequest.createUserMessage("Hello", CONFIG, systemPrompt);

      // Then
      assertThat(request.messages()).hasSize(2);
      assertThat(request.messages().get(0).role()).isEqualTo("system");
      assertThat(request.messages().get(0).content()).contains("content");
      assertThat(request.messages().get(1).role()).isEqualTo("user");
      assertThat(request.messages().get(1).content()).isEqualTo("Hello");
    }

    @Test
    @DisplayName("should create request with history")
    void shouldCreateRequestWithHistory() {
      // Given
      SystemPrompt systemPrompt = SystemPrompt.empty();
      Instant now = Instant.now();
      List<ConversationMessage> history =
          List.of(
              new ConversationMessage(MessageRole.USER, "Hello", now, Optional.empty()),
              new ConversationMessage(MessageRole.ASSISTANT, "Hi there", now, Optional.empty()));

      // When
      AIChatRequest request = AIChatRequest.createWithHistory(history, CONFIG, systemPrompt);

      // Then - empty system prompt doesn't add system message
      assertThat(request.messages()).hasSize(2);
      assertThat(request.messages().get(0).role()).isEqualTo("user");
      assertThat(request.messages().get(0).content()).isEqualTo("Hello");
      assertThat(request.messages().get(1).role()).isEqualTo("assistant");
      assertThat(request.messages().get(1).content()).isEqualTo("Hi there");
    }

    @Test
    @DisplayName("should map TOOL role to user")
    void shouldMapToolRoleToUser() {
      // Given
      SystemPrompt systemPrompt = SystemPrompt.empty();
      Instant now = Instant.now();
      List<ConversationMessage> history =
          List.of(new ConversationMessage(MessageRole.TOOL, "Tool result", now, Optional.empty()));

      // When
      AIChatRequest request = AIChatRequest.createWithHistory(history, CONFIG, systemPrompt);

      // Then - TOOL role is mapped to user, empty system prompt doesn't add message
      assertThat(request.messages()).hasSize(1);
      assertThat(request.messages().get(0).role()).isEqualTo("user");
      assertThat(request.messages().get(0).content()).isEqualTo("Tool result");
    }

    @Test
    @DisplayName("should create streaming request with history")
    void shouldCreateStreamingRequestWithHistory() {
      // Given
      SystemPrompt systemPrompt = SystemPrompt.empty();
      Instant now = Instant.now();
      List<ConversationMessage> history =
          List.of(new ConversationMessage(MessageRole.USER, "Question", now, Optional.empty()));

      // When
      AIChatRequest request = AIChatRequest.createWithHistory(history, CONFIG, systemPrompt);

      // Then
      assertThat(request.stream()).isTrue();
    }
  }

  @Nested
  @DisplayName("AIChannelRestriction")
  class AIChannelRestrictionTests {

    @Test
    @DisplayName("should create empty allowlist mode")
    void shouldCreateEmptyAllowlistMode() {
      // When
      AIChannelRestriction restriction = new AIChannelRestriction(123L);

      // Then
      assertThat(restriction.guildId()).isEqualTo(123L);
      assertThat(restriction.allowedChannels()).isEmpty();
      assertThat(restriction.allowedCategories()).isEmpty();
      assertThat(restriction.isUnrestricted()).isTrue();
    }

    @Test
    @DisplayName("should create with allowed channels")
    void shouldCreateWithAllowedChannels() {
      // Given
      AllowedChannel channel = new AllowedChannel(456L, "general");
      Set<AllowedChannel> channels = Set.of(channel);

      // When
      AIChannelRestriction restriction = new AIChannelRestriction(123L, channels);

      // Then
      assertThat(restriction.allowedChannels()).hasSize(1);
      assertThat(restriction.isUnrestricted()).isFalse();
    }

    @Test
    @DisplayName("should throw exception when allowedChannels is null")
    void shouldThrowExceptionWhenAllowedChannelsNull() {
      // When/Then
      assertThatThrownBy(() -> new AIChannelRestriction(123L, (Set<AllowedChannel>) null, Set.of()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("允許頻道集合不可為 null");
    }

    @Test
    @DisplayName("should throw exception when allowedCategories is null")
    void shouldThrowExceptionWhenAllowedCategoriesNull() {
      // Given
      AllowedChannel channel = new AllowedChannel(456L, "general");

      // When/Then
      assertThatThrownBy(() -> new AIChannelRestriction(123L, Set.of(channel), null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("允許類別集合不可為 null");
    }

    @Test
    @DisplayName("should allow channel when explicitly set")
    void shouldAllowChannelWhenExplicitlySet() {
      // Given
      AllowedChannel channel = new AllowedChannel(456L, "general");
      AIChannelRestriction restriction = new AIChannelRestriction(123L, Set.of(channel));

      // When
      boolean result = restriction.isChannelAllowed(456L);

      // Then
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("should not allow channel when not in allowed list")
    void shouldNotAllowChannelWhenNotInAllowedList() {
      // Given
      AllowedChannel channel = new AllowedChannel(456L, "general");
      AIChannelRestriction restriction = new AIChannelRestriction(123L, Set.of(channel));

      // When
      boolean result = restriction.isChannelAllowed(789L);

      // Then
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("should deny channel when allowlist is empty")
    void shouldDenyChannelWhenAllowlistIsEmpty() {
      // Given
      AIChannelRestriction restriction = new AIChannelRestriction(123L);

      // When
      boolean result = restriction.isChannelAllowed(456L);

      // Then
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("should allow channel by category")
    void shouldAllowChannelByCategory() {
      // Given
      AllowedCategory category = new AllowedCategory(100L, "General");
      AIChannelRestriction restriction = new AIChannelRestriction(123L, Set.of(), Set.of(category));

      // When
      boolean result = restriction.isChannelAllowed(456L, 100L);

      // Then
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("should add channel with withChannelAdded")
    void shouldAddChannelWithWithChannelAdded() {
      // Given
      AIChannelRestriction restriction = new AIChannelRestriction(123L);
      AllowedChannel newChannel = new AllowedChannel(456L, "general");

      // When
      AIChannelRestriction updated = restriction.withChannelAdded(newChannel);

      // Then
      assertThat(updated.allowedChannels()).hasSize(1);
      assertThat(updated.allowedChannels()).contains(newChannel);
    }

    @Test
    @DisplayName("should remove channel with withChannelRemoved")
    void shouldRemoveChannelWithWithChannelRemoved() {
      // Given
      AllowedChannel channel1 = new AllowedChannel(456L, "general");
      AllowedChannel channel2 = new AllowedChannel(789L, "random");
      AIChannelRestriction restriction = new AIChannelRestriction(123L, Set.of(channel1, channel2));

      // When
      AIChannelRestriction updated = restriction.withChannelRemoved(456L);

      // Then
      assertThat(updated.allowedChannels()).hasSize(1);
      assertThat(updated.allowedChannels()).doesNotContain(channel1);
    }

    @Test
    @DisplayName("should add category with withCategoryAdded")
    void shouldAddCategoryWithWithCategoryAdded() {
      // Given
      AIChannelRestriction restriction = new AIChannelRestriction(123L);
      AllowedCategory newCategory = new AllowedCategory(100L, "General");

      // When
      AIChannelRestriction updated = restriction.withCategoryAdded(newCategory);

      // Then
      assertThat(updated.allowedCategories()).hasSize(1);
      assertThat(updated.allowedCategories()).contains(newCategory);
    }

    @Test
    @DisplayName("should remove category with withCategoryRemoved")
    void shouldRemoveCategoryWithWithCategoryRemoved() {
      // Given
      AllowedCategory category1 = new AllowedCategory(100L, "General");
      AllowedCategory category2 = new AllowedCategory(200L, "Random");
      AIChannelRestriction restriction =
          new AIChannelRestriction(123L, Set.of(), Set.of(category1, category2));

      // When
      AIChannelRestriction updated = restriction.withCategoryRemoved(100L);

      // Then
      assertThat(updated.allowedCategories()).hasSize(1);
      assertThat(updated.allowedCategories()).doesNotContain(category1);
    }
  }

  @Nested
  @DisplayName("AIChatStreamChunk")
  class AIChatStreamChunkTests {

    @Test
    @DisplayName("should extract content from choices")
    void shouldExtractContentFromChoices() {
      // Given
      AIChatStreamChunk.StreamChoice.Delta delta =
          new AIChatStreamChunk.StreamChoice.Delta("Hello", null);
      AIChatStreamChunk.StreamChoice choice = new AIChatStreamChunk.StreamChoice(0, delta, null);
      AIChatStreamChunk chunk =
          new AIChatStreamChunk("id", "object", 123L, "model", List.of(choice));

      // When
      String content = chunk.extractContent();

      // Then
      assertThat(content).isEqualTo("Hello");
    }

    @Test
    @DisplayName("should return null when choices is empty")
    void shouldReturnNullWhenChoicesEmpty() {
      // Given
      AIChatStreamChunk chunk = new AIChatStreamChunk("id", "object", 123L, "model", List.of());

      // When
      String content = chunk.extractContent();

      // Then
      assertThat(content).isNull();
    }

    @Test
    @DisplayName("should return null when choices is null")
    void shouldReturnNullWhenChoicesNull() {
      // Given
      AIChatStreamChunk chunk = new AIChatStreamChunk("id", "object", 123L, "model", null);

      // When
      String content = chunk.extractContent();

      // Then
      assertThat(content).isNull();
    }

    @Test
    @DisplayName("should return null when delta is null")
    void shouldReturnNullWhenDeltaNull() {
      // Given
      AIChatStreamChunk.StreamChoice choice = new AIChatStreamChunk.StreamChoice(0, null, null);
      AIChatStreamChunk chunk =
          new AIChatStreamChunk("id", "object", 123L, "model", List.of(choice));

      // When
      String content = chunk.extractContent();

      // Then
      assertThat(content).isNull();
    }

    @Test
    @DisplayName("should extract reasoning content")
    void shouldExtractReasoningContent() {
      // Given
      AIChatStreamChunk.StreamChoice.Delta delta =
          new AIChatStreamChunk.StreamChoice.Delta(null, "thinking...");
      AIChatStreamChunk.StreamChoice choice = new AIChatStreamChunk.StreamChoice(0, delta, null);
      AIChatStreamChunk chunk =
          new AIChatStreamChunk("id", "object", 123L, "model", List.of(choice));

      // When
      String reasoning = chunk.extractReasoningContent();

      // Then
      assertThat(reasoning).isEqualTo("thinking...");
    }

    @Test
    @DisplayName("should check if finished when finish_reason is stop")
    void shouldCheckIfFinishedWhenFinishReasonIsStop() {
      // Given
      AIChatStreamChunk.StreamChoice.Delta delta =
          new AIChatStreamChunk.StreamChoice.Delta("done", null);
      AIChatStreamChunk.StreamChoice choice = new AIChatStreamChunk.StreamChoice(0, delta, "stop");
      AIChatStreamChunk chunk =
          new AIChatStreamChunk("id", "object", 123L, "model", List.of(choice));

      // When
      boolean finished = chunk.isFinished();

      // Then
      assertThat(finished).isTrue();
    }

    @Test
    @DisplayName("should return false when not finished")
    void shouldReturnFalseWhenNotFinished() {
      // Given
      AIChatStreamChunk.StreamChoice.Delta delta =
          new AIChatStreamChunk.StreamChoice.Delta("thinking", null);
      AIChatStreamChunk.StreamChoice choice = new AIChatStreamChunk.StreamChoice(0, delta, null);
      AIChatStreamChunk chunk =
          new AIChatStreamChunk("id", "object", 123L, "model", List.of(choice));

      // When
      boolean finished = chunk.isFinished();

      // Then
      assertThat(finished).isFalse();
    }

    @Test
    @DisplayName("should return false when choices is empty for isFinished")
    void shouldReturnFalseWhenChoicesEmptyForIsFinished() {
      // Given
      AIChatStreamChunk chunk = new AIChatStreamChunk("id", "object", 123L, "model", List.of());

      // When
      boolean finished = chunk.isFinished();

      // Then
      assertThat(finished).isFalse();
    }
  }
}
