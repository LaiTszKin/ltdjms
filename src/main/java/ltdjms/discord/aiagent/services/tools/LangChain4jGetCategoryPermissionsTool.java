package ltdjms.discord.aiagent.services.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import ltdjms.discord.shared.di.JDAProvider;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.channel.concrete.Category;

/** 獲取 Discord 類別權限設定工具（LangChain4J 版本）。 */
public final class LangChain4jGetCategoryPermissionsTool {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(LangChain4jGetCategoryPermissionsTool.class);

  @Inject
  public LangChain4jGetCategoryPermissionsTool() {
    // JDA 將從 JDAProvider 延遲獲取
  }

  /**
   * 獲取 Discord 類別的權限覆寫資訊。
   *
   * @param categoryId 類別 ID（字串格式，避免 JSON 精度損失）
   * @param parameters 調用參數（包含 guildId、channelId、userId）
   * @return 權限覆寫列表 JSON 字串
   */
  @Tool(
      """
      獲取 Discord 類別的權限覆寫資訊。

      使用場景：
      - 當用戶詢問特定類別的權限設定時使用
      - 需要查看哪些角色或成員有特殊權限時使用
      - 檢查類別的訪問控制設定時使用

      返回資訊：
      - 權限覆寫總數
      - 每個覆寫的類型（角色/成員）、ID、允許的權限、拒絕的權限

      重要：
      - 所有 ID 以「字串」形式返回，避免 JSON 數字溢位造成精度損失
      - 返回的權限覆寫包含角色和成員兩種類型
      """)
  public String getCategoryPermissions(
      @P(
              value =
                  """
                  要查詢的類別 ID。

                  必須是有效的 Discord 類別 ID（字串格式）。

                  範例：
                  - "123456789012345678"：查詢指定類別的權限
                  """,
              required = true)
          String categoryId,
      InvocationParameters parameters) {

    // 1. 驗證 categoryId 參數
    if (categoryId == null || categoryId.isBlank()) {
      LOGGER.error("LangChain4jGetCategoryPermissionsTool: categoryId 未提供");
      return buildErrorResponse("categoryId 未提供");
    }

    // 解析類別 ID
    long categoryIdLong;
    try {
      categoryIdLong = parseCategoryId(categoryId);
    } catch (NumberFormatException e) {
      String errorMsg = String.format("無效的類別 ID: %s", categoryId);
      LOGGER.warn("LangChain4jGetCategoryPermissionsTool: {}", errorMsg);
      return buildErrorResponse(errorMsg);
    }

    // 2. 從 InvocationParameters 獲取執行上下文
    Long guildId = parameters.get("guildId");
    if (guildId == null) {
      LOGGER.error("LangChain4jGetCategoryPermissionsTool: guildId 未設置");
      return buildErrorResponse("guildId 未設置");
    }

    // 3. 獲取 Guild
    Guild guild = JDAProvider.getJda().getGuildById(guildId);
    if (guild == null) {
      String errorMsg = String.format("找不到指定的伺服器: %d", guildId);
      LOGGER.warn("LangChain4jGetCategoryPermissionsTool: {}", errorMsg);
      return buildErrorResponse("找不到伺服器");
    }

    String authorizationError =
        ToolCallerAuthorizationGuard.validateAdministrator(
            parameters, guild, LOGGER, "LangChain4jGetCategoryPermissionsTool");
    if (authorizationError != null) {
      return buildErrorResponse(authorizationError);
    }

    // 4. 獲取類別
    Category category = guild.getCategoryById(categoryIdLong);
    if (category == null) {
      String errorMsg = String.format("找不到指定的類別: %s", categoryId);
      LOGGER.warn("LangChain4jGetCategoryPermissionsTool: {}", errorMsg);
      return buildErrorResponse("找不到類別");
    }

    try {
      // 5. 獲取所有權限覆寫
      List<PermissionOverride> overrides = category.getPermissionOverrides();
      List<Map<String, Object>> permissionInfos = new ArrayList<>();

      for (PermissionOverride override : overrides) {
        permissionInfos.add(buildPermissionInfo(override));
      }

      // 6. 返回 JSON 格式結果
      String jsonResult = buildJsonResult(category, permissionInfos);
      LOGGER.info("LangChain4jGetCategoryPermissionsTool: 找到 {} 個權限覆寫", permissionInfos.size());
      return jsonResult;

    } catch (Exception e) {
      String errorMsg = String.format("獲取類別權限失敗: %s", e.getMessage());
      LOGGER.error("LangChain4jGetCategoryPermissionsTool: {}", errorMsg, e);
      return buildErrorResponse(errorMsg);
    }
  }

  /**
   * 解析類別 ID（支援字串和數字格式）。
   *
   * @param categoryId 類別 ID
   * @return 解析後的 long 值
   */
  private long parseCategoryId(String categoryId) {
    String trimmed = categoryId.trim();
    // 移除可能的 <#> 和 <> 標記
    if (trimmed.startsWith("<#") && trimmed.endsWith(">")) {
      trimmed = trimmed.substring(2, trimmed.length() - 1);
    } else if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
      trimmed = trimmed.substring(1, trimmed.length() - 1);
    }
    return Long.parseLong(trimmed);
  }

  /**
   * 構建權限覆寫資訊 Map。
   *
   * @param override 權限覆寫
   * @return 權限覆寫資訊 Map
   */
  private Map<String, Object> buildPermissionInfo(PermissionOverride override) {
    Map<String, Object> info = new LinkedHashMap<>();
    info.put("id", String.valueOf(override.getIdLong()));
    info.put("type", override.isRoleOverride() ? "role" : "member");

    // 添加允許的權限
    List<String> allowed = permissionListToString(override.getAllowed());
    if (!allowed.isEmpty()) {
      info.put("allowed", allowed);
    }

    // 添加拒絕的權限
    List<String> denied = permissionListToString(override.getDenied());
    if (!denied.isEmpty()) {
      info.put("denied", denied);
    }

    return info;
  }

  /**
   * 將 EnumSet<Permission> 轉換為可讀的字串列表。
   *
   * @param permissions 權限 EnumSet
   * @return 權限名稱列表
   */
  private List<String> permissionListToString(
      java.util.EnumSet<net.dv8tion.jda.api.Permission> permissions) {
    if (permissions == null || permissions.isEmpty()) {
      return List.of();
    }

    List<String> permissionNames = new ArrayList<>();
    for (net.dv8tion.jda.api.Permission permission : permissions) {
      permissionNames.add(permission.name());
    }
    return permissionNames;
  }

  /**
   * 構建 JSON 格式結果。
   *
   * @param category 類別
   * @param permissionInfos 權限覆寫資訊列表
   * @return JSON 字串
   */
  private String buildJsonResult(Category category, List<Map<String, Object>> permissionInfos) {
    StringBuilder json = new StringBuilder();
    json.append("{\n");
    json.append("  \"success\": true,\n");
    json.append("  \"categoryId\": \"").append(category.getIdLong()).append("\",\n");
    json.append("  \"categoryName\": \"").append(escapeJson(category.getName())).append("\",\n");
    json.append("  \"count\": ").append(permissionInfos.size()).append(",\n");
    json.append("  \"overrides\": [\n");

    for (int i = 0; i < permissionInfos.size(); i++) {
      if (i > 0) {
        json.append(",\n");
      }
      Map<String, Object> info = permissionInfos.get(i);
      json.append("    {\n");
      json.append("      \"id\": \"").append(info.get("id")).append("\",\n");
      json.append("      \"type\": \"").append(info.get("type")).append("\"");

      @SuppressWarnings("unchecked")
      List<String> allowed = (List<String>) info.get("allowed");
      if (allowed != null && !allowed.isEmpty()) {
        json.append(",\n");
        json.append("      \"allowed\": [");
        for (int j = 0; j < allowed.size(); j++) {
          if (j > 0) {
            json.append(", ");
          }
          json.append("\"").append(allowed.get(j)).append("\"");
        }
        json.append("]");
      }

      @SuppressWarnings("unchecked")
      List<String> denied = (List<String>) info.get("denied");
      if (denied != null && !denied.isEmpty()) {
        json.append(",\n");
        json.append("      \"denied\": [");
        for (int j = 0; j < denied.size(); j++) {
          if (j > 0) {
            json.append(", ");
          }
          json.append("\"").append(denied.get(j)).append("\"");
        }
        json.append("]");
      }

      json.append("\n    }");
    }

    json.append("\n  ]\n");
    json.append("}");
    return json.toString();
  }

  /**
   * 轉義 JSON 字串中的特殊字符。
   *
   * @param value 原始字串
   * @return 轉義後的字串
   */
  private String escapeJson(String value) {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }

  /**
   * 構建錯誤回應。
   *
   * @param error 錯誤訊息
   * @return JSON 格式的錯誤回應
   */
  private String buildErrorResponse(String error) {
    return """
    {
      "success": false,
      "error": "%s"
    }
    """
        .formatted(escapeJson(error));
  }
}
