package ltdjms.discord.markdown.unit.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ltdjms.discord.aichat.services.AIChatService;
import ltdjms.discord.markdown.autofix.MarkdownAutoFixer;
import ltdjms.discord.markdown.services.MarkdownValidatingAIChatService;
import ltdjms.discord.markdown.validation.MarkdownValidator;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;

@DisplayName("MarkdownValidatingAIChatService - 第一次成功測試")
class MarkdownValidatingAIChatServiceTest_SuccessFirstTry {

  private AIChatService mockDelegate;
  private MarkdownValidator mockValidator;
  private MarkdownAutoFixer mockAutoFixer;
  private MarkdownValidatingAIChatService service;

  @BeforeEach
  void setUp() {
    mockDelegate = mock(AIChatService.class);
    mockValidator = mock(MarkdownValidator.class);
    mockAutoFixer = mock(MarkdownAutoFixer.class);
    service =
        new MarkdownValidatingAIChatService(
            mockDelegate, mockValidator, mockAutoFixer, true, false);
  }

  @Test
  @DisplayName("第一次生成的回應格式正確應直接返回")
  void validResponseOnFirstTry_shouldReturnDirectly() {
    // Given
    long guildId = 123L;
    String channelId = "456";
    String userId = "789";
    String userMessage = "解釋 Java 中的 CompletableFuture";
    String validResponse = "CompletableFuture 是 Java 8 引入的非同步編程工具。";

    Result<java.util.List<String>, DomainError> successResult =
        Result.ok(java.util.List.of(validResponse));

    when(mockDelegate.generateResponse(eq(guildId), eq(channelId), eq(userId), eq(userMessage)))
        .thenReturn(successResult);
    when(mockValidator.validate(validResponse))
        .thenReturn(new MarkdownValidator.ValidationResult.Valid(validResponse));

    // When
    var result = service.generateResponse(guildId, channelId, userId, userMessage);

    // Then
    assertTrue(result.isOk());
    assertEquals(java.util.List.of(validResponse), result.getValue());

    // 驗證只調用一次
    verify(mockDelegate, times(1))
        .generateResponse(anyLong(), anyString(), anyString(), anyString());
    verify(mockValidator, times(1)).validate(any());
  }
}
