package ltdjms.discord.aiagent.services.tools;

import java.util.EnumSet;
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
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

/** 修改 Discord 角色權限工具（LangChain4J 版本）。 */
public final class LangChain4jModifyRolePermissionsTool {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(LangChain4jModifyRolePermissionsTool.class);

  @Inject
  public LangChain4jModifyRolePermissionsTool() {
    // JDA 將從 JDAProvider 延遲獲取
  }

  @Tool(
      """
      修改 Discord 角色的伺服器層級權限。

      使用場景：
      - 當需要添加或移除角色的伺服器權限時使用
      - 調整角色權限等級時使用

      注意：
      - 這會修改角色本身的基本權限
      - 不是修改頻道層級的權限覆寫
      - 權限修改是基於現有權限的增量操作
      """)
  public String modifyRolePermissions(
      @P(value = "角色 ID", required = true) String roleId,
      @P(value = "要添加的權限列表", required = false) List<String> permissionsToAdd,
      @P(value = "要移除的權限列表", required = false) List<String> permissionsToRemove,
      InvocationParameters parameters) {

    // 1. 驗證必要參數
    if (roleId == null || roleId.isBlank()) {
      return buildErrorResponse("roleId 未提供");
    }

    // 解析 ID
    long roleIdLong;
    try {
      roleIdLong = parseId(roleId);
    } catch (NumberFormatException e) {
      return buildErrorResponse("無效的 ID 格式");
    }

    // 檢查是否有任何權限操作
    boolean hasChanges =
        (permissionsToAdd != null && !permissionsToAdd.isEmpty())
            || (permissionsToRemove != null && !permissionsToRemove.isEmpty());

    if (!hasChanges) {
      return buildErrorResponse("未指定任何權限修改操作");
    }

    // 2. 從 InvocationParameters 獲取執行上下文
    Long guildId = parameters.get("guildId");
    if (guildId == null) {
      return buildErrorResponse("guildId 未設置");
    }

    // 3. 獲取 Guild
    Guild guild = JDAProvider.getJda().getGuildById(guildId);
    if (guild == null) {
      return buildErrorResponse("找不到伺服器");
    }

    String authorizationError =
        ToolCallerAuthorizationGuard.validateAdministrator(
            parameters, guild, LOGGER, "LangChain4jModifyRolePermissionsTool");
    if (authorizationError != null) {
      return buildErrorResponse(authorizationError);
    }

    // 4. 獲取角色
    Role role = guild.getRoleById(roleIdLong);
    if (role == null) {
      return buildErrorResponse("找不到指定的角色");
    }

    try {
      // 5. 獲取現有權限
      EnumSet<Permission> currentPermissions = EnumSet.copyOf(role.getPermissions());
      List<String> beforePermissions = permissionListToString(currentPermissions);

      // 6. 計算新權限
      EnumSet<Permission> newPermissions = currentPermissions.clone();

      if (permissionsToAdd != null && !permissionsToAdd.isEmpty()) {
        for (String permName : permissionsToAdd) {
          try {
            Permission perm = Permission.valueOf(permName.toUpperCase().trim());
            newPermissions.add(perm);
          } catch (IllegalArgumentException e) {
            LOGGER.warn("無效的權限名稱: {}", permName);
          }
        }
      }

      if (permissionsToRemove != null && !permissionsToRemove.isEmpty()) {
        for (String permName : permissionsToRemove) {
          try {
            Permission perm = Permission.valueOf(permName.toUpperCase().trim());
            newPermissions.remove(perm);
          } catch (IllegalArgumentException e) {
            LOGGER.warn("無效的權限名稱: {}", permName);
          }
        }
      }

      List<String> afterPermissions = permissionListToString(newPermissions);

      // 7. 應用修改
      role.getManager().setPermissions(newPermissions).complete();

      LOGGER.info(
          "修改角色權限: roleId={}, before={}, after={}", roleIdLong, currentPermissions, newPermissions);

      return buildSuccessResponse(
          role,
          role.getPermissionsRaw(),
          Permission.getRaw(newPermissions),
          beforePermissions,
          afterPermissions);

    } catch (InsufficientPermissionException e) {
      LOGGER.warn("權限不足: {}", e.getMessage());
      return buildErrorResponse("權限不足: " + e.getMessage());

    } catch (Exception e) {
      LOGGER.error("修改角色權限失敗", e);
      return buildErrorResponse("修改失敗: " + e.getMessage());
    }
  }

  private long parseId(String id) {
    String trimmed = id.trim();
    if (trimmed.startsWith("<@&") && trimmed.endsWith(">")) {
      trimmed = trimmed.substring(3, trimmed.length() - 1);
    }
    return Long.parseLong(trimmed);
  }

  private List<String> permissionListToString(EnumSet<Permission> permissions) {
    if (permissions == null || permissions.isEmpty()) {
      return List.of();
    }
    return permissions.stream().map(Permission::name).sorted().collect(Collectors.toList());
  }

  private String buildSuccessResponse(
      Role role,
      long beforeRaw,
      long afterRaw,
      List<String> beforePermissions,
      List<String> afterPermissions) {
    StringBuilder json = new StringBuilder();
    json.append("{\n");
    json.append("  \"success\": true,\n");
    json.append("  \"message\": \"角色權限修改成功\",\n");
    json.append("  \"role\": {\n");
    json.append("    \"id\": \"").append(role.getIdLong()).append("\",\n");
    json.append("    \"name\": \"").append(escapeJson(role.getName())).append("\"\n");
    json.append("  },\n");
    json.append("  \"before\": {\n");
    json.append("    \"permissions\": ")
        .append(permissionListToJson(beforePermissions))
        .append(",\n");
    json.append("    \"count\": ").append(beforePermissions.size()).append(",\n");
    json.append("    \"raw\": ").append(beforeRaw).append("\n");
    json.append("  },\n");
    json.append("  \"after\": {\n");
    json.append("    \"permissions\": ")
        .append(permissionListToJson(afterPermissions))
        .append(",\n");
    json.append("    \"count\": ").append(afterPermissions.size()).append(",\n");
    json.append("    \"raw\": ").append(afterRaw).append("\n");
    json.append("  },\n");
    json.append("  \"changes\": {\n");
    json.append("    \"added\": [],\n");
    json.append("    \"removed\": [],\n");
    json.append("    \"addedCount\": 0,\n");
    json.append("    \"removedCount\": 0\n");
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

  private String escapeJson(String value) {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }

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
