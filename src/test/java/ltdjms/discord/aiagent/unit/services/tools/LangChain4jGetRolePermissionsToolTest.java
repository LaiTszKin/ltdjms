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
import net.dv8tion.jda.api.entities.Member;
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
    Member mockCaller = mock(Member.class);
    when(mockGuild.getMemberById(TEST_USER_ID)).thenReturn(mockCaller);
    when(mockCaller.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);

    Role mockRole = mock(Role.class);
    when(mockRole.getIdLong()).thenReturn(TEST_ROLE_ID);
    when(mockRole.getName()).thenReturn("版主");
    when(mockRole.getColorRaw()).thenReturn(3447003);
    when(mockRole.isHoisted()).thenReturn(true);
    when(mockRole.isMentionable()).thenReturn(true);
    when(mockRole.getPosition()).thenReturn(5);
    when(mockRole.isManaged()).thenReturn(false);
    when(mockRole.getPermissions())
        .thenReturn(
            EnumSet.of(
                Permission.ADMINISTRATOR, Permission.MANAGE_CHANNEL, Permission.VIEW_CHANNEL));

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
      assertThat(result).contains("\"id\": \"111111111111111111\"");
      assertThat(result).contains("\"name\": \"版主\"");
      assertThat(result).contains("ADMINISTRATOR");
    }
  }
}
