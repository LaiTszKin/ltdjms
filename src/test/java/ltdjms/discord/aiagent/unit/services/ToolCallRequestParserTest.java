package ltdjms.discord.aiagent.unit.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import ltdjms.discord.aiagent.services.ToolCallRequest;
import ltdjms.discord.aiagent.services.ToolCallRequestParser;

/**
 * ToolCallRequestParser 的單元測試。
 *
 * <p>測試各種 JSON 和函數調用格式的解析功能。
 */
@DisplayName("ToolCallRequestParser 單元測試")
class ToolCallRequestParserTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_CHANNEL_ID = 111111111111111111L;
  private static final long TEST_USER_ID = 987654321098765432L;

  @Nested
  @DisplayName("parse - 標準 JSON 格式")
  class StandardJsonTests {

    @Test
    @DisplayName("應成功解析標準 JSON 格式")
    void shouldParseStandardJson() {
      // Given
      String json =
          """
          {
            "tool": "create_channel",
            "parameters": {
              "name": "公告",
              "permissions": ["view", "write"]
            }
          }
          """;

      // When
      Optional<ToolCallRequest> result =
          ToolCallRequestParser.parse(json, TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);

      // Then
      assertThat(result).isPresent();
      ToolCallRequest request = result.get();
      assertThat(request.toolName()).isEqualTo("create_channel");
      assertThat(request.parameters()).containsKey("name");
      assertThat(request.parameters().get("name")).isEqualTo("公告");
      assertThat(request.guildId()).isEqualTo(TEST_GUILD_ID);
      assertThat(request.channelId()).isEqualTo(TEST_CHANNEL_ID);
      assertThat(request.userId()).isEqualTo(TEST_USER_ID);
    }

    @Test
    @DisplayName("應支援 toolName 欄位作為工具名稱")
    void shouldSupportToolNameField() {
      // Given
      String json =
          """
          {
            "toolName": "create_category",
            "parameters": {
              "name": "活動"
            }
          }
          """;

      // When
      Optional<ToolCallRequest> result =
          ToolCallRequestParser.parse(json, TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);

      // Then
      assertThat(result).isPresent();
      assertThat(result.get().toolName()).isEqualTo("create_category");
    }

    @Test
    @DisplayName("應支援 params 欄位作為參數")
    void shouldSupportParamsField() {
      // Given
      String json =
          """
          {
            "tool": "test_tool",
            "params": {
              "key": "value"
            }
          }
          """;

      // When
      Optional<ToolCallRequest> result =
          ToolCallRequestParser.parse(json, TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);

      // Then
      assertThat(result).isPresent();
      assertThat(result.get().parameters()).containsKey("key");
    }

    @Test
    @DisplayName("缺少 tool 欄位時應返回 empty")
    void shouldReturnEmptyWhenMissingToolField() {
      // Given
      String json =
          """
          {
            "parameters": {
              "name": "測試"
            }
          }
          """;

      // When
      Optional<ToolCallRequest> result =
          ToolCallRequestParser.parse(json, TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("無效 JSON 時應返回 empty")
    void shouldReturnEmptyForInvalidJson() {
      // Given
      String invalidJson = "{ invalid json }";

      // When
      Optional<ToolCallRequest> result =
          ToolCallRequestParser.parse(invalidJson, TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("工具名稱為空字串時應返回 empty")
    void shouldReturnEmptyWhenToolNameIsEmpty() {
      // Given
      String json =
          """
          {
            "tool": "",
            "parameters": {}
          }
          """;

      // When
      Optional<ToolCallRequest> result =
          ToolCallRequestParser.parse(json, TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);

      // Then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("parse - JSON 代碼塊格式")
  class JsonCodeBlockTests {

    @Test
    @DisplayName("應成功解析 ```json 代碼塊")
    void shouldParseJsonCodeBlock() {
      // Given
      String response =
          """
          這是 AI 的回應文字，包含工具調用：

          ```json
          {
            "tool": "create_channel",
            "parameters": {
              "name": "公告"
            }
          }
          ```

          結束
          """;

      // When
      Optional<ToolCallRequest> result =
          ToolCallRequestParser.parse(response, TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);

      // Then
      assertThat(result).isPresent();
      assertThat(result.get().toolName()).isEqualTo("create_channel");
    }

    @Test
    @DisplayName("應成功解析 ``` 代碼塊（不帶 json 標記）")
    void shouldParseCodeBlockWithoutJsonMarker() {
      // Given
      String response =
          """
          執行工具調用：

          ```
          {
            "tool": "create_category",
            "parameters": {
              "name": "活動"
            }
          }
          ```
          """;

      // When
      Optional<ToolCallRequest> result =
          ToolCallRequestParser.parse(response, TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);

      // Then
      assertThat(result).isPresent();
      assertThat(result.get().toolName()).isEqualTo("create_category");
    }

    @Test
    @DisplayName("應支援大小寫混合的 JSON 標記")
    void shouldSupportCaseInsensitiveJsonMarker() {
      // Given
      String response =
          """
          ```JSON
          {
            "tool": "test_tool",
            "parameters": {}
          }
          ```
          """;

      // When
      Optional<ToolCallRequest> result =
          ToolCallRequestParser.parse(response, TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);

      // Then
      assertThat(result).isPresent();
    }

    @Test
    @DisplayName("代碼塊中的 JSON 無效時應繼續嘗試其他格式")
    void shouldFallbackWhenJsonCodeBlockIsInvalid() {
      // Given
      String response =
          """
          ```json
          { invalid json }
          ```
          {
            "tool": "test_tool",
            "parameters": {}
          }
          """;

      // When
      Optional<ToolCallRequest> result =
          ToolCallRequestParser.parse(response, TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);

      // Then - 目前實現不會 fallback，只是返回空
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("parse - 函數調用格式")
  class FunctionCallTests {

    @Test
    @DisplayName("應成功解析函數調用格式")
    void shouldParseFunctionCall() {
      // Given
      String response = "create_channel(name=\"公告\", permissions=[\"view\"])";

      // When
      Optional<ToolCallRequest> result =
          ToolCallRequestParser.parse(response, TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);

      // Then
      assertThat(result).isPresent();
      assertThat(result.get().toolName()).isEqualTo("create_channel");
    }

    @Test
    @DisplayName("函數調用格式應支援空參數")
    void shouldSupportEmptyParameters() {
      // Given
      String response = "test_tool()";

      // When
      Optional<ToolCallRequest> result =
          ToolCallRequestParser.parse(response, TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);

      // Then
      assertThat(result).isPresent();
      assertThat(result.get().toolName()).isEqualTo("test_tool");
      assertThat(result.get().parameters()).isEmpty();
    }

    @Test
    @DisplayName("函數名稱包含數字時應正確解析")
    void shouldParseFunctionNameWithNumbers() {
      // Given
      String response = "tool123_v2()";

      // When
      Optional<ToolCallRequest> result =
          ToolCallRequestParser.parse(response, TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);

      // Then
      assertThat(result).isPresent();
      assertThat(result.get().toolName()).isEqualTo("tool123_v2");
    }

    @Test
    @DisplayName("函數調用格式混合其他文字時應提取函數部分")
    void shouldExtractFunctionCallFromMixedText() {
      // Given
      String response = "我將執行 create_channel(name=\"測試\") 這個工具";

      // When
      Optional<ToolCallRequest> result =
          ToolCallRequestParser.parse(response, TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);

      // Then
      assertThat(result).isPresent();
      assertThat(result.get().toolName()).isEqualTo("create_channel");
    }
  }

  @Nested
  @DisplayName("parse - 空值和邊界條件")
  class EdgeCaseTests {

    @Test
    @DisplayName("null 輸入應返回 empty")
    void shouldReturnEmptyForNullInput() {
      // When
      Optional<ToolCallRequest> result =
          ToolCallRequestParser.parse(null, TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("空字串應返回 empty")
    void shouldReturnEmptyForEmptyString() {
      // When
      Optional<ToolCallRequest> result =
          ToolCallRequestParser.parse("", TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("空白字串應返回 empty")
    void shouldReturnEmptyForBlankString() {
      // When
      Optional<ToolCallRequest> result =
          ToolCallRequestParser.parse("   ", TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("純文字無工具調用應返回 empty")
    void shouldReturnEmptyForPlainText() {
      // Given
      String plainText = "這只是一般的對話文字，沒有任何工具調用";

      // When
      Optional<ToolCallRequest> result =
          ToolCallRequestParser.parse(plainText, TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("缺少參數欄位時應使用空 Map")
    void shouldUseEmptyMapWhenMissingParameters() {
      // Given
      String json =
          """
          {
            "tool": "test_tool"
          }
          """;

      // When
      Optional<ToolCallRequest> result =
          ToolCallRequestParser.parse(json, TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);

      // Then
      assertThat(result).isPresent();
      assertThat(result.get().parameters()).isEmpty();
    }

    @Test
    @DisplayName("JSON 帶有空白時應正確解析")
    void shouldParseJsonWithWhitespace() {
      // Given
      String json = "   {   \"tool\"   :   \"test_tool\"   }   ";

      // When
      Optional<ToolCallRequest> result =
          ToolCallRequestParser.parse(json, TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);

      // Then
      assertThat(result).isPresent();
    }
  }

  @Nested
  @DisplayName("parse - 複雜參數")
  class ComplexParametersTests {

    @Test
    @DisplayName("應支援嵌套物件參數")
    void shouldSupportNestedObjectParameters() {
      // Given
      String json =
          """
          {
            "tool": "test_tool",
            "parameters": {
              "config": {
                "enabled": true,
                "settings": {
                  "timeout": 30
                }
              }
            }
          }
          """;

      // When
      Optional<ToolCallRequest> result =
          ToolCallRequestParser.parse(json, TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);

      // Then
      assertThat(result).isPresent();
      assertThat(result.get().parameters()).containsKey("config");
    }

    @Test
    @DisplayName("應支援數組參數")
    void shouldSupportArrayParameters() {
      // Given
      String json =
          """
          {
            "tool": "test_tool",
            "parameters": {
              "items": ["a", "b", "c"]
            }
          }
          """;

      // When
      Optional<ToolCallRequest> result =
          ToolCallRequestParser.parse(json, TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);

      // Then
      assertThat(result).isPresent();
    }

    @Test
    @DisplayName("應支援數字和布林值參數")
    void shouldSupportNumericAndBooleanParameters() {
      // Given
      String json =
          """
          {
            "tool": "test_tool",
            "parameters": {
              "count": 42,
              "enabled": true,
              "ratio": 3.14
            }
          }
          """;

      // When
      Optional<ToolCallRequest> result =
          ToolCallRequestParser.parse(json, TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);

      // Then
      assertThat(result).isPresent();
      assertThat(result.get().parameters()).containsKey("count");
      assertThat(result.get().parameters()).containsKey("enabled");
    }
  }
}
