package ltdjms.discord.markdown.services;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.aiagent.domain.ConversationMessage;
import ltdjms.discord.aichat.services.AIChatService;
import ltdjms.discord.aichat.services.StreamingResponseHandler;
import ltdjms.discord.markdown.validation.MarkdownErrorFormatter;
import ltdjms.discord.markdown.validation.MarkdownValidator;
import ltdjms.discord.markdown.validation.MarkdownValidator.ValidationResult;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;

/** Markdown 驗證裝飾器 包裝 AIChatService，在回應生成後驗證 Markdown 格式 格式錯誤時自動重新生成 */
public final class MarkdownValidatingAIChatService implements AIChatService {

  private static final Logger LOG = LoggerFactory.getLogger(MarkdownValidatingAIChatService.class);

  private final AIChatService delegate;
  private final MarkdownValidator validator;
  private final boolean enabled;
  private final MarkdownErrorFormatter errorFormatter;
  private final int maxRetryAttempts;
  private final boolean streamingBypassValidation;

  public MarkdownValidatingAIChatService(
      AIChatService delegate,
      MarkdownValidator validator,
      boolean enabled,
      MarkdownErrorFormatter errorFormatter,
      int maxRetryAttempts,
      boolean streamingBypassValidation) {
    this.delegate = delegate;
    this.validator = validator;
    this.enabled = enabled;
    this.errorFormatter = errorFormatter;
    this.maxRetryAttempts = maxRetryAttempts;
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
    // 帶對話歷史的回應直接委派，不進行驗證
    delegate.generateWithHistory(guildId, channelId, userId, history, handler);
  }

  private String buildRetryPrompt(String originalPrompt, String errorReport) {
    return String.format(
        """
        [系統提示：你的上一次回應存在 Markdown 格式錯誤]

        原始用戶訊息：
        %s

        格式驗證錯誤報告：
        %s

        請修正上述格式錯誤並重新生成回應。
        """,
        originalPrompt, errorReport);
  }

  private Result<List<String>, DomainError> validateAndGenerate(
      long guildId, String channelId, String userId, String userMessage) {

    if (!enabled) {
      return delegate.generateResponse(guildId, channelId, userId, userMessage);
    }

    String originalPrompt = userMessage;
    String currentPrompt = userMessage;
    String lastResponse = null;
    int attempt = 0;

    while (attempt < maxRetryAttempts) {
      attempt++;

      Result<List<String>, DomainError> result =
          delegate.generateResponse(guildId, channelId, userId, currentPrompt);

      if (result.isErr()) {
        return result;
      }

      String fullResponse = String.join("\n", result.getValue());
      lastResponse = fullResponse;

      ValidationResult validation = validator.validate(fullResponse);

      if (validation instanceof ValidationResult.Valid) {
        return result;
      }

      ValidationResult.Invalid invalid = (ValidationResult.Invalid) validation;
      String errorReport =
          errorFormatter.formatErrorReport(originalPrompt, invalid.errors(), attempt, fullResponse);

      currentPrompt = buildRetryPrompt(originalPrompt, errorReport);
      LOG.warn(
          "Markdown validation failed (attempt {}/{}): {} errors",
          attempt,
          maxRetryAttempts,
          invalid.errors().size());
    }

    LOG.warn("Markdown validation exceeded max attempts, returning last response");
    return Result.ok(List.of(lastResponse));
  }
}
