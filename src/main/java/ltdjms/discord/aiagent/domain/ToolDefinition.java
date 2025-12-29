package ltdjms.discord.aiagent.domain;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 工具定義。
 *
 * <p>描述一個可被 AI 調用的系統工具，包含工具名稱、描述和參數定義。 此定義會被轉換為 JSON Schema 注入至 AI 系統提示詞。
 *
 * @param name 工具名稱（唯一識別符）
 * @param description 工具描述（AI 用於理解工具用途）
 * @param parameters 參數定義列表
 */
public record ToolDefinition(String name, String description, List<ToolParameter> parameters) {

  public ToolDefinition {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(description, "description must not be null");
    Objects.requireNonNull(parameters, "parameters must not be null");
    if (name.isBlank()) {
      throw new IllegalArgumentException("工具名稱不能為空");
    }
    if (description.isBlank()) {
      throw new IllegalArgumentException("工具描述不能為空");
    }
  }

  /**
   * 轉換為 JSON Schema 格式（用於 AI 模型）。
   *
   * @return JSON Schema 字串
   */
  public String toJsonSchema() {
    String properties =
        parameters.stream().map(ToolParameter::toJsonProperty).collect(Collectors.joining(",\n  "));

    String required =
        parameters.stream()
            .filter(ToolParameter::required)
            .map(p -> "\"" + p.name() + "\"")
            .collect(Collectors.joining(", "));

    return """
    {
      "name": "%s",
      "description": "%s",
      "parameters": {
        "type": "object",
        "properties": {
      %s
        },
        "required": [%s]
      }
    }
    """
        .formatted(name, description, properties, required);
  }
}
