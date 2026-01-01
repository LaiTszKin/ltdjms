package ltdjms.discord.aiagent.services;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import ltdjms.discord.aiagent.domain.ChannelPermission;
import ltdjms.discord.aiagent.domain.PermissionSetting;
import ltdjms.discord.aiagent.domain.PermissionSetting.PermissionEnum;
import net.dv8tion.jda.api.Permission;

/**
 * 權限描述解析器。
 *
 * <p>將 AI 傳來的權限描述轉換為 Discord 權限設定。支援自然語言描述和結構化權限定義。
 */
public final class PermissionParser {

  /** 預設權限（唯讀） */
  private static final EnumSet<Permission> DEFAULT_PERMISSIONS =
      EnumSet.of(Permission.VIEW_CHANNEL);

  /** 權限關鍵字映射 */
  private static final Map<String, Permission> KEYWORD_MAP =
      Map.ofEntries(
          Map.entry("view", Permission.VIEW_CHANNEL),
          Map.entry("read", Permission.VIEW_CHANNEL),
          Map.entry("read_history", Permission.MESSAGE_HISTORY),
          Map.entry("write", Permission.MESSAGE_SEND),
          Map.entry("send", Permission.MESSAGE_SEND),
          Map.entry("attach", Permission.MESSAGE_ATTACH_FILES),
          Map.entry("embed", Permission.MESSAGE_EMBED_LINKS),
          Map.entry("speak", Permission.VOICE_SPEAK),
          Map.entry("connect", Permission.VOICE_CONNECT),
          Map.entry("manage", Permission.MANAGE_CHANNEL),
          Map.entry("admin", Permission.ADMINISTRATOR),
          Map.entry("moderator", Permission.MODERATE_MEMBERS),
          Map.entry("mod", Permission.MODERATE_MEMBERS));

  private PermissionParser() {
    // 工具類，不允許實例化
  }

  /**
   * 從權限描述解析為 ChannelPermission 列表。
   *
   * <p>支援多種格式：
   *
   * <ul>
   *   <li>"read-only" 或 "readonly": 唯讀權限
   *   <li>"full" 或 "all": 完整權限
   *   <li>"moderator": 管理員權限
   *   <li>Map 結構: {"roleId": 123, "permissions": ["view", "write"]}
   * </ul>
   *
   * @param permissionsData 權限資料（字串或列表）
   * @param roleId 角色 ID
   * @return 權限配置
   */
  public static ChannelPermission parse(Object permissionsData, long roleId) {
    if (permissionsData instanceof String description) {
      return parseFromDescription(description, roleId);
    }

    if (permissionsData instanceof List<?> list) {
      return parseFromList(list, roleId);
    }

    if (permissionsData instanceof Map<?, ?> map) {
      return parseFromMap(map, roleId);
    }

    // 預設唯讀
    return ChannelPermission.readOnly(roleId);
  }

  /**
   * 從自然語言描述解析權限。
   *
   * @param description 權限描述
   * @param roleId 角色 ID
   * @return 權限配置
   */
  private static ChannelPermission parseFromDescription(String description, long roleId) {
    String lowerDesc = description.toLowerCase().trim();

    if (lowerDesc.contains("full") || lowerDesc.contains("all")) {
      return ChannelPermission.fullAccess(roleId);
    }

    if (lowerDesc.contains("read") && lowerDesc.contains("only")) {
      return ChannelPermission.readOnly(roleId);
    }

    if (lowerDesc.contains("moderator") || lowerDesc.contains("mod")) {
      return new ChannelPermission(
          roleId,
          EnumSet.of(
              Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MODERATE_MEMBERS));
    }

    // 從關鍵字解析
    EnumSet<Permission> permissions = EnumSet.noneOf(Permission.class);
    for (Map.Entry<String, Permission> entry : KEYWORD_MAP.entrySet()) {
      if (lowerDesc.contains(entry.getKey())) {
        permissions.add(entry.getValue());
      }
    }

    return permissions.isEmpty()
        ? ChannelPermission.readOnly(roleId)
        : new ChannelPermission(roleId, permissions);
  }

  /**
   * 從列表格式解析權限。
   *
   * @param list 權限名稱列表
   * @param roleId 角色 ID
   * @return 權限配置
   */
  @SuppressWarnings("unchecked")
  private static ChannelPermission parseFromList(List<?> list, long roleId) {
    EnumSet<Permission> permissions = EnumSet.noneOf(Permission.class);

    for (Object item : list) {
      if (item instanceof String permName) {
        Permission perm = KEYWORD_MAP.get(permName.toLowerCase());
        if (perm != null) {
          permissions.add(perm);
        }
      } else if (item instanceof Map<?, ?> map) {
        ChannelPermission parsed = parseFromMap(map, roleId);
        permissions.addAll(parsed.permissionSet());
      }
    }

    return permissions.isEmpty()
        ? ChannelPermission.readOnly(roleId)
        : new ChannelPermission(roleId, permissions);
  }

  /**
   * 從 Map 格式解析權限。
   *
   * @param map 權限資料 Map
   * @param roleId 角色 ID（如果 Map 中有指定則使用 Map 中的）
   * @return 權限配置
   */
  @SuppressWarnings("unchecked")
  private static ChannelPermission parseFromMap(Map<?, ?> map, long roleId) {
    // 獲取 roleId（如果 Map 中有指定）
    Object roleIdObj = map.get("roleId");
    if (roleIdObj instanceof Number) {
      roleId = ((Number) roleIdObj).longValue();
    }

    // 獲取 permissions 列表
    Object permsObj = map.get("permissions");
    if (permsObj instanceof List<?> permsList) {
      return parseFromList(permsList, roleId);
    }

    // 獲取 permissionSet 字串
    Object permSetObj = map.get("permissionSet");
    if (permSetObj instanceof String permSetStr) {
      return parseFromDescription(permSetStr, roleId);
    }

    return ChannelPermission.readOnly(roleId);
  }

  /**
   * 解析多個權限配置（用於不同角色）。
   *
   * @param permissionsList 權限配置列表
   * @return 權限配置列表
   */
  public static List<ChannelPermission> parseMultiple(List<?> permissionsList) {
    return permissionsList.stream()
        .map(
            item -> {
              if (item instanceof Map<?, ?> map) {
                Object roleIdObj = map.get("roleId");
                long roleId = roleIdObj instanceof Number ? ((Number) roleIdObj).longValue() : 0L;
                return parse(item, roleId);
              }
              return ChannelPermission.readOnly(0L);
            })
        .toList();
  }

  /**
   * 驗證權限描述是否有效。
   *
   * @param description 權限描述
   * @return 是否有效
   */
  public static boolean isValidDescription(String description) {
    if (description == null || description.isBlank()) {
      return false;
    }

    String lowerDesc = description.toLowerCase().trim();
    return lowerDesc.contains("full")
        || lowerDesc.contains("all")
        || lowerDesc.contains("read")
        || lowerDesc.contains("view")
        || lowerDesc.contains("write")
        || lowerDesc.contains("send")
        || lowerDesc.contains("moderator")
        || lowerDesc.contains("mod")
        || lowerDesc.contains("admin")
        || lowerDesc.contains("manage");
  }

  /**
   * 將 PermissionEnum 轉換為 JDA Permission。
   *
   * @param permissionEnum 權限枚舉
   * @return JDA Permission，如果不匹配則返回 null
   */
  private static Permission toJdaPermission(PermissionEnum permissionEnum) {
    return switch (permissionEnum) {
      case ADMINISTRATOR -> Permission.ADMINISTRATOR;
      case MANAGE_CHANNELS -> Permission.MANAGE_CHANNEL;
      case MANAGE_ROLES -> Permission.MANAGE_ROLES;
      case MANAGE_SERVER -> Permission.MANAGE_SERVER;
      case VIEW_CHANNEL -> Permission.VIEW_CHANNEL;
      case MESSAGE_SEND -> Permission.MESSAGE_SEND;
      case MESSAGE_HISTORY -> Permission.MESSAGE_HISTORY;
      case VOICE_CONNECT -> Permission.VOICE_CONNECT;
      case VOICE_SPEAK -> Permission.VOICE_SPEAK;
      case PRIORITY_SPEAKER -> Permission.PRIORITY_SPEAKER;
    };
  }

  /**
   * 將 PermissionSetting 轉換為 ChannelPermission。
   *
   * @param setting 權限設定
   * @return 頻道權限
   */
  public static ChannelPermission parse(PermissionSetting setting) {
    EnumSet<Permission> permissions = EnumSet.noneOf(Permission.class);

    if (setting.allowSet() != null) {
      for (PermissionEnum permEnum : setting.allowSet()) {
        Permission jdaPerm = toJdaPermission(permEnum);
        if (jdaPerm != null) {
          permissions.add(jdaPerm);
        }
      }
    }

    return permissions.isEmpty()
        ? ChannelPermission.readOnly(setting.roleId())
        : new ChannelPermission(setting.roleId(), permissions);
  }
}
