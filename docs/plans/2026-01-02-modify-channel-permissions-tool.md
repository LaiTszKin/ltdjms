# 修改頻道權限工具實作計畫

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**目標:** 添加一個 LangChain4j AI Agent 工具，用於修改 Discord 頻道的權限設定，支持同時添加/移除多個權限。

**架構:** 使用 JDA 的 `PermissionOverrideAction` 和 `upsertPermissionOverride()` 方法來修改現有的權限覆寫。工具會讀取現有的允許/拒絕權限集合，根據用戶指定的操作添加或移除權限，然後應用更新。

**技術堆疊:** Java 17, LangChain4j, JDA 5.2.2, JUnit 5, Mockito, Testcontainers

---

## 任務總覽

1. 創建 Domain Model: `ModifyPermissionSetting`
2. 創建 Tool 實作: `LangChain4jModifyChannelPermissionsTool`
3. 單元測試: `LangChain4jModifyChannelPermissionsToolTest`
4. 整合測試: `LangChain4jModifyChannelPermissionsToolIntegrationTest`
5. DI 配置: 更新 `AIAgentModule`
6. 測試與驗證

---

### Task 1: 創建 Domain Model - ModifyPermissionSetting

**檔案:**
- 建立: `src/main/java/ltdjms/discord/aiagent/domain/ModifyPermissionSetting.java`

**Step 1: 寫入 Domain Model 類別**

```java
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
    @JsonProperty(value = "allowToRemove", required = false) java.util.Set<PermissionEnum> allowToRemove,
    @JsonProperty(value = "denyToAdd", required = false) java.util.Set<PermissionEnum> denyToAdd,
    @JsonProperty(value = "denyToRemove", required = false) java.util.Set<PermissionEnum> denyToRemove) {

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
        && (!allowToAdd.isEmpty() || !allowToRemove.isEmpty() || !denyToAdd.isEmpty() || !denyToRemove.isEmpty());
  }
}
```

**Step 2: 編譯驗證**

Run: `mvn compile -q`
Expected: 編譯成功，無錯誤

**Step 3: 提交**

```bash
git add src/main/java/ltdjms/discord/aiagent/domain/ModifyPermissionSetting.java
git commit -m "feat(aiagent): 新增 ModifyPermissionSetting domain model"
```

---

### Task 2: 創建 Tool 實作 - LangChain4jModifyChannelPermissionsTool

**檔案:**
- 建立: `src/main/java/ltdjms/discord/aiagent/services/tools/LangChain4jModifyChannelPermissionsTool.java`

**Step 1: 寫入 Tool 實作類別**

```java
package ltdjms.discord.aiagent.services.tools;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import ltdjms.discord.aiagent.domain.ModifyPermissionSetting;
import ltdjms.discord.aiagent.domain.ModifyPermissionSetting.PermissionEnum;
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
   * @param parameters 調用參數（包含 guildId、channelId、userId）
   * @return 修改結果 JSON 字串
   */
  @Tool(
      """
      修改 Discord 頻道的權限覆寫設定。

      使用場景：
      - 當需要為特定用戶或角色添加或移除頻道權限時使用
      - 需要修改現有權限覆寫時使用
      - 批量修改多個權限時使用

      返回資訊：
      - 修改是否成功
      - 修改前後的權限對比
      - 頻道和目標資訊

      重要：
      - 權限修改是基於現有權限的增量操作
      - allowToAdd: 添加到「允許」集合的權限
      - allowToRemove: 從「允許」集合移除的權限
      - denyToAdd: 添加到「拒絕」集合的權限
      - denyToRemove: 從「拒絕」集合移除的權限
      - 同一權限不能同時存在於「允許」和「拒絕」集合中
      """)
  public String modifyChannelPermissions(
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
                  - ["VIEW_CHANNEL", "MESSAGE_SEND"]：添加查看和發送訊息權限
                  - []：不添加任何允許權限
                  """,
              required = false)
          List<String> allowToAdd,
      @P(
              value =
                  """
                  要從「允許」集合移除的權限列表。

                  權限名稱字串列表，格式同 allowToAdd。

                  範例：
                  - ["MESSAGE_SEND"]：移除發送訊息權限
                  - []：不移除任何允許權限
                  """,
              required = false)
          List<String> allowToRemove,
      @P(
              value =
                  """
                  要添加到「拒絕」集合的權限列表。

                  權限名稱字串列表，格式同 allowToAdd。

                  範例：
                  - ["VOICE_CONNECT"]：拒絕連結語音頻道
                  - []：不添加任何拒絕權限
                  """,
              required = false)
          List<String> denyToAdd,
      @P(
              value =
                  """
                  要從「拒絕」集合移除的權限列表。

                  權限名稱字串列表，格式同 allowToAdd。

                  範例：
                  - ["VOICE_CONNECT"]：移除連結語音頻道的拒絕
                  - []：不移除任何拒絕權限
                  """,
              required = false)
          List<String> denyToRemove,
      InvocationParameters parameters) {

    // 1. 驗證必要參數
    if (channelId == null || channelId.isBlank()) {
      return buildErrorResponse("channelId 未提供");
    }
    if (targetId == null || targetId.isBlank()) {
      return buildErrorResponse("targetId 未提供");
    }

    // 解析 ID
    long channelIdLong;
    long targetIdLong;
    try {
      channelIdLong = parseId(channelId);
      targetIdLong = parseId(targetId);
    } catch (NumberFormatException e) {
      return buildErrorResponse("無效的 ID 格式");
    }

    // 設定預設 targetType
    if (targetType == null || targetType.isBlank()) {
      targetType = "role";
    }
    if (!targetType.equals("member") && !targetType.equals("role")) {
      return buildErrorResponse("targetType 必須是 'member' 或 'role'");
    }

    // 檢查是否有任何權限操作
    boolean hasPermissionChanges =
        (allowToAdd != null && !allowToAdd.isEmpty())
            || (allowToRemove != null && !allowToRemove.isEmpty())
            || (denyToAdd != null && !denyToAdd.isEmpty())
            || (denyToRemove != null && !denyToRemove.isEmpty());

    if (!hasPermissionChanges) {
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

    // 4. 獲取頻道
    GuildChannel channel = guild.getGuildChannelById(channelIdLong);
    if (channel == null) {
      return buildErrorResponse("找不到頻道");
    }

    // 檢查頻道是否支持權限覆寫
    if (!(channel instanceof IPermissionContainer)) {
      return buildErrorResponse("頻道類型不支持權限覆寫");
    }
    IPermissionContainer permissionContainer = (IPermissionContainer) channel;

    // 5. 獲取或驗證目標（用戶或角色）
    boolean isMember = targetType.equals("member");
    if (isMember) {
      Member member = guild.getMemberById(targetIdLong);
      if (member == null) {
        return buildErrorResponse("找不到指定的用戶");
      }
    } else {
      Role role = guild.getRoleById(targetIdLong);
      if (role == null) {
        return buildErrorResponse("找不到指定的角色");
      }
    }

    try {
      // 6. 獲取現有權限覆寫
      PermissionOverride existingOverride =
          permissionContainer.getPermissionOverride(targetIdLong);

      // 獲取現有的允許和拒絕權限
      EnumSet<Permission> currentAllowed =
          existingOverride != null ? existingOverride.getAllowed() : EnumSet.noneOf(Permission.class);
      EnumSet<Permission> currentDenied =
          existingOverride != null ? existingOverride.getDenied() : EnumSet.noneOf(Permission.class);

      // 記錄修改前的權限
      List<String> beforeAllowed = permissionListToString(currentAllowed);
      List<String> beforeDenied = permissionListToString(currentDenied);

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
        Member member = guild.getMemberById(targetIdLong);
        permissionContainer
            .upsertPermissionOverride(member)
            .setPermissions(newAllowed, newDenied)
            .complete();
      } else {
        Role role = guild.getRoleById(targetIdLong);
        permissionContainer
            .upsertPermissionOverride(role)
            .setPermissions(newAllowed, newDenied)
            .complete();
      }

      // 記錄修改後的權限
      List<String> afterAllowed = permissionListToString(newAllowed);
      List<String> afterDenied = permissionListToString(newDenied);

      LOGGER.info(
          "修改頻道 {} 權限: 目標={}, 類型={}, 允許={}, 拒絕={}",
          channel.getIdLong(),
          targetIdLong,
          targetType,
          newAllowed,
          newDenied);

      // 9. 返回成功結果
      return buildSuccessResponse(
          channel,
          targetIdLong,
          targetType,
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
   * @param channel 頻道
   * @param targetId 目標 ID
   * @param targetType 目標類型
   * @param beforeAllowed 修改前的允許權限
   * @param beforeDenied 修改前的拒絕權限
   * @param afterAllowed 修改後的允許權限
   * @param afterDenied 修改後的拒絕權限
   * @return JSON 格式的成功回應
   */
  private String buildSuccessResponse(
      GuildChannel channel,
      long targetId,
      String targetType,
      List<String> beforeAllowed,
      List<String> beforeDenied,
      List<String> afterAllowed,
      List<String> afterDenied) {
    StringBuilder json = new StringBuilder();
    json.append("{\n");
    json.append("  \"success\": true,\n");
    json.append("  \"message\": \"權限修改成功\",\n");
    json.append("  \"channelId\": \"").append(channel.getIdLong()).append("\",\n");
    json.append("  \"channelName\": \"").append(escapeJson(channel.getName())).append("\",\n");
    json.append("  \"targetId\": \"").append(targetId).append("\",\n");
    json.append("  \"targetType\": \"").append(targetType).append("\",\n");

    // 修改前的權限
    json.append("  \"before\": {\n");
    json.append("    \"allowed\": ").append(permissionListToJson(beforeAllowed)).append(",\n");
    json.append("    \"denied\": ").append(permissionListToJson(beforeDenied)).append("\n");
    json.append("  },\n");

    // 修改後的權限
    json.append("  \"after\": {\n");
    json.append("    \"allowed\": ").append(permissionListToJson(afterAllowed)).append(",\n");
    json.append("    \"denied\": ").append(permissionListToJson(afterDenied)).append("\n");
    json.append("  }\n");

    json.append("}");
    return json.toString();
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
    return value.replace("\\", "\\\\")
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
    """.formatted(escapeJson(error));
  }
}
```

**Step 2: 編譯驗證**

Run: `mvn compile -q`
Expected: 編譯成功，無錯誤

**Step 3: 提交**

```bash
git add src/main/java/ltdjms/discord/aiagent/services/tools/LangChain4jModifyChannelPermissionsTool.java
git commit -m "feat(aiagent): 新增 LangChain4jModifyChannelPermissionsTool"
```

---

### Task 3: 單元測試 - LangChain4jModifyChannelPermissionsToolTest

**檔案:**
- 建立: `src/test/java/ltdjms/discord/aiagent/unit/services/tools/LangChain4jModifyChannelPermissionsToolTest.java`

**Step 1: 寫入單元測試類別**

```java
package ltdjms.discord.aiagent.unit.services.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.langchain4j.invocation.InvocationParameters;
import ltdjms.discord.aiagent.services.tools.LangChain4jModifyChannelPermissionsTool;
import ltdjms.discord.shared.di.JDAProvider;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.managers.PermissionOverrideAction;
import net.dv8tion.jda.api.requests.RestAction;

@DisplayName("T025: LangChain4jModifyChannelPermissionsTool 單元測試")
class LangChain4jModifyChannelPermissionsToolTest {

  private static final long TEST_GUILD_ID = 123456789L;
  private static final long TEST_CHANNEL_ID = 987654321L;
  private static final long TEST_ROLE_ID = 111222333L;
  private static final long TEST_MEMBER_ID = 444555666L;

  private LangChain4jModifyChannelPermissionsTool tool;
  private Guild mockGuild;
  private GuildChannel mockChannel;
  private IPermissionContainer mockPermissionContainer;
  private net.dv8tion.jda.api.JDA mockJda;
  private InvocationParameters parameters;

  @BeforeEach
  void setUp() {
    mockGuild = mock(Guild.class);
    mockJda = mock(net.dv8tion.jda.api.JDA.class);
    tool = new LangChain4jModifyChannelPermissionsTool();
    parameters = new InvocationParameters();

    // 設置測試參數
    parameters.put("guildId", TEST_GUILD_ID);
    parameters.put("channelId", TEST_CHANNEL_ID);
    parameters.put("userId", TEST_MEMBER_ID);

    JDAProvider.setJda(mockJda);
    when(mockJda.getGuildById(TEST_GUILD_ID)).thenReturn(mockGuild);

    // Mock 頻道（同時實現 IPermissionContainer）
    mockChannel =
        mock(
            GuildChannel.class,
            withSettings().extraInterfaces(IPermissionContainer.class));
    mockPermissionContainer = (IPermissionContainer) mockChannel;

    when(mockGuild.getGuildChannelById(TEST_CHANNEL_ID)).thenReturn(mockChannel);
    when(mockChannel.getIdLong()).thenReturn(TEST_CHANNEL_ID);
    when(mockChannel.getName()).thenReturn("test-channel");
    when(mockChannel.getType()).thenReturn(net.dv8tion.jda.api.ChannelType.TEXT);
  }

  @AfterEach
  void tearDown() {
    JDAProvider.clear();
  }

  @Nested
  @DisplayName("參數驗證測試")
  class ParameterValidationTests {

    @Test
    @DisplayName("缺少 channelId 應返回錯誤")
    void missingChannelIdShouldReturnError() {
      String result = tool.modifyChannelPermissions(null, "123", "role", null, null, null, null, parameters);

      assertTrue(result.contains("\"success\": false"));
      assertTrue(result.contains("channelId 未提供"));
    }

    @Test
    @DisplayName("缺少 targetId 應返回錯誤")
    void missingTargetIdShouldReturnError() {
      String result = tool.modifyChannelPermissions("123", null, "role", null, null, null, null, parameters);

      assertTrue(result.contains("\"success\": false"));
      assertTrue(result.contains("targetId 未提供"));
    }

    @Test
    @DisplayName("無效的 targetType 應返回錯誤")
    void invalidTargetTypeShouldReturnError() {
      String result =
          tool.modifyChannelPermissions("123", "456", "invalid", null, null, null, null, parameters);

      assertTrue(result.contains("\"success\": false"));
      assertTrue(result.contains("targetType 必須是 'member' 或 'role'"));
    }

    @Test
    @DisplayName("未指定任何權限操作應返回錯誤")
    void noPermissionChangesShouldReturnError() {
      String result =
          tool.modifyChannelPermissions(
              String.valueOf(TEST_CHANNEL_ID), "456", "role", null, null, null, null, parameters);

      assertTrue(result.contains("\"success\": false"));
      assertTrue(result.contains("未指定任何權限修改操作"));
    }
  }

  @Nested
  @DisplayName("錯誤處理測試")
  class ErrorHandlingTests {

    @Test
    @DisplayName("找不到頻道應返回錯誤")
    void channelNotFoundShouldReturnError() {
      when(mockGuild.getGuildChannelById(TEST_CHANNEL_ID)).thenReturn(null);

      String result =
          tool.modifyChannelPermissions(
              String.valueOf(TEST_CHANNEL_ID),
              String.valueOf(TEST_ROLE_ID),
              "role",
              List.of("VIEW_CHANNEL"),
              null,
              null,
              null,
              parameters);

      assertTrue(result.contains("\"success\": false"));
      assertTrue(result.contains("找不到頻道"));
    }

    @Test
    @DisplayName("找不到角色應返回錯誤")
    void roleNotFoundShouldReturnError() {
      when(mockGuild.getRoleById(TEST_ROLE_ID)).thenReturn(null);

      String result =
          tool.modifyChannelPermissions(
              String.valueOf(TEST_CHANNEL_ID),
              String.valueOf(TEST_ROLE_ID),
              "role",
              List.of("VIEW_CHANNEL"),
              null,
              null,
              null,
              parameters);

      assertTrue(result.contains("\"success\": false"));
      assertTrue(result.contains("找不到指定的角色"));
    }

    @Test
    @DisplayName("找不到用戶應返回錯誤")
    void memberNotFoundShouldReturnError() {
      when(mockGuild.getMemberById(TEST_MEMBER_ID)).thenReturn(null);

      String result =
          tool.modifyChannelPermissions(
              String.valueOf(TEST_CHANNEL_ID),
              String.valueOf(TEST_MEMBER_ID),
              "member",
              List.of("VIEW_CHANNEL"),
              null,
              null,
              null,
              parameters);

      assertTrue(result.contains("\"success\": false"));
      assertTrue(result.contains("找不到指定的用戶"));
    }
  }

  @Nested
  @DisplayName("正常情況測試 - 角色權限")
  class RolePermissionTests {

    private Role mockRole;
    private PermissionOverride mockOverride;
    private PermissionOverrideAction mockAction;

    @BeforeEach
    void setUpRoleTests() {
      mockRole = mock(Role.class);
      when(mockRole.getIdLong()).thenReturn(TEST_ROLE_ID);
      when(mockGuild.getRoleById(TEST_ROLE_ID)).thenReturn(mockRole);

      mockOverride = mock(PermissionOverride.class);
      when(mockPermissionContainer.getPermissionOverride(TEST_ROLE_ID)).thenReturn(mockOverride);
      when(mockOverride.getAllowed()).thenReturn(EnumSet.noneOf(Permission.class));
      when(mockOverride.getDenied()).thenReturn(EnumSet.noneOf(Permission.class));

      mockAction = mock(PermissionOverrideAction.class);
      when(mockPermissionContainer.upsertPermissionOverride(mockRole)).thenReturn(mockAction);
      when(mockAction.setPermissions(any(), any())).thenReturn(mockAction);
      when(mockAction.complete()).thenReturn(mockOverride);
    }

    @Test
    @DisplayName("添加允許權限應成功")
    void addAllowedPermissionsShouldSucceed() {
      String result =
          tool.modifyChannelPermissions(
              String.valueOf(TEST_CHANNEL_ID),
              String.valueOf(TEST_ROLE_ID),
              "role",
              List.of("VIEW_CHANNEL", "MESSAGE_SEND"),
              null,
              null,
              null,
              parameters);

      assertTrue(result.contains("\"success\": true"));
      assertTrue(result.contains("權限修改成功"));

      verify(mockAction).setPermissions(any(EnumSet.class), any(EnumSet.class));
      verify(mockAction).complete();
    }

    @Test
    @DisplayName("移除允許權限應成功")
    void removeAllowedPermissionsShouldSucceed() {
      when(mockOverride.getAllowed())
          .thenReturn(EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND));

      String result =
          tool.modifyChannelPermissions(
              String.valueOf(TEST_CHANNEL_ID),
              String.valueOf(TEST_ROLE_ID),
              "role",
              null,
              List.of("MESSAGE_SEND"),
              null,
              null,
              parameters);

      assertTrue(result.contains("\"success\": true"));

      verify(mockAction).setPermissions(any(EnumSet.class), any(EnumSet.class));
    }

    @Test
    @DisplayName("添加拒絕權限應成功")
    void addDeniedPermissionsShouldSucceed() {
      String result =
          tool.modifyChannelPermissions(
              String.valueOf(TEST_CHANNEL_ID),
              String.valueOf(TEST_ROLE_ID),
              "role",
              null,
              null,
              List.of("VOICE_CONNECT"),
              null,
              parameters);

      assertTrue(result.contains("\"success\": true"));

      verify(mockAction).setPermissions(any(EnumSet.class), any(EnumSet.class));
    }

    @Test
    @DisplayName("同時添加和移除權限應成功")
    void addAndRemovePermissionsShouldSucceed() {
      when(mockOverride.getAllowed()).thenReturn(EnumSet.of(Permission.MESSAGE_READ));

      String result =
          tool.modifyChannelPermissions(
              String.valueOf(TEST_CHANNEL_ID),
              String.valueOf(TEST_ROLE_ID),
              "role",
              List.of("VIEW_CHANNEL", "MESSAGE_SEND"),
              List.of("MESSAGE_READ"),
              List.of("VOICE_CONNECT"),
              null,
              parameters);

      assertTrue(result.contains("\"success\": true"));
      assertTrue(result.contains("\"before\""));
      assertTrue(result.contains("\"after\""));
    }
  }

  @Nested
  @DisplayName("正常情況測試 - 用戶權限")
  class MemberPermissionTests {

    private Member mockMember;
    private PermissionOverride mockOverride;
    private PermissionOverrideAction mockAction;

    @BeforeEach
    void setUpMemberTests() {
      mockMember = mock(Member.class);
      when(mockMember.getIdLong()).thenReturn(TEST_MEMBER_ID);
      when(mockGuild.getMemberById(TEST_MEMBER_ID)).thenReturn(mockMember);

      mockOverride = mock(PermissionOverride.class);
      when(mockPermissionContainer.getPermissionOverride(TEST_MEMBER_ID)).thenReturn(mockOverride);
      when(mockOverride.getAllowed()).thenReturn(EnumSet.noneOf(Permission.class));
      when(mockOverride.getDenied()).thenReturn(EnumSet.noneOf(Permission.class));

      mockAction = mock(PermissionOverrideAction.class);
      when(mockPermissionContainer.upsertPermissionOverride(mockMember)).thenReturn(mockAction);
      when(mockAction.setPermissions(any(), any())).thenReturn(mockAction);
      when(mockAction.complete()).thenReturn(mockOverride);
    }

    @Test
    @DisplayName("為用戶添加權限應成功")
    void addMemberPermissionsShouldSucceed() {
      String result =
          tool.modifyChannelPermissions(
              String.valueOf(TEST_CHANNEL_ID),
              String.valueOf(TEST_MEMBER_ID),
              "member",
              List.of("VIEW_CHANNEL"),
              null,
              null,
              null,
              parameters);

      assertTrue(result.contains("\"success\": true"));
      assertTrue(result.contains("\"targetType\": \"member\""));

      verify(mockAction).setPermissions(any(EnumSet.class), any(EnumSet.class));
    }
  }

  @Nested
  @DisplayName("ID 解析測試")
  class IdParsingTests {

    private Role mockRole;
    private PermissionOverride mockOverride;
    private PermissionOverrideAction mockAction;

    @BeforeEach
    void setUpIdParsingTests() {
      mockRole = mock(Role.class);
      when(mockRole.getIdLong()).thenReturn(TEST_ROLE_ID);
      when(mockGuild.getRoleById(TEST_ROLE_ID)).thenReturn(mockRole);

      mockOverride = mock(PermissionOverride.class);
      when(mockPermissionContainer.getPermissionOverride(TEST_ROLE_ID)).thenReturn(mockOverride);
      when(mockOverride.getAllowed()).thenReturn(EnumSet.noneOf(Permission.class));
      when(mockOverride.getDenied()).thenReturn(EnumSet.noneOf(Permission.class));

      mockAction = mock(PermissionOverrideAction.class);
      when(mockPermissionContainer.upsertPermissionOverride(mockRole)).thenReturn(mockAction);
      when(mockAction.setPermissions(any(), any())).thenReturn(mockAction);
      when(mockAction.complete()).thenReturn(mockOverride);
    }

    @Test
    @DisplayName("支援純數字 ID")
    void plainNumericIdShouldWork() {
      String result =
          tool.modifyChannelPermissions(
              "987654321",
              "111222333",
              "role",
              List.of("VIEW_CHANNEL"),
              null,
              null,
              null,
              parameters);

      assertTrue(result.contains("\"success\": true"));
    }

    @Test
    @DisplayName("支援 Discord 格式頻道 ID <#123>")
    void discordFormatChannelIdShouldWork() {
      String result =
          tool.modifyChannelPermissions(
              "<#987654321>",
              "111222333",
              "role",
              List.of("VIEW_CHANNEL"),
              null,
              null,
              null,
              parameters);

      assertTrue(result.contains("\"success\": true"));
    }

    @Test
    @DisplayName("支援 Discord 格式角色 ID <@&123>")
    void discordFormatRoleIdShouldWork() {
      String result =
          tool.modifyChannelPermissions(
              "987654321",
              "<@&111222333>",
              "role",
              List.of("VIEW_CHANNEL"),
              null,
              null,
              null,
              parameters);

      assertTrue(result.contains("\"success\": true"));
    }
  }
}
```

**Step 2: 執行單元測試**

Run: `mvn test -Dtest=LangChain4jModifyChannelPermissionsToolTest -q`
Expected: 測試通過

**Step 3: 提交**

```bash
git add src/test/java/ltdjms/discord/aiagent/unit/services/tools/LangChain4jModifyChannelPermissionsToolTest.java
git commit -m "test(aiagent): 新增 LangChain4jModifyChannelPermissionsTool 單元測試"
```

---

### Task 4: 整合測試 - LangChain4jModifyChannelPermissionsToolIntegrationTest

**檔案:**
- 建立: `src/test/java/ltdjms/discord/aiagent/integration/services/LangChain4jModifyChannelPermissionsToolIntegrationTest.java`

**Step 1: 寫入整合測試類別**

```java
package ltdjms.discord.aiagent.integration.services;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.langchain4j.invocation.InvocationParameters;
import ltdjms.discord.aiagent.services.tools.LangChain4jModifyChannelPermissionsTool;
import ltdjms.discord.test.base.IntegrationTestBase;

/**
 * LangChain4jModifyChannelPermissionsTool 整合測試。
 *
 * <p>測試真實 Discord API 互動（使用測試伺服器）。
 */
@DisplayName("T025: LangChain4jModifyChannelPermissionsTool 整合測試")
class LangChain4jModifyChannelPermissionsToolIntegrationTest extends IntegrationTestBase {

  @Test
  @DisplayName("應成功修改角色權限")
  void shouldModifyRolePermissions() {
    // Arrange
    LangChain4jModifyChannelPermissionsTool tool = new LangChain4jModifyChannelPermissionsTool();
    InvocationParameters parameters = new InvocationParameters();
    parameters.put("guildId", getTestGuildId());
    parameters.put("channelId", getTestChannelId());
    parameters.put("userId", getTestUserId());

    // 先創建一個測試角色
    long testRoleId = createTestRole("permission-test-role");

    try {
      // Act: 添加權限
      String result =
          tool.modifyChannelPermissions(
              getTestChannelId(),
              String.valueOf(testRoleId),
              "role",
              List.of("VIEW_CHANNEL", "MESSAGE_SEND"),
              null,
              null,
              null,
              parameters);

      // Assert
      assertNotNull(result);
      assertTrue(result.contains("\"success\": true"), "應成功修改權限: " + result);
      assertTrue(result.contains("\"before\""), "應包含修改前權限");
      assertTrue(result.contains("\"after\""), "應包含修改後權限");

    } finally {
      // 清理測試角色
      deleteTestRole(testRoleId);
    }
  }

  @Test
  @DisplayName("應成功添加和移除權限")
  void shouldAddAndRemovePermissions() {
    // Arrange
    LangChain4jModifyChannelPermissionsTool tool = new LangChain4jModifyChannelPermissionsTool();
    InvocationParameters parameters = new InvocationParameters();
    parameters.put("guildId", getTestGuildId());
    parameters.put("channelId", getTestChannelId());
    parameters.put("userId", getTestUserId());

    long testRoleId = createTestRole("add-remove-perm-role");

    try {
      // Act: 先添加權限
      String addResult =
          tool.modifyChannelPermissions(
              getTestChannelId(),
              String.valueOf(testRoleId),
              "role",
              List.of("VIEW_CHANNEL", "MESSAGE_SEND", "MESSAGE_ADD_REACTION"),
              null,
              null,
              null,
              parameters);

      assertTrue(addResult.contains("\"success\": true"));

      // 再移除部分權限
      String removeResult =
          tool.modifyChannelPermissions(
              getTestChannelId(),
              String.valueOf(testRoleId),
              "role",
              null,
              List.of("MESSAGE_SEND"),
              null,
              null,
              parameters);

      // Assert
      assertTrue(removeResult.contains("\"success\": true"), "應成功移除權限");

    } finally {
      deleteTestRole(testRoleId);
    }
  }

  @Test
  @DisplayName("應成功設置拒絕權限")
  void shouldSetDeniedPermissions() {
    // Arrange
    LangChain4jModifyChannelPermissionsTool tool = new LangChain4jModifyChannelPermissionsTool();
    InvocationParameters parameters = new InvocationParameters();
    parameters.put("guildId", getTestGuildId());
    parameters.put("channelId", getTestChannelId());
    parameters.put("userId", getTestUserId());

    long testRoleId = createTestRole("deny-perm-role");

    try {
      // Act
      String result =
          tool.modifyChannelPermissions(
              getTestChannelId(),
              String.valueOf(testRoleId),
              "role",
              null,
              null,
              List.of("VOICE_CONNECT", "MESSAGE_SEND"),
              null,
              parameters);

      // Assert
      assertTrue(result.contains("\"success\": true"), "應成功設置拒絕權限");

    } finally {
      deleteTestRole(testRoleId);
    }
  }

  @Test
  @DisplayName("應成功修改用戶權限")
  void shouldModifyMemberPermissions() {
    // Arrange
    LangChain4jModifyChannelPermissionsTool tool = new LangChain4jModifyChannelPermissionsTool();
    InvocationParameters parameters = new InvocationParameters();
    parameters.put("guildId", getTestGuildId());
    parameters.put("channelId", getTestChannelId());
    parameters.put("userId", getTestUserId());

    // Act
    String result =
        tool.modifyChannelPermissions(
            getTestChannelId(),
            getTestUserId(),
            "member",
            List.of("VIEW_CHANNEL"),
            null,
            null,
            null,
            parameters);

    // Assert
    assertTrue(result.contains("\"success\": true"), "應成功修改用戶權限");
    assertTrue(result.contains("\"targetType\": \"member\""));
  }

  @Test
  @DisplayName("應正確處理從允許移到拒絕的權限")
  void shouldHandlePermissionMoveFromAllowToDeny() {
    // Arrange
    LangChain4jModifyChannelPermissionsTool tool = new LangChain4jModifyChannelPermissionsTool();
    InvocationParameters parameters = new InvocationParameters();
    parameters.put("guildId", getTestGuildId());
    parameters.put("channelId", getTestChannelId());
    parameters.put("userId", getTestUserId());

    long testRoleId = createTestRole("move-perm-role");

    try {
      // Act: 先添加到允許
      String addResult =
          tool.modifyChannelPermissions(
              getTestChannelId(),
              String.valueOf(testRoleId),
              "role",
              List.of("MESSAGE_SEND"),
              null,
              null,
              null,
              parameters);

      assertTrue(addResult.contains("\"success\": true"));

      // 再移到拒絕（應從允許中移除）
      String denyResult =
          tool.modifyChannelPermissions(
              getTestChannelId(),
              String.valueOf(testRoleId),
              "role",
              null,
              null,
              List.of("MESSAGE_SEND"),
              null,
              parameters);

      // Assert
      assertTrue(denyResult.contains("\"success\": true"), "應成功將權限從允許移到拒絕");

    } finally {
      deleteTestRole(testRoleId);
    }
  }
}
```

**Step 2: 執行整合測試**

Run: `mvn test -Dtest=LangChain4jModifyChannelPermissionsToolIntegrationTest -P integration-test -q`
Expected: 測試通過（需要真實 Discord 連接）

**Step 3: 提交**

```bash
git add src/test/java/ltdjms/discord/aiagent/integration/services/LangChain4jModifyChannelPermissionsToolIntegrationTest.java
git commit -m "test(aiagent): 新增 LangChain4jModifyChannelPermissionsTool 整合測試"
```

---

### Task 5: DI 配置 - 更新 AIAgentModule

**檔案:**
- 修改: `src/main/java/ltdjms/discord/shared/di/AIAgentModule.java`

**Step 1: 查看 AIAgentModule 結構**

Run: `grep -n "LangChain4jGetChannelPermissionsTool" src/main/java/ltdjms/discord/shared/di/AIAgentModule.java`
Expected: 找到類似的提供者方法模式

**Step 2: 添加新的 Tool Provider 方法**

在 `AIAgentModule.java` 中添加：

```java
@Provides
@Singleton
public LangChain4jModifyChannelPermissionsTool provideLangChain4jModifyChannelPermissionsTool() {
  return new LangChain4jModifyChannelPermissionsTool();
}
```

**Step 3: 更新 AIChatService Provider 方法**

修改 `provideAIChatService` 方法，添加新工具參數：

```java
@Provides
@Singleton
public AIChatService provideAIChatService(
    // ... 現有參數
    LangChain4jGetChannelPermissionsTool getChannelPermissionsTool,
    LangChain4jModifyChannelPermissionsTool modifyChannelPermissionsTool) {  // 新增

  return new LangChain4jAIChatService(
      // ... 現有參數
      getChannelPermissionsTool,
      modifyChannelPermissionsTool);  // 新增
}
```

**Step 4: 更新 LangChain4jAIChatService 建構函數**

修改 `LangChain4jAIChatService.java`，添加新工具欄位和參數：

```java
private final LangChain4jModifyChannelPermissionsTool modifyChannelPermissionsTool;

@Inject
public LangChain4jAIChatService(
    // ... 現有參數
    LangChain4jGetChannelPermissionsTool getChannelPermissionsTool,
    LangChain4jModifyChannelPermissionsTool modifyChannelPermissionsTool) {
  // ... 現有賦值
  this.modifyChannelPermissionsTool = modifyChannelPermissionsTool;
}
```

**Step 5: 註冊工具到 Agent**

在 `LangChain4jAIChatService` 的工具註冊邏輯中添加：

```java
.tool(modifyChannelPermissionsTool)
```

**Step 6: 編譯驗證**

Run: `mvn compile -q`
Expected: 編譯成功

**Step 7: 提交**

```bash
git add src/main/java/ltdjms/discord/shared/di/AIAgentModule.java
git add src/main/java/ltdjms/discord/aichat/services/LangChain4jAIChatService.java
git commit -m "feat(aiagent): 註冊 LangChain4jModifyChannelPermissionsTool 到 DI"
```

---

### Task 6: 測試與驗證

**Step 1: 執行完整測試套件**

Run: `mvn test -q`
Expected: 所有測試通過

**Step 2: 執行整合測試**

Run: `mvn test -P integration-test -q`
Expected: 整合測試通過

**Step 3: 檢查覆蓋率**

Run: `mvn jacoco:report -q`
Expected: 覆蓋率達到 80% 以上

**Step 4: 本地驗證**

1. 啟動機器人: `make run`
2. 在 AI 頻道測試指令：
   - 「幫我在 #general 頻道給 @role 添加 VIEW_CHANNEL 和 MESSAGE_SEND 權限」
   - 「移除 #test 頻道中 @user 的 MESSAGE_SEND 權限」
   - 「在 #voice 頻道拒絕 @role 連結語音」

**Step 5: 最終提交**

```bash
git add .
git commit -m "docs(aiagent): 完成修改頻道權限工具實作"
```

---

## 相關檔案參考

| 類型 | 路徑 |
|------|------|
| Domain Model | `src/main/java/ltdjms/discord/aiagent/domain/ModifyPermissionSetting.java` |
| Tool 實作 | `src/main/java/ltdjms/discord/aiagent/services/tools/LangChain4jModifyChannelPermissionsTool.java` |
| 單元測試 | `src/test/java/ltdjms/discord/aiagent/unit/services/tools/LangChain4jModifyChannelPermissionsToolTest.java` |
| 整合測試 | `src/test/java/ltdjms/discord/aiagent/integration/services/LangChain4jModifyChannelPermissionsToolIntegrationTest.java` |
| DI 配置 | `src/main/java/ltdjms/discord/shared/di/AIAgentModule.java` |
| AI 服務 | `src/main/java/ltdjms/discord/aichat/services/LangChain4jAIChatService.java` |

---

## 技能參考

- `superpowers:test-driven-development`: TDD 開發流程
- `superpowers:verification-before-completion`: 完成前驗證
