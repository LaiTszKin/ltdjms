package ltdjms.discord.aiagent.unit.services.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.langchain4j.invocation.InvocationParameters;
import ltdjms.discord.aiagent.services.tools.LangChain4jModifyChannelPermissionsTool;
import ltdjms.discord.shared.di.JDAProvider;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.managers.channel.attribute.IPermissionContainerManager;

@DisplayName("T025: LangChain4jModifyChannelPermissionsTool 單元測試")
class LangChain4jModifyChannelPermissionsToolTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_CHANNEL_ID = 999999999999999999L;
  private static final long TEST_ROLE_ID = 111111111111111111L;
  private static final long TEST_MEMBER_ID = 222222222222222222L;
  private static final long TEST_CALLER_ID = 333333333333333333L;

  private LangChain4jModifyChannelPermissionsTool tool;
  private Guild mockGuild;
  private GuildChannel mockChannel;
  private IPermissionContainer mockPermissionContainer;
  private JDA mockJda;
  private InvocationParameters parameters;

  @BeforeEach
  void setUp() {
    mockGuild = mock(Guild.class);
    mockJda = mock(JDA.class);
    tool = new LangChain4jModifyChannelPermissionsTool();
    parameters = new InvocationParameters();

    // 設置測試參數
    parameters.put("guildId", TEST_GUILD_ID);
    parameters.put("channelId", TEST_CHANNEL_ID);
    parameters.put("userId", TEST_CALLER_ID);

    JDAProvider.setJda(mockJda);
    when(mockJda.getGuildById(TEST_GUILD_ID)).thenReturn(mockGuild);
    Member mockCaller = mock(Member.class);
    when(mockGuild.getMemberById(TEST_CALLER_ID)).thenReturn(mockCaller);
    when(mockCaller.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);

    // Mock 頻道（同時實現 IPermissionContainer）
    mockChannel =
        mock(GuildChannel.class, withSettings().extraInterfaces(IPermissionContainer.class));
    mockPermissionContainer = (IPermissionContainer) mockChannel;

    when(mockGuild.getGuildChannelById(TEST_CHANNEL_ID)).thenReturn(mockChannel);
    when(mockChannel.getIdLong()).thenReturn(TEST_CHANNEL_ID);
    when(mockChannel.getName()).thenReturn("test-channel");
    when(mockChannel.getType()).thenReturn(net.dv8tion.jda.api.entities.channel.ChannelType.TEXT);
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
    @DisplayName("缺少 channelId 應返回錯誤")
    void missingChannelIdShouldReturnError() {
      String result =
          tool.modifyChannelSettings(null, "123", "role", null, null, null, null, null, parameters);

      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("channelId 未提供");
    }

    @Test
    @DisplayName("缺少 targetId 應返回錯誤")
    void missingTargetIdShouldReturnError() {
      String result =
          tool.modifyChannelSettings(
              "123", null, "role", List.of("VIEW_CHANNEL"), null, null, null, null, parameters);

      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("targetId 未提供");
    }

    @Test
    @DisplayName("無效的 targetType 應返回錯誤")
    void invalidTargetTypeShouldReturnError() {
      String result =
          tool.modifyChannelSettings(
              "123", "456", "invalid", List.of("VIEW_CHANNEL"), null, null, null, null, parameters);

      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("targetType 必須是 'member' 或 'role'");
    }

    @Test
    @DisplayName("未指定任何權限操作應返回錯誤")
    void noPermissionChangesShouldReturnError() {
      String result =
          tool.modifyChannelSettings(
              String.valueOf(TEST_CHANNEL_ID),
              "456",
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
    @DisplayName("新頻道名稱為空白應返回錯誤")
    void blankChannelNameShouldReturnError() {
      String result =
          tool.modifyChannelSettings(
              String.valueOf(TEST_CHANNEL_ID),
              null,
              null,
              null,
              null,
              null,
              null,
              "   ",
              parameters);

      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("新的頻道名稱不能為空白");
    }

    @Test
    @DisplayName("新頻道名稱超過上限應返回錯誤")
    void tooLongChannelNameShouldReturnError() {
      String result =
          tool.modifyChannelSettings(
              String.valueOf(TEST_CHANNEL_ID),
              null,
              null,
              null,
              null,
              null,
              null,
              "a".repeat(101),
              parameters);

      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("頻道名稱不能超過 100 字");
    }

    @Test
    @DisplayName("僅改名應成功")
    void renameOnlyShouldSucceed() {
      @SuppressWarnings("unchecked")
      IPermissionContainerManager<?, ?> channelManager = mock(IPermissionContainerManager.class);
      doReturn(channelManager).when(mockChannel).getManager();
      doReturn(channelManager).when(channelManager).setName("new-channel");
      doNothing().when(channelManager).complete();

      String result =
          tool.modifyChannelSettings(
              String.valueOf(TEST_CHANNEL_ID),
              null,
              null,
              null,
              null,
              null,
              null,
              "new-channel",
              parameters);

      assertThat(result).contains("\"success\": true");
      assertThat(result).contains("\"renamed\": true");
      assertThat(result).contains("\"permissionsUpdated\": false");
      assertThat(result).contains("\"channelName\": \"new-channel\"");
    }
  }

  @Nested
  @DisplayName("錯誤處理測試")
  class ErrorHandlingTests {

    @Test
    @DisplayName("找不到頻道應返回錯誤")
    void channelNotFoundShouldReturnError() {
      when(mockGuild.getGuildChannelById(TEST_CHANNEL_ID)).thenReturn(null);

      String result =
          tool.modifyChannelSettings(
              String.valueOf(TEST_CHANNEL_ID),
              String.valueOf(TEST_ROLE_ID),
              "role",
              List.of("VIEW_CHANNEL"),
              null,
              null,
              null,
              null,
              parameters);

      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("找不到頻道");
    }

    @Test
    @DisplayName("找不到角色應返回錯誤")
    void roleNotFoundShouldReturnError() {
      when(mockGuild.getRoleById(TEST_ROLE_ID)).thenReturn(null);

      String result =
          tool.modifyChannelSettings(
              String.valueOf(TEST_CHANNEL_ID),
              String.valueOf(TEST_ROLE_ID),
              "role",
              List.of("VIEW_CHANNEL"),
              null,
              null,
              null,
              null,
              parameters);

      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("找不到指定的角色");
    }

    @Test
    @DisplayName("找不到用戶應返回錯誤")
    void memberNotFoundShouldReturnError() {
      when(mockGuild.getMemberById(TEST_MEMBER_ID)).thenReturn(null);

      String result =
          tool.modifyChannelSettings(
              String.valueOf(TEST_CHANNEL_ID),
              String.valueOf(TEST_MEMBER_ID),
              "member",
              List.of("VIEW_CHANNEL"),
              null,
              null,
              null,
              null,
              parameters);

      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("找不到指定的用戶");
    }
  }

  @Nested
  @DisplayName("ID 解析測試")
  class IdParsingTests {

    @Test
    @DisplayName("無效 ID 格式應返回錯誤")
    void invalidIdFormatShouldReturnError() {
      String result =
          tool.modifyChannelSettings(
              "invalid",
              "123",
              "role",
              List.of("VIEW_CHANNEL"),
              null,
              null,
              null,
              null,
              parameters);

      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("無效的 ID 格式");
    }

    @Test
    @DisplayName("支援純數字頻道 ID")
    void plainNumericChannelIdShouldWork() {
      // 設置角色 mock
      when(mockGuild.getRoleById(TEST_ROLE_ID))
          .thenReturn(mock(net.dv8tion.jda.api.entities.Role.class));

      String result =
          tool.modifyChannelSettings(
              "987654321",
              "111222333",
              "role",
              List.of("VIEW_CHANNEL"),
              null,
              null,
              null,
              null,
              parameters);

      // 應該到達角色查找階段（而不是在 ID 解析階段失敗）
      assertThat(result).contains("\"success\": false");
      // 錯誤應該是關於角色查找，而不是 ID 格式
      assertThat(result).doesNotContain("無效的 ID 格式");
    }
  }
}
