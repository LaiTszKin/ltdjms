package ltdjms.discord.markdown.services;

import java.util.List;

import ltdjms.discord.aiagent.domain.ConversationMessage;
import ltdjms.discord.aiagent.domain.MessageRole;
import ltdjms.discord.aichat.services.AIChatService;
import ltdjms.discord.aichat.services.MessageSplitter;
import ltdjms.discord.aichat.services.StreamingResponseHandler;
import ltdjms.discord.markdown.autofix.MarkdownAutoFixer;
import ltdjms.discord.markdown.validation.MarkdownValidator;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;

/** Markdown 驗證裝飾器 包裝 AIChatService，在回應生成後預修復並驗證輸出 */
public final class MarkdownValidatingAIChatService implements AIChatService {

  private final AIChatService delegate;
  private final MarkdownValidator validator;
  private final MarkdownAutoFixer autofixer;
  private final DiscordMarkdownSanitizer sanitizer;
  private final DiscordMarkdownPaginator paginator;
  private final boolean enabled;
  private final boolean streamingBypassValidation;

  public MarkdownValidatingAIChatService(
      AIChatService delegate,
      MarkdownValidator validator,
      MarkdownAutoFixer autofixer,
      DiscordMarkdownSanitizer sanitizer,
      DiscordMarkdownPaginator paginator,
      boolean enabled,
      boolean streamingBypassValidation) {
    this.delegate = delegate;
    this.validator = validator;
    this.autofixer = autofixer;
    this.sanitizer = sanitizer;
    this.paginator = paginator;
    this.enabled = enabled;
    this.streamingBypassValidation = streamingBypassValidation;
  }

  @Override
  public Result<List<String>, DomainError> generateResponse(
      long guildId, String channelId, String userId, String userMessage) {

    return validateAndGenerate(guildId, channelId, userId, userMessage);
  }

  @Override
  public void generateStreamingResponse(
      long guildId,
      String channelId,
      String userId,
      String userMessage,
      StreamingResponseHandler handler) {
    if (!enabled || streamingBypassValidation) {
      delegate.generateStreamingResponse(guildId, channelId, userId, userMessage, handler);
      return;
    }
    streamWithValidation(
        (streamHandler) ->
            delegate.generateStreamingResponse(
                guildId, channelId, userId, userMessage, streamHandler),
        handler);
  }

  @Override
  public void generateStreamingResponse(
      long guildId,
      String channelId,
      String userId,
      String userMessage,
      long messageId,
      StreamingResponseHandler handler) {
    if (!enabled || streamingBypassValidation) {
      delegate.generateStreamingResponse(
          guildId, channelId, userId, userMessage, messageId, handler);
      return;
    }
    streamWithValidation(
        (streamHandler) ->
            delegate.generateStreamingResponse(
                guildId, channelId, userId, userMessage, messageId, streamHandler),
        handler);
  }

  @Override
  public void generateWithHistory(
      long guildId,
      String channelId,
      String userId,
      List<ConversationMessage> history,
      StreamingResponseHandler handler) {
    if (!enabled || streamingBypassValidation) {
      delegate.generateWithHistory(guildId, channelId, userId, history, handler);
      return;
    }

    String originalPrompt = extractLastUserMessage(history);
    if (originalPrompt == null || originalPrompt.isBlank()) {
      handler.onChunk(
          "",
          true,
          DomainError.invalidInput("No user message found in history"),
          StreamingResponseHandler.ChunkType.CONTENT);
      return;
    }

    List<ConversationMessage> updatedHistory = replaceLastUserMessage(history, originalPrompt);
    streamWithValidation(
        (streamHandler) ->
            delegate.generateWithHistory(guildId, channelId, userId, updatedHistory, streamHandler),
        handler);
  }

  private Result<List<String>, DomainError> validateAndGenerate(
      long guildId, String channelId, String userId, String userMessage) {

    if (!enabled) {
      return delegate.generateResponse(guildId, channelId, userId, userMessage);
    }

    Result<List<String>, DomainError> result =
        delegate.generateResponse(guildId, channelId, userId, userMessage);

    if (result.isErr()) {
      return result;
    }

    String fullResponse = String.join("\n", result.getValue());
    DiscordMarkdownStreamProcessor processor = buildStreamProcessor();
    List<String> pages = new java.util.ArrayList<>();
    pages.addAll(processor.onChunk(fullResponse));
    pages.addAll(processor.flush());
    if (pages.isEmpty()) {
      pages = MessageSplitter.split(fullResponse);
    }
    return Result.ok(pages);
  }

  private void streamWithValidation(
      java.util.function.Consumer<StreamingResponseHandler> delegateCall,
      StreamingResponseHandler handler) {
    DiscordMarkdownStreamProcessor processor = buildStreamProcessor();
    delegateCall.accept(
        new StreamingResponseHandler() {
          @Override
          public void onChunk(String chunk, boolean isComplete, DomainError error, ChunkType type) {
            if (error != null) {
              handler.onChunk("", true, error, ChunkType.CONTENT);
              return;
            }
            if (type == ChunkType.REASONING) {
              handler.onChunk(chunk, isComplete, null, ChunkType.REASONING);
              return;
            }

            if (chunk != null && !chunk.isEmpty()) {
              List<String> pages = processor.onChunk(chunk);
              emitPages(handler, pages, false);
            }

            if (isComplete) {
              List<String> remaining = processor.flush();
              emitPages(handler, remaining, true);
              if (remaining.isEmpty()) {
                handler.onChunk("", true, null, ChunkType.CONTENT);
              }
            }
          }
        });
  }

  private void emitPages(StreamingResponseHandler handler, List<String> pages, boolean isComplete) {
    if (pages == null || pages.isEmpty()) {
      return;
    }
    for (int i = 0; i < pages.size(); i++) {
      boolean isLast = isComplete && (i == pages.size() - 1);
      handler.onChunk(pages.get(i), isLast, null, StreamingResponseHandler.ChunkType.CONTENT);
    }
  }

  private DiscordMarkdownStreamProcessor buildStreamProcessor() {
    return new DiscordMarkdownStreamProcessor(
        new MarkdownHeadingSegmenter(), validator, autofixer, sanitizer, paginator);
  }

  private String extractLastUserMessage(List<ConversationMessage> history) {
    if (history == null || history.isEmpty()) {
      return null;
    }
    for (int i = history.size() - 1; i >= 0; i--) {
      ConversationMessage message = history.get(i);
      if (message.role() == MessageRole.USER) {
        return message.content();
      }
    }
    return null;
  }

  private List<ConversationMessage> replaceLastUserMessage(
      List<ConversationMessage> history, String newContent) {
    if (history == null || history.isEmpty()) {
      return history;
    }
    List<ConversationMessage> updated = new java.util.ArrayList<>(history);
    for (int i = updated.size() - 1; i >= 0; i--) {
      ConversationMessage message = updated.get(i);
      if (message.role() == MessageRole.USER) {
        updated.set(
            i,
            new ConversationMessage(
                message.role(),
                newContent,
                message.timestamp(),
                message.toolCall(),
                message.reasoningContent()));
        return updated;
      }
    }
    return updated;
  }
}
