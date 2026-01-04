# Discord 角色與類別權限管理工具實作計畫

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 為 AI Agent 添加四個 Discord 權限管理工具，支援類別權限和角色管理

**Architecture:** 遵循現有 LangChain4J 工具模式，使用 @Tool 註解、InvocationParameters 傳遞上下文、JDA API 執行 Discord 操作，透過 Dagger 2 依賴注入整合

**Tech Stack:** Java 17, LangChain4j 1.7.1, JDA 5.2.2, JUnit 5, Mockito, Dagger 2.52

---

## Task 1: 創建 RoleCreateInfo 領域模型

**Files:**
- Create: `src/main/java/ltdjms/discord/aiagent/domain/RoleCreateInfo.java`

**Step 1: 創建 RoleCreateInfo 類別**

```java
package ltdjms.discord.aiagent.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;

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
```

**Step 2: 編譯驗證**

Run: `mvn compile -q`
Expected: 編譯成功，無錯誤

**Step 3: Commit**

```bash
git add src/main/java/ltdjms/discord/aiagent/domain/RoleCreateInfo.java
git commit -m "feat(aiagent): add RoleCreateInfo domain model for role creation"
```

---

## Task 2: 實作 LangChain4jModifyCategoryPermissionsTool

**Files:**
- Create: `src/main/java/ltdjms/discord/aiagent/services/tools/LangChain4jModifyCategoryPermissionsTool.java`

**Step 1: 寫失敗的單元測試**

Create: `src/test/java/ltdjms/discord/aiagent/unit/services/tools/LangChain4jModifyCategoryPermissionsToolTest.java`

```java
package ltdjms.discord.aiagent.unit.services.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.langchain4j.invocation.InvocationParameters;
import ltdjms.discord.aiagent.services.tools.LangChain4jModifyCategoryPermissionsTool;
import ltdjms.discord.shared.di.JDAProvider;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer;
import net.dv8tion.jda.api.entities.channel.concrete.Category;

@DisplayName("T026: LangChain4jModifyCategoryPermissionsTool 單元測試")
class LangChain4jModifyCategoryPermissionsToolTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_CATEGORY_ID = 888888888888888888L;
  private static final long TEST_ROLE_ID = 111111111111111111L;

  private LangChain4jModifyCategoryPermissionsTool tool;
  private Guild mockGuild;
  private Category mockCategory;
  private IPermissionContainer mockPermissionContainer;
  private JDA mockJda;
  private InvocationParameters parameters;

  @BeforeEach
  void setUp() {
    mockGuild = mock(Guild.class);
    mockJda = mock(JDA.class);
    tool = new LangChain4jModifyCategoryPermissionsTool();
    parameters = new InvocationParameters();

    parameters.put("guildId", TEST_GUILD_ID);
    parameters.put("channelId", TEST_CATEGORY_ID);
    parameters.put("userId", TEST_ROLE_ID);

    JDAProvider.setJda(mockJda);
    when(mockJda.getGuildById(TEST_GUILD_ID)).thenReturn(mockGuild);

    mockCategory = mock(Category.class);
    mockPermissionContainer = mockCategory;

    when(mockGuild.getCategoryById(TEST_CATEGORY_ID)).thenReturn(mockCategory);
    when(mockCategory.getIdLong()).thenReturn(TEST_CATEGORY_ID);
    when(mockCategory.getName()).thenReturn("test-category");
    when(mockPermissionContainer.getPermissionOverrides()).thenReturn(List.of());
  }

  @AfterEach
  void tearDown() {
    JDAProvider.clear();
  }

  @Nested
  @DisplayName("參數驗證測試")
  class ParameterValidationTests {

    @Test
    @DisplayName("缺少 categoryId 應返回錯誤")
    void missingCategoryIdShouldReturnError() {
      String result = tool.modifyCategoryPermissions(null, "123", "role", null, null, null, null, parameters);

      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("categoryId 未提供");
    }

    @Test
    @DisplayName("缺少 targetId 應返回錯誤")
    void missingTargetIdShouldReturnError() {
      String result = tool.modifyCategoryPermissions("123", null, "role", null, null, null, null, parameters);

      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("targetId 未提供");
    }
  }
}
```

**Step 2: 執行測試確認失敗**

Run: `mvn test -Dtest=LangChain4jModifyCategoryPermissionsToolTest -q`
Expected: FAIL with "cannot find symbol" (工具類別尚未建立)

**Step 3: 實作最小可通過代碼**

Create: `src/main/java/ltdjms/discord/aiagent/services/tools/LangChain4jModifyCategoryPermissionsTool.java`

```java
package ltdjms.discord.aiagent.services.tools;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

/**
 * 修改 Discord 類別的權限覆寫設定工具（LangChain4J 版本）。
 */
public final class LangChain4jModifyCategoryPermissionsTool {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(LangChain4jModifyCategoryPermissionsTool.class);

  @Inject
  public LangChain4jModifyCategoryPermissionsTool() {
    // JDA 將從 JDAProvider 延遲獲取
  }

  @Tool(
      """
      修改 Discord 類別的權限覆寫設定。

      使用場景：
      - 當需要為特定用戶或角色添加或移除類別權限時使用
      - 需要修改現有類別權限覆寫時使用
      - 批量修改多個權限時使用

      返回資訊：
      - 修改是否成功
      - 修改前後的權限對比
      - 類別和目標資訊

      重要限制：
      - 同一權限不能同時存在於「允許」和「拒絕」集合中
      - 拒絕權限優級高於允許權限
      """)
  public String modifyCategoryPermissions(
      @P(value = "要修改權限的類別 ID", required = true) String categoryId,
      @P(value = "目標 ID（用戶 ID 或角色 ID）", required = true) String targetId,
      @P(value = "目標類型（member 或 role）", required = false) String targetType,
      @P(value = "要添加的允許權限列表", required = false) List<String> allowToAdd,
      @P(value = "要移除的允許權限列表", required = false) List<String> allowToRemove,
      @P(value = "要添加的拒絕權限列表", required = false) List<String> denyToAdd,
      @P(value = "要移除的拒絕權限列表", required = false) List<String> denyToRemove,
      InvocationParameters parameters) {

    // 1. 驗證必要參數
    if (categoryId == null || categoryId.isBlank()) {
      return buildErrorResponse("categoryId 未提供");
    }
    if (targetId == null || targetId.isBlank()) {
      return buildErrorResponse("targetId 未提供");
    }

    // 解析 ID
    long categoryIdLong;
    long targetIdLong;
    try {
      categoryIdLong = parseId(categoryId);
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

    // 4. 獲取類別
    Category category = guild.getCategoryById(categoryIdLong);
    if (category == null) {
      return buildErrorResponse("找不到指定的類別");
    }

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

      List<String> beforeAllowed = permissionListToString(currentAllowed);
      List<String> beforeDenied = permissionListToString(currentDenied);

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
        Member member = guild.getMemberById(targetIdLong);
        category.upsertPermissionOverride(member)
            .setPermissions(newAllowed, newDenied)
            .complete();
      } else {
        Role role = guild.getRoleById(targetIdLong);
        category.upsertPermissionOverride(role)
            .setPermissions(newAllowed, newDenied)
            .complete();
      }

      List<String> afterAllowed = permissionListToString(newAllowed);
      List<String> afterDenied = permissionListToString(newDenied);

      LOGGER.info(
          "修改類別 {} 權限: 目標={}, 類型={}, 允許={}, 拒絕={}",
          category.getIdLong(),
          targetIdLong,
          targetType,
          newAllowed,
          newDenied);

      return buildSuccessResponse(
          category,
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
      Category category,
      long targetId,
      String targetType,
      List<String> beforeAllowed,
      List<String> beforeDenied,
      List<String> afterAllowed,
      List<String> afterDenied) {
    StringBuilder json = new StringBuilder();
    json.append("{\n");
    json.append("  \"success\": true,\n");
    json.append("  \"message\": \"類別權限修改成功\",\n");
    json.append("  \"categoryId\": \"").append(category.getIdLong()).append("\",\n");
    json.append("  \"categoryName\": \"").append(escapeJson(category.getName())).append("\",\n");
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
    """.formatted(escapeJson(error));
  }
}
```

**Step 4: 執行測試確認通過**

Run: `mvn test -Dtest=LangChain4jModifyCategoryPermissionsToolTest -q`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/ltdjms/discord/aiagent/services/tools/LangChain4jModifyCategoryPermissionsTool.java
git add src/test/java/ltdjms/discord/aiagent/unit/services/tools/LangChain4jModifyCategoryPermissionsToolTest.java
git commit -m "feat(aiagent): add LangChain4jModifyCategoryPermissionsTool for category permission management"
```

---

## Task 3: 實作 LangChain4jCreateRoleTool

**Files:**
- Create: `src/main/java/ltdjms/discord/aiagent/services/tools/LangChain4jCreateRoleTool.java`
- Create: `src/test/java/ltdjms/discord/aiagent/unit/services/tools/LangChain4jCreateRoleToolTest.java`

**Step 1: 寫失敗的單元測試**

Create: `src/test/java/ltdjms/discord/aiagent/unit/services/tools/LangChain4jCreateRoleToolTest.java`

```java
package ltdjms.discord.aiagent.unit.services.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.langchain4j.invocation.InvocationParameters;
import ltdjms.discord.aiagent.domain.RoleCreateInfo;
import ltdjms.discord.aiagent.services.tools.LangChain4jCreateRoleTool;
import ltdjms.discord.shared.di.JDAProvider;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.requests.restaction.RoleAction;

@DisplayName("T027: LangChain4jCreateRoleTool 單元測試")
class LangChain4jCreateRoleToolTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_USER_ID = 222222222222222222L;

  private LangChain4jCreateRoleTool tool;
  private Guild mockGuild;
  private JDA mockJda;
  private InvocationParameters parameters;

  @BeforeEach
  void setUp() {
    mockGuild = mock(Guild.class);
    mockJda = mock(JDA.class);
    tool = new LangChain4jCreateRoleTool();
    parameters = new InvocationParameters();

    parameters.put("guildId", TEST_GUILD_ID);
    parameters.put("channelId", 999999999999999999L);
    parameters.put("userId", TEST_USER_ID);

    JDAProvider.setJda(mockJda);
    when(mockJda.getGuildById(TEST_GUILD_ID)).thenReturn(mockGuild);

    Role mockRole = mock(Role.class);
    when(mockRole.getIdLong()).thenReturn(111111111111111111L);
    when(mockRole.getName()).thenReturn("測試角色");
    when(mockRole.getColorRaw()).thenReturn(16711680);

    RoleAction mockAction = mock(RoleAction.class);
    when(mockAction.complete()).thenReturn(mockRole);
    when(mockGuild.createRole()).thenReturn(mockAction);
  }

  @AfterEach
  void tearDown() {
    JDAProvider.clear();
  }

  @Nested
  @DisplayName("參數驗證測試")
  class ParameterValidationTests {

    @Test
    @DisplayName("缺少 name 應返回錯誤")
    void missingNameShouldReturnError() {
      String result = tool.createRole(null, null, null, null, null, parameters);

      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("角色名稱不能為空");
    }

    @Test
    @DisplayName("空字串 name 應返回錯誤")
    void emptyNameShouldReturnError() {
      String result = tool.createRole("   ", null, null, null, null, parameters);

      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("角色名稱不能為空");
    }
  }
}
```

**Step 2: 執行測試確認失敗**

Run: `mvn test -Dtest=LangChain4jCreateRoleToolTest -q`
Expected: FAIL with "cannot find symbol"

**Step 3: 實作最小可通過代碼**

Create: `src/main/java/ltdjms/discord/aiagent/services/tools/LangChain4jCreateRoleTool.java`

```java
package ltdjms.discord.aiagent.services.tools;

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
import ltdjms.discord.aiagent.domain.RoleCreateInfo;
import ltdjms.discord.shared.di.JDAProvider;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

/**
 * 創建 Discord 角色工具（LangChain4J 版本）。
 */
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
      return buildErrorResponse("角色名稱不能為空");
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

      Role role = guild.createRole()
          .setName(name.trim())
          .setColor(colorInt)
          .setPermissions(permissionSet)
          .setHoisted(hoistValue)
          .setMentionable(mentionableValue)
          .complete();

      LOGGER.info("創建角色成功: guildId={}, roleId={}, name={}", guildId, role.getIdLong(), role.getName());

      return buildSuccessResponse(role);

    } catch (InsufficientPermissionException e) {
      LOGGER.warn("權限不足: {}", e.getMessage());
      return buildErrorResponse("權限不足: " + e.getMessage());

    } catch (Exception e) {
      LOGGER.error("創建角色失敗", e);
      return buildErrorResponse("創建失敗: " + e.getMessage());
    }
  }

  private String buildSuccessResponse(Role role) {
    StringBuilder json = new StringBuilder();
    json.append("{\n");
    json.append("  \"success\": true,\n");
    json.append("  \"message\": \"角色創建成功\",\n");
    json.append("  \"role\": {\n");
    json.append("    \"id\": \"").append(role.getIdLong()).append("\",\n");
    json.append("    \"name\": \"").append(escapeJson(role.getName())).append("\",\n");
    json.append("    \"color\": ").append(role.getColorRaw()).append(",\n");
    json.append("    \"colorHex\": \"#").append(String.format("%06X", role.getColorRaw())).append("\",\n");

    List<String> permissions = role.getPermissions().stream()
        .map(Permission::name)
        .sorted()
        .collect(Collectors.toList());
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
    """.formatted(escapeJson(error));
  }
}
```

**Step 4: 執行測試確認通過**

Run: `mvn test -Dtest=LangChain4jCreateRoleToolTest -q`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/ltdjms/discord/aiagent/services/tools/LangChain4jCreateRoleTool.java
git add src/test/java/ltdjms/discord/aiagent/unit/services/tools/LangChain4jCreateRoleToolTest.java
git commit -m "feat(aiagent): add LangChain4jCreateRoleTool for role creation"
```

---

## Task 4: 實作 LangChain4jGetRolePermissionsTool

**Files:**
- Create: `src/main/java/ltdjms/discord/aiagent/services/tools/LangChain4jGetRolePermissionsTool.java`
- Create: `src/test/java/ltdjms/discord/aiagent/unit/services/tools/LangChain4jGetRolePermissionsToolTest.java`

**Step 1: 寫失敗的單元測試**

Create: `src/test/java/ltdjms/discord/aiagent/unit/services/tools/LangChain4jGetRolePermissionsToolTest.java`

```java
package ltdjms.discord.aiagent.unit.services.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.EnumSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.langchain4j.invocation.InvocationParameters;
import ltdjms.discord.aiagent.services.tools.LangChain4jGetRolePermissionsTool;
import ltdjms.discord.shared.di.JDAProvider;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;

@DisplayName("T028: LangChain4jGetRolePermissionsTool 單元測試")
class LangChain4jGetRolePermissionsToolTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_ROLE_ID = 111111111111111111L;
  private static final long TEST_USER_ID = 222222222222222222L;

  private LangChain4jGetRolePermissionsTool tool;
  private Guild mockGuild;
  private JDA mockJda;
  private InvocationParameters parameters;

  @BeforeEach
  void setUp() {
    mockGuild = mock(Guild.class);
    mockJda = mock(JDA.class);
    tool = new LangChain4jGetRolePermissionsTool();
    parameters = new InvocationParameters();

    parameters.put("guildId", TEST_GUILD_ID);
    parameters.put("channelId", 999999999999999999L);
    parameters.put("userId", TEST_USER_ID);

    JDAProvider.setJda(mockJda);
    when(mockJda.getGuildById(TEST_GUILD_ID)).thenReturn(mockGuild);

    Role mockRole = mock(Role.class);
    when(mockRole.getIdLong()).thenReturn(TEST_ROLE_ID);
    when(mockRole.getName()).thenReturn("版主");
    when(mockRole.getColorRaw()).thenReturn(3447003);
    when(mockRole.isHoisted()).thenReturn(true);
    when(mockRole.isMentionable()).thenReturn(true);
    when(mockRole.getPosition()).thenReturn(5);
    when(mockRole.isManaged()).thenReturn(false);
    when(mockRole.getPermissions()).thenReturn(EnumSet.of(
        Permission.ADMINISTRATOR,
        Permission.MANAGE_CHANNELS,
        Permission.VIEW_CHANNEL
    ));

    when(mockGuild.getRoleById(TEST_ROLE_ID)).thenReturn(mockRole);
  }

  @AfterEach
  void tearDown() {
    JDAProvider.clear();
  }

  @Nested
  @DisplayName("參數驗證測試")
  class ParameterValidationTests {

    @Test
    @DisplayName("缺少 roleId 應返回錯誤")
    void missingRoleIdShouldReturnError() {
      String result = tool.getRolePermissions(null, parameters);

      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("roleId 未提供");
    }
  }

  @Nested
  @DisplayName("正常操作測試")
  class SuccessTests {

    @Test
    @DisplayName("成功獲取角色權限")
    void shouldGetRolePermissionsSuccessfully() {
      String result = tool.getRolePermissions("111111111111111111", parameters);

      assertThat(result).contains("\"success\": true");
      assertThat(result).contains("\"id\": 111111111111111111");
      assertThat(result).contains("\"name\": \"版主\"");
      assertThat(result).contains("ADMINISTRATOR");
    }
  }
}
```

**Step 2: 執行測試確認失敗**

Run: `mvn test -Dtest=LangChain4jGetRolePermissionsToolTest -q`
Expected: FAIL with "cannot find symbol"

**Step 3: 實作最小可通過代碼**

Create: `src/main/java/ltdjms/discord/aiagent/services/tools/LangChain4jGetRolePermissionsTool.java`

```java
package ltdjms.discord.aiagent.services.tools;

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

/**
 * 讀取 Discord 角色權限工具（LangChain4J 版本）。
 */
public final class LangChain4jGetRolePermissionsTool {

  private static final Logger LOGGER = LoggerFactory.getLogger(LangChain4jGetRolePermissionsTool.class);

  @Inject
  public LangChain4jGetRolePermissionsTool() {
    // JDA 將從 JDAProvider 延遲獲取
  }

  @Tool(
      """
      讀取 Discord 角色的伺服器層級權限。

      使用場景：
      - 當需要查看角色擁有的權限時使用
      - 權限審查或問題排查時使用

      返回資訊：
      - 角色基本資訊
      - 完整的伺服器層級權限列表
      - 是否為管理員角色
      """)
  public String getRolePermissions(
      @P(value = "角色 ID", required = true) String roleId,
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

    // 4. 獲取角色
    Role role = guild.getRoleById(roleIdLong);
    if (role == null) {
      return buildErrorResponse("找不到指定的角色");
    }

    try {
      LOGGER.info("讀取角色權限: guildId={}, roleId={}", guildId, roleIdLong);
      return buildSuccessResponse(role);

    } catch (Exception e) {
      LOGGER.error("讀取角色權限失敗", e);
      return buildErrorResponse("讀取失敗: " + e.getMessage());
    }
  }

  private long parseId(String id) {
    String trimmed = id.trim();
    if (trimmed.startsWith("<@&") && trimmed.endsWith(">")) {
      trimmed = trimmed.substring(3, trimmed.length() - 1);
    }
    return Long.parseLong(trimmed);
  }

  private String buildSuccessResponse(Role role) {
    StringBuilder json = new StringBuilder();
    json.append("{\n");
    json.append("  \"success\": true,\n");
    json.append("  \"role\": {\n");
    json.append("    \"id\": \"").append(role.getIdLong()).append("\",\n");
    json.append("    \"name\": \"").append(escapeJson(role.getName())).append("\",\n");
    json.append("    \"color\": ").append(role.getColorRaw()).append(",\n");
    json.append("    \"colorHex\": \"#").append(String.format("%06X", role.getColorRaw())).append("\",\n");
    json.append("    \"hoisted\": ").append(role.isHoisted()).append(",\n");
    json.append("    \"mentionable\": ").append(role.isMentionable()).append(",\n");
    json.append("    \"position\": ").append(role.getPosition()).append(",\n");
    json.append("    \"managed\": ").append(role.isManaged()).append(",\n");

    List<String> permissions = role.getPermissions().stream()
        .map(Permission::name)
        .sorted()
        .collect(Collectors.toList());
    json.append("    \"permissions\": ").append(permissionListToJson(permissions)).append(",\n");
    json.append("    \"permissionCount\": ").append(permissions.size()).append(",\n");

    boolean isAdmin = role.getPermissions().contains(Permission.ADMINISTRATOR);
    json.append("    \"isAdmin\": ").append(isAdmin).append("\n");
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
    """.formatted(escapeJson(error));
  }
}
```

**Step 4: 執行測試確認通過**

Run: `mvn test -Dtest=LangChain4jGetRolePermissionsToolTest -q`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/ltdjms/discord/aiagent/services/tools/LangChain4jGetRolePermissionsTool.java
git add src/test/java/ltdjms/discord/aiagent/unit/services/tools/LangChain4jGetRolePermissionsToolTest.java
git commit -m "feat(aiagent): add LangChain4jGetRolePermissionsTool for reading role permissions"
```

---

## Task 5: 實作 LangChain4jModifyRolePermissionsTool

**Files:**
- Create: `src/main/java/ltdjms/discord/aiagent/services/tools/LangChain4jModifyRolePermissionsTool.java`
- Create: `src/test/java/ltdjms/discord/aiagent/unit/services/tools/LangChain4jModifyRolePermissionsToolTest.java`

**Step 1: 寫失敗的單元測試**

Create: `src/test/java/ltdjms/discord/aiagent/unit/services/tools/LangChain4jModifyRolePermissionsToolTest.java`

```java
package ltdjms.discord.aiagent.unit.services.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.langchain4j.invocation.InvocationParameters;
import ltdjms.discord.aiagent.services.tools.LangChain4jModifyRolePermissionsTool;
import ltdjms.discord.shared.di.JDAProvider;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.requests.restaction.RoleAction;

@DisplayName("T029: LangChain4jModifyRolePermissionsTool 單元測試")
class LangChain4jModifyRolePermissionsToolTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_ROLE_ID = 111111111111111111L;
  private static final long TEST_USER_ID = 222222222222222222L;

  private LangChain4jModifyRolePermissionsTool tool;
  private Guild mockGuild;
  private JDA mockJda;
  private InvocationParameters parameters;

  @BeforeEach
  void setUp() {
    mockGuild = mock(Guild.class);
    mockJda = mock(JDA.class);
    tool = new LangChain4jModifyRolePermissionsTool();
    parameters = new InvocationParameters();

    parameters.put("guildId", TEST_GUILD_ID);
    parameters.put("channelId", 999999999999999999L);
    parameters.put("userId", TEST_USER_ID);

    JDAProvider.setJda(mockJda);
    when(mockJda.getGuildById(TEST_GUILD_ID)).thenReturn(mockGuild);

    Role mockRole = mock(Role.class);
    when(mockRole.getIdLong()).thenReturn(TEST_ROLE_ID);
    when(mockRole.getName()).thenReturn("版主");
    when(mockRole.getPermissionsRaw()).thenReturn(66048L); // VIEW_CHANNEL + MESSAGE_SEND

    when(mockGuild.getRoleById(TEST_ROLE_ID)).thenReturn(mockRole);
  }

  @AfterEach
  void tearDown() {
    JDAProvider.clear();
  }

  @Nested
  @DisplayName("參數驗證測試")
  class ParameterValidationTests {

    @Test
    @DisplayName("缺少 roleId 應返回錯誤")
    void missingRoleIdShouldReturnError() {
      String result = tool.modifyRolePermissions(null, null, null, parameters);

      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("roleId 未提供");
    }

    @Test
    @DisplayName("未指定任何操作應返回錯誤")
    void noChangesShouldReturnError() {
      String result = tool.modifyRolePermissions("111", null, null, parameters);

      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("未指定任何權限修改操作");
    }
  }
}
```

**Step 2: 執行測試確認失敗**

Run: `mvn test -Dtest=LangChain4jModifyRolePermissionsToolTest -q`
Expected: FAIL with "cannot find symbol"

**Step 3: 實作最小可通過代碼**

Create: `src/main/java/ltdjms/discord/aiagent/services/tools/LangChain4jModifyRolePermissionsTool.java`

```java
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

/**
 * 修改 Discord 角色權限工具（LangChain4J 版本）。
 */
public final class LangChain4jModifyRolePermissionsTool {

  private static final Logger LOGGER = LoggerFactory.getLogger(LangChain4jModifyRolePermissionsTool.class);

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

    // 4. 獲取角色
    Role role = guild.getRoleById(roleIdLong);
    if (role == null) {
      return buildErrorResponse("找不到指定的角色");
    }

    try {
      // 5. 獲取現有權限
      long currentPermissionsRaw = role.getPermissionsRaw();
      EnumSet<Permission> currentPermissions = Permission.getPermissions(currentPermissionsRaw);

      List<String> beforePermissions = permissionListToString(currentPermissions);

      // 6. 計算新權限
      long newPermissionsRaw = currentPermissionsRaw;

      if (permissionsToAdd != null && !permissionsToAdd.isEmpty()) {
        for (String permName : permissionsToAdd) {
          try {
            Permission perm = Permission.valueOf(permName.toUpperCase().trim());
            newPermissionsRaw |= perm.getValue();
          } catch (IllegalArgumentException e) {
            LOGGER.warn("無效的權限名稱: {}", permName);
          }
        }
      }

      if (permissionsToRemove != null && !permissionsToRemove.isEmpty()) {
        for (String permName : permissionsToRemove) {
          try {
            Permission perm = Permission.valueOf(permName.toUpperCase().trim());
            newPermissionsRaw &= ~perm.getValue();
          } catch (IllegalArgumentException e) {
            LOGGER.warn("無效的權限名稱: {}", permName);
          }
        }
      }

      EnumSet<Permission> newPermissions = Permission.getPermissions(newPermissionsRaw);
      List<String> afterPermissions = permissionListToString(newPermissions);

      // 7. 應用修改
      role.getManager().setPermissions(newPermissionsRaw).complete();

      LOGGER.info(
          "修改角色權限: roleId={}, before={}, after={}",
          roleIdLong,
          currentPermissions,
          newPermissions);

      return buildSuccessResponse(
          role,
          currentPermissionsRaw,
          newPermissionsRaw,
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
    json.append("    \"permissions\": ").append(permissionListToJson(beforePermissions)).append(",\n");
    json.append("    \"count\": ").append(beforePermissions.size()).append(",\n");
    json.append("    \"raw\": ").append(beforeRaw).append("\n");
    json.append("  },\n");
    json.append("  \"after\": {\n");
    json.append("    \"permissions\": ").append(permissionListToJson(afterPermissions)).append(",\n");
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
    """.formatted(escapeJson(error));
  }
}
```

**Step 4: 執行測試確認通過**

Run: `mvn test -Dtest=LangChain4jModifyRolePermissionsToolTest -q`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/ltdjms/discord/aiagent/services/tools/LangChain4jModifyRolePermissionsTool.java
git add src/test/java/ltdjms/discord/aiagent/unit/services/tools/LangChain4jModifyRolePermissionsToolTest.java
git commit -m "feat(aiagent): add LangChain4jModifyRolePermissionsTool for modifying role permissions"
```

---

## Task 6: 在 AIAgentModule 中註冊新工具

**Files:**
- Modify: `src/main/java/ltdjms/discord/shared/di/AIAgentModule.java`

**Step 1: 在 AIAgentModule 添加 provider 方法**

在 `AIAgentModule.java` 中的 `provideLangChain4jModifyChannelPermissionsTool` 方法後添加：

```java
/**
 * 提供 LangChain4J 修改類別權限工具。
 *
 * @return LangChain4jModifyCategoryPermissionsTool 實例
 */
@Provides
@Singleton
public LangChain4jModifyCategoryPermissionsTool provideLangChain4jModifyCategoryPermissionsTool() {
  return new LangChain4jModifyCategoryPermissionsTool();
}

/**
 * 提供 LangChain4J 創建角色工具。
 *
 * @return LangChain4jCreateRoleTool 實例
 */
@Provides
@Singleton
public LangChain4jCreateRoleTool provideLangChain4jCreateRoleTool() {
  return new LangChain4jCreateRoleTool();
}

/**
 * 提供 LangChain4J 獲取角色權限工具。
 *
 * @return LangChain4jGetRolePermissionsTool 實例
 */
@Provides
@Singleton
public LangChain4jGetRolePermissionsTool provideLangChain4jGetRolePermissionsTool() {
  return new LangChain4jGetRolePermissionsTool();
}

/**
 * 提供 LangChain4J 修改角色權限工具。
 *
 * @return LangChain4jModifyRolePermissionsTool 實例
 */
@Provides
@Singleton
public LangChain4jModifyRolePermissionsTool provideLangChain4jModifyRolePermissionsTool() {
  return new LangChain4jModifyRolePermissionsTool();
}
```

**Step 2: 編譯驗證**

Run: `mvn compile -q`
Expected: 編譯成功，無錯誤

**Step 3: Commit**

```bash
git add src/main/java/ltdjms/discord/shared/di/AIAgentModule.java
git commit -m "feat(aiagent): register new permission and role tools in AIAgentModule"
```

---

## Task 7: 在 LangChain4jAIChatService 中註冊新工具

**Files:**
- Modify: `src/main/java/ltdjms/discord/aichat/services/LangChain4jAIChatService.java`

**Step 1: 添加建構函數參數**

修改建構函數簽名和字段，在 `modifyChannelPermissionsTool` 後添加：

```java
private final LangChain4jModifyCategoryPermissionsTool modifyCategoryPermissionsTool;
private final LangChain4jCreateRoleTool createRoleTool;
private final LangChain4jGetRolePermissionsTool getRolePermissionsTool;
private final LangChain4jModifyRolePermissionsTool modifyRolePermissionsTool;
```

**Step 2: 修改建構函數**

在建構函數參數列表和字段賦值中添加新工具：

```java
public LangChain4jAIChatService(
    AIServiceConfig config,
    PromptLoader promptLoader,
    DomainEventPublisher eventPublisher,
    StreamingChatModel streamingChatModel,
    ChatMemoryProvider chatMemoryProvider,
    ToolExecutionInterceptor toolExecutionInterceptor,
    InMemoryToolCallHistory toolCallHistory,
    LangChain4jCreateChannelTool createChannelTool,
    LangChain4jCreateCategoryTool createCategoryTool,
    LangChain4jListChannelsTool listChannelsTool,
    LangChain4jListCategoriesTool listCategoriesTool,
    LangChain4jListRolesTool listRolesTool,
    LangChain4jGetChannelPermissionsTool getChannelPermissionsTool,
    LangChain4jModifyChannelPermissionsTool modifyChannelPermissionsTool,
    LangChain4jModifyCategoryPermissionsTool modifyCategoryPermissionsTool,
    LangChain4jCreateRoleTool createRoleTool,
    LangChain4jGetRolePermissionsTool getRolePermissionsTool,
    LangChain4jModifyRolePermissionsTool modifyRolePermissionsTool,
    AIAgentChannelConfigService agentChannelConfigService) {
  // ... 現有代碼 ...
  this.modifyCategoryPermissionsTool = modifyCategoryPermissionsTool;
  this.createRoleTool = createRoleTool;
  this.getRolePermissionsTool = getRolePermissionsTool;
  this.modifyRolePermissionsTool = modifyRolePermissionsTool;
  // ... 更新 agentServiceFactory 和 LOG.info ...
}
```

**Step 3: 在 DefaultAgentServiceFactory 中添加工具**

修改 `DefaultAgentServiceFactory` 的建構函數和 `create` 方法：

```java
DefaultAgentServiceFactory(
    StreamingChatModel streamingChatModel,
    ChatMemoryProvider chatMemoryProvider,
    LangChain4jListChannelsTool listChannelsTool,
    LangChain4jListCategoriesTool listCategoriesTool,
    LangChain4jListRolesTool listRolesTool,
    LangChain4jGetChannelPermissionsTool getChannelPermissionsTool,
    LangChain4jModifyChannelPermissionsTool modifyChannelPermissionsTool,
    LangChain4jCreateChannelTool createChannelTool,
    LangChain4jCreateCategoryTool createCategoryTool,
    LangChain4jModifyCategoryPermissionsTool modifyCategoryPermissionsTool,
    LangChain4jCreateRoleTool createRoleTool,
    LangChain4jGetRolePermissionsTool getRolePermissionsTool,
    LangChain4jModifyRolePermissionsTool modifyRolePermissionsTool) {
  this.streamingChatModel = streamingChatModel;
  this.chatMemoryProvider = chatMemoryProvider;
  this.listChannelsTool = listChannelsTool;
  this.listCategoriesTool = listCategoriesTool;
  this.listRolesTool = listRolesTool;
  this.getChannelPermissionsTool = getChannelPermissionsTool;
  this.modifyChannelPermissionsTool = modifyChannelPermissionsTool;
  this.createChannelTool = createChannelTool;
  this.createCategoryTool = createCategoryTool;
  this.modifyCategoryPermissionsTool = modifyCategoryPermissionsTool;
  this.createRoleTool = createRoleTool;
  this.getRolePermissionsTool = getRolePermissionsTool;
  this.modifyRolePermissionsTool = modifyRolePermissionsTool;
}

@Override
public LangChain4jAgentService create(boolean agentToolsEnabled, String systemPrompt) {
  var builder =
      AiServices.builder(LangChain4jAgentService.class)
          .streamingChatModel(streamingChatModel)
          .chatMemoryProvider(chatMemoryProvider)
          .systemMessageProvider(memoryId -> systemPrompt);

  if (agentToolsEnabled) {
    builder.tools(
        createChannelTool,
        createCategoryTool,
        listChannelsTool,
        listCategoriesTool,
        listRolesTool,
        getChannelPermissionsTool,
        modifyChannelPermissionsTool,
        modifyCategoryPermissionsTool,
        createRoleTool,
        getRolePermissionsTool,
        modifyRolePermissionsTool);
  }

  return builder.build();
}
```

**Step 4: 更新 LOG.info**

將工具數量從 7 更新為 11：

```java
LOG.info(
    "LangChain4jAIChatService initialized with model: {}, tools: 11, reasoning: {}",
    config.model(),
    config.showReasoning());
```

**Step 5: 編譯驗證**

Run: `mvn compile -q`
Expected: 編譯成功，無錯誤

**Step 6: Commit**

```bash
git add src/main/java/ltdjms/discord/aichat/services/LangChain4jAIChatService.java
git commit -m "feat(aiagent): register new permission and role tools in LangChain4jAIChatService"
```

---

## Task 8: 執行完整測試套件

**Step 1: 執行所有單元測試**

Run: `mvn test -q`
Expected: 所有測試通過

**Step 2: 執行整合測試（如有）**

Run: `mvn test -Dtest=*IntegrationTest -q`
Expected: 所有整合測試通過

**Step 3: 執行覆蓋率檢查**

Run: `mvn verify -q`
Expected: 覆蓋率 >= 80%

**Step 4: Commit**

```bash
git add .
git commit -m "test(aiagent): all tests passing for new permission and role tools"
```

---

## 相關文檔參考

- [修改頻道權限工具實作計畫](docs/plans/2026-01-02-modify-channel-permissions-tool.md)
- [列出角色工具設計](docs/plans/2026-01-01-list-roles-tool-design.md)
- [類別與角色權限工具設計](docs/plans/2026-01-05-role-category-permission-tools-design.md)
