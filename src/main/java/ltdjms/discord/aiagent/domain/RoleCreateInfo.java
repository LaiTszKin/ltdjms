package ltdjms.discord.aiagent.domain;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 創建角色的請求參數。
 *
 * @param name 角色名稱（必填）
 * @param color 顏色 RGB 十進制值（可選，例如 0xFF0000 為紅色）
 * @param permissions 權限集合（可選）
 * @param hoist 是否分隔顯示（可選，預設 false）
 * @param mentionable 是否可提及（可選，預設 false）
 */
public record RoleCreateInfo(
    @JsonProperty("name") String name,
    @JsonProperty(value = "color", required = false) Integer color,
    @JsonProperty(value = "permissions", required = false) Set<PermissionEnum> permissions,
    @JsonProperty(value = "hoist", required = false) Boolean hoist,
    @JsonProperty(value = "mentionable", required = false) Boolean mentionable) {

  /** 權限枚舉（與 JDA Permission 共享） */
  public enum PermissionEnum {
    ADMINISTRATOR,
    MANAGE_CHANNELS,
    MANAGE_ROLES,
    MANAGE_SERVER,
    VIEW_CHANNEL,
    MESSAGE_SEND,
    MESSAGE_HISTORY,
    MESSAGE_ATTACH_FILES,
    MESSAGE_EMBED_LINKS,
    VOICE_CONNECT,
    VOICE_SPEAK,
    PRIORITY_SPEAKER,
    MANAGE_MESSAGES,
    MESSAGE_READ,
    MESSAGE_ADD_REACTION,
    MESSAGE_EXT_EMOJI,
    MANAGE_WEBHOOKS,
    KICK_MEMBERS,
    BAN_MEMBERS
  }

  public RoleCreateInfo {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("角色名稱不能為空");
    }
    if (color == null) {
      color = 0; // 預設無顏色
    }
    if (permissions == null) {
      permissions = Set.of();
    }
    if (hoist == null) {
      hoist = false;
    }
    if (mentionable == null) {
      mentionable = false;
    }
  }
}
