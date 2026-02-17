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
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

/**
 * 修改 Discord 頻道權限設定工具（LangChain4J 版本）。
 *
 * <p>使用 LangChain4J 的 @Tool 註解，通過 InvocationParameters 獲取執行上下文。
 */
public final class LangChain4jModifyChannelPermissionsTool {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(LangChain4jModifyChannelPermissionsTool.class);
  private static final int MAX_CHANNEL_NAME_LENGTH = 100;

  @Inject
  public LangChain4jModifyChannelPermissionsTool() {
    // JDA 將從 JDAProvider 延遲獲取
  }

  /**
   * 修改 Discord 頻道的權限覆寫設定。
   *
   * @param channelId 頻道 ID（字串格式，避免 JSON 精度損失）
   * @param targetId 目標 ID（用戶 ID 或角色 ID）
   * @param targetType 目標類型（"member" 或 "role"）
   * @param allowToAdd 要添加的允許權限列表（可選）
   * @param allowToRemove 要移除的允許權限列表（可選）
   * @param denyToAdd 要添加的拒絕權限列表（可選）
   * @param denyToRemove 要移除的拒絕權限列表（可選）
   * @param newName 新的頻道名稱（可選）
   * @param parameters 調用參數（包含 guildId、channelId、userId）
   * @return 修改結果 JSON 字串
   */
  @Tool(
      """
      修改 Discord 頻道的權限覆寫設定。

      使用場景：
      - 當需要為特定用戶或角色添加或移除頻道權限時使用
      - 當需要重新命名頻道時使用
      - 需要修改現有權限覆寫時使用
      - 批量修改多個權限時使用
      - 當用戶要求「禁止」、「不允許」、「拒絕」某個權限時使用

      返回資訊：
      - 修改是否成功
      - 是否更新了頻道名稱
      - 修改前後的權限對比
      - 頻道和目標資訊

      權限系統概念（重要）：
      Discord 權限系統有三種狀態：
      1. 明確允許（✓）：使用 allowToAdd 添加，該角色/用戶擁有此權限
      2. 明確拒絕（✗）：使用 denyToAdd 添加，該角色/用戶被禁止此權限（會覆蓋角色層級權限）
      3. 中立（/）：不設置，使用角色層級的預設權限

      參數使用指南：
      - allowToAdd: 當用戶說「允許」、「給予」、「添加」權限時使用
      - allowToRemove: 當用戶說「移除允許」（但不完全禁止）時使用
      - denyToAdd: 當用戶說「禁止」、「不允許」、「拒絕」、「阻止」權限時使用（這是積極禁止）
      - denyToRemove: 當用戶說「恢復」、「不再拒絕」權限時使用

      關鍵差異：
      - 「不授予」權限：不設置 allowToAdd（保持中立），但仍會使用角色層級權限
      - 「禁止」權限：設置 denyToAdd（明確拒絕），會覆蓋角色層級權限

      使用範例：
      - 「禁止普通成員發言」→ denyToAdd: ["MESSAGE_SEND"]（而非 allowToRemove）
      - 「只允許管理員發言」→ denyToAdd: ["MESSAGE_SEND"] for @everyone
      - 「允許管理員發言」→ allowToAdd: ["MESSAGE_SEND"] for 管理員角色

      重要限制：
      - 同一權限不能同時存在於「允許」和「拒絕」集合中
      - 拒絕權限優級高於允許權限
      """)
  public String modifyChannelSettings(
      @P(
              value =
                  """
                  要修改權限的頻道 ID。

                  必須是有效的 Discord 頻道 ID（字串格式）。

                  範例：
                  - "123456789012345678"：修改指定頻道的權限
                  """,
              required = true)
          String channelId,
      @P(
              value =
                  """
                  目標 ID（用戶 ID 或角色 ID）。

                  必須是有效的 Discord 用戶 ID 或角色 ID（字串格式）。

                  範例：
                  - "123456789012345678"：用戶 ID
                  - "987654321098765432"：角色 ID
                  """,
              required = true)
          String targetId,
      @P(
              value =
                  """
                  目標類型。

                  必須是 "member"（用戶）或 "role"（角色）。

                  範例：
                  - "member"：修改用戶權限
                  - "role"：修改角色權限（預設）
                  """,
              required = false)
          String targetType,
      @P(
              value =
                  """
                  要添加到「允許」集合的權限列表。

                  **當用戶說「允許」、「給予」、「添加」權限時使用**

                  這會明確允許該角色/用戶執行特定操作，即使角色層級沒有此權限。

                  權限名稱字串列表，支援的權限：
                  - ADMINISTRATOR: 管理員
                  - MANAGE_CHANNELS: 管理頻道
                  - MANAGE_ROLES: 管理角色
                  - MANAGE_SERVER: 管理伺服器
                  - VIEW_CHANNEL: 查看頻道
                  - MESSAGE_SEND: 發送訊息
                  - MESSAGE_HISTORY: 讀取訊息歷史
                  - MESSAGE_ATTACH_FILES: 附加檔案
                  - MESSAGE_EMBED_LINKS: 嵌入連結
                  - VOICE_CONNECT: 連結頻道
                  - VOICE_SPEAK: 說話
                  - PRIORITY_SPEAKER: 優先權
                  - MANAGE_MESSAGES: 管理訊息
                  - MESSAGE_READ: 讀取訊息
                  - MESSAGE_ADD_REACTION: 添加反應
                  - MESSAGE_EXT_EMOJI: 使用外部表情

                  範例：
                  - ["VIEW_CHANNEL", "MESSAGE_SEND"]：允許查看和發送訊息
                  - ["MESSAGE_SEND"]：允許發言（用於「允許某人發言」）
                  - []：不添加任何允許權限
                  """,
              required = false)
          List<String> allowToAdd,
      @P(
              value =
                  """
                  要從「允許」集合移除的權限列表。

                  **注意：移除允許 ≠ 禁止。若要禁止權限，請使用 denyToAdd**

                  當用戶說「移除允許」、「撤銷許可」時使用（但不是完全禁止）。

                  移除明確允許後，該角色/用戶仍可能透過角色層級獲得該權限。
                  這不會阻止他們執行操作，只是移除了頻道層級的特別許可。

                  權限名稱字串列表，格式同 allowToAdd。

                  範例：
                  - ["MESSAGE_SEND"]：移除發送訊息的明確允許（但若角色層級有此權限，仍可發言）
                  - []：不移除任何允許權限

                  對比：
                  - allowToRemove: 移除「特別許可」（角色層級權限仍有效）
                  - denyToAdd: 完全「禁止」該權限（覆蓋所有來源）
                  """,
              required = false)
          List<String> allowToRemove,
      @P(
              value =
                  """
                  要添加到「拒絕」集合的權限列表。

                  **重要：這是「禁止」權限的正確方式**

                  當用戶使用以下詞彙時，應使用此參數：
                  - 「禁止」、「不允許」、「拒絕」、「阻止」
                  - 「不能」、「無法」、「不讓」
                  - 「限制」、「封鎖」

                  Discord 權限系統中，明確拒絕（✗）會覆蓋角色層級的所有權限設定。
                  這是唯一能真正阻止普通成員執行某操作的方式。

                  權限名稱字串列表，格式同 allowToAdd。

                  範例：
                  - ["MESSAGE_SEND"]：禁止發送訊息（用於「禁止發言」）
                  - ["VIEW_CHANNEL"]：禁止查看頻道（用於「隱藏頻道」）
                  - ["VOICE_CONNECT"]：禁止連結語音頻道
                  - []：不添加任何拒絕權限

                  對比：
                  - 若只想「不給予」權限：不設置 allowToAdd（仍可能透過角色層級獲得）
                  - 若要「完全禁止」權限：設置 denyToAdd（會覆蓋所有來源的權限）
                  """,
              required = false)
          List<String> denyToAdd,
      @P(
              value =
                  """
                  要從「拒絕」集合移除的權限列表。

                  **當用戶說「恢復」、「不再拒絕」、「允許再次」權限時使用**

                  移除明確拒絕後，該角色/用戶將不再被禁止該權限，
                  會恢復使用角色層級的預設權限。

                  權限名稱字串列表，格式同 allowToAdd。

                  範例：
                  - ["MESSAGE_SEND"]：移除發送訊息的拒絕（恢復為角色層級權限）
                  - ["VOICE_CONNECT"]：移除連結語音頻道的拒絕
                  - []：不移除任何拒絕權限

                  對比：
                  - denyToRemove: 移除「禁止」標記（恢復預設行為）
                  - allowToAdd: 主動給予「許可」（明確允許）
                  """,
              required = false)
          List<String> denyToRemove,
      @P(value = "新的頻道名稱（可選，最多 100 字）", required = false) String newName,
      InvocationParameters parameters) {

    // 1. 驗證必要參數
    if (channelId == null || channelId.isBlank()) {
      return buildErrorResponse("channelId 未提供");
    }

    String normalizedName = normalizeName(newName);
    if (newName != null && normalizedName == null) {
      return buildErrorResponse("新的頻道名稱不能為空白");
    }
    if (normalizedName != null && normalizedName.length() > MAX_CHANNEL_NAME_LENGTH) {
      return buildErrorResponse(
          String.format("頻道名稱不能超過 %d 字（當前: %d）", MAX_CHANNEL_NAME_LENGTH, normalizedName.length()));
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
    long channelIdLong;
    Long targetIdLong = null;
    String resolvedTargetType = targetType;
    try {
      channelIdLong = parseId(channelId);
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
            parameters, guild, LOGGER, "LangChain4jModifyChannelPermissionsTool");
    if (authorizationError != null) {
      return buildErrorResponse(authorizationError);
    }

    // 4. 獲取頻道
    GuildChannel channel = guild.getGuildChannelById(channelIdLong);
    if (channel == null) {
      return buildErrorResponse("找不到頻道");
    }

    // 權限修改時才需要檢查頻道是否支持權限覆寫
    if (hasPermissionChanges && !(channel instanceof IPermissionContainer)) {
      return buildErrorResponse("頻道類型不支持權限覆寫");
    }
    IPermissionContainer permissionContainer =
        hasPermissionChanges ? (IPermissionContainer) channel : null;

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
        // 6. 獲取現有權限覆寫（通過遍歷找到匹配的）
        PermissionOverride existingOverride = null;
        for (PermissionOverride override : permissionContainer.getPermissionOverrides()) {
          if (override.getIdLong() == targetIdLong) {
            existingOverride = override;
            break;
          }
        }

        // 獲取現有的允許和拒絕權限
        EnumSet<Permission> currentAllowed =
            existingOverride != null
                ? existingOverride.getAllowed()
                : EnumSet.noneOf(Permission.class);
        EnumSet<Permission> currentDenied =
            existingOverride != null
                ? existingOverride.getDenied()
                : EnumSet.noneOf(Permission.class);

        // 記錄修改前的權限
        beforeAllowed = permissionListToString(currentAllowed);
        beforeDenied = permissionListToString(currentDenied);

        // 7. 計算新的權限集合
        EnumSet<Permission> newAllowed = currentAllowed.clone();
        EnumSet<Permission> newDenied = currentDenied.clone();

        // 處理允許權限的添加
        if (allowToAdd != null && !allowToAdd.isEmpty()) {
          EnumSet<Permission> toAdd = parsePermissionList(allowToAdd);
          for (Permission perm : toAdd) {
            // 從拒絕集合中移除（如果存在）
            newDenied.remove(perm);
            // 添加到允許集合
            newAllowed.add(perm);
          }
        }

        // 處理允許權限的移除
        if (allowToRemove != null && !allowToRemove.isEmpty()) {
          EnumSet<Permission> toRemove = parsePermissionList(allowToRemove);
          newAllowed.removeAll(toRemove);
        }

        // 處理拒絕權限的添加
        if (denyToAdd != null && !denyToAdd.isEmpty()) {
          EnumSet<Permission> toAdd = parsePermissionList(denyToAdd);
          for (Permission perm : toAdd) {
            // 從允許集合中移除（如果存在）
            newAllowed.remove(perm);
            // 添加到拒絕集合
            newDenied.add(perm);
          }
        }

        // 處理拒絕權限的移除
        if (denyToRemove != null && !denyToRemove.isEmpty()) {
          EnumSet<Permission> toRemove = parsePermissionList(denyToRemove);
          newDenied.removeAll(toRemove);
        }

        // 8. 應用權限修改
        if (isMember) {
          permissionContainer
              .upsertPermissionOverride(targetMember)
              .setPermissions(newAllowed, newDenied)
              .complete();
        } else {
          permissionContainer
              .upsertPermissionOverride(targetRole)
              .setPermissions(newAllowed, newDenied)
              .complete();
        }

        // 記錄修改後的權限
        afterAllowed = permissionListToString(newAllowed);
        afterDenied = permissionListToString(newDenied);
      }

      if (hasRename) {
        channel.getManager().setName(normalizedName).complete();
      }

      LOGGER.info(
          "修改頻道設定: channelId={}, renamed={}, permissionsUpdated={}",
          channel.getIdLong(),
          hasRename,
          hasPermissionChanges);

      // 9. 返回成功結果
      return buildSuccessResponse(
          channel.getIdLong(),
          hasRename ? normalizedName : channel.getName(),
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
      LOGGER.error("修改頻道權限失敗", e);
      return buildErrorResponse("修改失敗: " + e.getMessage());
    }
  }

  /**
   * 解析 ID（支援字串和數字格式）。
   *
   * @param id ID 字串
   * @return 解析後的 long 值
   */
  private long parseId(String id) {
    String trimmed = id.trim();
    // 移除可能的 <#>、<@>、<@&> 和 <> 標記
    if (trimmed.startsWith("<@&") && trimmed.endsWith(">")) {
      trimmed = trimmed.substring(3, trimmed.length() - 1);
    } else if (trimmed.startsWith("<@") && trimmed.endsWith(">")) {
      trimmed = trimmed.substring(2, trimmed.length() - 1);
    } else if (trimmed.startsWith("<#") && trimmed.endsWith(">")) {
      trimmed = trimmed.substring(2, trimmed.length() - 1);
    } else if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
      trimmed = trimmed.substring(1, trimmed.length() - 1);
    }
    return Long.parseLong(trimmed);
  }

  /**
   * 解析權限列表字串為 EnumSet。
   *
   * @param permissionList 權限名稱列表
   * @return 權限 EnumSet
   */
  private EnumSet<Permission> parsePermissionList(List<String> permissionList) {
    EnumSet<Permission> permissions = EnumSet.noneOf(Permission.class);
    for (String permName : permissionList) {
      try {
        Permission perm = Permission.valueOf(permName.toUpperCase().trim());
        permissions.add(perm);
      } catch (IllegalArgumentException e) {
        LOGGER.warn("無效的權限名稱: {}", permName);
        // 忽略無效的權限名稱
      }
    }
    return permissions;
  }

  /**
   * 將 EnumSet<Permission> 轉換為可讀的字串列表。
   *
   * @param permissions 權限 EnumSet
   * @return 權限名稱列表
   */
  private List<String> permissionListToString(EnumSet<Permission> permissions) {
    if (permissions == null || permissions.isEmpty()) {
      return List.of();
    }
    return permissions.stream().map(Permission::name).sorted().collect(Collectors.toList());
  }

  /**
   * 構建成功回應。
   *
   * @param channelId 頻道 ID
   * @param channelName 頻道名稱
   * @param renamed 是否更新了名稱
   * @param permissionsUpdated 是否更新了權限
   * @param targetId 目標 ID
   * @param targetType 目標類型
   * @param beforeAllowed 修改前的允許權限
   * @param beforeDenied 修改前的拒絕權限
   * @param afterAllowed 修改後的允許權限
   * @param afterDenied 修改後的拒絕權限
   * @return JSON 格式的成功回應
   */
  private String buildSuccessResponse(
      long channelId,
      String channelName,
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
    json.append("  \"channelId\": \"").append(channelId).append("\",\n");
    json.append("  \"channelName\": \"").append(escapeJson(channelName)).append("\",\n");
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
      return "頻道名稱與權限修改成功";
    }
    if (renamed) {
      return "頻道名稱修改成功";
    }
    return "頻道權限修改成功";
  }

  private String normalizeName(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  /**
   * 將權限列表轉換為 JSON 陣列字串。
   *
   * @param permissions 權限列表
   * @return JSON 陣列字串
   */
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
