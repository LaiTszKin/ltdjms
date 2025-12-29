package ltdjms.discord.aiagent.domain;

import java.util.EnumSet;
import java.util.Objects;

import net.dv8tion.jda.api.Permission;

/**
 * 頻道權限設定。
 *
 * <p>定義特定角色在頻道中的權限。
 *
 * @param roleId 角色 ID（@everyone 為長整數 -1 或伺服器的 everyone 角色 ID）
 * @param permissionSet 權限集合
 */
public record ChannelPermission(long roleId, EnumSet<Permission> permissionSet) {

  public ChannelPermission {
    Objects.requireNonNull(permissionSet, "permissionSet must not be null");
  }

  /**
   * 建立唯讀權限。
   *
   * @param roleId 角色 ID
   * @return 唯讀權限配置
   */
  public static ChannelPermission readOnly(long roleId) {
    return new ChannelPermission(roleId, EnumSet.of(Permission.VIEW_CHANNEL));
  }

  /**
   * 建立完整權限。
   *
   * @param roleId 角色 ID
   * @return 完整權限配置
   */
  public static ChannelPermission fullAccess(long roleId) {
    return new ChannelPermission(roleId, EnumSet.allOf(Permission.class));
  }
}
