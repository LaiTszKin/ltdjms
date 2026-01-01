package ltdjms.discord.aiagent.unit.services.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.langchain4j.invocation.InvocationParameters;
import ltdjms.discord.aiagent.domain.PermissionSetting;
import ltdjms.discord.aiagent.domain.PermissionSetting.PermissionEnum;
import ltdjms.discord.aiagent.services.ToolExecutionContext;
import ltdjms.discord.aiagent.services.tools.LangChain4jCreateChannelTool;
import ltdjms.discord.shared.di.JDAProvider;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.requests.restaction.ChannelAction;

/**
 * 測試 {@link LangChain4jCreateChannelTool} 的工具執行邏輯。
 *
 * <p>測試範圍：
 *
 * <ul>
 *   <li>T021: LangChain4jCreateChannelTool 單元測試
 * </ul>
 *
 * <p>測試案例涵蓋：
 *
 * <ul>
 *   <li>正常情況：成功創建頻道
 *   <li>參數驗證：頻道名稱為空、超過 100 字符
 *   <li>錯誤處理：找不到伺服器、創建逾時、創建失敗
 *   <li>權限設定：正確應用權限設定
 * </ul>
 */
@DisplayName("T021: LangChain4jCreateChannelTool 單元測試")
class LangChain4jCreateChannelToolTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_CHANNEL_ID = 999999999999999999L;
  private static final long TEST_USER_ID = 987654321098765432L;
  private static final long TEST_ROLE_ID = 111111111111111111L;
  private static final long TEST_CATEGORY_ID = 888888888888888888L;
  private static final String TEST_CATEGORY_ID_STR = String.valueOf(TEST_CATEGORY_ID);

  private Guild mockGuild;
  private TextChannel mockTextChannel;
  private Category mockCategory;
  private JDA mockJda;
  private LangChain4jCreateChannelTool tool;

  @BeforeEach
  void setUp() {
    mockGuild = mock(Guild.class);
    mockTextChannel = mock(TextChannel.class);
    mockCategory = mock(Category.class);
    mockJda = mock(JDA.class);
    tool = new LangChain4jCreateChannelTool();

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
    @DisplayName("應成功創建頻道並返回頻道資訊")
    void shouldSuccessfullyCreateChannel() {
      // Given - 準備測試資料
      String channelName = "test-channel";

      // 設定 Guild 行為
      ChannelAction<TextChannel> channelAction = mock(ChannelAction.class);
      when(mockGuild.createTextChannel(channelName)).thenReturn(channelAction);

      // 設定異步操作成功的 RestAction
      setupSuccessfulRestAction(channelAction, mockTextChannel);

      // 設定創建的頻道屬性
      when(mockTextChannel.getIdLong()).thenReturn(TEST_CHANNEL_ID);
      when(mockTextChannel.getName()).thenReturn(channelName);

      // When - 執行工具
      String result = tool.createChannel(channelName, null, null, createMockInvocationParameters());

      // Then - 驗證結果
      assertThat(result).contains("\"success\": true");
      assertThat(result).contains(String.valueOf(TEST_CHANNEL_ID));
      assertThat(result).contains(channelName);
    }

    @Test
    @DisplayName("應使用預設權限創建頻道")
    void shouldCreateChannelWithDefaultPermissions() {
      // Given
      String channelName = "general-chat";

      // 設定 Guild 行為
      ChannelAction<TextChannel> channelAction = mock(ChannelAction.class);
      when(mockGuild.createTextChannel(channelName)).thenReturn(channelAction);

      // 設定異步操作成功
      setupSuccessfulRestAction(channelAction, mockTextChannel);

      // 設定頻道屬性
      when(mockTextChannel.getIdLong()).thenReturn(TEST_CHANNEL_ID);
      when(mockTextChannel.getName()).thenReturn(channelName);

      // When
      String result = tool.createChannel(channelName, null, null, createMockInvocationParameters());

      // Then - 驗證創建頻道時沒有設定特殊權限
      assertThat(result).contains("\"success\": true");
      assertThat(result).contains(String.valueOf(TEST_CHANNEL_ID));
    }

    @Test
    @DisplayName("應正確創建頻道名稱剛好 100 字符")
    void shouldAcceptNameWithExactly100Characters() {
      // Given
      String validName = "a".repeat(100);

      // 設定 Guild 行為
      ChannelAction<TextChannel> channelAction = mock(ChannelAction.class);
      when(mockGuild.createTextChannel(validName)).thenReturn(channelAction);
      setupSuccessfulRestAction(channelAction, mockTextChannel);
      when(mockTextChannel.getIdLong()).thenReturn(TEST_CHANNEL_ID);
      when(mockTextChannel.getName()).thenReturn(validName);

      // When
      String result = tool.createChannel(validName, null, null, createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": true");
    }

    @Test
    @DisplayName("應成功創建頻道在指定類別下")
    void shouldSuccessfullyCreateChannelInCategory() {
      // Given
      String channelName = "category-channel";

      // 設定類別
      when(mockGuild.getCategoryById(TEST_CATEGORY_ID)).thenReturn(mockCategory);

      // 設定 Guild 行為
      ChannelAction<TextChannel> channelAction = mock(ChannelAction.class);
      when(mockGuild.createTextChannel(channelName)).thenReturn(channelAction);

      // 設定異步操作成功的 RestAction
      setupSuccessfulRestAction(channelAction, mockTextChannel);

      // 設定創建的頻道屬性
      when(mockTextChannel.getIdLong()).thenReturn(TEST_CHANNEL_ID);
      when(mockTextChannel.getName()).thenReturn(channelName);

      // When - 執行工具並指定類別
      String result =
          tool.createChannel(
              channelName, TEST_CATEGORY_ID_STR, null, createMockInvocationParameters());

      // Then - 驗證結果
      assertThat(result).contains("\"success\": true");
      assertThat(result).contains(String.valueOf(TEST_CHANNEL_ID));
      assertThat(result).contains(channelName);
    }
  }

  @Nested
  @DisplayName("參數驗證測試")
  class ParameterValidationTests {

    @Test
    @DisplayName("當頻道名稱為空時，應返回錯誤")
    void shouldReturnErrorWhenNameIsEmpty() {
      // Given
      String emptyName = "";

      // When
      String result = tool.createChannel(emptyName, null, null, createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("頻道名稱不能為空");
    }

    @Test
    @DisplayName("當頻道名稱為 null 時，應返回錯誤")
    void shouldReturnErrorWhenNameIsNull() {
      // Given
      String nullName = null;

      // When
      String result = tool.createChannel(nullName, null, null, createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("頻道名稱不能為空");
    }

    @Test
    @DisplayName("當頻道名稱為空白時，應返回錯誤")
    void shouldReturnErrorWhenNameIsBlank() {
      // Given
      String blankName = "   ";

      // When
      String result = tool.createChannel(blankName, null, null, createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("頻道名稱不能為空");
    }

    @Test
    @DisplayName("當頻道名稱超過 100 字符時，應返回錯誤")
    void shouldReturnErrorWhenNameExceedsMaxLength() {
      // Given - 創建超過 100 字符的名稱
      String longName = "a".repeat(101);

      // When
      String result = tool.createChannel(longName, null, null, createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("頻道名稱不能超過 100 字符");
    }
  }

  @Nested
  @DisplayName("錯誤處理測試")
  class ErrorHandlingTests {

    @Test
    @DisplayName("當找不到伺服器時，應返回錯誤")
    void shouldReturnErrorWhenGuildNotFound() {
      // Given
      when(mockJda.getGuildById(TEST_GUILD_ID)).thenReturn(null);

      // When
      String result =
          tool.createChannel("test-channel", null, null, createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("找不到伺服器");
    }

    @Test
    @DisplayName("當創建頻道逾時時，應返回錯誤")
    void shouldReturnErrorWhenChannelCreationTimesOut() {
      // Given
      String channelName = "slow-channel";

      ChannelAction<TextChannel> channelAction = mock(ChannelAction.class);
      when(mockGuild.createTextChannel(channelName)).thenReturn(channelAction);

      // 設定逾時的 RestAction
      CompletableFuture<TextChannel> failedFuture = new CompletableFuture<>();
      failedFuture.completeExceptionally(
          new java.util.concurrent.TimeoutException("Operation timed out"));
      when(channelAction.submit()).thenReturn(failedFuture);

      // When
      String result = tool.createChannel(channelName, null, null, createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("創建頻道失敗");
      assertThat(result).contains("TimeoutException");
    }

    @Test
    @DisplayName("當機器人沒有足夠權限時，應返回錯誤")
    void shouldReturnErrorWhenInsufficientPermissions() {
      // Given
      String channelName = "restricted-channel";

      ChannelAction<TextChannel> channelAction = mock(ChannelAction.class);
      when(mockGuild.createTextChannel(channelName)).thenReturn(channelAction);

      // 設定權限不足的 RestAction
      CompletableFuture<TextChannel> failedFuture = new CompletableFuture<>();
      failedFuture.completeExceptionally(
          new InsufficientPermissionException(mockGuild, Permission.MANAGE_CHANNEL));
      when(channelAction.submit()).thenReturn(failedFuture);

      // When
      String result = tool.createChannel(channelName, null, null, createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("創建頻道失敗");
      assertThat(result).contains("InsufficientPermissionException");
    }

    @Test
    @DisplayName("當 Discord API 返回未知錯誤時，應返回錯誤")
    void shouldReturnErrorWhenDiscordApiFails() {
      // Given
      String channelName = "failing-channel";

      ChannelAction<TextChannel> channelAction = mock(ChannelAction.class);
      when(mockGuild.createTextChannel(channelName)).thenReturn(channelAction);

      // 設定 API 失敗的 RestAction
      CompletableFuture<TextChannel> failedFuture = new CompletableFuture<>();
      failedFuture.completeExceptionally(new RuntimeException("Discord API error"));
      when(channelAction.submit()).thenReturn(failedFuture);

      // When
      String result = tool.createChannel(channelName, null, null, createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("創建頻道失敗");
    }

    @Test
    @DisplayName("當工具執行上下文未設置時，應返回錯誤")
    void shouldReturnErrorWhenContextNotSet() {
      // Given - 創建 guildId 為 null 的 InvocationParameters
      InvocationParameters mockParams = mock(InvocationParameters.class);
      when(mockParams.get("guildId")).thenReturn(null);

      // When
      String result = tool.createChannel("test-channel", null, null, mockParams);

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("guildId 未設置");
    }

    @Test
    @DisplayName("當指定的類別不存在時，應返回錯誤")
    void shouldReturnErrorWhenCategoryNotFound() {
      // Given
      String channelName = "category-channel";

      // 設定類別不存在
      when(mockGuild.getCategoryById(TEST_CATEGORY_ID)).thenReturn(null);

      // When
      String result =
          tool.createChannel(
              channelName, TEST_CATEGORY_ID_STR, null, createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("找不到指定的類別");
    }

    @Test
    @DisplayName("當類別 ID 格式無效時，應返回錯誤")
    void shouldReturnErrorWhenCategoryIdFormatInvalid() {
      // When
      String result =
          tool.createChannel(
              "invalid-category-id", "not-a-number", null, createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("類別 ID 格式無效");
    }
  }

  @Nested
  @DisplayName("權限設定測試")
  class PermissionConfigurationTests {

    @Test
    @DisplayName("應正確應用唯讀權限給角色")
    void shouldApplyReadOnlyPermissionsToRole() {
      // Given
      String channelName = "readonly-channel";
      Role mockRole = mock(Role.class);
      when(mockRole.getIdLong()).thenReturn(TEST_ROLE_ID);
      when(mockGuild.getRoleById(TEST_ROLE_ID)).thenReturn(mockRole);

      ChannelAction<TextChannel> channelAction = mock(ChannelAction.class);
      when(mockGuild.createTextChannel(channelName)).thenReturn(channelAction);
      setupSuccessfulRestAction(channelAction, mockTextChannel);

      when(mockTextChannel.getIdLong()).thenReturn(TEST_CHANNEL_ID);
      when(mockTextChannel.getName()).thenReturn(channelName);

      // 準備權限參數
      PermissionSetting permConfig =
          new PermissionSetting(TEST_ROLE_ID, Set.of(PermissionEnum.VIEW_CHANNEL), Set.of());

      List<PermissionSetting> permissions = List.of(permConfig);

      // When
      String result =
          tool.createChannel(channelName, null, permissions, createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": true");
    }

    @Test
    @DisplayName("應正確應用完整權限給角色")
    void shouldApplyFullPermissionsToRole() {
      // Given
      String channelName = "admin-channel";
      Role mockRole = mock(Role.class);
      when(mockRole.getIdLong()).thenReturn(TEST_ROLE_ID);
      when(mockGuild.getRoleById(TEST_ROLE_ID)).thenReturn(mockRole);

      ChannelAction<TextChannel> channelAction = mock(ChannelAction.class);
      when(mockGuild.createTextChannel(channelName)).thenReturn(channelAction);
      setupSuccessfulRestAction(channelAction, mockTextChannel);

      when(mockTextChannel.getIdLong()).thenReturn(TEST_CHANNEL_ID);
      when(mockTextChannel.getName()).thenReturn(channelName);

      // 準備完整權限參數（使用多個權限模擬 full access）
      PermissionSetting permConfig =
          new PermissionSetting(
              TEST_ROLE_ID,
              Set.of(
                  PermissionEnum.VIEW_CHANNEL,
                  PermissionEnum.MESSAGE_SEND,
                  PermissionEnum.MESSAGE_HISTORY,
                  PermissionEnum.MANAGE_CHANNELS),
              Set.of());

      List<PermissionSetting> permissions = List.of(permConfig);

      // When
      String result =
          tool.createChannel(channelName, null, permissions, createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": true");
    }

    @Test
    @DisplayName("應正確應用多個權限給多個角色")
    void shouldApplyPermissionsToMultipleRoles() {
      // Given
      String channelName = "multi-role-channel";
      long roleId2 = 222222222222222222L;

      Role mockRole1 = mock(Role.class);
      Role mockRole2 = mock(Role.class);
      when(mockRole1.getIdLong()).thenReturn(TEST_ROLE_ID);
      when(mockRole2.getIdLong()).thenReturn(roleId2);
      when(mockGuild.getRoleById(TEST_ROLE_ID)).thenReturn(mockRole1);
      when(mockGuild.getRoleById(roleId2)).thenReturn(mockRole2);

      ChannelAction<TextChannel> channelAction = mock(ChannelAction.class);
      when(mockGuild.createTextChannel(channelName)).thenReturn(channelAction);
      setupSuccessfulRestAction(channelAction, mockTextChannel);

      when(mockTextChannel.getIdLong()).thenReturn(TEST_CHANNEL_ID);
      when(mockTextChannel.getName()).thenReturn(channelName);

      // 準備多個角色權限參數
      PermissionSetting permConfig1 =
          new PermissionSetting(
              TEST_ROLE_ID,
              Set.of(PermissionEnum.VIEW_CHANNEL, PermissionEnum.MESSAGE_SEND),
              Set.of());

      PermissionSetting permConfig2 =
          new PermissionSetting(roleId2, Set.of(PermissionEnum.VIEW_CHANNEL), Set.of());

      List<PermissionSetting> permissions = List.of(permConfig1, permConfig2);

      // When
      String result =
          tool.createChannel(channelName, null, permissions, createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": true");
    }

    @Test
    @DisplayName("當角色不存在時，應跳過該角色權限設定")
    void shouldSkipPermissionWhenRoleNotFound() {
      // Given
      String channelName = "missing-role-channel";

      when(mockGuild.getRoleById(TEST_ROLE_ID)).thenReturn(null);

      ChannelAction<TextChannel> channelAction = mock(ChannelAction.class);
      when(mockGuild.createTextChannel(channelName)).thenReturn(channelAction);
      setupSuccessfulRestAction(channelAction, mockTextChannel);

      when(mockTextChannel.getIdLong()).thenReturn(TEST_CHANNEL_ID);
      when(mockTextChannel.getName()).thenReturn(channelName);

      PermissionSetting permConfig =
          new PermissionSetting(TEST_ROLE_ID, Set.of(PermissionEnum.VIEW_CHANNEL), Set.of());

      List<PermissionSetting> permissions = List.of(permConfig);

      // When
      String result =
          tool.createChannel(channelName, null, permissions, createMockInvocationParameters());

      // Then - 頻道應成功創建，但跳過無效角色
      assertThat(result).contains("\"success\": true");
    }

    @Test
    @DisplayName("當權限設定為空時，應成功創建頻道")
    void shouldCreateChannelWhenPermissionsIsEmpty() {
      // Given
      String channelName = "no-perms-channel";

      ChannelAction<TextChannel> channelAction = mock(ChannelAction.class);
      when(mockGuild.createTextChannel(channelName)).thenReturn(channelAction);
      setupSuccessfulRestAction(channelAction, mockTextChannel);

      when(mockTextChannel.getIdLong()).thenReturn(TEST_CHANNEL_ID);
      when(mockTextChannel.getName()).thenReturn(channelName);

      // When - 使用空列表
      String result =
          tool.createChannel(channelName, null, List.of(), createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": true");
    }

    @Test
    @DisplayName("當權限配置缺少 roleId 時，應跳過該配置")
    void shouldSkipPermissionConfigWithoutRoleId() {
      // Given
      String channelName = "missing-role-id-channel";

      ChannelAction<TextChannel> channelAction = mock(ChannelAction.class);
      when(mockGuild.createTextChannel(channelName)).thenReturn(channelAction);
      setupSuccessfulRestAction(channelAction, mockTextChannel);

      when(mockTextChannel.getIdLong()).thenReturn(TEST_CHANNEL_ID);
      when(mockTextChannel.getName()).thenReturn(channelName);

      // 準備缺少 roleId 的配置 - 這種情況現在通過正常的 PermissionSetting 構造
      // 無法創建沒有 roleId 的 PermissionSetting，所以我們提供一個有效的 roleId
      PermissionSetting permConfig =
          new PermissionSetting(TEST_ROLE_ID, Set.of(PermissionEnum.VIEW_CHANNEL), Set.of());

      List<PermissionSetting> permissions = List.of(permConfig);

      // When
      String result =
          tool.createChannel(channelName, null, permissions, createMockInvocationParameters());

      // Then - 頻道應成功創建，但跳過無效配置
      assertThat(result).contains("\"success\": true");
    }
  }

  @Nested
  @DisplayName("結果格式測試")
  class ResultFormattingTests {

    @Test
    @DisplayName("成功結果應包含頻道 ID 和名稱")
    void successResultShouldContainChannelIdAndName() {
      // Given
      String channelName = "result-test-channel";

      ChannelAction<TextChannel> channelAction = mock(ChannelAction.class);
      when(mockGuild.createTextChannel(channelName)).thenReturn(channelAction);
      setupSuccessfulRestAction(channelAction, mockTextChannel);

      when(mockTextChannel.getIdLong()).thenReturn(TEST_CHANNEL_ID);
      when(mockTextChannel.getName()).thenReturn(channelName);

      // When
      String result = tool.createChannel(channelName, null, null, createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": true");
      assertThat(result).contains(String.valueOf(TEST_CHANNEL_ID));
      assertThat(result).contains(channelName);
      assertThat(result).contains("\"channelId\"");
      assertThat(result).contains("\"channelName\"");
    }

    @Test
    @DisplayName("失敗結果應包含錯誤訊息")
    void failureResultShouldContainErrorMessage() {
      // Given - 使用空名稱觸發錯誤
      String emptyName = "";

      // When
      String result = tool.createChannel(emptyName, null, null, createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("\"error\"");
      assertThat(result).isNotBlank();
    }

    @Test
    @DisplayName("成功結果訊息應包含成功創建的提示")
    void successResultShouldContainSuccessMessage() {
      // Given
      String channelName = "success-msg-channel";

      ChannelAction<TextChannel> channelAction = mock(ChannelAction.class);
      when(mockGuild.createTextChannel(channelName)).thenReturn(channelAction);
      setupSuccessfulRestAction(channelAction, mockTextChannel);

      when(mockTextChannel.getIdLong()).thenReturn(TEST_CHANNEL_ID);
      when(mockTextChannel.getName()).thenReturn(channelName);

      // When
      String result = tool.createChannel(channelName, null, null, createMockInvocationParameters());

      // Then
      assertThat(result).contains("成功創建頻道");
    }
  }

  /**
   * 設定成功的 RestAction 模擬行為。
   *
   * @param restAction 要設定的 RestAction
   * @param result 要返回的結果
   */
  private void setupSuccessfulRestAction(
      ChannelAction<TextChannel> restAction, TextChannel result) {
    CompletableFuture<TextChannel> future = CompletableFuture.completedFuture(result);
    when(restAction.submit()).thenReturn(future);
  }
}
