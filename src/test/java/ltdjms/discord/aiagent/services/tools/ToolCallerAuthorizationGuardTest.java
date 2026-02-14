package ltdjms.discord.aiagent.services.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import dev.langchain4j.invocation.InvocationParameters;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.requests.restaction.CacheRestAction;

@DisplayName("ToolCallerAuthorizationGuard 單元測試")
class ToolCallerAuthorizationGuardTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_USER_ID = 222222222222222222L;

  private InvocationParameters parametersWithUserId(long userId) {
    InvocationParameters parameters = new InvocationParameters();
    parameters.put("userId", userId);
    return parameters;
  }

  @Test
  @DisplayName("非管理員應被拒絕")
  void shouldRejectNonAdminCaller() {
    Guild guild = mock(Guild.class);
    Member caller = mock(Member.class);
    Logger logger = mock(Logger.class);
    InvocationParameters parameters = parametersWithUserId(TEST_USER_ID);

    when(guild.getIdLong()).thenReturn(TEST_GUILD_ID);
    when(guild.getMemberById(TEST_USER_ID)).thenReturn(caller);
    when(guild.getOwnerIdLong()).thenReturn(999L);
    when(caller.hasPermission(Permission.ADMINISTRATOR)).thenReturn(false);

    String error =
        ToolCallerAuthorizationGuard.validateAdministrator(parameters, guild, logger, "TestTool");

    assertThat(error).isEqualTo("你沒有權限使用此工具");
  }

  @Test
  @DisplayName("管理員應允許執行")
  void shouldAllowAdminCaller() {
    Guild guild = mock(Guild.class);
    Member caller = mock(Member.class);
    Logger logger = mock(Logger.class);
    InvocationParameters parameters = parametersWithUserId(TEST_USER_ID);

    when(guild.getMemberById(TEST_USER_ID)).thenReturn(caller);
    when(caller.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);

    String error =
        ToolCallerAuthorizationGuard.validateAdministrator(parameters, guild, logger, "TestTool");

    assertThat(error).isNull();
  }

  @Test
  @DisplayName("快取未命中但可成功拉取成員時應允許")
  void shouldAllowWhenMemberRetrievedFromApi() {
    Guild guild = mock(Guild.class);
    Member caller = mock(Member.class);
    @SuppressWarnings("unchecked")
    CacheRestAction<Member> retrieveAction = mock(CacheRestAction.class);
    Logger logger = mock(Logger.class);
    InvocationParameters parameters = parametersWithUserId(TEST_USER_ID);

    when(guild.getMemberById(TEST_USER_ID)).thenReturn(null);
    when(guild.retrieveMemberById(TEST_USER_ID)).thenReturn(retrieveAction);
    when(retrieveAction.complete()).thenReturn(caller);
    when(caller.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);

    String error =
        ToolCallerAuthorizationGuard.validateAdministrator(parameters, guild, logger, "TestTool");

    assertThat(error).isNull();
  }

  @Test
  @DisplayName("當 userId 缺失時應返回錯誤")
  void shouldRejectWhenUserIdMissing() {
    Guild guild = mock(Guild.class);
    Logger logger = mock(Logger.class);
    InvocationParameters parameters = new InvocationParameters();

    String error =
        ToolCallerAuthorizationGuard.validateAdministrator(parameters, guild, logger, "TestTool");

    assertThat(error).isEqualTo("userId 未設置");
  }

  @Test
  @DisplayName("當成員拉取失敗時應返回錯誤")
  void shouldRejectWhenMemberRetrievalFails() {
    Guild guild = mock(Guild.class);
    Logger logger = mock(Logger.class);
    InvocationParameters parameters = parametersWithUserId(TEST_USER_ID);

    when(guild.getMemberById(TEST_USER_ID)).thenReturn(null);
    when(guild.retrieveMemberById(TEST_USER_ID)).thenThrow(new RuntimeException("api down"));

    String error =
        ToolCallerAuthorizationGuard.validateAdministrator(parameters, guild, logger, "TestTool");

    assertThat(error).isEqualTo("找不到呼叫者成員資訊");
  }

  @Test
  @DisplayName("伺服器擁有者應允許執行")
  void shouldAllowGuildOwner() {
    Guild guild = mock(Guild.class);
    Member caller = mock(Member.class);
    Logger logger = mock(Logger.class);
    InvocationParameters parameters = parametersWithUserId(TEST_USER_ID);

    when(guild.getMemberById(TEST_USER_ID)).thenReturn(caller);
    when(guild.getOwnerIdLong()).thenReturn(TEST_USER_ID);
    when(caller.hasPermission(Permission.ADMINISTRATOR)).thenReturn(false);

    String error =
        ToolCallerAuthorizationGuard.validateAdministrator(parameters, guild, logger, "TestTool");

    assertThat(error).isNull();
  }
}
