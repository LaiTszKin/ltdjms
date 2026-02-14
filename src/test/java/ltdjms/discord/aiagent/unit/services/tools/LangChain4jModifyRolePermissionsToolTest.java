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
import ltdjms.discord.aiagent.services.tools.LangChain4jModifyRolePermissionsTool;
import ltdjms.discord.shared.di.JDAProvider;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

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
    Member mockCaller = mock(Member.class);
    when(mockGuild.getMemberById(TEST_USER_ID)).thenReturn(mockCaller);
    when(mockCaller.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);

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
