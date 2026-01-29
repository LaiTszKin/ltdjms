package ltdjms.discord.markdown.unit.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ltdjms.discord.aichat.services.AIChatService;
import ltdjms.discord.markdown.autofix.MarkdownAutoFixer;
import ltdjms.discord.markdown.services.DiscordMarkdownPaginator;
import ltdjms.discord.markdown.services.DiscordMarkdownSanitizer;
import ltdjms.discord.markdown.services.MarkdownValidatingAIChatService;
import ltdjms.discord.markdown.validation.MarkdownValidator;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;

@DisplayName("MarkdownValidatingAIChatService - 預修復流程測試")
class MarkdownValidatingAIChatServiceTest_Reformat {

  private AIChatService mockDelegate;
  private MarkdownValidator mockValidator;
  private MarkdownAutoFixer mockAutoFixer;
  private MarkdownValidatingAIChatService service;

  @BeforeEach
  void setUp() {
    mockDelegate = mock(AIChatService.class);
    mockValidator = mock(MarkdownValidator.class);
    mockAutoFixer = mock(MarkdownAutoFixer.class);
    DiscordMarkdownSanitizer sanitizer = new DiscordMarkdownSanitizer();
    DiscordMarkdownPaginator paginator = new DiscordMarkdownPaginator();
    service =
        new MarkdownValidatingAIChatService(
            mockDelegate, mockValidator, mockAutoFixer, sanitizer, paginator, true, false);
  }

  @Test
  @DisplayName("格式錯誤應先預修復並返回")
  void invalidResponse_shouldReformatAndReturn() {
    // Given
    long guildId = 123L;
    String channelId = "456";
    String userId = "789";
    String userMessage = "解釋 CompletableFuture";
    String invalidResponse = "```java without closing";
    String reformattedResponse = "```java\n// fixed\n```";

    List<MarkdownValidator.MarkdownError> errors =
        List.of(
            new MarkdownValidator.MarkdownError(
                MarkdownValidator.ErrorType.UNCLOSED_CODE_BLOCK,
                1,
                1,
                "```java",
                "Add closing ```"));

    when(mockDelegate.generateResponse(anyLong(), anyString(), anyString(), eq(userMessage)))
        .thenReturn(Result.ok(List.of(invalidResponse)));
    when(mockValidator.validate(invalidResponse))
        .thenReturn(new MarkdownValidator.ValidationResult.Invalid(errors));
    when(mockAutoFixer.autoFix(invalidResponse)).thenReturn(reformattedResponse);
    when(mockValidator.validate(reformattedResponse))
        .thenReturn(new MarkdownValidator.ValidationResult.Valid(reformattedResponse));

    // When
    var result = service.generateResponse(guildId, channelId, userId, userMessage);

    // Then
    assertTrue(result.isOk());
    assertEquals(List.of(reformattedResponse), result.getValue());

    // 驗證只調用一次
    verify(mockDelegate, times(1))
        .generateResponse(anyLong(), anyString(), anyString(), anyString());
    verify(mockAutoFixer, times(1)).autoFix(invalidResponse);
    verify(mockValidator, times(1)).validate(invalidResponse);
    verify(mockValidator, times(1)).validate(reformattedResponse);
  }

  @Test
  @DisplayName("預修復後仍無效時應直接返回預修復結果")
  void reformattedStillInvalid_shouldReturnReformatted() {
    // Given
    String invalidResponse = "Always invalid";
    String reformatted = "Always invalid (reformatted)";
    List<MarkdownValidator.MarkdownError> errors =
        List.of(
            new MarkdownValidator.MarkdownError(
                MarkdownValidator.ErrorType.MALFORMED_LIST, 1, 1, "bad", "fix"));

    when(mockDelegate.generateResponse(anyLong(), anyString(), anyString(), anyString()))
        .thenReturn(Result.ok(List.of(invalidResponse)));
    when(mockValidator.validate(invalidResponse))
        .thenReturn(new MarkdownValidator.ValidationResult.Invalid(errors));
    when(mockAutoFixer.autoFix(invalidResponse)).thenReturn(reformatted);
    when(mockValidator.validate(reformatted))
        .thenReturn(new MarkdownValidator.ValidationResult.Invalid(errors));

    // When
    var result = service.generateResponse(123L, "456", "789", "test");

    // Then
    assertTrue(result.isOk());
    assertEquals(List.of(reformatted), result.getValue());
    verify(mockDelegate, times(1))
        .generateResponse(anyLong(), anyString(), anyString(), anyString());
    verify(mockAutoFixer, times(1)).autoFix(invalidResponse);
    verify(mockValidator, times(1)).validate(invalidResponse);
    verify(mockValidator, times(1)).validate(reformatted);
  }

  @Test
  @DisplayName("委託服務錯誤應直接返回不重試")
  void delegateError_shouldReturnErrorDirectly() {
    // Given
    var domainError =
        new DomainError(DomainError.Category.UNEXPECTED_FAILURE, "LLM API error", null);

    when(mockDelegate.generateResponse(anyLong(), anyString(), anyString(), anyString()))
        .thenReturn(Result.err(domainError));

    // When
    var result = service.generateResponse(123L, "456", "789", "test");

    // Then
    assertTrue(result.isErr());
    assertSame(domainError, result.getError());

    // 驗證只調用一次
    verify(mockDelegate, times(1))
        .generateResponse(anyLong(), anyString(), anyString(), anyString());
  }
}
