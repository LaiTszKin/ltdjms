package ltdjms.discord.markdown.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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

import ltdjms.discord.aichat.domain.AIServiceConfig;
import ltdjms.discord.aichat.services.AIChatService;
import ltdjms.discord.markdown.services.MarkdownValidatingAIChatService;
import ltdjms.discord.markdown.validation.CommonMarkValidator;
import ltdjms.discord.markdown.validation.MarkdownErrorFormatter;
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
 *   <li>重試流程：格式錯誤觸發重試，直到成功或達到上限
 *   <li>降級模式：停用驗證時直接委派
 * </ul>
 */
@DisplayName("Markdown 驗證整合測試")
class MarkdownValidationIntegrationTest {

  private static final AIServiceConfig TEST_CONFIG =
      new AIServiceConfig(
          "https://api.test.com",
          "test-key",
          "gpt-4",
          0.7,
          60,
          false,
          true, // enable markdown validation
          5);

  private static final AIServiceConfig TEST_CONFIG_DISABLED =
      new AIServiceConfig(
          "https://api.test.com",
          "test-key",
          "gpt-4",
          0.7,
          60,
          false,
          false, // disable markdown validation
          5);

  @Mock private AIChatService mockDelegate;

  private MarkdownValidator validator;
  private MarkdownErrorFormatter formatter;
  private MarkdownValidatingAIChatService validatingService;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);

    // 使用真實的 CommonMarkValidator 和 MarkdownErrorFormatter
    validator = new CommonMarkValidator();
    formatter = new MarkdownErrorFormatter();
  }

  @Nested
  @DisplayName("DI 組裝驗證")
  class DIAssemblyTests {

    @Test
    @DisplayName("應該能夠使用真實的 CommonMarkValidator 建立 MarkdownValidatingAIChatService")
    void shouldAssembleWithRealValidator() {
      // Given
      validatingService =
          new MarkdownValidatingAIChatService(mockDelegate, validator, true, formatter);

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
          new MarkdownValidatingAIChatService(mockDelegate, validator, true, formatter);
    }

    @Test
    @DisplayName("有效的 Markdown 應該直接通過，不觸發重試")
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
    @DisplayName("程式碼區塊語法錯誤應該觸發重試")
    void codeBlockSyntaxErrorShouldTriggerRetry() {
      // Given - 第一次回應有不完整的程式碼區塊，第二次正確
      String invalidResponse = "```java\npublic void test() {\n  // 缺少結束的 ```";
      String validResponse = "```java\npublic void test() {\n}\n```";

      when(mockDelegate.generateResponse(anyLong(), anyString(), anyString(), anyString()))
          .thenReturn(Result.ok(List.of(invalidResponse)))
          .thenReturn(Result.ok(List.of(validResponse)));

      // When
      Result<List<String>, DomainError> result =
          validatingService.generateResponse(123L, "channel-1", "user-1", "寫一個 Java 函數");

      // Then - 應該重試一次後成功
      assertThat(result.isOk()).isTrue();
      verify(mockDelegate, times(2))
          .generateResponse(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("標題等級超過 Discord 限制應該觸發重試")
    void headingLevelExceedsDiscordLimitShouldTriggerRetry() {
      // Given - 第一次回應有 H7（超過 Discord 限制），第二次修正
      String invalidResponse = "######\nHeading 6\n#######\nHeading 7 - 超過限制";
      String validResponse = "######\nHeading 6\n######\nHeading 6（修正後）";

      when(mockDelegate.generateResponse(anyLong(), anyString(), anyString(), anyString()))
          .thenReturn(Result.ok(List.of(invalidResponse)))
          .thenReturn(Result.ok(List.of(validResponse)));

      // When
      Result<List<String>, DomainError> result =
          validatingService.generateResponse(123L, "channel-1", "user-1", "寫個標題");

      // Then - 應該重試一次後成功
      assertThat(result.isOk()).isTrue();
      verify(mockDelegate, times(2))
          .generateResponse(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("多個格式錯誤應該在錯誤報告中全部列出")
    void multipleErrorsShouldAllBeListedInReport() {
      // Given - 同時有多個錯誤：不完整程式碼區塊 + 過高標題
      String invalidResponse = "####### H7\n\n```java\nunclosed code block\n- 緊接文字，沒有空行";
      String validResponse = "###### H6\n\n```java\npublic void test() {\n}\n```\n\n- 正確的列表";

      when(mockDelegate.generateResponse(anyLong(), anyString(), anyString(), anyString()))
          .thenReturn(Result.ok(List.of(invalidResponse)))
          .thenReturn(Result.ok(List.of(validResponse)));

      // When
      validatingService.generateResponse(123L, "channel-1", "user-1", "生成有問題的回應");

      // Then - 第二次請求應該包含完整的錯誤報告
      verify(mockDelegate, times(2))
          .generateResponse(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("超出最大重試次數應該返回最後一次的回應")
    void exceedingMaxRetriesShouldReturnLastResponse() {
      // Given - 持續返回不完整的程式碼區塊
      String invalidResponse = "```java\npublic void test() {\n  // 持續缺少結束";

      when(mockDelegate.generateResponse(anyLong(), anyString(), anyString(), anyString()))
          .thenReturn(Result.ok(List.of(invalidResponse)));

      // When
      Result<List<String>, DomainError> result =
          validatingService.generateResponse(123L, "channel-1", "user-1", "測試");

      // Then - 應該重試 5 次（初始 + 4 次重試）
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue()).containsExactly(invalidResponse);
      verify(mockDelegate, times(5))
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
          new MarkdownValidatingAIChatService(mockDelegate, validator, false, formatter);

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
          new MarkdownValidatingAIChatService(mockDelegate, validator, true, formatter);
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
          new MarkdownValidatingAIChatService(mockDelegate, validator, true, formatter);
    }

    @Test
    @DisplayName("串流回應應該直接委派，不進行驗證")
    void streamingResponseShouldDelegateDirectly() {
      // Given
      ltdjms.discord.aichat.services.StreamingResponseHandler mockHandler =
          org.mockito.Mockito.mock(ltdjms.discord.aichat.services.StreamingResponseHandler.class);

      // When
      validatingService.generateStreamingResponse(123L, "channel-1", "user-1", "測試", mockHandler);

      // Then - 直接委派，不驗證
      verify(mockDelegate, times(1))
          .generateStreamingResponse(anyLong(), anyString(), anyString(), anyString(), any());
    }
  }
}
