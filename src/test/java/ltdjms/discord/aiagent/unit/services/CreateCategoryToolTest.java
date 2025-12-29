package ltdjms.discord.aiagent.unit.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import ltdjms.discord.aiagent.domain.ToolExecutionResult;
import ltdjms.discord.aiagent.services.ToolContext;
import ltdjms.discord.aiagent.services.tools.CreateCategoryTool;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.ChannelAction;

/**
 * 測試 {@link ltdjms.discord.aiagent.services.tools.CreateCategoryTool CreateCategoryTool} 的工具執行邏輯。
 *
 * <p>測試範圍：
 *
 * <ul>
 *   <li>T043: CreateCategoryTool 單元測試
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
@DisplayName("T043: CreateCategoryTool 單元測試")
class CreateCategoryToolTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_CATEGORY_ID = 999999999999999999L;
  private static final long TEST_USER_ID = 987654321098765432L;
  private static final long TEST_ROLE_ID = 111111111111111111L;

  private Guild mockGuild;
  private Category mockCategory;
  private JDA mockJda;
  private ToolContext toolContext;
  private CreateCategoryTool createCategoryTool;

  @BeforeEach
  void setUp() {
    mockGuild = mock(Guild.class);
    mockCategory = mock(Category.class);
    mockJda = mock(JDA.class);
    toolContext = new ToolContext(TEST_GUILD_ID, TEST_CATEGORY_ID, TEST_USER_ID, mockJda);
    createCategoryTool = new CreateCategoryTool();

    // 設定 JDA 基本行為
    when(mockJda.getGuildById(TEST_GUILD_ID)).thenReturn(mockGuild);
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
      Map<String, Object> parameters = new HashMap<>();
      parameters.put("name", categoryName);

      ToolExecutionResult result = createCategoryTool.execute(parameters, toolContext);

      // Then - 驗證結果
      assertThat(result.success()).isTrue();
      assertThat(result.result()).isPresent();
      assertThat(result.result().get()).contains(String.valueOf(TEST_CATEGORY_ID));
      assertThat(result.result().get()).contains(categoryName);
    }

    @Test
    @DisplayName("應使用預設權限創建類別")
    void shouldCreateCategoryWithDefaultPermissions() {
      // Given
      String categoryName = "general";

      // 設定 Guild 行為
      ChannelAction<Category> categoryAction = mock(ChannelAction.class);
      when(mockGuild.createCategory(categoryName)).thenReturn(categoryAction);

      // 設定異步操作成功
      setupSuccessfulRestAction(categoryAction, mockCategory);

      // 設定類別屬性
      when(mockCategory.getIdLong()).thenReturn(TEST_CATEGORY_ID);
      when(mockCategory.getName()).thenReturn(categoryName);

      // When
      Map<String, Object> parameters = new HashMap<>();
      parameters.put("name", categoryName);

      ToolExecutionResult result = createCategoryTool.execute(parameters, toolContext);

      // Then - 驗證創建類別時沒有設定特殊權限
      assertThat(result.success()).isTrue();
      assertThat(result.result()).isPresent();
    }
  }

  @Nested
  @DisplayName("參數驗證測試")
  class ParameterValidationTests {

    @Test
    @DisplayName("當類別名稱為空時，應返回錯誤")
    void shouldReturnErrorWhenNameIsEmpty() {
      // Given
      Map<String, Object> parameters = new HashMap<>();
      parameters.put("name", "");

      // When
      ToolExecutionResult result = createCategoryTool.execute(parameters, toolContext);

      // Then
      assertThat(result.success()).isFalse();
      assertThat(result.error()).isPresent();
      assertThat(result.error().get()).contains("類別名稱不能為空");
    }

    @Test
    @DisplayName("當類別名稱為 null 時，應返回錯誤")
    void shouldReturnErrorWhenNameIsNull() {
      // Given
      Map<String, Object> parameters = new HashMap<>();
      parameters.put("name", null);

      // When
      ToolExecutionResult result = createCategoryTool.execute(parameters, toolContext);

      // Then
      assertThat(result.success()).isFalse();
      assertThat(result.error()).isPresent();
      assertThat(result.error().get()).contains("類別名稱不能為空");
    }

    @Test
    @DisplayName("當類別名稱超過 100 字符時，應返回錯誤")
    void shouldReturnErrorWhenNameExceedsMaxLength() {
      // Given - 創建超過 100 字符的名稱
      String longName = "a".repeat(101);
      Map<String, Object> parameters = new HashMap<>();
      parameters.put("name", longName);

      // When
      ToolExecutionResult result = createCategoryTool.execute(parameters, toolContext);

      // Then
      assertThat(result.success()).isFalse();
      assertThat(result.error()).isPresent();
      assertThat(result.error().get()).contains("類別名稱不能超過 100 字符");
    }

    @Test
    @DisplayName("當類別名稱剛好 100 字符時，應成功創建")
    void shouldAcceptNameWithExactly100Characters() {
      // Given
      String validName = "a".repeat(100);
      Map<String, Object> parameters = new HashMap<>();
      parameters.put("name", validName);

      // 設定 Guild 行為
      ChannelAction<Category> categoryAction = mock(ChannelAction.class);
      when(mockGuild.createCategory(validName)).thenReturn(categoryAction);
      setupSuccessfulRestAction(categoryAction, mockCategory);
      when(mockCategory.getIdLong()).thenReturn(TEST_CATEGORY_ID);
      when(mockCategory.getName()).thenReturn(validName);

      // When
      ToolExecutionResult result = createCategoryTool.execute(parameters, toolContext);

      // Then
      assertThat(result.success()).isTrue();
      assertThat(result.result()).isPresent();
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

      Map<String, Object> parameters = new HashMap<>();
      parameters.put("name", "test-category");

      // When
      ToolExecutionResult result = createCategoryTool.execute(parameters, toolContext);

      // Then
      assertThat(result.success()).isFalse();
      assertThat(result.error()).isPresent();
      assertThat(result.error().get()).contains("找不到伺服器");
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

      Map<String, Object> parameters = new HashMap<>();
      parameters.put("name", categoryName);

      // When
      ToolExecutionResult result = createCategoryTool.execute(parameters, toolContext);

      // Then
      assertThat(result.success()).isFalse();
      assertThat(result.error()).isPresent();
      assertThat(result.error().get()).contains("Timeout");
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

      Map<String, Object> parameters = new HashMap<>();
      parameters.put("name", categoryName);

      // When
      ToolExecutionResult result = createCategoryTool.execute(parameters, toolContext);

      // Then
      assertThat(result.success()).isFalse();
      assertThat(result.error()).isPresent();
      assertThat(result.error().get()).contains("InsufficientPermissionException");
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

      Map<String, Object> parameters = new HashMap<>();
      parameters.put("name", categoryName);

      // When
      ToolExecutionResult result = createCategoryTool.execute(parameters, toolContext);

      // Then
      assertThat(result.success()).isFalse();
      assertThat(result.error()).isPresent();
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
      Map<String, Object> permConfig = new HashMap<>();
      permConfig.put("roleId", TEST_ROLE_ID);
      permConfig.put("permissions", List.of("view"));

      Map<String, Object> parameters = new HashMap<>();
      parameters.put("name", categoryName);
      parameters.put("permissions", List.of(permConfig));

      // When
      ToolExecutionResult result = createCategoryTool.execute(parameters, toolContext);

      // Then
      assertThat(result.success()).isTrue();
      assertThat(parameters.get("permissions")).isInstanceOf(List.class);
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
      Map<String, Object> permConfig = new HashMap<>();
      permConfig.put("roleId", TEST_ROLE_ID);
      permConfig.put("permissions", List.of("full"));

      Map<String, Object> parameters = new HashMap<>();
      parameters.put("name", categoryName);
      parameters.put("permissions", List.of(permConfig));

      // When
      ToolExecutionResult result = createCategoryTool.execute(parameters, toolContext);

      // Then
      assertThat(result.success()).isTrue();
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
      Map<String, Object> permConfig1 = new HashMap<>();
      permConfig1.put("roleId", TEST_ROLE_ID);
      permConfig1.put("permissions", List.of("view", "write"));

      Map<String, Object> permConfig2 = new HashMap<>();
      permConfig2.put("roleId", roleId2);
      permConfig2.put("permissions", List.of("view"));

      Map<String, Object> parameters = new HashMap<>();
      parameters.put("name", categoryName);
      parameters.put("permissions", List.of(permConfig1, permConfig2));

      // When
      ToolExecutionResult result = createCategoryTool.execute(parameters, toolContext);

      // Then
      assertThat(result.success()).isTrue();
    }

    @Test
    @DisplayName("當使用字串描述權限時，應正確解析")
    void shouldParseStringPermissionDescription() {
      // Given
      String categoryName = "descriptive-permission-category";
      Role mockRole = mock(Role.class);
      when(mockRole.getIdLong()).thenReturn(TEST_ROLE_ID);
      when(mockGuild.getRoleById(TEST_ROLE_ID)).thenReturn(mockRole);

      ChannelAction<Category> categoryAction = mock(ChannelAction.class);
      when(mockGuild.createCategory(categoryName)).thenReturn(categoryAction);
      setupSuccessfulRestAction(categoryAction, mockCategory);

      when(mockCategory.getIdLong()).thenReturn(TEST_CATEGORY_ID);
      when(mockCategory.getName()).thenReturn(categoryName);

      // 使用字串描述權限
      Map<String, Object> parameters = new HashMap<>();
      parameters.put("name", categoryName);
      parameters.put("permissions", "read-only");

      // When
      ToolExecutionResult result = createCategoryTool.execute(parameters, toolContext);

      // Then
      assertThat(result.success()).isTrue();
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

      Map<String, Object> permConfig = new HashMap<>();
      permConfig.put("roleId", TEST_ROLE_ID);
      permConfig.put("permissions", List.of("view"));

      Map<String, Object> parameters = new HashMap<>();
      parameters.put("name", categoryName);
      parameters.put("permissions", List.of(permConfig));

      // When
      ToolExecutionResult result = createCategoryTool.execute(parameters, toolContext);

      // Then - 類別應成功創建，但跳過無效角色
      assertThat(result.success()).isTrue();
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

      Map<String, Object> parameters = new HashMap<>();
      parameters.put("name", categoryName);

      // When
      ToolExecutionResult result = createCategoryTool.execute(parameters, toolContext);

      // Then
      assertThat(result.success()).isTrue();
      assertThat(result.result()).isPresent();
      assertThat(result.result().get()).contains(String.valueOf(TEST_CATEGORY_ID));
      assertThat(result.result().get()).contains(categoryName);
    }

    @Test
    @DisplayName("失敗結果應包含錯誤訊息")
    void failureResultShouldContainErrorMessage() {
      // Given
      Map<String, Object> parameters = new HashMap<>();
      parameters.put("name", "");

      // When
      ToolExecutionResult result = createCategoryTool.execute(parameters, toolContext);

      // Then
      assertThat(result.success()).isFalse();
      assertThat(result.error()).isPresent();
      assertThat(result.error().get()).isNotBlank();
    }
  }

  /**
   * 設定成功的 RestAction 模擬行為。
   *
   * @param restAction 要設定的 RestAction
   * @param result 要返回的結果
   * @param <T> 結果類型
   */
  private <T> void setupSuccessfulRestAction(RestAction<T> restAction, T result) {
    CompletableFuture<T> future = CompletableFuture.completedFuture(result);
    when(restAction.submit()).thenReturn(future);
    when(restAction.complete()).thenReturn(result);
  }
}
