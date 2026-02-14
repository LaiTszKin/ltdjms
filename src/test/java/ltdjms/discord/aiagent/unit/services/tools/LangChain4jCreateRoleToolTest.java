package ltdjms.discord.aiagent.unit.services.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.langchain4j.invocation.InvocationParameters;
import ltdjms.discord.aiagent.services.tools.LangChain4jCreateRoleTool;
import ltdjms.discord.shared.di.JDAProvider;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
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
    Member mockCaller = mock(Member.class);
    when(mockGuild.getMemberById(TEST_USER_ID)).thenReturn(mockCaller);
    when(mockCaller.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);

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

  @Test
  @DisplayName("非管理員呼叫應被拒絕")
  void shouldRejectNonAdminCaller() {
    Member nonAdmin = mock(Member.class);
    when(mockGuild.getMemberById(TEST_USER_ID)).thenReturn(nonAdmin);
    when(nonAdmin.hasPermission(Permission.ADMINISTRATOR)).thenReturn(false);

    String result = tool.createRole("new-role", null, null, null, null, parameters);

    assertThat(result).contains("\"success\": false");
    assertThat(result).contains("你沒有權限使用此工具");
  }
}
