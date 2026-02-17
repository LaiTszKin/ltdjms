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
import ltdjms.discord.aiagent.services.tools.LangChain4jGetCategoryPermissionsTool;
import ltdjms.discord.shared.di.JDAProvider;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.channel.concrete.Category;

/** 測試 {@link LangChain4jGetCategoryPermissionsTool} 的工具執行邏輯。 */
@DisplayName("T030: LangChain4jGetCategoryPermissionsTool 單元測試")
class LangChain4jGetCategoryPermissionsToolTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_CHANNEL_ID = 999999999999999999L;
  private static final long TEST_USER_ID = 987654321098765432L;
  private static final long TEST_CATEGORY_ID = 111111111111111111L;
  private static final long TEST_ROLE_ID = 222222222222222222L;
  private static final long TEST_MEMBER_ID = 333333333333333333L;

  private Guild mockGuild;
  private JDA mockJda;
  private LangChain4jGetCategoryPermissionsTool tool;

  @BeforeEach
  void setUp() {
    mockGuild = mock(Guild.class);
    mockJda = mock(JDA.class);
    tool = new LangChain4jGetCategoryPermissionsTool();

    JDAProvider.setJda(mockJda);

    when(mockJda.getGuildById(TEST_GUILD_ID)).thenReturn(mockGuild);
    Member mockCaller = mock(Member.class);
    when(mockGuild.getMemberById(TEST_USER_ID)).thenReturn(mockCaller);
    when(mockCaller.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);

    ToolExecutionContext.setContext(TEST_GUILD_ID, TEST_CHANNEL_ID, TEST_USER_ID);
  }

  @AfterEach
  void tearDown() {
    ToolExecutionContext.clearContext();
    JDAProvider.clear();
  }

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
    @DisplayName("應成功獲取類別權限覆寫")
    void shouldSuccessfullyGetCategoryPermissions() {
      Category mockCategory = mock(Category.class);
      when(mockCategory.getIdLong()).thenReturn(TEST_CATEGORY_ID);
      when(mockCategory.getName()).thenReturn("test-category");

      PermissionOverride roleOverride = mock(PermissionOverride.class);
      when(roleOverride.getIdLong()).thenReturn(TEST_ROLE_ID);
      when(roleOverride.isRoleOverride()).thenReturn(true);
      when(roleOverride.getAllowed())
          .thenReturn(EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND));
      when(roleOverride.getDenied()).thenReturn(EnumSet.of(Permission.MANAGE_CHANNEL));

      PermissionOverride memberOverride = mock(PermissionOverride.class);
      when(memberOverride.getIdLong()).thenReturn(TEST_MEMBER_ID);
      when(memberOverride.isRoleOverride()).thenReturn(false);
      when(memberOverride.getAllowed()).thenReturn(EnumSet.of(Permission.VIEW_CHANNEL));
      when(memberOverride.getDenied()).thenReturn(EnumSet.noneOf(Permission.class));

      when(mockCategory.getPermissionOverrides()).thenReturn(List.of(roleOverride, memberOverride));
      when(mockGuild.getCategoryById(TEST_CATEGORY_ID)).thenReturn(mockCategory);

      String result =
          tool.getCategoryPermissions(
              String.valueOf(TEST_CATEGORY_ID), createMockInvocationParameters());

      assertThat(result).contains("\"success\": true");
      assertThat(result).contains("\"categoryId\": \"" + TEST_CATEGORY_ID + "\"");
      assertThat(result).contains("\"categoryName\": \"test-category\"");
      assertThat(result).contains("\"count\": 2");
      assertThat(result).contains("\"type\": \"role\"");
      assertThat(result).contains("\"type\": \"member\"");
      assertThat(result).contains("VIEW_CHANNEL");
      assertThat(result).contains("MESSAGE_SEND");
      assertThat(result).contains("MANAGE_CHANNEL");
    }

    @Test
    @DisplayName("應處理空權限覆寫列表")
    void shouldHandleEmptyPermissionOverrides() {
      Category mockCategory = mock(Category.class);
      when(mockCategory.getIdLong()).thenReturn(TEST_CATEGORY_ID);
      when(mockCategory.getName()).thenReturn("empty-category");
      when(mockCategory.getPermissionOverrides()).thenReturn(new ArrayList<>());

      when(mockGuild.getCategoryById(TEST_CATEGORY_ID)).thenReturn(mockCategory);

      String result =
          tool.getCategoryPermissions(
              String.valueOf(TEST_CATEGORY_ID), createMockInvocationParameters());

      assertThat(result).contains("\"success\": true");
      assertThat(result).contains("\"count\": 0");
      assertThat(result).contains("\"overrides\":");
    }

    @Test
    @DisplayName("應支援 Discord 類別連結格式")
    void shouldSupportDiscordCategoryLinkFormat() {
      Category mockCategory = mock(Category.class);
      when(mockCategory.getIdLong()).thenReturn(TEST_CATEGORY_ID);
      when(mockCategory.getName()).thenReturn("linked-category");
      when(mockCategory.getPermissionOverrides()).thenReturn(List.of());

      when(mockGuild.getCategoryById(TEST_CATEGORY_ID)).thenReturn(mockCategory);

      String result =
          tool.getCategoryPermissions(
              "<#" + TEST_CATEGORY_ID + ">", createMockInvocationParameters());

      assertThat(result).contains("\"success\": true");
      assertThat(result).contains("\"categoryName\": \"linked-category\"");
    }
  }

  @Nested
  @DisplayName("參數驗證測試")
  class ParameterValidationTests {

    @Test
    @DisplayName("當類別 ID 為 null 時，應返回錯誤")
    void shouldReturnErrorWhenCategoryIdIsNull() {
      String result = tool.getCategoryPermissions(null, createMockInvocationParameters());

      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("categoryId 未提供");
    }

    @Test
    @DisplayName("當類別 ID 為空白時，應返回錯誤")
    void shouldReturnErrorWhenCategoryIdIsBlank() {
      String result = tool.getCategoryPermissions("   ", createMockInvocationParameters());

      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("categoryId 未提供");
    }

    @Test
    @DisplayName("當類別 ID 格式無效時，應返回錯誤")
    void shouldReturnErrorWhenCategoryIdIsInvalid() {
      String result = tool.getCategoryPermissions("invalid-id", createMockInvocationParameters());

      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("無效的類別 ID");
    }
  }

  @Nested
  @DisplayName("錯誤處理測試")
  class ErrorHandlingTests {

    @Test
    @DisplayName("當找不到類別時，應返回錯誤")
    void shouldReturnErrorWhenCategoryNotFound() {
      when(mockGuild.getCategoryById(TEST_CATEGORY_ID)).thenReturn(null);

      String result =
          tool.getCategoryPermissions(
              String.valueOf(TEST_CATEGORY_ID), createMockInvocationParameters());

      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("找不到類別");
    }

    @Test
    @DisplayName("當找不到伺服器時，應返回錯誤")
    void shouldReturnErrorWhenGuildNotFound() {
      when(mockJda.getGuildById(TEST_GUILD_ID)).thenReturn(null);

      String result =
          tool.getCategoryPermissions(
              String.valueOf(TEST_CATEGORY_ID), createMockInvocationParameters());

      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("找不到伺服器");
    }

    @Test
    @DisplayName("當 guildId 未設置時，應返回錯誤")
    void shouldReturnErrorWhenGuildIdMissing() {
      InvocationParameters mockParams = mock(InvocationParameters.class);
      when(mockParams.get("guildId")).thenReturn(null);

      String result = tool.getCategoryPermissions(String.valueOf(TEST_CATEGORY_ID), mockParams);

      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("guildId 未設置");
    }
  }
}
