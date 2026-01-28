package ltdjms.discord.markdown.unit.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ltdjms.discord.aiagent.domain.ConversationMessage;
import ltdjms.discord.aiagent.domain.MessageRole;
import ltdjms.discord.aichat.services.AIChatService;
import ltdjms.discord.aichat.services.StreamingResponseHandler;
import ltdjms.discord.markdown.autofix.MarkdownAutoFixer;
import ltdjms.discord.markdown.services.MarkdownValidatingAIChatService;
import ltdjms.discord.markdown.validation.MarkdownValidator;
import ltdjms.discord.shared.DomainError;

@DisplayName("MarkdownValidatingAIChatService - 帶對話歷史測試")
class MarkdownValidatingAIChatServiceTest_WithHistory {

  @Test
  @DisplayName("應該在帶歷史的串流中自動修復並輸出有效 Markdown")
  void generateWithHistory_shouldAutoFixAndReturnValidatedContent() {
    AIChatService mockDelegate = mock(AIChatService.class);
    MarkdownValidator mockValidator = mock(MarkdownValidator.class);
    MarkdownAutoFixer mockAutoFixer = mock(MarkdownAutoFixer.class);
    MarkdownValidatingAIChatService service =
        new MarkdownValidatingAIChatService(
            mockDelegate, mockValidator, mockAutoFixer, true, false);

    long guildId = 123L;
    String channelId = "456";
    String userId = "789";
    List<ConversationMessage> history =
        List.of(
            new ConversationMessage(
                MessageRole.USER, "請列出可用工具", Instant.now(), Optional.empty(), Optional.empty()));

    String invalidResponse = "#標題\n-項目1";
    String fixedResponse = "# 標題\n- 項目1";

    doAnswer(
            invocation -> {
              StreamingResponseHandler handler = invocation.getArgument(4);
              handler.onChunk(
                  invalidResponse, true, null, StreamingResponseHandler.ChunkType.CONTENT);
              return null;
            })
        .when(mockDelegate)
        .generateWithHistory(anyLong(), anyString(), anyString(), any(), any());

    when(mockValidator.validate(invalidResponse))
        .thenReturn(
            new MarkdownValidator.ValidationResult.Invalid(
                List.of(
                    new MarkdownValidator.MarkdownError(
                        MarkdownValidator.ErrorType.HEADING_FORMAT,
                        1,
                        1,
                        invalidResponse,
                        "在 # 後加入空格"))));

    when(mockAutoFixer.autoFix(invalidResponse)).thenReturn(fixedResponse);
    when(mockValidator.validate(fixedResponse))
        .thenReturn(new MarkdownValidator.ValidationResult.Valid(fixedResponse));

    StringBuilder content = new StringBuilder();
    AtomicReference<DomainError> errorRef = new AtomicReference<>();
    AtomicBoolean complete = new AtomicBoolean(false);

    service.generateWithHistory(
        guildId,
        channelId,
        userId,
        history,
        new StreamingResponseHandler() {
          @Override
          public void onChunk(String chunk, boolean isComplete, DomainError error, ChunkType type) {
            if (error != null) {
              errorRef.set(error);
              complete.set(true);
              return;
            }
            if (type == ChunkType.CONTENT) {
              content.append(chunk);
            }
            if (isComplete) {
              complete.set(true);
            }
          }
        });

    assertTrue(complete.get());
    assertNull(errorRef.get());
    assertEquals(fixedResponse, content.toString());
  }
}
