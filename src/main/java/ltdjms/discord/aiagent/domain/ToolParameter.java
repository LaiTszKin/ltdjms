package ltdjms.discord.aiagent.domain;

import java.util.Objects;

/**
 * 工具參數定義。
 *
 * @param name 參數名稱
 * @param type 參數類型（STRING, NUMBER, BOOLEAN, ARRAY, OBJECT）
 * @param description 參數描述
 * @param required 是否必填
 * @param defaultValue 預設值（可選）
 */
public record ToolParameter(
    String name, ParamType type, String description, boolean required, Object defaultValue) {

  /** 參數類型枚舉。 */
  public enum ParamType {
    /** 字串類型 */
    STRING,
    /** 數字類型 */
    NUMBER,
    /** 布林類型 */
    BOOLEAN,
    /** 陣列類型 */
    ARRAY,
    /** 物件類型 */
    OBJECT
  }

  public ToolParameter {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(description, "description must not be null");
  }

  /**
   * 轉換為 JSON Schema 屬性格式。
   *
   * @return JSON Schema 屬性字串
   */
  public String toJsonProperty() {
    String defaultPart = defaultValue != null ? "\"default\": " + defaultValue : "";
    return """
    "%s": {
      "type": "%s",
      "description": "%s"
    %s
    }
    """
        .formatted(name, type.name().toLowerCase(), description, defaultPart);
  }
}
