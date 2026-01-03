package ltdjms.discord.markdown.unit.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ltdjms.discord.aichat.services.AIChatService;
import ltdjms.discord.markdown.services.MarkdownValidatingAIChatService;
import ltdjms.discord.markdown.validation.*;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;

@DisplayName("MarkdownValidatingAIChatService - 重試邏輯測試")
class MarkdownValidatingAIChatServiceTest_Retry {

  private AIChatService mockDelegate;
  private MarkdownValidator mockValidator;
  private MarkdownErrorFormatter formatter;
  private MarkdownValidatingAIChatService service;

  @BeforeEach
  void setUp() {
    mockDelegate = mock(AIChatService.class);
    mockValidator = mock(MarkdownValidator.class);
    formatter = new MarkdownErrorFormatter();
    service =
        new MarkdownValidatingAIChatService(mockDelegate, mockValidator, true, formatter, 5, false);
  }

  @Test
  @DisplayName("格式錯誤應觸發重試並在第二次成功")
  void invalidThenValid_shouldRetryAndSucceed() {
    // Given
    long guildId = 123L;
    String channelId = "456";
    String userId = "789";
    String userMessage = "解釋 CompletableFuture";
    String invalidResponse = "```java without closing";
    String validResponse = "正確的回應";

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
    when(mockDelegate.generateResponse(anyLong(), anyString(), anyString(), contains("系統提示")))
        .thenReturn(Result.ok(List.of(validResponse)));
    when(mockValidator.validate(invalidResponse))
        .thenReturn(new MarkdownValidator.ValidationResult.Invalid(errors));
    when(mockValidator.validate(validResponse))
        .thenReturn(new MarkdownValidator.ValidationResult.Valid(validResponse));

    // When
    var result = service.generateResponse(guildId, channelId, userId, userMessage);

    // Then
    assertTrue(result.isOk());
    assertEquals(List.of(validResponse), result.getValue());

    // 驗證調用了兩次
    verify(mockDelegate, times(2))
        .generateResponse(anyLong(), anyString(), anyString(), anyString());
  }

  @Test
  @DisplayName("超過重試次數應返回最後結果")
  void exceedMaxRetries_shouldReturnLastResponse() {
    // Given
    String invalidResponse = "Always invalid";
    List<MarkdownValidator.MarkdownError> errors =
        List.of(
            new MarkdownValidator.MarkdownError(
                MarkdownValidator.ErrorType.MALFORMED_LIST, 1, 1, "bad", "fix"));

    when(mockDelegate.generateResponse(anyLong(), anyString(), anyString(), anyString()))
        .thenReturn(Result.ok(List.of(invalidResponse)));
    when(mockValidator.validate(anyString()))
        .thenReturn(new MarkdownValidator.ValidationResult.Invalid(errors));

    // When
    var result = service.generateResponse(123L, "456", "789", "test");

    // Then
    assertTrue(result.isOk());
    assertEquals(List.of(invalidResponse), result.getValue());

    // 驗證調用了 MAX_RETRY_ATTEMPTS 次
    verify(mockDelegate, times(5))
        .generateResponse(anyLong(), anyString(), anyString(), anyString());
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
