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

/** 創建 Discord 角色工具（LangChain4J 版本）。 */
public final class LangChain4jCreateRoleTool {

  private static final Logger LOGGER = LoggerFactory.getLogger(LangChain4jCreateRoleTool.class);

  @Inject
  public LangChain4jCreateRoleTool() {
    // JDA 將從 JDAProvider 延遲獲取
  }

  @Tool(
      """
      創建新的 Discord 身分組（角色）。

      使用場景：
      - 當需要創建新的角色分組時使用
      - 設置特定權限的角色時使用

      返回資訊：
      - 新創建角色的完整資訊
      - 包括角色 ID（可用於後續操作）
      """)
  public String createRole(
      @P(value = "角色名稱", required = true) String name,
      @P(value = "顏色（RGB 十六進制字串，例如 FF0000 為紅色）", required = false) String color,
      @P(value = "權限列表", required = false) List<String> permissions,
      @P(value = "是否分隔顯示（將角色在成員列表中單獨顯示）", required = false) Boolean hoist,
      @P(value = "是否可提及（允許 @role）", required = false) Boolean mentionable,
      InvocationParameters parameters) {

    // 1. 驗證必要參數
    if (name == null || name.isBlank()) {
      return ToolJsonResponses.error("角色名稱不能為空");
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
            parameters, guild, LOGGER, "LangChain4jCreateRoleTool");
    if (authorizationError != null) {
      return ToolJsonResponses.error(authorizationError);
    }

    try {
      // 4. 解析顏色
      int colorInt = 0;
      if (color != null && !color.isBlank()) {
        try {
          colorInt = Integer.parseInt(color.trim(), 16);
        } catch (NumberFormatException e) {
          LOGGER.warn("無效的顏色格式: {}, 使用預設值", color);
        }
      }

      // 5. 解析權限
      EnumSet<Permission> permissionSet = EnumSet.noneOf(Permission.class);
      if (permissions != null && !permissions.isEmpty()) {
        for (String permName : permissions) {
          try {
            Permission perm = Permission.valueOf(permName.toUpperCase().trim());
            permissionSet.add(perm);
          } catch (IllegalArgumentException e) {
            LOGGER.warn("無效的權限名稱: {}", permName);
          }
        }
      }

      // 6. 創建角色
      boolean hoistValue = hoist != null && hoist;
      boolean mentionableValue = mentionable != null && mentionable;

      Role role =
          guild
              .createRole()
              .setName(name.trim())
              .setColor(colorInt)
              .setPermissions(permissionSet)
              .setHoisted(hoistValue)
              .setMentionable(mentionableValue)
              .complete();

      LOGGER.info(
          "創建角色成功: guildId={}, roleId={}, name={}", guildId, role.getIdLong(), role.getName());

      return buildSuccessResponse(role);

    } catch (InsufficientPermissionException e) {
      LOGGER.warn("權限不足: {}", e.getMessage());
      return ToolJsonResponses.error("權限不足: " + e.getMessage());

    } catch (Exception e) {
      LOGGER.error("創建角色失敗", e);
      return ToolJsonResponses.error("創建失敗: " + e.getMessage());
    }
  }

  private String buildSuccessResponse(Role role) {
    StringBuilder json = new StringBuilder();
    json.append("{\n");
    json.append("  \"success\": true,\n");
    json.append("  \"message\": \"角色創建成功\",\n");
    json.append("  \"role\": {\n");
    json.append("    \"id\": \"").append(role.getIdLong()).append("\",\n");
    json.append("    \"name\": \"")
        .append(ToolJsonResponses.escapeJson(role.getName()))
        .append("\",\n");
    json.append("    \"color\": ").append(role.getColorRaw()).append(",\n");
    json.append("    \"colorHex\": \"#")
        .append(String.format("%06X", role.getColorRaw()))
        .append("\",\n");

    List<String> permissions =
        role.getPermissions().stream().map(Permission::name).sorted().collect(Collectors.toList());
    json.append("    \"permissions\": ").append(permissionListToJson(permissions)).append(",\n");
    json.append("    \"permissionCount\": ").append(permissions.size()).append(",\n");
    json.append("    \"hoisted\": ").append(role.isHoisted()).append(",\n");
    json.append("    \"mentionable\": ").append(role.isMentionable()).append(",\n");
    json.append("    \"position\": ").append(role.getPosition()).append("\n");
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
