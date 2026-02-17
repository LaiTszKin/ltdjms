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
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

/** 修改 Discord 類別的權限覆寫設定工具（LangChain4J 版本）。 */
public final class LangChain4jModifyCategoryPermissionsTool {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(LangChain4jModifyCategoryPermissionsTool.class);
  private static final int MAX_CATEGORY_NAME_LENGTH = 100;

  @Inject
  public LangChain4jModifyCategoryPermissionsTool() {
    // JDA 將從 JDAProvider 延遲獲取
  }

  @Tool(
      """
      修改 Discord 類別的權限覆寫設定。

      使用場景：
      - 當需要為特定用戶或角色添加或移除類別權限時使用
      - 當需要重新命名類別時使用
      - 需要修改現有類別權限覆寫時使用
      - 批量修改多個權限時使用

      返回資訊：
      - 修改是否成功
      - 是否更新了類別名稱
      - 修改前後的權限對比
      - 類別和目標資訊

      重要限制：
      - 同一權限不能同時存在於「允許」和「拒絕」集合中
      - 拒絕權限優級高於允許權限
      """)
  public String modifyCategorySettings(
      @P(value = "要修改權限的類別 ID", required = true) String categoryId,
      @P(value = "目標 ID（用戶 ID 或角色 ID）", required = false) String targetId,
      @P(value = "目標類型（member 或 role）", required = false) String targetType,
      @P(value = "要添加的允許權限列表", required = false) List<String> allowToAdd,
      @P(value = "要移除的允許權限列表", required = false) List<String> allowToRemove,
      @P(value = "要添加的拒絕權限列表", required = false) List<String> denyToAdd,
      @P(value = "要移除的拒絕權限列表", required = false) List<String> denyToRemove,
      @P(value = "新的類別名稱（可選，最多 100 字）", required = false) String newName,
      InvocationParameters parameters) {

    // 1. 驗證必要參數
    if (categoryId == null || categoryId.isBlank()) {
      return buildErrorResponse("categoryId 未提供");
    }

    String normalizedName = normalizeName(newName);
    if (newName != null && normalizedName == null) {
      return buildErrorResponse("新的類別名稱不能為空白");
    }
    if (normalizedName != null && normalizedName.length() > MAX_CATEGORY_NAME_LENGTH) {
      return buildErrorResponse(
          String.format(
              "類別名稱不能超過 %d 字（當前: %d）", MAX_CATEGORY_NAME_LENGTH, normalizedName.length()));
    }

    // 檢查是否有任何修改操作
    boolean hasPermissionChanges =
        (allowToAdd != null && !allowToAdd.isEmpty())
            || (allowToRemove != null && !allowToRemove.isEmpty())
            || (denyToAdd != null && !denyToAdd.isEmpty())
            || (denyToRemove != null && !denyToRemove.isEmpty());
    boolean hasRename = normalizedName != null;

    if (!hasPermissionChanges && !hasRename) {
      return buildErrorResponse("未指定任何權限或名稱修改操作");
    }

    // 解析 ID
    long categoryIdLong;
    Long targetIdLong = null;
    String resolvedTargetType = targetType;
    try {
      categoryIdLong = parseId(categoryId);
      if (hasPermissionChanges) {
        if (targetId == null || targetId.isBlank()) {
          return buildErrorResponse("targetId 未提供");
        }
        targetIdLong = parseId(targetId);
      }
    } catch (NumberFormatException e) {
      return buildErrorResponse("無效的 ID 格式");
    }

    if (hasPermissionChanges) {
      if (resolvedTargetType == null || resolvedTargetType.isBlank()) {
        resolvedTargetType = "role";
      }
      if (!resolvedTargetType.equals("member") && !resolvedTargetType.equals("role")) {
        return buildErrorResponse("targetType 必須是 'member' 或 'role'");
      }
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
            parameters, guild, LOGGER, "LangChain4jModifyCategoryPermissionsTool");
    if (authorizationError != null) {
      return buildErrorResponse(authorizationError);
    }

    // 4. 獲取類別
    Category category = guild.getCategoryById(categoryIdLong);
    if (category == null) {
      return buildErrorResponse("找不到指定的類別");
    }

    // 5. 獲取或驗證目標（用戶或角色）
    boolean isMember = "member".equals(resolvedTargetType);
    Member targetMember = null;
    Role targetRole = null;
    if (hasPermissionChanges) {
      if (isMember) {
        targetMember = guild.getMemberById(targetIdLong);
        if (targetMember == null) {
          return buildErrorResponse("找不到指定的用戶");
        }
      } else {
        targetRole = guild.getRoleById(targetIdLong);
        if (targetRole == null) {
          return buildErrorResponse("找不到指定的角色");
        }
      }
    }

    try {
      List<String> beforeAllowed = List.of();
      List<String> beforeDenied = List.of();
      List<String> afterAllowed = List.of();
      List<String> afterDenied = List.of();

      if (hasPermissionChanges) {
        // 6. 獲取現有權限覆寫
        PermissionOverride existingOverride = null;
        for (PermissionOverride override : category.getPermissionOverrides()) {
          if (override.getIdLong() == targetIdLong) {
            existingOverride = override;
            break;
          }
        }

        EnumSet<Permission> currentAllowed =
            existingOverride != null
                ? existingOverride.getAllowed()
                : EnumSet.noneOf(Permission.class);
        EnumSet<Permission> currentDenied =
            existingOverride != null
                ? existingOverride.getDenied()
                : EnumSet.noneOf(Permission.class);

        beforeAllowed = permissionListToString(currentAllowed);
        beforeDenied = permissionListToString(currentDenied);

        // 7. 計算新的權限集合
        EnumSet<Permission> newAllowed = currentAllowed.clone();
        EnumSet<Permission> newDenied = currentDenied.clone();

        if (allowToAdd != null && !allowToAdd.isEmpty()) {
          EnumSet<Permission> toAdd = parsePermissionList(allowToAdd);
          for (Permission perm : toAdd) {
            newDenied.remove(perm);
            newAllowed.add(perm);
          }
        }

        if (allowToRemove != null && !allowToRemove.isEmpty()) {
          EnumSet<Permission> toRemove = parsePermissionList(allowToRemove);
          newAllowed.removeAll(toRemove);
        }

        if (denyToAdd != null && !denyToAdd.isEmpty()) {
          EnumSet<Permission> toAdd = parsePermissionList(denyToAdd);
          for (Permission perm : toAdd) {
            newAllowed.remove(perm);
            newDenied.add(perm);
          }
        }

        if (denyToRemove != null && !denyToRemove.isEmpty()) {
          EnumSet<Permission> toRemove = parsePermissionList(denyToRemove);
          newDenied.removeAll(toRemove);
        }

        // 8. 應用權限修改
        if (isMember) {
          category
              .upsertPermissionOverride(targetMember)
              .setPermissions(newAllowed, newDenied)
              .complete();
        } else {
          category
              .upsertPermissionOverride(targetRole)
              .setPermissions(newAllowed, newDenied)
              .complete();
        }

        afterAllowed = permissionListToString(newAllowed);
        afterDenied = permissionListToString(newDenied);
      }

      if (hasRename) {
        category.getManager().setName(normalizedName).complete();
      }

      LOGGER.info(
          "修改類別設定: categoryId={}, renamed={}, permissionsUpdated={}",
          category.getIdLong(),
          hasRename,
          hasPermissionChanges);

      return buildSuccessResponse(
          category.getIdLong(),
          hasRename ? normalizedName : category.getName(),
          hasRename,
          hasPermissionChanges,
          targetIdLong,
          resolvedTargetType,
          beforeAllowed,
          beforeDenied,
          afterAllowed,
          afterDenied);

    } catch (InsufficientPermissionException e) {
      LOGGER.warn("權限不足: {}", e.getMessage());
      return buildErrorResponse("權限不足: " + e.getMessage());

    } catch (Exception e) {
      LOGGER.error("修改類別權限失敗", e);
      return buildErrorResponse("修改失敗: " + e.getMessage());
    }
  }

  private long parseId(String id) {
    String trimmed = id.trim();
    if (trimmed.startsWith("<@&") && trimmed.endsWith(">")) {
      trimmed = trimmed.substring(3, trimmed.length() - 1);
    } else if (trimmed.startsWith("<@") && trimmed.endsWith(">")) {
      trimmed = trimmed.substring(2, trimmed.length() - 1);
    }
    return Long.parseLong(trimmed);
  }

  private EnumSet<Permission> parsePermissionList(List<String> permissionList) {
    EnumSet<Permission> permissions = EnumSet.noneOf(Permission.class);
    for (String permName : permissionList) {
      try {
        Permission perm = Permission.valueOf(permName.toUpperCase().trim());
        permissions.add(perm);
      } catch (IllegalArgumentException e) {
        LOGGER.warn("無效的權限名稱: {}", permName);
      }
    }
    return permissions;
  }

  private List<String> permissionListToString(EnumSet<Permission> permissions) {
    if (permissions == null || permissions.isEmpty()) {
      return List.of();
    }
    return permissions.stream().map(Permission::name).sorted().collect(Collectors.toList());
  }

  private String buildSuccessResponse(
      long categoryId,
      String categoryName,
      boolean renamed,
      boolean permissionsUpdated,
      Long targetId,
      String targetType,
      List<String> beforeAllowed,
      List<String> beforeDenied,
      List<String> afterAllowed,
      List<String> afterDenied) {
    StringBuilder json = new StringBuilder();
    json.append("{\n");
    json.append("  \"success\": true,\n");
    json.append("  \"message\": \"")
        .append(buildSuccessMessage(renamed, permissionsUpdated))
        .append("\",\n");
    json.append("  \"categoryId\": \"").append(categoryId).append("\",\n");
    json.append("  \"categoryName\": \"").append(escapeJson(categoryName)).append("\",\n");
    json.append("  \"renamed\": ").append(renamed).append(",\n");
    json.append("  \"permissionsUpdated\": ").append(permissionsUpdated);
    if (permissionsUpdated) {
      json.append(",\n");
      json.append("  \"targetId\": \"").append(targetId).append("\",\n");
      json.append("  \"targetType\": \"").append(targetType).append("\",\n");
      json.append("  \"before\": {\n");
      json.append("    \"allowed\": ").append(permissionListToJson(beforeAllowed)).append(",\n");
      json.append("    \"denied\": ").append(permissionListToJson(beforeDenied)).append("\n");
      json.append("  },\n");
      json.append("  \"after\": {\n");
      json.append("    \"allowed\": ").append(permissionListToJson(afterAllowed)).append(",\n");
      json.append("    \"denied\": ").append(permissionListToJson(afterDenied)).append("\n");
      json.append("  }\n");
    } else {
      json.append("\n");
    }
    json.append("}");
    return json.toString();
  }

  private String buildSuccessMessage(boolean renamed, boolean permissionsUpdated) {
    if (renamed && permissionsUpdated) {
      return "類別名稱與權限修改成功";
    }
    if (renamed) {
      return "類別名稱修改成功";
    }
    return "類別權限修改成功";
  }

  private String normalizeName(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
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
