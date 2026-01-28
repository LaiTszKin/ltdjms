package ltdjms.discord.markdown.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import ltdjms.discord.aichat.services.AIChatService;
import ltdjms.discord.aichat.services.StreamingResponseHandler;
import ltdjms.discord.markdown.autofix.MarkdownAutoFixer;
import ltdjms.discord.markdown.autofix.RegexBasedAutoFixer;
import ltdjms.discord.markdown.services.MarkdownValidatingAIChatService;
import ltdjms.discord.markdown.validation.CommonMarkValidator;
import ltdjms.discord.markdown.validation.MarkdownValidator;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;

/**
 * 整合測試，驗證 Markdown 驗證裝飾器的完整行為。
 *
 * <p>測試範圍包含：
 *
 * <ul>
 *   <li>DI 組裝驗證：驗證 CommonMarkValidator 與 MarkdownValidatingAIChatService 可正確組裝
 *   <li>驗證流程：有效的 Markdown 直接通過
 *   <li>重格式化流程：格式錯誤時直接重格式化，不重試
 *   <li>降級模式：停用驗證時直接委派
 * </ul>
 */
@DisplayName("Markdown 驗證整合測試")
class MarkdownValidationIntegrationTest {

  @Mock private AIChatService mockDelegate;

  private MarkdownValidator validator;
  private MarkdownAutoFixer autofixer;
  private MarkdownValidatingAIChatService validatingService;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);

    // 使用真實的 CommonMarkValidator 和 MarkdownAutoFixer
    validator = new CommonMarkValidator();
    autofixer = new RegexBasedAutoFixer();
  }

  @Nested
  @DisplayName("DI 組裝驗證")
  class DIAssemblyTests {

    @Test
    @DisplayName("應該能夠使用真實的 CommonMarkValidator 建立 MarkdownValidatingAIChatService")
    void shouldAssembleWithRealValidator() {
      // Given
      validatingService =
          new MarkdownValidatingAIChatService(mockDelegate, validator, autofixer, true, false);

      // Then - 無異常拋出
      assertThat(validatingService).isNotNull();
    }
  }

  @Nested
  @DisplayName("驗證流程測試")
  class ValidationFlowTests {

    @BeforeEach
    void setUpValidatingService() {
      validatingService =
          new MarkdownValidatingAIChatService(mockDelegate, validator, autofixer, true, false);
    }

    @Test
    @DisplayName("有效的 Markdown 應該直接通過，不進行重格式化")
    void validMarkdownShouldPass() {
      // Given
      String validResponse = "## 這是標題\n\n這是段落文字。\n\n- 列表項目 1\n- 列表項目 2";
      when(mockDelegate.generateResponse(anyLong(), anyString(), anyString(), anyString()))
          .thenReturn(Result.ok(List.of(validResponse)));

      // When
      Result<List<String>, DomainError> result =
          validatingService.generateResponse(123L, "channel-1", "user-1", "測試訊息");

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue()).containsExactly(validResponse);
      verify(mockDelegate, times(1))
          .generateResponse(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("格式錯誤時應直接重格式化，不重試")
    void invalidMarkdownShouldBeReformattedWithoutRetry() {
      // Given - 標題缺少空格（#Heading）
      String invalidResponse = "#Heading without space";
      when(mockDelegate.generateResponse(anyLong(), anyString(), anyString(), anyString()))
          .thenReturn(Result.ok(List.of(invalidResponse)));

      // When
      Result<List<String>, DomainError> result =
          validatingService.generateResponse(123L, "channel-1", "user-1", "測試");

      // Then - 應該重格式化並成功，不重試
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().get(0)).contains("# Heading");
      verify(mockDelegate, times(1))
          .generateResponse(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("重格式化後仍無效時，應直接返回重格式化結果")
    void invalidMarkdownStillInvalidShouldReturnReformatted() {
      // Given - 表格是 Discord 不支援語法
      String invalidResponse = "| 欄位 A | 欄位 B |\n|--------|--------|\n| 值 1   | 值 2   |";
      when(mockDelegate.generateResponse(anyLong(), anyString(), anyString(), anyString()))
          .thenReturn(Result.ok(List.of(invalidResponse)));

      // When
      Result<List<String>, DomainError> result =
          validatingService.generateResponse(123L, "channel-1", "user-1", "測試");

      // Then - 不重試，直接返回重格式化結果（可能與原始相同）
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue()).containsExactly(invalidResponse);
      verify(mockDelegate, times(1))
          .generateResponse(anyLong(), anyString(), anyString(), anyString());
    }
  }

  @Nested
  @DisplayName("降級模式測試")
  class DegradationModeTests {

    @Test
    @DisplayName("停用驗證時應該直接委派，不進行驗證")
    void disabledValidationShouldDelegateDirectly() {
      // Given
      MarkdownValidatingAIChatService disabledService =
          new MarkdownValidatingAIChatService(mockDelegate, validator, autofixer, false, false);

      String anyResponse = "```\nunclosed block"; // 即使有錯誤也應該通過
      when(mockDelegate.generateResponse(anyLong(), anyString(), anyString(), anyString()))
          .thenReturn(Result.ok(List.of(anyResponse)));

      // When
      Result<List<String>, DomainError> result =
          disabledService.generateResponse(123L, "channel-1", "user-1", "測試");

      // Then - 直接返回，不驗證
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue()).containsExactly(anyResponse);
      verify(mockDelegate, times(1))
          .generateResponse(anyLong(), anyString(), anyString(), anyString());
    }
  }

  @Nested
  @DisplayName("委派錯誤處理測試")
  class DelegateErrorTests {

    @BeforeEach
    void setUpValidatingService() {
      validatingService =
          new MarkdownValidatingAIChatService(mockDelegate, validator, autofixer, true, false);
    }

    @Test
    @DisplayName("委派服務回傳錯誤時應該直接返回，不進行驗證")
    void delegateErrorShouldReturnDirectly() {
      // Given
      DomainError error = new DomainError(DomainError.Category.UNEXPECTED_FAILURE, "API 錯誤", null);
      when(mockDelegate.generateResponse(anyLong(), anyString(), anyString(), anyString()))
          .thenReturn(Result.err(error));

      // When
      Result<List<String>, DomainError> result =
          validatingService.generateResponse(123L, "channel-1", "user-1", "測試");

      // Then - 直接返回錯誤，不驗證
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError()).isEqualTo(error);
      verify(mockDelegate, times(1))
          .generateResponse(anyLong(), anyString(), anyString(), anyString());
    }
  }

  @Nested
  @DisplayName("串流回應測試")
  class StreamingResponseTests {

    @BeforeEach
    void setUpValidatingService() {
      validatingService =
          new MarkdownValidatingAIChatService(mockDelegate, validator, autofixer, true, false);
    }

    @Test
    @DisplayName("串流回應預設應先驗證再回傳分段")
    void streamingResponseShouldValidateAndEmit() {
      // Given
      StreamingResponseHandler mockHandler =
          org.mockito.Mockito.mock(StreamingResponseHandler.class);
      when(mockDelegate.generateResponse(anyLong(), anyString(), anyString(), anyString()))
          .thenReturn(Result.ok(List.of("合法回應")));

      // When
      validatingService.generateStreamingResponse(123L, "channel-1", "user-1", "測試", mockHandler);

      // Then - 透過 onChunk 回傳結果，且不使用委派的串流方法
      verify(mockDelegate, times(1))
          .generateResponse(anyLong(), anyString(), anyString(), anyString());
      verify(mockDelegate, never())
          .generateStreamingResponse(anyLong(), anyString(), anyString(), anyString(), any());
      verify(mockHandler, times(1)).onChunk(eq("合法回應"), eq(true), isNull(), any());
    }

    @Test
    @DisplayName("串流回應（帶 messageId）預設應驗證並回傳分段")
    void streamingResponseWithMessageIdShouldValidateAndEmit() {
      // Given
      StreamingResponseHandler mockHandler =
          org.mockito.Mockito.mock(StreamingResponseHandler.class);
      long messageId = 999L;
      when(mockDelegate.generateResponse(anyLong(), anyString(), anyString(), anyString()))
          .thenReturn(Result.ok(List.of("合法回應 2")));

      // When
      validatingService.generateStreamingResponse(
          123L, "channel-1", "user-1", "測試", messageId, mockHandler);

      // Then - 透過 onChunk 回傳結果，且不使用委派的串流方法
      verify(mockDelegate, times(1))
          .generateResponse(anyLong(), anyString(), anyString(), anyString());
      verify(mockDelegate, never())
          .generateStreamingResponse(
              anyLong(), anyString(), anyString(), anyString(), eq(messageId), any());
      verify(mockHandler, times(1)).onChunk(eq("合法回應 2"), eq(true), isNull(), any());
    }

    @Test
    @DisplayName("帶對話歷史的串流回應應驗證並重格式化後回傳")
    void streamingResponseWithHistoryShouldValidateAndEmit() {
      // Given
      StreamingResponseHandler mockHandler =
          org.mockito.Mockito.mock(StreamingResponseHandler.class);
      java.util.List<ltdjms.discord.aiagent.domain.ConversationMessage> history =
          java.util.List.of(
              new ltdjms.discord.aiagent.domain.ConversationMessage(
                  ltdjms.discord.aiagent.domain.MessageRole.USER,
                  "請列出可用工具",
                  java.time.Instant.now(),
                  java.util.Optional.empty(),
                  java.util.Optional.empty()));

      String invalidResponse = "#標題\n-項目1";

      doAnswer(
              invocation -> {
                StreamingResponseHandler handler = invocation.getArgument(4);
                handler.onChunk(
                    invalidResponse, true, null, StreamingResponseHandler.ChunkType.CONTENT);
                return null;
              })
          .when(mockDelegate)
          .generateWithHistory(anyLong(), anyString(), anyString(), any(), any());

      StringBuilder content = new StringBuilder();
      validatingService.generateWithHistory(
          123L,
          "channel-1",
          "user-1",
          history,
          new StreamingResponseHandler() {
            @Override
            public void onChunk(
                String chunk, boolean isComplete, DomainError error, ChunkType type) {
              if (error == null && type == ChunkType.CONTENT) {
                content.append(chunk);
              }
            }
          });

      // Then
      assertThat(content.toString()).contains("# 標題");
      verify(mockDelegate, times(1))
          .generateWithHistory(anyLong(), anyString(), anyString(), any(), any());
      verify(mockDelegate, never())
          .generateStreamingResponse(anyLong(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("啟用串流繞過時應直接委派原始串流")
    void streamingBypassShouldDelegate() {
      // Given
      var bypassService =
          new MarkdownValidatingAIChatService(mockDelegate, validator, autofixer, true, true);
      StreamingResponseHandler mockHandler =
          org.mockito.Mockito.mock(StreamingResponseHandler.class);

      // When
      bypassService.generateStreamingResponse(123L, "channel-1", "user-1", "測試", mockHandler);

      // Then
      verify(mockDelegate, times(1))
          .generateStreamingResponse(anyLong(), anyString(), anyString(), anyString(), any());
    }
  }
}
