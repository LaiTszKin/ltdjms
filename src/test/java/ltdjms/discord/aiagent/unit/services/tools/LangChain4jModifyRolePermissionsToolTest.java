package ltdjms.discord.aiagent.unit.services.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.EnumSet;

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
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.managers.RoleManager;

@DisplayName("T029: LangChain4jModifyRolePermissionsTool 單元測試")
class LangChain4jModifyRolePermissionsToolTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_ROLE_ID = 111111111111111111L;
  private static final long TEST_USER_ID = 222222222222222222L;

  private LangChain4jModifyRolePermissionsTool tool;
  private Guild mockGuild;
  private Role mockRole;
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
    Member mockCaller = mock(Member.class);
    when(mockGuild.getMemberById(TEST_USER_ID)).thenReturn(mockCaller);
    when(mockCaller.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);

    mockRole = mock(Role.class);
    when(mockRole.getIdLong()).thenReturn(TEST_ROLE_ID);
    when(mockRole.getName()).thenReturn("版主");
    when(mockRole.getPermissionsRaw()).thenReturn(66048L); // VIEW_CHANNEL + MESSAGE_SEND
    when(mockRole.getPermissions())
        .thenReturn(EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND));

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
      String result = tool.modifyRoleSettings(null, null, null, null, parameters);

      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("roleId 未提供");
    }

    @Test
    @DisplayName("未指定任何操作應返回錯誤")
    void noChangesShouldReturnError() {
      String result =
          tool.modifyRoleSettings(String.valueOf(TEST_ROLE_ID), null, null, null, parameters);

      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("未指定任何權限或名稱修改操作");
    }

    @Test
    @DisplayName("新角色名稱為空白應返回錯誤")
    void blankRoleNameShouldReturnError() {
      String result =
          tool.modifyRoleSettings(String.valueOf(TEST_ROLE_ID), "   ", null, null, parameters);

      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("新的角色名稱不能為空白");
    }

    @Test
    @DisplayName("新角色名稱超過上限應返回錯誤")
    void tooLongRoleNameShouldReturnError() {
      String result =
          tool.modifyRoleSettings(
              String.valueOf(TEST_ROLE_ID), "a".repeat(101), null, null, parameters);

      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("角色名稱不能超過 100 字");
    }

    @Test
    @DisplayName("僅改名應成功")
    void renameOnlyShouldSucceed() {
      RoleManager mockManager = mock(RoleManager.class);
      doReturn(mockManager).when(mockRole).getManager();
      doReturn(mockManager).when(mockManager).setName("新角色");
      doNothing().when(mockManager).complete();

      String result =
          tool.modifyRoleSettings(String.valueOf(TEST_ROLE_ID), "新角色", null, null, parameters);

      assertThat(result).contains("\"success\": true");
      assertThat(result).contains("\"renamed\": true");
      assertThat(result).contains("\"permissionsUpdated\": false");
      assertThat(result).contains("\"name\": \"新角色\"");
    }
  }
}
