package ltdjms.discord.aiagent.unit.services.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.managers.channel.concrete.CategoryManager;

@DisplayName("T026: LangChain4jModifyCategoryPermissionsTool 單元測試")
class LangChain4jModifyCategoryPermissionsToolTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_CATEGORY_ID = 888888888888888888L;
  private static final long TEST_ROLE_ID = 111111111111111111L;
  private static final long TEST_CALLER_ID = 222222222222222222L;

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
    parameters.put("userId", TEST_CALLER_ID);

    JDAProvider.setJda(mockJda);
    when(mockJda.getGuildById(TEST_GUILD_ID)).thenReturn(mockGuild);
    Member mockCaller = mock(Member.class);
    when(mockGuild.getMemberById(TEST_CALLER_ID)).thenReturn(mockCaller);
    when(mockCaller.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);

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
      String result =
          tool.modifyCategorySettings(
              null, "123", "role", null, null, null, null, null, parameters);

      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("categoryId 未提供");
    }

    @Test
    @DisplayName("缺少 targetId 應返回錯誤")
    void missingTargetIdShouldReturnError() {
      String result =
          tool.modifyCategorySettings(
              "123", null, "role", List.of("VIEW_CHANNEL"), null, null, null, null, parameters);

      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("targetId 未提供");
    }

    @Test
    @DisplayName("未指定任何操作應返回錯誤")
    void noChangesShouldReturnError() {
      String result =
          tool.modifyCategorySettings(
              String.valueOf(TEST_CATEGORY_ID),
              "123",
              "role",
              null,
              null,
              null,
              null,
              null,
              parameters);

      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("未指定任何權限或名稱修改操作");
    }

    @Test
    @DisplayName("新類別名稱為空白應返回錯誤")
    void blankCategoryNameShouldReturnError() {
      String result =
          tool.modifyCategorySettings(
              String.valueOf(TEST_CATEGORY_ID),
              null,
              null,
              null,
              null,
              null,
              null,
              "   ",
              parameters);

      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("新的類別名稱不能為空白");
    }

    @Test
    @DisplayName("新類別名稱超過上限應返回錯誤")
    void tooLongCategoryNameShouldReturnError() {
      String result =
          tool.modifyCategorySettings(
              String.valueOf(TEST_CATEGORY_ID),
              null,
              null,
              null,
              null,
              null,
              null,
              "a".repeat(101),
              parameters);

      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("類別名稱不能超過 100 字");
    }

    @Test
    @DisplayName("僅改名應成功")
    void renameOnlyShouldSucceed() {
      CategoryManager mockManager = mock(CategoryManager.class);
      doReturn(mockManager).when(mockCategory).getManager();
      doReturn(mockManager).when(mockManager).setName("new-category");
      doNothing().when(mockManager).complete();

      String result =
          tool.modifyCategorySettings(
              String.valueOf(TEST_CATEGORY_ID),
              null,
              null,
              null,
              null,
              null,
              null,
              "new-category",
              parameters);

      assertThat(result).contains("\"success\": true");
      assertThat(result).contains("\"renamed\": true");
      assertThat(result).contains("\"permissionsUpdated\": false");
      assertThat(result).contains("\"categoryName\": \"new-category\"");
    }
  }
}
