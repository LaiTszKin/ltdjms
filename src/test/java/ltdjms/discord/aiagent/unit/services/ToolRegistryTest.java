package ltdjms.discord.aiagent.unit.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import ltdjms.discord.aiagent.domain.ToolDefinition;
import ltdjms.discord.aiagent.domain.ToolParameter;
import ltdjms.discord.aiagent.domain.ToolParameter.ParamType;
import ltdjms.discord.aiagent.services.DefaultToolRegistry;
import ltdjms.discord.aiagent.services.ToolRegistry;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.Unit;

/**
 * ToolRegistry 的單元測試。
 *
 * <p>測試工具註冊中心的註冊、取消註冊、查詢和提示詞生成功能。
 */
@DisplayName("ToolRegistry")
class ToolRegistryTest {

  private ToolRegistry toolRegistry;

  private static final ToolDefinition TEST_TOOL =
      new ToolDefinition(
          "test_tool",
          "測試工具",
          List.of(new ToolParameter("param1", ParamType.STRING, "第一個參數", true, null)));

  private static final ToolDefinition WEATHER_TOOL =
      new ToolDefinition(
          "weather_tool",
          "天氣查詢工具",
          List.of(
              new ToolParameter("location", ParamType.STRING, "地點", true, null),
              new ToolParameter("unit", ParamType.STRING, "單位", false, "\"celsius\"")));

  @BeforeEach
  void setUp() {
    toolRegistry = new DefaultToolRegistry();
  }

  @Nested
  @DisplayName("register - 註冊工具")
  class RegisterTests {

    @Test
    @DisplayName("應成功註冊新工具")
    void shouldRegisterNewToolSuccessfully() {
      // When
      Result<Unit, DomainError> result = toolRegistry.register(TEST_TOOL);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(toolRegistry.isRegistered("test_tool")).isTrue();
    }

    @Test
    @DisplayName("重複註冊相同名稱工具應返回錯誤")
    void shouldReturnErrorWhenRegisteringDuplicateTool() {
      // Given - 已註冊一個工具
      toolRegistry.register(TEST_TOOL);

      // When - 嘗試註冊相同名稱的工具
      ToolDefinition duplicateTool =
          new ToolDefinition(
              "test_tool",
              "重複的測試工具",
              List.of(new ToolParameter("param2", ParamType.NUMBER, "第二個參數", true, null)));
      Result<Unit, DomainError> result = toolRegistry.register(duplicateTool);

      // Then
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().category()).isEqualTo(DomainError.Category.INVALID_INPUT);
      assertThat(result.getError().message()).contains("已存在");
    }

    @Test
    @DisplayName("註冊空名稱工具應拋出異常")
    void shouldThrowExceptionWhenRegisteringToolWithEmptyName() {
      // When/Then
      assertThatThrownBy(
              () ->
                  toolRegistry.register(
                      new ToolDefinition(
                          "",
                          "空名稱工具",
                          List.of(
                              new ToolParameter("param1", ParamType.STRING, "參數", true, null)))))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("工具名稱不能為空");
    }

    @Test
    @DisplayName("註冊多個不同工具應全部成功")
    void shouldRegisterMultipleDifferentTools() {
      // When
      Result<Unit, DomainError> result1 = toolRegistry.register(TEST_TOOL);
      Result<Unit, DomainError> result2 = toolRegistry.register(WEATHER_TOOL);

      // Then
      assertThat(result1.isOk()).isTrue();
      assertThat(result2.isOk()).isTrue();
      assertThat(toolRegistry.getAllTools()).hasSize(2);
    }
  }

  @Nested
  @DisplayName("unregister - 取消註冊工具")
  class UnregisterTests {

    @Test
    @DisplayName("應成功取消註冊已存在的工具")
    void shouldUnregisterExistingToolSuccessfully() {
      // Given - 已註冊一個工具
      toolRegistry.register(TEST_TOOL);

      // When
      Result<Unit, DomainError> result = toolRegistry.unregister("test_tool");

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(toolRegistry.isRegistered("test_tool")).isFalse();
    }

    @Test
    @DisplayName("取消註冊不存在的工具應返回錯誤")
    void shouldReturnErrorWhenUnregisteringNonExistentTool() {
      // When
      Result<Unit, DomainError> result = toolRegistry.unregister("non_existent_tool");

      // Then
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().category()).isEqualTo(DomainError.Category.INVALID_INPUT);
      assertThat(result.getError().message()).contains("不存在");
    }

    @Test
    @DisplayName("取消註冊後工具不應出現在 getAllTools 中")
    void shouldNotIncludeUnregisteredToolInGetAllTools() {
      // Given
      toolRegistry.register(TEST_TOOL);
      toolRegistry.register(WEATHER_TOOL);

      // When
      toolRegistry.unregister("test_tool");

      // Then
      assertThat(toolRegistry.getAllTools())
          .hasSize(1)
          .allMatch(tool -> tool.name().equals("weather_tool"));
    }

    @Test
    @DisplayName("連續取消註冊多個工具應全部成功")
    void shouldUnregisterMultipleToolsSuccessfully() {
      // Given
      toolRegistry.register(TEST_TOOL);
      toolRegistry.register(WEATHER_TOOL);

      // When
      Result<Unit, DomainError> result1 = toolRegistry.unregister("test_tool");
      Result<Unit, DomainError> result2 = toolRegistry.unregister("weather_tool");

      // Then
      assertThat(result1.isOk()).isTrue();
      assertThat(result2.isOk()).isTrue();
      assertThat(toolRegistry.getAllTools()).isEmpty();
    }
  }

  @Nested
  @DisplayName("getTool - 獲取工具")
  class GetToolTests {

    @Test
    @DisplayName("應成功獲取已註冊的工具")
    void shouldGetExistingToolSuccessfully() {
      // Given
      toolRegistry.register(TEST_TOOL);

      // When
      Result<ToolDefinition, DomainError> result = toolRegistry.getTool("test_tool");

      // Then
      assertThat(result.isOk()).isTrue();
      ToolDefinition tool = result.getValue();
      assertThat(tool.name()).isEqualTo("test_tool");
      assertThat(tool.description()).isEqualTo("測試工具");
      assertThat(tool.parameters()).hasSize(1);
      assertThat(tool.parameters().get(0).name()).isEqualTo("param1");
    }

    @Test
    @DisplayName("獲取不存在的工具應返回錯誤")
    void shouldReturnErrorWhenGettingNonExistentTool() {
      // Given
      toolRegistry.register(TEST_TOOL);

      // When
      Result<ToolDefinition, DomainError> result = toolRegistry.getTool("non_existent_tool");

      // Then
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().category()).isEqualTo(DomainError.Category.INVALID_INPUT);
      assertThat(result.getError().message()).contains("不存在");
    }

    @Test
    @DisplayName("獲取工具應返回相同的實例引用")
    void shouldReturnSameInstanceWhenGettingTool() {
      // Given
      toolRegistry.register(TEST_TOOL);
      Result<ToolDefinition, DomainError> result1 = toolRegistry.getTool("test_tool");

      // When
      Result<ToolDefinition, DomainError> result2 = toolRegistry.getTool("test_tool");

      // Then
      assertThat(result1.getValue()).isSameAs(result2.getValue());
    }

    @Test
    @DisplayName("應該能夠獲取註冊的多個不同工具")
    void shouldGetMultipleRegisteredTools() {
      // Given
      toolRegistry.register(TEST_TOOL);
      toolRegistry.register(WEATHER_TOOL);

      // When
      Result<ToolDefinition, DomainError> result1 = toolRegistry.getTool("test_tool");
      Result<ToolDefinition, DomainError> result2 = toolRegistry.getTool("weather_tool");

      // Then
      assertThat(result1.isOk()).isTrue();
      assertThat(result2.isOk()).isTrue();
      assertThat(result1.getValue().name()).isEqualTo("test_tool");
      assertThat(result2.getValue().name()).isEqualTo("weather_tool");
    }
  }

  @Nested
  @DisplayName("getAllTools - 獲取所有工具")
  class GetAllToolsTests {

    @Test
    @DisplayName("應返回空列表當沒有註冊任何工具")
    void shouldReturnEmptyListWhenNoToolsRegistered() {
      // When
      List<ToolDefinition> tools = toolRegistry.getAllTools();

      // Then
      assertThat(tools).isEmpty();
    }

    @Test
    @DisplayName("應返回所有已註冊的工具")
    void shouldReturnAllRegisteredTools() {
      // Given
      toolRegistry.register(TEST_TOOL);
      toolRegistry.register(WEATHER_TOOL);

      // When
      List<ToolDefinition> tools = toolRegistry.getAllTools();

      // Then
      assertThat(tools).hasSize(2);
      assertThat(tools)
          .extracting(ToolDefinition::name)
          .containsExactlyInAnyOrder("test_tool", "weather_tool");
    }

    @Test
    @DisplayName("返回的工具列表應包含完整定義")
    void shouldReturnCompleteToolDefinitions() {
      // Given
      toolRegistry.register(TEST_TOOL);

      // When
      List<ToolDefinition> tools = toolRegistry.getAllTools();

      // Then
      assertThat(tools).hasSize(1);
      ToolDefinition tool = tools.get(0);
      assertThat(tool.name()).isEqualTo("test_tool");
      assertThat(tool.description()).isEqualTo("測試工具");
      assertThat(tool.parameters()).hasSize(1);
      assertThat(tool.parameters().get(0).name()).isEqualTo("param1");
      assertThat(tool.parameters().get(0).type()).isEqualTo(ParamType.STRING);
      assertThat(tool.parameters().get(0).required()).isTrue();
    }

    @Test
    @DisplayName("取消註冊工具後不應出現在返回列表中")
    void shouldNotIncludeUnregisteredTools() {
      // Given
      toolRegistry.register(TEST_TOOL);
      toolRegistry.register(WEATHER_TOOL);
      toolRegistry.unregister("test_tool");

      // When
      List<ToolDefinition> tools = toolRegistry.getAllTools();

      // Then
      assertThat(tools).hasSize(1);
      assertThat(tools.get(0).name()).isEqualTo("weather_tool");
    }

    @Test
    @DisplayName("返回的列表應為不可修改")
    void shouldReturnUnmodifiableList() {
      // Given
      toolRegistry.register(TEST_TOOL);
      List<ToolDefinition> tools = toolRegistry.getAllTools();

      // When/Then
      assertThatThrownBy(() -> tools.add(WEATHER_TOOL))
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Nested
  @DisplayName("isRegistered - 檢查工具是否已註冊")
  class IsRegisteredTests {

    @Test
    @DisplayName("應返回 true 當工具已註冊")
    void shouldReturnTrueWhenToolIsRegistered() {
      // Given
      toolRegistry.register(TEST_TOOL);

      // When
      boolean result = toolRegistry.isRegistered("test_tool");

      // Then
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("應返回 false 當工具未註冊")
    void shouldReturnFalseWhenToolIsNotRegistered() {
      // When
      boolean result = toolRegistry.isRegistered("non_existent_tool");

      // Then
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("取消註冊後應返回 false")
    void shouldReturnFalseAfterUnregistering() {
      // Given
      toolRegistry.register(TEST_TOOL);

      // When
      toolRegistry.unregister("test_tool");
      boolean result = toolRegistry.isRegistered("test_tool");

      // Then
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("空名稱應返回 false")
    void shouldReturnFalseForEmptyName() {
      // When
      boolean result = toolRegistry.isRegistered("");

      // Then
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("null 名稱應返回 false")
    void shouldReturnFalseForNullName() {
      // When
      boolean result = toolRegistry.isRegistered(null);

      // Then
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("應正確區分大小寫")
    void shouldCaseSensitiveWhenCheckingRegistration() {
      // Given
      toolRegistry.register(TEST_TOOL);

      // When
      boolean result1 = toolRegistry.isRegistered("test_tool");
      boolean result2 = toolRegistry.isRegistered("TEST_TOOL");
      boolean result3 = toolRegistry.isRegistered("Test_Tool");

      // Then
      assertThat(result1).isTrue();
      assertThat(result2).isFalse();
      assertThat(result3).isFalse();
    }
  }

  @Nested
  @DisplayName("getToolsPrompt - 獲取工具提示詞")
  class GetToolsPromptTests {

    @Test
    @DisplayName("應返回 JSON Schema 格式的工具定義")
    void shouldReturnJsonSchemaFormat() {
      // Given
      toolRegistry.register(TEST_TOOL);

      // When
      String prompt = toolRegistry.getToolsPrompt();

      // Then
      assertThat(prompt).contains("\"name\": \"test_tool\"");
      assertThat(prompt).contains("\"description\": \"測試工具\"");
      assertThat(prompt).contains("\"type\": \"object\"");
      assertThat(prompt).contains("\"properties\":");
      assertThat(prompt).contains("\"required\":");
    }

    @Test
    @DisplayName("應包含所有已註冊的工具")
    void shouldIncludeAllRegisteredTools() {
      // Given
      toolRegistry.register(TEST_TOOL);
      toolRegistry.register(WEATHER_TOOL);

      // When
      String prompt = toolRegistry.getToolsPrompt();

      // Then
      assertThat(prompt).contains("\"name\": \"test_tool\"");
      assertThat(prompt).contains("\"name\": \"weather_tool\"");
    }

    @Test
    @DisplayName("應正確格式化參數定義")
    void shouldFormatParametersCorrectly() {
      // Given
      toolRegistry.register(TEST_TOOL);

      // When
      String prompt = toolRegistry.getToolsPrompt();

      // Then
      assertThat(prompt).contains("\"param1\"");
      assertThat(prompt).contains("\"type\": \"string\"");
      assertThat(prompt).contains("\"description\": \"第一個參數\"");
      assertThat(prompt).contains("\"required\": [\"param1\"]");
    }

    @Test
    @DisplayName("應正確處理選填參數與預設值")
    void shouldHandleOptionalParametersWithDefaults() {
      // Given
      toolRegistry.register(WEATHER_TOOL);

      // When
      String prompt = toolRegistry.getToolsPrompt();

      // Then
      assertThat(prompt).contains("\"location\"");
      assertThat(prompt).contains("\"unit\"");
      assertThat(prompt).contains("\"default\": \"celsius\"");
      assertThat(prompt).contains("\"required\": [\"location\"]");
    }

    @Test
    @DisplayName("無工具時應返回空陣列格式")
    void shouldReturnEmptyArrayFormatWhenNoTools() {
      // When
      String prompt = toolRegistry.getToolsPrompt();

      // Then
      assertThat(prompt).isEqualTo("[]");
    }

    @Test
    @DisplayName("應產生有效的 JSON 陣列格式")
    void shouldProduceValidJsonArrayFormat() {
      // Given
      toolRegistry.register(TEST_TOOL);
      toolRegistry.register(WEATHER_TOOL);

      // When
      String prompt = toolRegistry.getToolsPrompt();

      // Then
      assertThat(prompt).startsWith("[");
      assertThat(prompt).endsWith("]");
      assertThat(prompt).contains("}\n, {"); // 多個工具間的分隔符（含換行）
    }

    @Test
    @DisplayName("取消註冊工具後不應出現在提示詞中")
    void shouldNotIncludeUnregisteredToolsInPrompt() {
      // Given
      toolRegistry.register(TEST_TOOL);
      toolRegistry.register(WEATHER_TOOL);
      toolRegistry.unregister("test_tool");

      // When
      String prompt = toolRegistry.getToolsPrompt();

      // Then
      assertThat(prompt).doesNotContain("test_tool");
      assertThat(prompt).contains("weather_tool");
    }

    @Test
    @DisplayName("應正確處理數字類型參數")
    void shouldHandleNumberTypeParameters() {
      // Given
      ToolDefinition numberTool =
          new ToolDefinition(
              "number_tool",
              "數字工具",
              List.of(new ToolParameter("count", ParamType.NUMBER, "計數", true, null)));
      toolRegistry.register(numberTool);

      // When
      String prompt = toolRegistry.getToolsPrompt();

      // Then
      assertThat(prompt).contains("\"type\": \"number\"");
    }

    @Test
    @DisplayName("應正確處理布林類型參數")
    void shouldHandleBooleanTypeParameters() {
      // Given
      ToolDefinition booleanTool =
          new ToolDefinition(
              "boolean_tool",
              "布林工具",
              List.of(new ToolParameter("enabled", ParamType.BOOLEAN, "啟用", true, null)));
      toolRegistry.register(booleanTool);

      // When
      String prompt = toolRegistry.getToolsPrompt();

      // Then
      assertThat(prompt).contains("\"type\": \"boolean\"");
    }

    @Test
    @DisplayName("應正確處理陣列類型參數")
    void shouldHandleArrayTypeParameters() {
      // Given
      ToolDefinition arrayTool =
          new ToolDefinition(
              "array_tool",
              "陣列工具",
              List.of(new ToolParameter("items", ParamType.ARRAY, "項目", true, null)));
      toolRegistry.register(arrayTool);

      // When
      String prompt = toolRegistry.getToolsPrompt();

      // Then
      assertThat(prompt).contains("\"type\": \"array\"");
    }

    @Test
    @DisplayName("應正確處理物件類型參數")
    void shouldHandleObjectTypeParameters() {
      // Given
      ToolDefinition objectTool =
          new ToolDefinition(
              "object_tool",
              "物件工具",
              List.of(new ToolParameter("data", ParamType.OBJECT, "資料", true, null)));
      toolRegistry.register(objectTool);

      // When
      String prompt = toolRegistry.getToolsPrompt();

      // Then
      assertThat(prompt).contains("\"type\": \"object\"");
    }

    @Test
    @DisplayName("應正確處理多個必填參數")
    void shouldHandleMultipleRequiredParameters() {
      // Given
      ToolDefinition multiParamTool =
          new ToolDefinition(
              "multi_param_tool",
              "多參數工具",
              List.of(
                  new ToolParameter("param1", ParamType.STRING, "參數一", true, null),
                  new ToolParameter("param2", ParamType.STRING, "參數二", true, null),
                  new ToolParameter("param3", ParamType.STRING, "參數三", false, null)));
      toolRegistry.register(multiParamTool);

      // When
      String prompt = toolRegistry.getToolsPrompt();

      // Then
      assertThat(prompt).contains("\"required\": [\"param1\", \"param2\"]");
    }

    @Test
    @DisplayName("當所有參數皆選填時 required 應為空陣列")
    void shouldHaveEmptyRequiredArrayWhenAllParametersOptional() {
      // Given
      ToolDefinition optionalTool =
          new ToolDefinition(
              "optional_tool",
              "選填工具",
              List.of(
                  new ToolParameter("param1", ParamType.STRING, "參數一", false, null),
                  new ToolParameter("param2", ParamType.STRING, "參數二", false, null)));
      toolRegistry.register(optionalTool);

      // When
      String prompt = toolRegistry.getToolsPrompt();

      // Then
      assertThat(prompt).contains("\"required\": []");
    }
  }
}
