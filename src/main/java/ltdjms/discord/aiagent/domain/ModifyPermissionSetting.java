package ltdjms.discord.aiagent.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 修改頻道權限設定資料傳輸物件。
 *
 * <p>用於指定要添加或移除的權限。
 *
 * @param targetId 目標 ID（用戶 ID 或角色 ID）
 * @param targetType 目標類型（"member" 或 "role"）
 * @param allowToAdd 要添加的允許權限集合（可選）
 * @param allowToRemove 要移除的允許權限集合（可選）
 * @param denyToAdd 要添加的拒絕權限集合（可選）
 * @param denyToRemove 要移除的拒絕權限集合（可選）
 */
public record ModifyPermissionSetting(
    @JsonProperty("targetId") long targetId,
    @JsonProperty("targetType") String targetType,
    @JsonProperty(value = "allowToAdd", required = false) java.util.Set<PermissionEnum> allowToAdd,
    @JsonProperty(value = "allowToRemove", required = false)
        java.util.Set<PermissionEnum> allowToRemove,
    @JsonProperty(value = "denyToAdd", required = false) java.util.Set<PermissionEnum> denyToAdd,
    @JsonProperty(value = "denyToRemove", required = false)
        java.util.Set<PermissionEnum> denyToRemove) {

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
    /** 附加檔案 */
    MESSAGE_ATTACH_FILES,
    /** 嵌入連結 */
    MESSAGE_EMBED_LINKS,
    /** 連結頻道 */
    VOICE_CONNECT,
    /** 說話 */
    VOICE_SPEAK,
    /** 優先權 */
    PRIORITY_SPEAKER,
    /** 管理訊息 */
    MANAGE_MESSAGES,
    /** 讀取訊息 */
    MESSAGE_READ,
    /** 添加反應 */
    MESSAGE_ADD_REACTION,
    /** 使用外部表情 */
    MESSAGE_EXT_EMOJI
  }

  /**
   * 建構空的權限設定實例。
   *
   * <p>用於 JSON 反序列化。
   */
  public ModifyPermissionSetting {
    if (targetType == null) {
      targetType = "role";
    }
    if (allowToAdd == null) {
      allowToAdd = java.util.Set.of();
    }
    if (allowToRemove == null) {
      allowToRemove = java.util.Set.of();
    }
    if (denyToAdd == null) {
      denyToAdd = java.util.Set.of();
    }
    if (denyToRemove == null) {
      denyToRemove = java.util.Set.of();
    }
  }

  /**
   * 驗證設定是否有效。
   *
   * @return 是否有效
   */
  public boolean isValid() {
    return targetId != 0
        && (!allowToAdd.isEmpty()
            || !allowToRemove.isEmpty()
            || !denyToAdd.isEmpty()
            || !denyToRemove.isEmpty());
  }
}
