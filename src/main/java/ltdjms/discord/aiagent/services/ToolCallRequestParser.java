package ltdjms.discord.aiagent.services;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 解析 AI 回應中的工具調用請求。
 *
 * <p>支援多種 JSON 格式和函數調用格式的解析：
 *
 * <ul>
 *   <li>標準 JSON: {@code {"tool": "create_channel", "parameters": {...}}}
 *   <li>JSON 代碼塊: 包裹在 {@code ```json ... ```} 中的 JSON
 *   <li>函數調用格式: {@code create_channel(name="公告", permissions=[...])}
 * </ul>
 *
 * <p>解析失敗時返回 {@code Optional.empty()} 並記錄警告日誌。
 */
public final class ToolCallRequestParser {

  private static final Logger LOGGER = LoggerFactory.getLogger(ToolCallRequestParser.class);

  /** Jackson ObjectMapper 用於 JSON 解析 */
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  /** JSON 代碼塊正則表達式：匹配 ```json ... ``` 或 ``` ... ``` */
  private static final Pattern JSON_CODE_BLOCK_PATTERN =
      Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```", Pattern.CASE_INSENSITIVE);

  /** 函數調用格式正則表達式：匹配 tool_name(key=value, ...) */
  private static final Pattern FUNCTION_CALL_PATTERN =
      Pattern.compile("([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(([^)]*)\\)", Pattern.DOTALL);

  private ToolCallRequestParser() {
    // 工具類，不允許實例化
  }

  /**
   * 從 AI 回應中解析工具調用請求。
   *
   * <p>嘗試按順序解析以下格式：
   *
   * <ol>
   *   <li>JSON 代碼塊格式
   *   <li>標準 JSON 格式
   *   <li>函數調用格式
   * </ol>
   *
   * @param aiResponse AI 回應文字
   * @param guildId 伺服器 ID
   * @param channelId 頻道 ID
   * @param userId 觸發用戶 ID
   * @return 解析成功的工具調用請求，或 {@code Optional.empty()}
   */
  public static Optional<ToolCallRequest> parse(
      String aiResponse, long guildId, long channelId, long userId) {

    if (aiResponse == null || aiResponse.isBlank()) {
      LOGGER.warn("AI 回應為空，無法解析工具調用請求");
      return Optional.empty();
    }

    // 1. 嘗試提取並解析 JSON 代碼塊
    Optional<String> jsonContent = extractJsonCodeBlock(aiResponse);
    if (jsonContent.isPresent()) {
      Optional<ToolCallRequest> result = parseJson(jsonContent.get(), guildId, channelId, userId);
      if (result.isPresent()) {
        return result;
      }
    }

    // 2. 嘗試直接解析為 JSON
    Optional<ToolCallRequest> directJsonResult =
        parseJson(aiResponse.trim(), guildId, channelId, userId);
    if (directJsonResult.isPresent()) {
      return directJsonResult;
    }

    // 3. 嘗試解析函數調用格式
    Optional<ToolCallRequest> functionCallResult =
        parseFunctionCall(aiResponse, guildId, channelId, userId);
    if (functionCallResult.isPresent()) {
      return functionCallResult;
    }

    LOGGER.warn("無法解析 AI 回應中的工具調用請求: {}", aiResponse);
    return Optional.empty();
  }

  /**
   * 提取 JSON 代碼塊。
   *
   * <p>支援以下格式：
   *
   * <ul>
   *   <li>{@code ```json ... ```}
   *   <li>{@code ``` ... ```}
   * </ul>
   *
   * @param response AI 回應文字
   * @return 提取的 JSON 內容，或 {@code Optional.empty()}
   */
  private static Optional<String> extractJsonCodeBlock(String response) {
    Matcher matcher = JSON_CODE_BLOCK_PATTERN.matcher(response);
    if (matcher.find()) {
      String jsonContent = matcher.group(1).trim();
      LOGGER.debug("從代碼塊中提取 JSON: {}", jsonContent);
      return Optional.of(jsonContent);
    }
    return Optional.empty();
  }

  /**
   * 解析 JSON 格式的工具調用請求。
   *
   * <p>支援的 JSON 結構：
   *
   * <pre>{@code
   * {
   *   "tool": "create_channel",
   *   "parameters": {
   *     "name": "公告",
   *     "permissions": [...]
   *   }
   * }
   * }</pre>
   *
   * @param json JSON 字串
   * @param guildId 伺服器 ID
   * @param channelId 頻道 ID
   * @param userId 觸發用戶 ID
   * @return 解析成功的工具調用請求，或 {@code Optional.empty()}
   */
  private static Optional<ToolCallRequest> parseJson(
      String json, long guildId, long channelId, long userId) {

    try {
      JsonNode rootNode = OBJECT_MAPPER.readTree(json);

      // 提取工具名稱
      String toolName = extractToolName(rootNode);
      if (toolName == null || toolName.isBlank()) {
        LOGGER.warn("JSON 中缺少有效的 'tool' 欄位: {}", json);
        return Optional.empty();
      }

      // 提取參數
      Map<String, Object> parameters = extractParameters(rootNode);

      ToolCallRequest request =
          new ToolCallRequest(toolName, parameters, guildId, channelId, userId);

      LOGGER.info(
          "成功解析 JSON 格式的工具調用請求: tool={}, guildId={}, channelId={}, userId={}",
          toolName,
          guildId,
          channelId,
          userId);

      return Optional.of(request);

    } catch (JsonProcessingException e) {
      LOGGER.debug("JSON 解析失敗: {}", e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * 解析函數調用格式的工具調用請求。
   *
   * <p>支援的格式：
   *
   * <pre>{@code
   * create_channel(name="公告", permissions=[...])
   * }</pre>
   *
   * @param response AI 回應文字
   * @param guildId 伺服器 ID
   * @param channelId 頻道 ID
   * @param userId 觸發用戶 ID
   * @return 解析成功的工具調用請求，或 {@code Optional.empty()}
   */
  private static Optional<ToolCallRequest> parseFunctionCall(
      String response, long guildId, long channelId, long userId) {

    Matcher matcher = FUNCTION_CALL_PATTERN.matcher(response);
    if (!matcher.find()) {
      return Optional.empty();
    }

    String toolName = matcher.group(1);
    String parametersStr = matcher.group(2);

    if (toolName.isBlank()) {
      LOGGER.warn("函數調用格式中的工具名稱為空");
      return Optional.empty();
    }

    try {
      Map<String, Object> parameters = parseFunctionParameters(parametersStr);

      ToolCallRequest request =
          new ToolCallRequest(toolName, parameters, guildId, channelId, userId);

      LOGGER.info(
          "成功解析函數調用格式的工具調用請求: tool={}, guildId={}, channelId={}, userId={}",
          toolName,
          guildId,
          channelId,
          userId);

      return Optional.of(request);

    } catch (Exception e) {
      LOGGER.warn("解析函數調用參數失敗: {}", e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * 從 JSON 節點提取工具名稱。
   *
   * <p>支援的欄位名稱：{@code tool} 或 {@code toolName}。
   *
   * @param rootNode JSON 根節點
   * @return 工具名稱，或 {@code null} 如果不存在
   */
  private static String extractToolName(JsonNode rootNode) {
    JsonNode toolNode = rootNode.get("tool");
    if (toolNode == null || !toolNode.isTextual()) {
      toolNode = rootNode.get("toolName");
    }
    return toolNode != null && toolNode.isTextual() ? toolNode.asText() : null;
  }

  /**
   * 從 JSON 節點提取參數。
   *
   * <p>支援的欄位名稱：{@code parameters} 或 {@code params}。 如果不存在，返回空 Map。
   *
   * @param rootNode JSON 根節點
   * @return 參數 Map
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> extractParameters(JsonNode rootNode) {
    JsonNode paramsNode = rootNode.get("parameters");
    if (paramsNode == null) {
      paramsNode = rootNode.get("params");
    }

    if (paramsNode == null || !paramsNode.isObject()) {
      return Map.of();
    }

    try {
      return OBJECT_MAPPER.convertValue(paramsNode, Map.class);
    } catch (IllegalArgumentException e) {
      LOGGER.warn("無法轉換參數節點為 Map: {}", e.getMessage());
      return Map.of();
    }
  }

  /**
   * 解析函數調用格式的參數字串。
   *
   * <p>簡化實作：僅支援基本字串參數。 完整實作應支援：字串、數字、布林值、列表、嵌套物件等。
   *
   * @param paramsStr 參數字串（例如：{@code name="公告", readonly=true}）
   * @return 參數 Map
   */
  private static Map<String, Object> parseFunctionParameters(String paramsStr) {
    // 簡化實作：返回空 Map
    // 完整實作需要解析 key=value 格式的參數
    // 這可以作為未來的改進點

    if (paramsStr == null || paramsStr.isBlank()) {
      return Map.of();
    }

    // 嘗試將參數字串解析為 JSON
    try {
      String jsonized = "{" + paramsStr + "}";
      JsonNode paramsNode = OBJECT_MAPPER.readTree(jsonized);
      return OBJECT_MAPPER.convertValue(paramsNode, Map.class);
    } catch (JsonProcessingException e) {
      LOGGER.debug("無法將函數參數解析為 JSON: {}", e.getMessage());
      return Map.of();
    }
  }
}
