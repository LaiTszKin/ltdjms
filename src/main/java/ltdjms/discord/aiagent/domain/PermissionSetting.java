package ltdjms.discord.aiagent.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 權限設定資料傳輸物件。
 *
 * <p>用於 LangChain4J 工具方法，替代 {@code Map<String, Object>} 以避免嵌套泛型問題。
 *
 * @param roleId 角色 ID
 * @param allowSet 允許的權限集合（可選）
 * @param denySet 拒絕的權限集合（可選）
 */
public record PermissionSetting(
    @JsonProperty("roleId") long roleId,
    @JsonProperty(value = "allowSet", required = false) java.util.Set<PermissionEnum> allowSet,
    @JsonProperty(value = "denySet", required = false) java.util.Set<PermissionEnum> denySet) {

  /**
   * 權限枚舉（字串格式）。
   *
   * <p>對應 Discord 的 {@link net.dv8tion.jda.api.Permission}。
   */
  public enum PermissionEnum {
    /** 管理員 */
    ADMINISTRATOR,
    /** 管理頻道 */
    MANAGE_CHANNELS,
    /** 管理角色 */
    MANAGE_ROLES,
    /** 管理伺服器 */
    MANAGE_SERVER,
    /** 查看頻道 */
    VIEW_CHANNEL,
    /** 發送訊息 */
    MESSAGE_SEND,
    /** 讀取訊息歷史 */
    MESSAGE_HISTORY,
    /** 連結頻道 */
    VOICE_CONNECT,
    /** 說話 */
    VOICE_SPEAK,
    /** 優先權 */
    PRIORITY_SPEAKER
  }

  /**
   * 建構空的權限設定實例。
   *
   * <p>用於 JSON 反序列化。
   */
  public PermissionSetting {
    if (allowSet == null) {
      allowSet = java.util.Set.of();
    }
    if (denySet == null) {
      denySet = java.util.Set.of();
    }
  }
}
