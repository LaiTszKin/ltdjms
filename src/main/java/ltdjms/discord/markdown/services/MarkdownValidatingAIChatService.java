package ltdjms.discord.markdown.services;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.aiagent.domain.ConversationMessage;
import ltdjms.discord.aiagent.domain.MessageRole;
import ltdjms.discord.aichat.services.AIChatService;
import ltdjms.discord.aichat.services.MessageSplitter;
import ltdjms.discord.aichat.services.StreamingResponseHandler;
import ltdjms.discord.markdown.autofix.MarkdownAutoFixer;
import ltdjms.discord.markdown.validation.MarkdownValidator;
import ltdjms.discord.markdown.validation.MarkdownValidator.ValidationResult;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;

/** Markdown 驗證裝飾器 包裝 AIChatService，在回應生成後驗證並統一重新格式化輸出 */
public final class MarkdownValidatingAIChatService implements AIChatService {

  private static final Logger LOG = LoggerFactory.getLogger(MarkdownValidatingAIChatService.class);

  private final AIChatService delegate;
  private final MarkdownValidator validator;
  private final MarkdownAutoFixer autofixer;
  private final boolean enabled;
  private final boolean streamingBypassValidation;

  public MarkdownValidatingAIChatService(
      AIChatService delegate,
      MarkdownValidator validator,
      MarkdownAutoFixer autofixer,
      boolean enabled,
      boolean streamingBypassValidation) {
    this.delegate = delegate;
    this.validator = validator;
    this.autofixer = autofixer;
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

    Result<List<String>, DomainError> result =
        validateAndGenerate(guildId, channelId, userId, userMessage);

    if (result.isErr()) {
      handler.onChunk("", true, result.getError(), StreamingResponseHandler.ChunkType.CONTENT);
      return;
    }

    List<String> messages = result.getValue();
    for (int i = 0; i < messages.size(); i++) {
      boolean isLast = (i == messages.size() - 1);
      handler.onChunk(messages.get(i), isLast, null, StreamingResponseHandler.ChunkType.CONTENT);
    }
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

    Result<List<String>, DomainError> result =
        validateAndGenerate(guildId, channelId, userId, userMessage);

    if (result.isErr()) {
      handler.onChunk("", true, result.getError(), StreamingResponseHandler.ChunkType.CONTENT);
      return;
    }

    List<String> messages = result.getValue();
    for (int i = 0; i < messages.size(); i++) {
      boolean isLast = (i == messages.size() - 1);
      handler.onChunk(messages.get(i), isLast, null, StreamingResponseHandler.ChunkType.CONTENT);
    }
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

    validateAndGenerateWithHistory(guildId, channelId, userId, history, handler, originalPrompt);
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
    ValidationResult validation = validator.validate(fullResponse);

    if (validation instanceof ValidationResult.Valid) {
      return result;
    }

    ValidationResult.Invalid invalid = (ValidationResult.Invalid) validation;
    String reformatted = autofixer.autoFix(fullResponse);
    ValidationResult reformattedValidation = validator.validate(reformatted);

    if (reformattedValidation instanceof ValidationResult.Valid) {
      LOG.info("Markdown 重格式化成功: {} 個錯誤被修復", invalid.errors().size());
    } else if (reformattedValidation instanceof ValidationResult.Invalid invalidAfter) {
      LOG.warn("Markdown 重格式化後仍有格式錯誤: {} 個錯誤", invalidAfter.errors().size());
    }

    return Result.ok(MessageSplitter.split(reformatted));
  }

  private void validateAndGenerateWithHistory(
      long guildId,
      String channelId,
      String userId,
      List<ConversationMessage> history,
      StreamingResponseHandler handler,
      String originalPrompt) {
    List<ConversationMessage> updatedHistory = replaceLastUserMessage(history, originalPrompt);

    StringBuilder fullResponse = new StringBuilder();

    delegate.generateWithHistory(
        guildId,
        channelId,
        userId,
        updatedHistory,
        new StreamingResponseHandler() {
          @Override
          public void onChunk(String chunk, boolean isComplete, DomainError error, ChunkType type) {
            if (error != null) {
              handler.onChunk("", true, error, ChunkType.CONTENT);
              return;
            }
            if (type == ChunkType.CONTENT) {
              fullResponse.append(chunk);
            }
            if (!isComplete) {
              return;
            }

            String responseText = fullResponse.toString();
            ValidationResult validation = validator.validate(responseText);

            if (validation instanceof ValidationResult.Valid) {
              deliverValidatedResponse(handler, responseText);
              return;
            }

            ValidationResult.Invalid invalid = (ValidationResult.Invalid) validation;
            String reformatted = autofixer.autoFix(responseText);
            ValidationResult reformattedValidation = validator.validate(reformatted);
            if (reformattedValidation instanceof ValidationResult.Valid) {
              LOG.info("Markdown 重格式化成功（with history）: {} 個錯誤被修復", invalid.errors().size());
            } else if (reformattedValidation instanceof ValidationResult.Invalid invalidAfter) {
              LOG.warn("Markdown 重格式化後仍有格式錯誤（with history）: {} 個錯誤", invalidAfter.errors().size());
            }

            deliverValidatedResponse(handler, reformatted);
          }
        });
  }

  private void deliverValidatedResponse(StreamingResponseHandler handler, String content) {
    List<String> messages = MessageSplitter.split(content);
    for (int i = 0; i < messages.size(); i++) {
      boolean isLast = (i == messages.size() - 1);
      handler.onChunk(messages.get(i), isLast, null, StreamingResponseHandler.ChunkType.CONTENT);
    }
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
