package ltdjms.discord.aichat.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ltdjms.discord.aichat.domain.AIChatResponse;

/** 測試 {@link AIChatResponse} 的 JSON 解析功能。 */
class AIChatResponseTest {

  private static final ObjectMapper mapper = new ObjectMapper();

  @Test
  void testJsonDeserialization_shouldParseCorrectly() throws JsonProcessingException {
    // 前置
    String json =
        """
        {
          "id": "chatcmpl-123",
          "object": "chat.completion",
          "created": 1677652288,
          "model": "gpt-3.5-turbo",
          "choices": [
            {
              "index": 0,
              "message": {
                "role": "assistant",
                "content": "我是一個 AI 助手"
              },
              "finish_reason": "stop"
            }
          ],
          "usage": {
            "prompt_tokens": 10,
            "completion_tokens": 20,
            "total_tokens": 30
          }
        }
        """;

    // 執行
    AIChatResponse response = mapper.readValue(json, AIChatResponse.class);

    // 驗證
    assertThat(response.id()).isEqualTo("chatcmpl-123");
    assertThat(response.object()).isEqualTo("chat.completion");
    assertThat(response.created()).isEqualTo(1677652288L);
    assertThat(response.model()).isEqualTo("gpt-3.5-turbo");
    assertThat(response.choices()).hasSize(1);
    assertThat(response.choices().get(0).index()).isEqualTo(0);
    assertThat(response.choices().get(0).message().role()).isEqualTo("assistant");
    assertThat(response.choices().get(0).message().content()).isEqualTo("我是一個 AI 助手");
    assertThat(response.choices().get(0).finishReason()).isEqualTo("stop");
    assertThat(response.usage().promptTokens()).isEqualTo(10);
    assertThat(response.usage().completionTokens()).isEqualTo(20);
    assertThat(response.usage().totalTokens()).isEqualTo(30);
  }

  @Test
  void testJsonDeserialization_shouldIgnoreUnknownFields() throws JsonProcessingException {
    // 前置
    String json =
        """
        {
          "id": "chatcmpl-456",
          "object": "chat.completion",
          "created": 1677652299,
          "model": "gpt-4.1",
          "extra_top_level": "ignored",
          "choices": [
            {
              "index": 0,
              "message": {
                "role": "assistant",
                "content": "正常回應",
                "reasoning_content": "這段應該被忽略"
              },
              "finish_reason": "stop",
              "extra_choice_field": 123
            }
          ],
          "usage": {
            "prompt_tokens": 5,
            "completion_tokens": 7,
            "total_tokens": 12,
            "extra_usage_field": true
          }
        }
        """;

    // 執行
    AIChatResponse response = mapper.readValue(json, AIChatResponse.class);

    // 驗證
    assertThat(response.model()).isEqualTo("gpt-4.1");
    assertThat(response.choices()).hasSize(1);
    assertThat(response.choices().get(0).message().content()).isEqualTo("正常回應");
    assertThat(response.usage().totalTokens()).isEqualTo(12);
  }

  @Test
  void testGetContent_shouldReturnFirstChoiceContent() {
    // 前置
    AIChatResponse response =
        new AIChatResponse(
            "chatcmpl-123",
            "chat.completion",
            1677652288L,
            "gpt-3.5-turbo",
            List.of(
                new AIChatResponse.Choice(
                    0, new AIChatResponse.Choice.AIMessage("assistant", "回應內容", null), "stop")),
            new AIChatResponse.Usage(10, 20, 30));

    // 執行
    String content = response.getContent();

    // 驗證
    assertThat(content).isEqualTo("回應內容");
  }

  @Test
  void testGetContent_withEmptyChoices_shouldReturnEmptyString() {
    // 前置
    AIChatResponse response =
        new AIChatResponse(
            "chatcmpl-123",
            "chat.completion",
            1677652288L,
            "gpt-3.5-turbo",
            List.of(),
            new AIChatResponse.Usage(10, 20, 30));

    // 執行
    String content = response.getContent();

    // 驗證
    assertThat(content).isEmpty();
  }

  @Test
  void testGetContent_withNullChoices_shouldReturnEmptyString() {
    // 前置
    AIChatResponse response =
        new AIChatResponse(
            "chatcmpl-123",
            "chat.completion",
            1677652288L,
            "gpt-3.5-turbo",
            null,
            new AIChatResponse.Usage(10, 20, 30));

    // 執行
    String content = response.getContent();

    // 驗證
    assertThat(content).isEmpty();
  }

  @Test
  void testGetContent_withNullMessage_shouldReturnEmptyString() {
    // 前置
    AIChatResponse response =
        new AIChatResponse(
            "chatcmpl-123",
            "chat.completion",
            1677652288L,
            "gpt-3.5-turbo",
            List.of(new AIChatResponse.Choice(0, null, "stop")),
            new AIChatResponse.Usage(10, 20, 30));

    // 執行
    String content = response.getContent();

    // 驗證
    assertThat(content).isEmpty();
  }

  @Test
  void testGetContent_withNullMessageContent_shouldReturnEmptyString() {
    // 前置
    AIChatResponse response =
        new AIChatResponse(
            "chatcmpl-123",
            "chat.completion",
            1677652288L,
            "gpt-3.5-turbo",
            List.of(
                new AIChatResponse.Choice(
                    0, new AIChatResponse.Choice.AIMessage("assistant", null, null), "stop")),
            new AIChatResponse.Usage(10, 20, 30));

    // 執行
    String content = response.getContent();

    // 驗證
    assertThat(content).isEmpty();
  }
}
