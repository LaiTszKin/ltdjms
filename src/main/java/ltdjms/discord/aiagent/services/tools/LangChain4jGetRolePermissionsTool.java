package ltdjms.discord.aiagent.services.tools;

import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import ltdjms.discord.shared.di.JDAProvider;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;

/** 讀取 Discord 角色權限工具（LangChain4J 版本）。 */
public final class LangChain4jGetRolePermissionsTool {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(LangChain4jGetRolePermissionsTool.class);

  @Inject
  public LangChain4jGetRolePermissionsTool() {
    // JDA 將從 JDAProvider 延遲獲取
  }

  @Tool(
      """
      讀取 Discord 角色的伺服器層級權限。

      使用場景：
      - 當需要查看角色擁有的權限時使用
      - 權限審查或問題排查時使用

      返回資訊：
      - 角色基本資訊
      - 完整的伺服器層級權限列表
      - 是否為管理員角色
      """)
  public String getRolePermissions(
      @P(value = "角色 ID", required = true) String roleId, InvocationParameters parameters) {

    // 1. 驗證必要參數
    if (roleId == null || roleId.isBlank()) {
      return ToolJsonResponses.error("roleId 未提供");
    }

    // 解析 ID
    long roleIdLong;
    try {
      roleIdLong = parseId(roleId);
    } catch (NumberFormatException e) {
      return ToolJsonResponses.error("無效的 ID 格式");
    }

    // 2. 從 InvocationParameters 獲取執行上下文
    Long guildId = parameters.get("guildId");
    if (guildId == null) {
      return ToolJsonResponses.error("guildId 未設置");
    }

    // 3. 獲取 Guild
    Guild guild = JDAProvider.getJda().getGuildById(guildId);
    if (guild == null) {
      return ToolJsonResponses.error("找不到伺服器");
    }

    String authorizationError =
        ToolCallerAuthorizationGuard.validateAdministrator(
            parameters, guild, LOGGER, "LangChain4jGetRolePermissionsTool");
    if (authorizationError != null) {
      return ToolJsonResponses.error(authorizationError);
    }

    // 4. 獲取角色
    Role role = guild.getRoleById(roleIdLong);
    if (role == null) {
      return ToolJsonResponses.error("找不到指定的角色");
    }

    try {
      LOGGER.info("讀取角色權限: guildId={}, roleId={}", guildId, roleIdLong);
      return buildSuccessResponse(role);

    } catch (Exception e) {
      LOGGER.error("讀取角色權限失敗", e);
      return ToolJsonResponses.error("讀取失敗: " + e.getMessage());
    }
  }

  private long parseId(String id) {
    String trimmed = id.trim();
    if (trimmed.startsWith("<@&") && trimmed.endsWith(">")) {
      trimmed = trimmed.substring(3, trimmed.length() - 1);
    }
    return Long.parseLong(trimmed);
  }

  private String buildSuccessResponse(Role role) {
    StringBuilder json = new StringBuilder();
    json.append("{\n");
    json.append("  \"success\": true,\n");
    json.append("  \"role\": {\n");
    json.append("    \"id\": \"").append(role.getIdLong()).append("\",\n");
    json.append("    \"name\": \"")
        .append(ToolJsonResponses.escapeJson(role.getName()))
        .append("\",\n");
    json.append("    \"color\": ").append(role.getColorRaw()).append(",\n");
    json.append("    \"colorHex\": \"#")
        .append(String.format("%06X", role.getColorRaw()))
        .append("\",\n");
    json.append("    \"hoisted\": ").append(role.isHoisted()).append(",\n");
    json.append("    \"mentionable\": ").append(role.isMentionable()).append(",\n");
    json.append("    \"position\": ").append(role.getPosition()).append(",\n");
    json.append("    \"managed\": ").append(role.isManaged()).append(",\n");

    List<String> permissions =
        role.getPermissions().stream().map(Permission::name).sorted().collect(Collectors.toList());
    json.append("    \"permissions\": ").append(permissionListToJson(permissions)).append(",\n");
    json.append("    \"permissionCount\": ").append(permissions.size()).append(",\n");

    boolean isAdmin = role.getPermissions().contains(Permission.ADMINISTRATOR);
    json.append("    \"isAdmin\": ").append(isAdmin).append("\n");
    json.append("  }\n");
    json.append("}");
    return json.toString();
  }

  private String permissionListToJson(List<String> permissions) {
    if (permissions == null || permissions.isEmpty()) {
      return "[]";
    }
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    for (int i = 0; i < permissions.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append("\"").append(permissions.get(i)).append("\"");
    }
    sb.append("]");
    return sb.toString();
  }
}
