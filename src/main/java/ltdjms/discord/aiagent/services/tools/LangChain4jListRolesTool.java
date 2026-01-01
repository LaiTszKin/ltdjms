package ltdjms.discord.aiagent.services.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import ltdjms.discord.shared.di.JDAProvider;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;

/**
 * 列出 Discord 角色工具（LangChain4J 版本）。
 *
 * <p>使用 LangChain4J 的 @Tool 註解，通過 ToolExecutionContext 獲取執行上下文。
 */
public final class LangChain4jListRolesTool {

  private static final Logger LOGGER = LoggerFactory.getLogger(LangChain4jListRolesTool.class);

  @Inject
  public LangChain4jListRolesTool() {
    // JDA 將從 JDAProvider 延遲獲取
  }

  /**
   * 獲取 Discord 伺服器中的所有角色資訊。
   *
   * @param parameters 調用參數（包含 guildId、channelId、userId）
   * @return 角色列表 JSON 字串
   */
  @Tool(
      """
      獲取 Discord 伺服器中的角色列表資訊。

      使用場景：
      - 當用戶詢問有哪些角色時使用
      - 創建頻道或類別前需要了解可用的角色 ID 時使用
      - 需要檢查特定角色是否存在時使用

      返回資訊：
      - 角色數量
      - 每個角色的 ID 和名稱

      排序規則：
      - 角色按權限等級從高到低排序
      - @everyone 角色固定排在最後（等級最低）

      應用提示：
      - 角色 ID 用於設置頻道/類別權限
      - 創建私密頻道時需要指定允許訪問的角色 ID
      """)
  public String listRoles(InvocationParameters parameters) {

    // 1. 從 InvocationParameters 獲取執行上下文
    Long guildId = parameters.get("guildId");
    if (guildId == null) {
      LOGGER.error("LangChain4jListRolesTool: guildId 未設置");
      return buildErrorResponse("guildId 未設置");
    }

    // 2. 獲取 Guild
    Guild guild = JDAProvider.getJda().getGuildById(guildId);
    if (guild == null) {
      String errorMsg = String.format("找不到指定的伺服器: %d", guildId);
      LOGGER.warn("LangChain4jListRolesTool: {}", errorMsg);
      return buildErrorResponse("找不到伺服器");
    }

    try {
      // 3. 收集角色資訊並按權限排序
      List<Role> roles = new ArrayList<>(guild.getRoles());
      roles.sort(Role::compareTo); // 使用 JDA 的 compareTo 方法按權限排序

      List<Map<String, Object>> roleInfos = new ArrayList<>();

      for (Role role : roles) {
        roleInfos.add(buildRoleInfo(role));
      }

      // 4. 返回 JSON 格式結果
      String jsonResult = buildJsonResult(roleInfos);
      LOGGER.info("LangChain4jListRolesTool: 找到 {} 個角色", roleInfos.size());
      return jsonResult;

    } catch (Exception e) {
      String errorMsg = String.format("獲取角色列表失敗: %s", e.getMessage());
      LOGGER.error("LangChain4jListRolesTool: {}", errorMsg, e);
      return buildErrorResponse(errorMsg);
    }
  }

  /**
   * 構建角色資訊 Map。
   *
   * @param role 角色
   * @return 角色資訊 Map
   */
  private Map<String, Object> buildRoleInfo(Role role) {
    Map<String, Object> info = new LinkedHashMap<>();
    info.put("id", role.getIdLong());
    info.put("name", role.getName());
    return info;
  }

  /**
   * 構建 JSON 格式結果。
   *
   * @param roleInfos 角色資訊列表
   * @return JSON 字串
   */
  private String buildJsonResult(List<Map<String, Object>> roleInfos) {
    StringBuilder json = new StringBuilder();
    json.append("{\n");
    json.append("  \"count\": ").append(roleInfos.size()).append(",\n");
    json.append("  \"roles\": [\n");

    for (int i = 0; i < roleInfos.size(); i++) {
      if (i > 0) {
        json.append(",\n");
      }
      Map<String, Object> info = roleInfos.get(i);
      json.append("    {\n");
      json.append("      \"id\": ").append(info.get("id")).append(",\n");
      json.append("      \"name\": \"").append(info.get("name")).append("\"");
      json.append("\n    }");
    }

    json.append("\n  ]\n");
    json.append("}");
    return json.toString();
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
        .formatted(error);
  }
}
