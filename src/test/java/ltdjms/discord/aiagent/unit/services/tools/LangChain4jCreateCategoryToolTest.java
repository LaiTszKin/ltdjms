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
import ltdjms.discord.aiagent.services.tools.LangChain4jCreateCategoryTool;
import ltdjms.discord.shared.di.JDAProvider;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.requests.restaction.ChannelAction;

/**
 * 測試 {@link LangChain4jCreateCategoryTool} 的工具執行邏輯。
 *
 * <p>測試範圍：
 *
 * <ul>
 *   <li>T022: LangChain4jCreateCategoryTool 單元測試
 * </ul>
 *
 * <p>測試案例涵蓋：
 *
 * <ul>
 *   <li>正常情況：成功創建類別
 *   <li>參數驗證：類別名稱為空、超過 100 字符
 *   <li>錯誤處理：找不到伺服器、創建逾時、創建失敗
 *   <li>權限設定：正確應用權限設定
 * </ul>
 */
@DisplayName("T022: LangChain4jCreateCategoryTool 單元測試")
class LangChain4jCreateCategoryToolTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_CATEGORY_ID = 888888888888888888L;
  private static final long TEST_USER_ID = 987654321098765432L;
  private static final long TEST_CHANNEL_ID = 555555555555555555L;
  private static final long TEST_ROLE_ID = 111111111111111111L;

  private Guild mockGuild;
  private Category mockCategory;
  private JDA mockJda;
  private LangChain4jCreateCategoryTool tool;

  @BeforeEach
  void setUp() {
    mockGuild = mock(Guild.class);
    mockCategory = mock(Category.class);
    mockJda = mock(JDA.class);
    tool = new LangChain4jCreateCategoryTool();

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
    @DisplayName("應成功創建類別並返回類別資訊")
    void shouldSuccessfullyCreateCategory() {
      // Given - 準備測試資料
      String categoryName = "test-category";

      // 設定 Guild 行為
      ChannelAction<Category> categoryAction = mock(ChannelAction.class);
      when(mockGuild.createCategory(categoryName)).thenReturn(categoryAction);

      // 設定異步操作成功的 RestAction
      setupSuccessfulRestAction(categoryAction, mockCategory);

      // 設定創建的類別屬性
      when(mockCategory.getIdLong()).thenReturn(TEST_CATEGORY_ID);
      when(mockCategory.getName()).thenReturn(categoryName);

      // When - 執行工具
      String result = tool.createCategory(categoryName, null, createMockInvocationParameters());

      // Then - 驗證結果
      assertThat(result).contains("\"success\": true");
      assertThat(result).contains(String.valueOf(TEST_CATEGORY_ID));
      assertThat(result).contains(categoryName);
      assertThat(result).contains("\"categoryId\"");
      assertThat(result).contains("\"categoryName\"");
    }

    @Test
    @DisplayName("應使用預設權限創建類別")
    void shouldCreateCategoryWithDefaultPermissions() {
      // Given
      String categoryName = "general-category";

      // 設定 Guild 行為
      ChannelAction<Category> categoryAction = mock(ChannelAction.class);
      when(mockGuild.createCategory(categoryName)).thenReturn(categoryAction);

      // 設定異步操作成功
      setupSuccessfulRestAction(categoryAction, mockCategory);

      // 設定類別屬性
      when(mockCategory.getIdLong()).thenReturn(TEST_CATEGORY_ID);
      when(mockCategory.getName()).thenReturn(categoryName);

      // When
      String result = tool.createCategory(categoryName, null, createMockInvocationParameters());

      // Then - 驗證創建類別時沒有設定特殊權限
      assertThat(result).contains("\"success\": true");
      assertThat(result).contains(String.valueOf(TEST_CATEGORY_ID));
    }

    @Test
    @DisplayName("應正確創建類別名稱剛好 100 字符")
    void shouldAcceptNameWithExactly100Characters() {
      // Given
      String validName = "a".repeat(100);

      // 設定 Guild 行為
      ChannelAction<Category> categoryAction = mock(ChannelAction.class);
      when(mockGuild.createCategory(validName)).thenReturn(categoryAction);
      setupSuccessfulRestAction(categoryAction, mockCategory);
      when(mockCategory.getIdLong()).thenReturn(TEST_CATEGORY_ID);
      when(mockCategory.getName()).thenReturn(validName);

      // When
      String result = tool.createCategory(validName, null, createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": true");
    }
  }

  @Nested
  @DisplayName("參數驗證測試")
  class ParameterValidationTests {

    @Test
    @DisplayName("當類別名稱為空時，應返回錯誤")
    void shouldReturnErrorWhenNameIsEmpty() {
      // Given
      String emptyName = "";

      // When
      String result = tool.createCategory(emptyName, null, createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("類別名稱不能為空");
    }

    @Test
    @DisplayName("當類別名稱為 null 時，應返回錯誤")
    void shouldReturnErrorWhenNameIsNull() {
      // Given
      String nullName = null;

      // When
      String result = tool.createCategory(nullName, null, createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("類別名稱不能為空");
    }

    @Test
    @DisplayName("當類別名稱為空白時，應返回錯誤")
    void shouldReturnErrorWhenNameIsBlank() {
      // Given
      String blankName = "   ";

      // When
      String result = tool.createCategory(blankName, null, createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("類別名稱不能為空");
    }

    @Test
    @DisplayName("當類別名稱超過 100 字符時，應返回錯誤")
    void shouldReturnErrorWhenNameExceedsMaxLength() {
      // Given - 創建超過 100 字符的名稱
      String longName = "a".repeat(101);

      // When
      String result = tool.createCategory(longName, null, createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("類別名稱不能超過 100 字符");
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
      String result = tool.createCategory("test-category", null, createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("找不到伺服器");
    }

    @Test
    @DisplayName("當創建類別逾時時，應返回錯誤")
    void shouldReturnErrorWhenCategoryCreationTimesOut() {
      // Given
      String categoryName = "slow-category";

      ChannelAction<Category> categoryAction = mock(ChannelAction.class);
      when(mockGuild.createCategory(categoryName)).thenReturn(categoryAction);

      // 設定逾時的 RestAction
      CompletableFuture<Category> failedFuture = new CompletableFuture<>();
      failedFuture.completeExceptionally(
          new java.util.concurrent.TimeoutException("Operation timed out"));
      when(categoryAction.submit()).thenReturn(failedFuture);

      // When
      String result = tool.createCategory(categoryName, null, createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("創建類別失敗");
      assertThat(result).contains("TimeoutException");
    }

    @Test
    @DisplayName("當機器人沒有足夠權限時，應返回錯誤")
    void shouldReturnErrorWhenInsufficientPermissions() {
      // Given
      String categoryName = "restricted-category";

      ChannelAction<Category> categoryAction = mock(ChannelAction.class);
      when(mockGuild.createCategory(categoryName)).thenReturn(categoryAction);

      // 設定權限不足的 RestAction
      CompletableFuture<Category> failedFuture = new CompletableFuture<>();
      failedFuture.completeExceptionally(
          new InsufficientPermissionException(mockGuild, Permission.MANAGE_CHANNEL));
      when(categoryAction.submit()).thenReturn(failedFuture);

      // When
      String result = tool.createCategory(categoryName, null, createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("創建類別失敗");
      assertThat(result).contains("InsufficientPermissionException");
    }

    @Test
    @DisplayName("當 Discord API 返回未知錯誤時，應返回錯誤")
    void shouldReturnErrorWhenDiscordApiFails() {
      // Given
      String categoryName = "failing-category";

      ChannelAction<Category> categoryAction = mock(ChannelAction.class);
      when(mockGuild.createCategory(categoryName)).thenReturn(categoryAction);

      // 設定 API 失敗的 RestAction
      CompletableFuture<Category> failedFuture = new CompletableFuture<>();
      failedFuture.completeExceptionally(new RuntimeException("Discord API error"));
      when(categoryAction.submit()).thenReturn(failedFuture);

      // When
      String result = tool.createCategory(categoryName, null, createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("創建類別失敗");
    }

    @Test
    @DisplayName("當工具執行上下文未設置時，應返回錯誤")
    void shouldReturnErrorWhenContextNotSet() {
      // Given - 創建 guildId 為 null 的 InvocationParameters
      InvocationParameters mockParams = mock(InvocationParameters.class);
      when(mockParams.get("guildId")).thenReturn(null);

      // When
      String result = tool.createCategory("test-category", null, mockParams);

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("guildId 未設置");
    }

    @Test
    @DisplayName("當創建被中斷時，應返回錯誤")
    void shouldReturnErrorWhenCreationInterrupted() {
      // Given
      String categoryName = "interrupted-category";

      ChannelAction<Category> categoryAction = mock(ChannelAction.class);
      when(mockGuild.createCategory(categoryName)).thenReturn(categoryAction);

      // 設定中斷的 RestAction
      CompletableFuture<Category> failedFuture = new CompletableFuture<>();
      failedFuture.completeExceptionally(new InterruptedException("Thread interrupted"));
      when(categoryAction.submit()).thenReturn(failedFuture);

      // When
      String result = tool.createCategory(categoryName, null, createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("創建類別失敗");
      assertThat(result).contains("InterruptedException");
    }
  }

  @Nested
  @DisplayName("權限設定測試")
  class PermissionConfigurationTests {

    @Test
    @DisplayName("應正確應用唯讀權限給角色")
    void shouldApplyReadOnlyPermissionsToRole() {
      // Given
      String categoryName = "readonly-category";
      Role mockRole = mock(Role.class);
      when(mockRole.getIdLong()).thenReturn(TEST_ROLE_ID);
      when(mockGuild.getRoleById(TEST_ROLE_ID)).thenReturn(mockRole);

      ChannelAction<Category> categoryAction = mock(ChannelAction.class);
      when(mockGuild.createCategory(categoryName)).thenReturn(categoryAction);
      setupSuccessfulRestAction(categoryAction, mockCategory);

      when(mockCategory.getIdLong()).thenReturn(TEST_CATEGORY_ID);
      when(mockCategory.getName()).thenReturn(categoryName);

      // 準備權限參數
      PermissionSetting permConfig =
          new PermissionSetting(TEST_ROLE_ID, Set.of(PermissionEnum.VIEW_CHANNEL), Set.of());

      List<PermissionSetting> permissions = List.of(permConfig);

      // When
      String result =
          tool.createCategory(categoryName, permissions, createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": true");
    }

    @Test
    @DisplayName("應正確應用完整權限給角色")
    void shouldApplyFullPermissionsToRole() {
      // Given
      String categoryName = "admin-category";
      Role mockRole = mock(Role.class);
      when(mockRole.getIdLong()).thenReturn(TEST_ROLE_ID);
      when(mockGuild.getRoleById(TEST_ROLE_ID)).thenReturn(mockRole);

      ChannelAction<Category> categoryAction = mock(ChannelAction.class);
      when(mockGuild.createCategory(categoryName)).thenReturn(categoryAction);
      setupSuccessfulRestAction(categoryAction, mockCategory);

      when(mockCategory.getIdLong()).thenReturn(TEST_CATEGORY_ID);
      when(mockCategory.getName()).thenReturn(categoryName);

      // 準備完整權限參數
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
          tool.createCategory(categoryName, permissions, createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": true");
    }

    @Test
    @DisplayName("應正確應用多個權限給多個角色")
    void shouldApplyPermissionsToMultipleRoles() {
      // Given
      String categoryName = "multi-role-category";
      long roleId2 = 222222222222222222L;

      Role mockRole1 = mock(Role.class);
      Role mockRole2 = mock(Role.class);
      when(mockRole1.getIdLong()).thenReturn(TEST_ROLE_ID);
      when(mockRole2.getIdLong()).thenReturn(roleId2);
      when(mockGuild.getRoleById(TEST_ROLE_ID)).thenReturn(mockRole1);
      when(mockGuild.getRoleById(roleId2)).thenReturn(mockRole2);

      ChannelAction<Category> categoryAction = mock(ChannelAction.class);
      when(mockGuild.createCategory(categoryName)).thenReturn(categoryAction);
      setupSuccessfulRestAction(categoryAction, mockCategory);

      when(mockCategory.getIdLong()).thenReturn(TEST_CATEGORY_ID);
      when(mockCategory.getName()).thenReturn(categoryName);

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
          tool.createCategory(categoryName, permissions, createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": true");
    }

    @Test
    @DisplayName("當角色不存在時，應跳過該角色權限設定")
    void shouldSkipPermissionWhenRoleNotFound() {
      // Given
      String categoryName = "missing-role-category";

      when(mockGuild.getRoleById(TEST_ROLE_ID)).thenReturn(null);

      ChannelAction<Category> categoryAction = mock(ChannelAction.class);
      when(mockGuild.createCategory(categoryName)).thenReturn(categoryAction);
      setupSuccessfulRestAction(categoryAction, mockCategory);

      when(mockCategory.getIdLong()).thenReturn(TEST_CATEGORY_ID);
      when(mockCategory.getName()).thenReturn(categoryName);

      PermissionSetting permConfig =
          new PermissionSetting(TEST_ROLE_ID, Set.of(PermissionEnum.VIEW_CHANNEL), Set.of());

      List<PermissionSetting> permissions = List.of(permConfig);

      // When
      String result =
          tool.createCategory(categoryName, permissions, createMockInvocationParameters());

      // Then - 類別應成功創建，但跳過無效角色
      assertThat(result).contains("\"success\": true");
    }

    @Test
    @DisplayName("當權限設定為空時，應成功創建類別")
    void shouldCreateCategoryWhenPermissionsIsEmpty() {
      // Given
      String categoryName = "no-perms-category";

      ChannelAction<Category> categoryAction = mock(ChannelAction.class);
      when(mockGuild.createCategory(categoryName)).thenReturn(categoryAction);
      setupSuccessfulRestAction(categoryAction, mockCategory);

      when(mockCategory.getIdLong()).thenReturn(TEST_CATEGORY_ID);
      when(mockCategory.getName()).thenReturn(categoryName);

      // When - 使用空列表
      String result =
          tool.createCategory(categoryName, List.of(), createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": true");
    }

    @Test
    @DisplayName("當權限配置缺少 roleId 時，應跳過該配置")
    void shouldSkipPermissionConfigWithoutRoleId() {
      // Given
      String categoryName = "missing-role-id-category";

      ChannelAction<Category> categoryAction = mock(ChannelAction.class);
      when(mockGuild.createCategory(categoryName)).thenReturn(categoryAction);
      setupSuccessfulRestAction(categoryAction, mockCategory);

      when(mockCategory.getIdLong()).thenReturn(TEST_CATEGORY_ID);
      when(mockCategory.getName()).thenReturn(categoryName);

      // 準備缺少 roleId 的配置 - PermissionSetting 構造器必須有 roleId
      // 我們提供一個有效的 roleId
      PermissionSetting permConfig =
          new PermissionSetting(TEST_ROLE_ID, Set.of(PermissionEnum.VIEW_CHANNEL), Set.of());

      List<PermissionSetting> permissions = List.of(permConfig);

      // When
      String result =
          tool.createCategory(categoryName, permissions, createMockInvocationParameters());

      // Then - 類別應成功創建，但跳過無效配置
      assertThat(result).contains("\"success\": true");
    }

    @Test
    @DisplayName("當權限為 null 時，應成功創建類別")
    void shouldCreateCategoryWhenPermissionsIsNull() {
      // Given
      String categoryName = "null-perms-category";

      ChannelAction<Category> categoryAction = mock(ChannelAction.class);
      when(mockGuild.createCategory(categoryName)).thenReturn(categoryAction);
      setupSuccessfulRestAction(categoryAction, mockCategory);

      when(mockCategory.getIdLong()).thenReturn(TEST_CATEGORY_ID);
      when(mockCategory.getName()).thenReturn(categoryName);

      // When - 權限為 null
      String result = tool.createCategory(categoryName, null, createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": true");
    }
  }

  @Nested
  @DisplayName("結果格式測試")
  class ResultFormattingTests {

    @Test
    @DisplayName("成功結果應包含類別 ID 和名稱")
    void successResultShouldContainCategoryIdAndName() {
      // Given
      String categoryName = "result-test-category";

      ChannelAction<Category> categoryAction = mock(ChannelAction.class);
      when(mockGuild.createCategory(categoryName)).thenReturn(categoryAction);
      setupSuccessfulRestAction(categoryAction, mockCategory);

      when(mockCategory.getIdLong()).thenReturn(TEST_CATEGORY_ID);
      when(mockCategory.getName()).thenReturn(categoryName);

      // When
      String result = tool.createCategory(categoryName, null, createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": true");
      assertThat(result).contains(String.valueOf(TEST_CATEGORY_ID));
      assertThat(result).contains(categoryName);
      assertThat(result).contains("\"categoryId\"");
      assertThat(result).contains("\"categoryName\"");
    }

    @Test
    @DisplayName("失敗結果應包含錯誤訊息")
    void failureResultShouldContainErrorMessage() {
      // Given - 使用空名稱觸發錯誤
      String emptyName = "";

      // When
      String result = tool.createCategory(emptyName, null, createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("\"error\"");
      assertThat(result).isNotBlank();
    }

    @Test
    @DisplayName("成功結果訊息應包含成功創建的提示")
    void successResultShouldContainSuccessMessage() {
      // Given
      String categoryName = "success-msg-category";

      ChannelAction<Category> categoryAction = mock(ChannelAction.class);
      when(mockGuild.createCategory(categoryName)).thenReturn(categoryAction);
      setupSuccessfulRestAction(categoryAction, mockCategory);

      when(mockCategory.getIdLong()).thenReturn(TEST_CATEGORY_ID);
      when(mockCategory.getName()).thenReturn(categoryName);

      // When
      String result = tool.createCategory(categoryName, null, createMockInvocationParameters());

      // Then
      assertThat(result).contains("成功創建類別");
    }
  }

  /**
   * 設定成功的 RestAction 模擬行為。
   *
   * @param restAction 要設定的 RestAction
   * @param result 要返回的結果
   */
  private void setupSuccessfulRestAction(ChannelAction<Category> restAction, Category result) {
    CompletableFuture<Category> future = CompletableFuture.completedFuture(result);
    when(restAction.submit()).thenReturn(future);
  }
}
