# Get Channel Permissions Tool Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 新增一個 AI Agent 工具，能夠讀取特定 Discord 頻道的權限設定（包含角色和成員的權限覆寫）

**Architecture:** 使用 LangChain4J 的 `@Tool` 註解創建新工具，通過 JDA 的 `GuildChannel.getPermissionOverrides()` API 獲取頻道權限覆寫，返回 JSON 格式的權限資訊

**Tech Stack:** Java 17 + JDA 5.2.2 + LangChain4J 1.7.1 + JUnit 5 + Mockito

---

## Task 1: 創建工具類別主檔案

**Files:**
- Create: `src/main/java/ltdjms/discord/aiagent/services/tools/LangChain4jGetChannelPermissionsTool.java`

**Step 1: Write the failing test**

先創建對應的測試檔案結構（詳見 Task 2），這裡先實作工具主檔案。

**Step 2: Create the tool class with basic structure**

```java
package ltdjms.discord.aiagent.services.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import ltdjms.discord.shared.di.JDAProvider;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;

/**
 * 獲取 Discord 頻道權限設定工具（LangChain4J 版本）。
 *
 * <p>使用 LangChain4J 的 @Tool 註解，通過 ToolExecutionContext 獲取執行上下文。
 */
public final class LangChain4jGetChannelPermissionsTool {

  private static final Logger LOGGER = LoggerFactory.getLogger(LangChain4jGetChannelPermissionsTool.class);

  @Inject
  public LangChain4jGetChannelPermissionsTool() {
    // JDA 將從 JDAProvider 延遲獲取
  }

  /**
   * 獲取 Discord 頻道的權限覆寫資訊。
   *
   * @param channelId 頻道 ID（字串格式，避免 JSON 精度損失）
   * @param parameters 調用參數（包含 guildId、channelId、userId）
   * @return 權限覆寫列表 JSON 字串
   */
  @Tool(
      """
      獲取 Discord 頻道的權限覆寫資訊。

      使用場景：
      - 當用戶詢問特定頻道的權限設定時使用
      - 需要查看哪些角色或成員有特殊權限時使用
      - 檢查頻道的訪問控制設定時使用

      返回資訊：
      - 權限覆寫總數
      - 每個覆寫的類型（角色/成員）、ID、允許的權限、拒絕的權限

      重要：
      - 所有 ID 以「字串」形式返回，避免 JSON 數字溢位造成精度損失
      - 返回的權限覆寫包含角色和成員兩種類型
      """)
  public String getChannelPermissions(
      @P(
              value =
                  """
                  要查詢的頻道 ID。

                  必須是有效的 Discord 頻道 ID（字串格式）。

                  範例：
                  - "123456789012345678"：查詢指定頻道的權限
                  """,
              required = true)
          String channelId,
      InvocationParameters parameters) {

    // 1. 驗證 channelId 參數
    if (channelId == null || channelId.isBlank()) {
      LOGGER.error("LangChain4jGetChannelPermissionsTool: channelId 未提供");
      return buildErrorResponse("channelId 未提供");
    }

    // 解析頻道 ID
    long channelIdLong;
    try {
      channelIdLong = parseChannelId(channelId);
    } catch (NumberFormatException e) {
      String errorMsg = String.format("無效的頻道 ID: %s", channelId);
      LOGGER.warn("LangChain4jGetChannelPermissionsTool: {}", errorMsg);
      return buildErrorResponse(errorMsg);
    }

    // 2. 從 InvocationParameters 獲取執行上下文
    Long guildId = parameters.get("guildId");
    if (guildId == null) {
      LOGGER.error("LangChain4jGetChannelPermissionsTool: guildId 未設置");
      return buildErrorResponse("guildId 未設置");
    }

    // 3. 獲取 Guild
    Guild guild = JDAProvider.getJda().getGuildById(guildId);
    if (guild == null) {
      String errorMsg = String.format("找不到指定的伺服器: %d", guildId);
      LOGGER.warn("LangChain4jGetChannelPermissionsTool: {}", errorMsg);
      return buildErrorResponse("找不到伺服器");
    }

    // 4. 獲取頻道
    GuildChannel channel = guild.getGuildChannelById(channelIdLong);
    if (channel == null) {
      String errorMsg = String.format("找不到指定的頻道: %s", channelId);
      LOGGER.warn("LangChain4jGetChannelPermissionsTool: {}", errorMsg);
      return buildErrorResponse("找不到頻道");
    }

    try {
      // 5. 獲取所有權限覆寫
      List<PermissionOverride> overrides = channel.getPermissionOverrides();
      List<Map<String, Object>> permissionInfos = new ArrayList<>();

      for (PermissionOverride override : overrides) {
        permissionInfos.add(buildPermissionInfo(override));
      }

      // 6. 返回 JSON 格式結果
      String jsonResult = buildJsonResult(channel, permissionInfos);
      LOGGER.info("LangChain4jGetChannelPermissionsTool: 找到 {} 個權限覆寫", permissionInfos.size());
      return jsonResult;

    } catch (Exception e) {
      String errorMsg = String.format("獲取頻道權限失敗: %s", e.getMessage());
      LOGGER.error("LangChain4jGetChannelPermissionsTool: {}", errorMsg, e);
      return buildErrorResponse(errorMsg);
    }
  }

  /**
   * 解析頻道 ID（支援字串和數字格式）。
   *
   * @param channelId 頻道 ID
   * @return 解析後的 long 值
   */
  private long parseChannelId(String channelId) {
    String trimmed = channelId.trim();
    // 移除可能的 <#> 和 <> 標記
    if (trimmed.startsWith("<#") && trimmed.endsWith(">")) {
      trimmed = trimmed.substring(2, trimmed.length() - 1);
    } else if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
      trimmed = trimmed.substring(1, trimmed.length() - 1);
    }
    return Long.parseLong(trimmed);
  }

  /**
   * 構建權限覆寫資訊 Map。
   *
   * @param override 權限覆寫
   * @return 權限覆寫資訊 Map
   */
  private Map<String, Object> buildPermissionInfo(PermissionOverride override) {
    Map<String, Object> info = new LinkedHashMap<>();
    info.put("id", String.valueOf(override.getIdLong()));
    info.put("type", override.isRoleOverride() ? "role" : "member");

    // 添加允許的權限
    List<String> allowed = permissionListToString(override.getAllowed());
    if (!allowed.isEmpty()) {
      info.put("allowed", allowed);
    }

    // 添加拒絕的權限
    List<String> denied = permissionListToString(override.getDenied());
    if (!denied.isEmpty()) {
      info.put("denied", denied);
    }

    return info;
  }

  /**
   * 將 EnumSet<Permission> 轉換為可讀的字串列表。
   *
   * @param permissions 權限 EnumSet
   * @return 權限名稱列表
   */
  private List<String> permissionListToString(java.util.EnumSet<net.dv8tion.jda.api.Permission> permissions) {
    if (permissions == null || permissions.isEmpty()) {
      return List.of();
    }

    List<String> permissionNames = new ArrayList<>();
    for (net.dv8tion.jda.api.Permission permission : permissions) {
      permissionNames.add(permission.name());
    }
    return permissionNames;
  }

  /**
   * 構建 JSON 格式結果。
   *
   * @param channel 頻道
   * @param permissionInfos 權限覆寫資訊列表
   * @return JSON 字串
   */
  private String buildJsonResult(GuildChannel channel, List<Map<String, Object>> permissionInfos) {
    StringBuilder json = new StringBuilder();
    json.append("{\n");
    json.append("  \"success\": true,\n");
    json.append("  \"channelId\": \"").append(channel.getIdLong()).append("\",\n");
    json.append("  \"channelName\": \"").append(escapeJson(channel.getName())).append("\",\n");
    json.append("  \"count\": ").append(permissionInfos.size()).append(",\n");
    json.append("  \"overrides\": [\n");

    for (int i = 0; i < permissionInfos.size(); i++) {
      if (i > 0) {
        json.append(",\n");
      }
      Map<String, Object> info = permissionInfos.get(i);
      json.append("    {\n");
      json.append("      \"id\": \"").append(info.get("id")).append("\",\n");
      json.append("      \"type\": \"").append(info.get("type")).append("\"");

      @SuppressWarnings("unchecked")
      List<String> allowed = (List<String>) info.get("allowed");
      if (allowed != null && !allowed.isEmpty()) {
        json.append(",\n");
        json.append("      \"allowed\": [");
        for (int j = 0; j < allowed.size(); j++) {
          if (j > 0) json.append(", ");
          json.append("\"").append(allowed.get(j)).append("\"");
        }
        json.append("]");
      }

      @SuppressWarnings("unchecked")
      List<String> denied = (List<String>) info.get("denied");
      if (denied != null && !denied.isEmpty()) {
        json.append(",\n");
        json.append("      \"denied\": [");
        for (int j = 0; j < denied.size(); j++) {
          if (j > 0) json.append(", ");
          json.append("\"").append(denied.get(j)).append("\"");
        }
        json.append("]");
      }

      json.append("\n    }");
    }

    json.append("\n  ]\n");
    json.append("}");
    return json.toString();
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
    """.formatted(error);
  }
}
```

**Step 3: Run test to verify it compiles**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

---

## Task 2: 創建單元測試

**Files:**
- Create: `src/test/java/ltdjms/discord/aiagent/unit/services/tools/LangChain4jGetChannelPermissionsToolTest.java`

**Step 1: Write the failing test**

```java
package ltdjms.discord.aiagent.unit.services.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.langchain4j.invocation.InvocationParameters;
import ltdjms.discord.aiagent.services.ToolExecutionContext;
import ltdjms.discord.aiagent.services.tools.LangChain4jGetChannelPermissionsTool;
import ltdjms.discord.shared.di.JDAProvider;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;

/**
 * 測試 {@link LangChain4jGetChannelPermissionsTool} 的工具執行邏輯。
 *
 * <p>測試範圍：
 *
 * <ul>
 *   <li>T024: LangChain4jGetChannelPermissionsTool 單元測試
 * </ul>
 *
 * <p>測試案例涵蓋：
 *
 * <ul>
 *   <li>正常情況：成功獲取頻道權限
 *   <li>參數驗證：無效的頻道 ID、空參數
 *   <li>錯誤處理：找不到頻道、找不到伺服器
 *   <li>結果格式：JSON 格式驗證、權限覆寫資訊
 * </ul>
 */
@DisplayName("T024: LangChain4jGetChannelPermissionsTool 單元測試")
class LangChain4jGetChannelPermissionsToolTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_CHANNEL_ID = 999999999999999999L;
  private static final long TEST_USER_ID = 987654321098765432L;
  private static final long TEST_ROLE_ID = 111111111111111111L;
  private static final long TEST_MEMBER_ID = 222222222222222222L;

  private Guild mockGuild;
  private JDA mockJda;
  private LangChain4jGetChannelPermissionsTool tool;

  @BeforeEach
  void setUp() {
    mockGuild = mock(Guild.class);
    mockJda = mock(JDA.class);
    tool = new LangChain4jGetChannelPermissionsTool();

    // 設定 JDAProvider
    JDAProvider.setJda(mockJda);

    // 設定 JDA 基本行為
    when(mockJda.getGuildById(TEST_GUILD_ID)).thenReturn(mockGuild);

    // 設定 ToolExecutionContext
    ToolExecutionContext.setContext(TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);
  }

  @AfterEach
  void tearDown() {
    ToolExecutionContext.clearContext();
    JDAProvider.clear();
  }

  /**
   * 創建 mock InvocationParameters。
   *
   * @return mock 的 InvocationParameters
   */
  private InvocationParameters createMockInvocationParameters() {
    InvocationParameters mockParams = mock(InvocationParameters.class);
    when(mockParams.get("guildId")).thenReturn(TEST_GUILD_ID);
    when(mockParams.get("channelId")).thenReturn(TEST_CHANNEL_ID);
    when(mockParams.get("userId")).thenReturn(TEST_USER_ID);
    return mockParams;
  }

  @Nested
  @DisplayName("正常情況測試")
  class SuccessTests {

    @Test
    @DisplayName("應成功獲取頻道權限覆寫")
    void shouldSuccessfullyGetChannelPermissions() {
      // Given - 準備測試資料
      GuildChannel mockChannel = mock(GuildChannel.class);
      when(mockChannel.getIdLong()).thenReturn(TEST_CHANNEL_ID);
      when(mockChannel.getName()).thenReturn("test-channel");

      // Mock 角色權限覆寫
      PermissionOverride roleOverride = mock(PermissionOverride.class);
      when(roleOverride.getIdLong()).thenReturn(TEST_ROLE_ID);
      when(roleOverride.isRoleOverride()).thenReturn(true);
      when(roleOverride.isMemberOverride()).thenReturn(false);
      when(roleOverride.getAllowed()).thenReturn(EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND));
      when(roleOverride.getDenied()).thenReturn(EnumSet.of(Permission.MANAGE_CHANNEL));

      // Mock 成員權限覆寫
      PermissionOverride memberOverride = mock(PermissionOverride.class);
      when(memberOverride.getIdLong()).thenReturn(TEST_MEMBER_ID);
      when(memberOverride.isRoleOverride()).thenReturn(false);
      when(memberOverride.isMemberOverride()).thenReturn(true);
      when(memberOverride.getAllowed()).thenReturn(EnumSet.of(Permission.VIEW_CHANNEL));
      when(memberOverride.getDenied()).thenReturn(EnumSet.noneOf(Permission.class));

      List<PermissionOverride> overrides = List.of(roleOverride, memberOverride);
      when(mockChannel.getPermissionOverrides()).thenReturn(overrides);

      when(mockGuild.getGuildChannelById(TEST_CHANNEL_ID)).thenReturn(mockChannel);

      // When - 執行工具
      String result = tool.getChannelPermissions(String.valueOf(TEST_CHANNEL_ID), createMockInvocationParameters());

      // Then - 驗證結果
      assertThat(result).contains("\"success\": true");
      assertThat(result).contains("\"channelId\": \"" + TEST_CHANNEL_ID + "\"");
      assertThat(result).contains("\"channelName\": \"test-channel\"");
      assertThat(result).contains("\"count\": 2");
      assertThat(result).contains("\"type\": \"role\"");
      assertThat(result).contains("\"type\": \"member\"");
      assertThat(result).contains("\"allowed\": [");
      assertThat(result).contains("VIEW_CHANNEL");
      assertThat(result).contains("MESSAGE_SEND");
      assertThat(result).contains("\"denied\": [");
      assertThat(result).contains("MANAGE_CHANNEL");
    }

    @Test
    @DisplayName("應處理空權限覆寫列表")
    void shouldHandleEmptyPermissionOverrides() {
      // Given - 空權限覆寫列表
      GuildChannel mockChannel = mock(GuildChannel.class);
      when(mockChannel.getIdLong()).thenReturn(TEST_CHANNEL_ID);
      when(mockChannel.getName()).thenReturn("empty-channel");
      when(mockChannel.getPermissionOverrides()).thenReturn(new ArrayList<>());

      when(mockGuild.getGuildChannelById(TEST_CHANNEL_ID)).thenReturn(mockChannel);

      // When
      String result = tool.getChannelPermissions(String.valueOf(TEST_CHANNEL_ID), createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": true");
      assertThat(result).contains("\"count\": 0");
      assertThat(result).contains("\"overrides\":");
    }

    @Test
    @DisplayName("應正確處理只有允許權限的覆寫")
    void shouldHandleAllowOnlyOverrides() {
      // Given
      GuildChannel mockChannel = mock(GuildChannel.class);
      when(mockChannel.getIdLong()).thenReturn(TEST_CHANNEL_ID);
      when(mockChannel.getName()).thenReturn("public-channel");

      PermissionOverride override = mock(PermissionOverride.class);
      when(override.getIdLong()).thenReturn(TEST_ROLE_ID);
      when(override.isRoleOverride()).thenReturn(true);
      when(override.getAllowed()).thenReturn(EnumSet.allOf(Permission.class));
      when(override.getDenied()).thenReturn(EnumSet.noneOf(Permission.class));

      when(mockChannel.getPermissionOverrides()).thenReturn(List.of(override));
      when(mockGuild.getGuildChannelById(TEST_CHANNEL_ID)).thenReturn(mockChannel);

      // When
      String result = tool.getChannelPermissions(String.valueOf(TEST_CHANNEL_ID), createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"allowed\": [");
      assertThat(result).doesNotContain("\"denied\":");
    }

    @Test
    @DisplayName("應支援 Discord 頻道連結格式")
    void shouldSupportDiscordChannelLinkFormat() {
      // Given
      GuildChannel mockChannel = mock(GuildChannel.class);
      when(mockChannel.getIdLong()).thenReturn(TEST_CHANNEL_ID);
      when(mockChannel.getName()).thenReturn("linked-channel");
      when(mockChannel.getPermissionOverrides()).thenReturn(List.of());

      when(mockGuild.getGuildChannelById(TEST_CHANNEL_ID)).thenReturn(mockChannel);

      // When - 使用 <#ID> 格式
      String result = tool.getChannelPermissions("<#" + TEST_CHANNEL_ID + ">", createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": true");
      assertThat(result).contains("\"channelName\": \"linked-channel\"");
    }
  }

  @Nested
  @DisplayName("參數驗證測試")
  class ParameterValidationTests {

    @Test
    @DisplayName("當頻道 ID 為 null 時，應返回錯誤")
    void shouldReturnErrorWhenChannelIdIsNull() {
      // When
      String result = tool.getChannelPermissions(null, createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("channelId 未提供");
    }

    @Test
    @DisplayName("當頻道 ID 為空白時，應返回錯誤")
    void shouldReturnErrorWhenChannelIdIsBlank() {
      // When
      String result = tool.getChannelPermissions("   ", createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("channelId 未提供");
    }

    @Test
    @DisplayName("當頻道 ID 格式無效時，應返回錯誤")
    void shouldReturnErrorWhenChannelIdIsInvalid() {
      // When
      String result = tool.getChannelPermissions("invalid-id", createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("無效的頻道 ID");
    }
  }

  @Nested
  @DisplayName("錯誤處理測試")
  class ErrorHandlingTests {

    @Test
    @DisplayName("當找不到頻道時，應返回錯誤")
    void shouldReturnErrorWhenChannelNotFound() {
      // Given
      when(mockGuild.getGuildChannelById(TEST_CHANNEL_ID)).thenReturn(null);

      // When
      String result = tool.getChannelPermissions(String.valueOf(TEST_CHANNEL_ID), createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("找不到頻道");
    }

    @Test
    @DisplayName("當找不到伺服器時，應返回錯誤")
    void shouldReturnErrorWhenGuildNotFound() {
      // Given
      when(mockJda.getGuildById(TEST_GUILD_ID)).thenReturn(null);

      // When
      String result = tool.getChannelPermissions(String.valueOf(TEST_CHANNEL_ID), createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("找不到伺服器");
    }

    @Test
    @DisplayName("當工具執行上下文未設置時，應返回錯誤")
    void shouldReturnErrorWhenContextNotSet() {
      // Given - guildId 為 null
      InvocationParameters mockParams = mock(InvocationParameters.class);
      when(mockParams.get("guildId")).thenReturn(null);

      // When
      String result = tool.getChannelPermissions(String.valueOf(TEST_CHANNEL_ID), mockParams);

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("guildId 未設置");
    }
  }

  @Nested
  @DisplayName("結果格式測試")
  class ResultFormattingTests {

    @Test
    @DisplayName("結果應包含頻道 ID 和名稱")
    void resultShouldContainChannelIdAndName() {
      // Given
      GuildChannel mockChannel = mock(GuildChannel.class);
      when(mockChannel.getIdLong()).thenReturn(TEST_CHANNEL_ID);
      when(mockChannel.getName()).thenReturn("test");
      when(mockChannel.getPermissionOverrides()).thenReturn(List.of());

      when(mockGuild.getGuildChannelById(TEST_CHANNEL_ID)).thenReturn(mockChannel);

      // When
      String result = tool.getChannelPermissions(String.valueOf(TEST_CHANNEL_ID), createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"channelId\": \"" + TEST_CHANNEL_ID + "\"");
      assertThat(result).contains("\"channelName\": \"test\"");
    }

    @Test
    @DisplayName("結果應為有效的 JSON 格式")
    void resultShouldBeValidJsonFormat() {
      // Given
      GuildChannel mockChannel = mock(GuildChannel.class);
      when(mockChannel.getIdLong()).thenReturn(TEST_CHANNEL_ID);
      when(mockChannel.getName()).thenReturn("test");
      when(mockChannel.getPermissionOverrides()).thenReturn(List.of());

      when(mockGuild.getGuildChannelById(TEST_CHANNEL_ID)).thenReturn(mockChannel);

      // When
      String result = tool.getChannelPermissions(String.valueOf(TEST_CHANNEL_ID), createMockInvocationParameters());

      // Then
      assertThat(result).startsWith("{");
      assertThat(result).endsWith("}");
      assertThat(result).contains("\"overrides\":");
      assertThat(result).contains("\"count\":");
    }

    @Test
    @DisplayName("應正確轉義 JSON 特殊字符")
    void shouldEscapeJsonSpecialCharacters() {
      // Given
      GuildChannel mockChannel = mock(GuildChannel.class);
      when(mockChannel.getIdLong()).thenReturn(TEST_CHANNEL_ID);
      when(mockChannel.getName()).thenReturn("test\"channel\nwith\\special");
      when(mockChannel.getPermissionOverrides()).thenReturn(List.of());

      when(mockGuild.getGuildChannelById(TEST_CHANNEL_ID)).thenReturn(mockChannel);

      // When
      String result = tool.getChannelPermissions(String.valueOf(TEST_CHANNEL_ID), createMockInvocationParameters());

      // Then
      assertThat(result).contains("\\\"");
      assertThat(result).contains("\\n");
      assertThat(result).contains("\\\\");
    }
  }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=LangChain4jGetChannelPermissionsToolTest -q`
Expected: Tests fail because tool class doesn't exist yet

**Step 3: Implement the tool class**

已完成於 Task 1 Step 2。

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=LangChain4jGetChannelPermissionsToolTest -q`
Expected: All tests pass

**Step 5: Commit**

```bash
git add src/main/java/ltdjms/discord/aiagent/services/tools/LangChain4jGetChannelPermissionsTool.java
git add src/test/java/ltdjms/discord/aiagent/unit/services/tools/LangChain4jGetChannelPermissionsToolTest.java
git commit -m "feat(aiagent): 新增 LangChain4jGetChannelPermissionsTool 工具"
```

---

## Task 3: 在 AIAgentModule 註冊新工具

**Files:**
- Modify: `src/main/java/ltdjms/discord/shared/di/AIAgentModule.java:1-391`

**Step 1: Add import statement**

在檔案開頭的 import 區域添加：
```java
import ltdjms.discord.aiagent.services.tools.LangChain4jGetChannelPermissionsTool;
```

**Step 2: Add provider method**

在 `provideLangChain4jListCategoriesTool()` 方法後添加：
```java
/**
 * 提供 LangChain4J 獲取頻道權限工具。
 *
 * @return LangChain4jGetChannelPermissionsTool 實例
 */
@Provides
@Singleton
public LangChain4jGetChannelPermissionsTool provideLangChain4jGetChannelPermissionsTool() {
  return new LangChain4jGetChannelPermissionsTool();
}
```

**Step 3: Run test to verify it compiles**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

**Step 4: Commit**

```bash
git add src/main/java/ltdjms/discord/shared/di/AIAgentModule.java
git commit -m "feat(di): 在 AIAgentModule 註冊 LangChain4jGetChannelPermissionsTool"
```

---

## Task 4: 注入新工具到 LangChain4jAIChatService

**Files:**
- Modify: `src/main/java/ltdjms/discord/aichat/services/LangChain4jAIChatService.java:1-100`
- Modify: `src/main/java/ltdjms/discord/aichat/services/LangChain4jAIChatService.java:100-200`（建構函式）
- Modify: `src/main/java/ltdjms/discord/aichat/services/LangChain4jAIChatService.java:200-300`（createAgentService 方法）

**Step 1: Add field to store the tool**

在類別的欄位區域（其他工具欄位附近）添加：
```java
private final LangChain4jGetChannelPermissionsTool getChannelPermissionsTool;
```

**Step 2: Add constructor parameter**

在建構函式中添加參數：
```java
LangChain4jGetChannelPermissionsTool getChannelPermissionsTool
```

並在建構函式中賦值：
```java
this.getChannelPermissionsTool = getChannelPermissionsTool;
```

**Step 3: Add tool to agent service**

在 `createAgentService()` 方法的 `.tools()` 調用中添加：
```java
.tools(
    // ... existing tools
    getChannelPermissionsTool)
```

**Step 4: Run test to verify it compiles**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

**Step 5: Commit**

```bash
git add src/main/java/ltdjms/discord/aichat/services/LangChain4jAIChatService.java
git commit -m "feat(aichat): 注入 LangChain4jGetChannelPermissionsTool 到 AI 服務"
```

---

## Task 5: 更新 AIAgentModule 的 provideAIChatService 方法

**Files:**
- Modify: `src/main/java/ltdjms/discord/shared/di/AIAgentModule.java:344-372`

**Step 1: Add parameter to provider method**

在 `provideAIChatService()` 方法的參數列表中添加：
```java
LangChain4jGetChannelPermissionsTool getChannelPermissionsTool
```

**Step 2: Pass parameter to constructor**

在建構 `LangChain4jAIChatService` 時傳遞參數：
```java
return new LangChain4jAIChatService(
    config,
    promptLoader,
    eventPublisher,
    streamingChatModel,
    chatMemoryProvider,
    toolExecutionInterceptor,
    toolCallHistory,
    createChannelTool,
    createCategoryTool,
    listChannelsTool,
    listCategoriesTool,
    listRolesTool,
    getChannelPermissionsTool);  // 新增
```

**Step 3: Run full test suite**

Run: `mvn test -q`
Expected: All tests pass

**Step 4: Commit**

```bash
git add src/main/java/ltdjms/discord/shared/di/AIAgentModule.java
git commit -m "feat(di): 更新 provideAIChatService 以注入新工具"
```

---

## Task 6: 創建整合測試

**Files:**
- Create: `src/test/java/ltdjms/discord/aiagent/integration/services/LangChain4jGetChannelPermissionsToolIntegrationTest.java`

**Step 1: Write the integration test**

```java
package ltdjms.discord.aiagent.integration.services;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import dev.langchain4j.invocation.InvocationParameters;
import ltdjms.discord.aiagent.services.ToolExecutionContext;
import ltdjms.discord.aiagent.services.tools.LangChain4jGetChannelPermissionsTool;
import ltdjms.discord.shared.di.JDAProvider;

/**
 * 整合測試 {@link LangChain4jGetChannelPermissionsTool}。
 *
 * <p>測試範圍：
 *
 * <ul>
 *   <li>T024: LangChain4jGetChannelPermissionsTool 整合測試
 * </ul>
 *
 * <p>使用真實的 Discord API 進行測試（需要有效的測試環境）。
 */
@Testcontainers
@DisplayName("T024: LangChain4jGetChannelPermissionsTool 整合測試")
class LangChain4jGetChannelPermissionsToolIntegrationTest {

  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
          .withDatabaseName("test_db")
          .withUsername("test")
          .withPassword("test");

  private LangChain4jGetChannelPermissionsTool tool;

  @BeforeAll
  static void setUpBeforeAll() {
    // 確保 PostgreSQL 容器已啟動
    POSTGRES.start();
  }

  @BeforeEach
  void setUp() {
    tool = new LangChain4jGetChannelPermissionsTool();

    // 注意：整合測試需要真實的 JDA 實例
    // 在實際環境中，需要使用 Test Discord 伺服器
    // 這裡僅作為模板，實際執行需要配置真實的 Discord Token
  }

  @AfterEach
  void tearDown() {
    ToolExecutionContext.clearContext();
    JDAProvider.clear();
  }

  @Test
  @DisplayName("整合測試範例（需要真實環境）")
  void integrationTestExample() {
    // 注意：此測試需要真實的 Discord 環境
    // 在 CI/CD 中應該跳過或使用 mock

    // Given
    // 設置真實的 guildId, channelId, userId
    // long guildId = ...;
    // long channelId = ...;
    // long userId = ...;

    // ToolExecutionContext.setContext(guildId, channelId, userId);

    // When
    // InvocationParameters params = createMockParameters(guildId, channelId, userId);
    // String result = tool.getChannelPermissions(String.valueOf(channelId), params);

    // Then
    // assertThat(result).contains("\"success\": true");
  }
}
```

**Step 2: Run integration test**

Run: `mvn test -Dtest=LangChain4jGetChannelPermissionsToolIntegrationTest -q`
Expected: Test compiles and runs (may skip in CI environment)

**Step 3: Commit**

```bash
git add src/test/java/ltdjms/discord/aiagent/integration/services/LangChain4jGetChannelPermissionsToolIntegrationTest.java
git commit -m "test(aiagent): 新增 LangChain4jGetChannelPermissionsTool 整合測試"
```

---

## Task 7: 驗證完整測試套件

**Files:**
- Test: All related tests

**Step 1: Run all tests**

Run: `mvn test -q`
Expected: All tests pass

**Step 2: Run with coverage**

Run: `mvn verify -q`
Expected: Coverage meets 80% threshold

**Step 3: Final commit if needed**

```bash
git commit -m "test(aiagent): 確認所有測試通過且覆蓋率符合要求"
```

---

## Summary

This implementation plan adds a new AI Agent tool to read Discord channel permissions. The tool:

1. Uses JDA's `GuildChannel.getPermissionOverrides()` API
2. Returns JSON formatted permission override information
3. Supports both role and member permission overrides
4. Handles various input formats (raw ID, Discord link format)
5. Follows the existing pattern of other tools in the codebase

**Files Modified/Created:**
- Created: `src/main/java/ltdjms/discord/aiagent/services/tools/LangChain4jGetChannelPermissionsTool.java`
- Created: `src/test/java/ltdjms/discord/aiagent/unit/services/tools/LangChain4jGetChannelPermissionsToolTest.java`
- Created: `src/test/java/ltdjms/discord/aiagent/integration/services/LangChain4jGetChannelPermissionsToolIntegrationTest.java`
- Modified: `src/main/java/ltdjms/discord/shared/di/AIAgentModule.java`
- Modified: `src/main/java/ltdjms/discord/aichat/services/LangChain4jAIChatService.java`

**JDA API References:**
- `GuildChannel.getPermissionOverrides()` - Get all permission overrides
- `PermissionOverride.getAllowed()` - Get allowed permissions
- `PermissionOverride.getDenied()` - Get denied permissions
- `PermissionOverride.isRoleOverride()` - Check if override is for role
- `PermissionOverride.isMemberOverride()` - Check if override is for member
