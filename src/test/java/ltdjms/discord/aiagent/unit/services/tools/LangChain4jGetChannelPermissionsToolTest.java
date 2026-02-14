package ltdjms.discord.aiagent.unit.services.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

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
import ltdjms.discord.aiagent.services.tools.LangChain4jGetChannelPermissionsTool;
import ltdjms.discord.shared.di.JDAProvider;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;

/**
 * 測試 {@link LangChain4jGetChannelPermissionsTool} 的工具執行邏輯。
 *
 * <p>測試範圍：
 *
 * <ul>
 *   <li>T024: LangChain4jGetChannelPermissionsTool 單元測試
 * </ul>
 *
 * <p>測試案例涵蓋：
 *
 * <ul>
 *   <li>正常情況：成功獲取頻道權限
 *   <li>參數驗證：無效的頻道 ID、空參數
 *   <li>錯誤處理：找不到頻道、找不到伺服器
 *   <li>結果格式：JSON 格式驗證、權限覆寫資訊
 * </ul>
 */
@DisplayName("T024: LangChain4jGetChannelPermissionsTool 單元測試")
class LangChain4jGetChannelPermissionsToolTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_CHANNEL_ID = 999999999999999999L;
  private static final long TEST_USER_ID = 987654321098765432L;
  private static final long TEST_ROLE_ID = 111111111111111111L;
  private static final long TEST_MEMBER_ID = 222222222222222222L;

  private Guild mockGuild;
  private JDA mockJda;
  private LangChain4jGetChannelPermissionsTool tool;

  @BeforeEach
  void setUp() {
    mockGuild = mock(Guild.class);
    mockJda = mock(JDA.class);
    tool = new LangChain4jGetChannelPermissionsTool();

    // 設定 JDAProvider
    JDAProvider.setJda(mockJda);

    // 設定 JDA 基本行為
    when(mockJda.getGuildById(TEST_GUILD_ID)).thenReturn(mockGuild);
    Member mockCaller = mock(Member.class);
    when(mockGuild.getMemberById(TEST_USER_ID)).thenReturn(mockCaller);
    when(mockCaller.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);

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
    @DisplayName("應成功獲取頻道權限覆寫")
    void shouldSuccessfullyGetChannelPermissions() {
      // Given - 準備測試資料
      // 創建同時實現 GuildChannel 和 IPermissionContainer 的 mock
      GuildChannel mockChannel =
          mock(GuildChannel.class, withSettings().extraInterfaces(IPermissionContainer.class));
      IPermissionContainer permissionContainer = (IPermissionContainer) mockChannel;

      when(mockChannel.getIdLong()).thenReturn(TEST_CHANNEL_ID);
      when(mockChannel.getName()).thenReturn("test-channel");
      when(mockChannel.getType()).thenReturn(net.dv8tion.jda.api.entities.channel.ChannelType.TEXT);

      // Mock 角色權限覆寫
      PermissionOverride roleOverride = mock(PermissionOverride.class);
      when(roleOverride.getIdLong()).thenReturn(TEST_ROLE_ID);
      when(roleOverride.isRoleOverride()).thenReturn(true);
      when(roleOverride.isMemberOverride()).thenReturn(false);
      when(roleOverride.getAllowed())
          .thenReturn(EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND));
      when(roleOverride.getDenied()).thenReturn(EnumSet.of(Permission.MANAGE_CHANNEL));

      // Mock 成員權限覆寫
      PermissionOverride memberOverride = mock(PermissionOverride.class);
      when(memberOverride.getIdLong()).thenReturn(TEST_MEMBER_ID);
      when(memberOverride.isRoleOverride()).thenReturn(false);
      when(memberOverride.isMemberOverride()).thenReturn(true);
      when(memberOverride.getAllowed()).thenReturn(EnumSet.of(Permission.VIEW_CHANNEL));
      when(memberOverride.getDenied()).thenReturn(EnumSet.noneOf(Permission.class));

      List<PermissionOverride> overrides = List.of(roleOverride, memberOverride);
      when(permissionContainer.getPermissionOverrides()).thenReturn(overrides);

      when(mockGuild.getGuildChannelById(TEST_CHANNEL_ID)).thenReturn(mockChannel);

      // When - 執行工具
      String result =
          tool.getChannelPermissions(
              String.valueOf(TEST_CHANNEL_ID), createMockInvocationParameters());

      // Then - 驗證結果
      assertThat(result).contains("\"success\": true");
      assertThat(result).contains("\"channelId\": \"" + TEST_CHANNEL_ID + "\"");
      assertThat(result).contains("\"channelName\": \"test-channel\"");
      assertThat(result).contains("\"count\": 2");
      assertThat(result).contains("\"type\": \"role\"");
      assertThat(result).contains("\"type\": \"member\"");
      assertThat(result).contains("\"allowed\": [");
      assertThat(result).contains("VIEW_CHANNEL");
      assertThat(result).contains("MESSAGE_SEND");
      assertThat(result).contains("\"denied\": [");
      assertThat(result).contains("MANAGE_CHANNEL");
    }

    @Test
    @DisplayName("應處理空權限覆寫列表")
    void shouldHandleEmptyPermissionOverrides() {
      // Given - 空權限覆寫列表
      GuildChannel mockChannel =
          mock(GuildChannel.class, withSettings().extraInterfaces(IPermissionContainer.class));
      IPermissionContainer permissionContainer = (IPermissionContainer) mockChannel;

      when(mockChannel.getIdLong()).thenReturn(TEST_CHANNEL_ID);
      when(mockChannel.getName()).thenReturn("empty-channel");
      when(mockChannel.getType()).thenReturn(net.dv8tion.jda.api.entities.channel.ChannelType.TEXT);
      when(permissionContainer.getPermissionOverrides()).thenReturn(new ArrayList<>());

      when(mockGuild.getGuildChannelById(TEST_CHANNEL_ID)).thenReturn(mockChannel);

      // When
      String result =
          tool.getChannelPermissions(
              String.valueOf(TEST_CHANNEL_ID), createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": true");
      assertThat(result).contains("\"count\": 0");
      assertThat(result).contains("\"overrides\":");
    }

    @Test
    @DisplayName("應正確處理只有允許權限的覆寫")
    void shouldHandleAllowOnlyOverrides() {
      // Given
      GuildChannel mockChannel =
          mock(GuildChannel.class, withSettings().extraInterfaces(IPermissionContainer.class));
      IPermissionContainer permissionContainer = (IPermissionContainer) mockChannel;

      when(mockChannel.getIdLong()).thenReturn(TEST_CHANNEL_ID);
      when(mockChannel.getName()).thenReturn("public-channel");
      when(mockChannel.getType()).thenReturn(net.dv8tion.jda.api.entities.channel.ChannelType.TEXT);

      PermissionOverride override = mock(PermissionOverride.class);
      when(override.getIdLong()).thenReturn(TEST_ROLE_ID);
      when(override.isRoleOverride()).thenReturn(true);
      when(override.getAllowed()).thenReturn(EnumSet.allOf(Permission.class));
      when(override.getDenied()).thenReturn(EnumSet.noneOf(Permission.class));

      when(permissionContainer.getPermissionOverrides()).thenReturn(List.of(override));
      when(mockGuild.getGuildChannelById(TEST_CHANNEL_ID)).thenReturn(mockChannel);

      // When
      String result =
          tool.getChannelPermissions(
              String.valueOf(TEST_CHANNEL_ID), createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"allowed\": [");
      assertThat(result).doesNotContain("\"denied\":");
    }

    @Test
    @DisplayName("應支援 Discord 頻道連結格式")
    void shouldSupportDiscordChannelLinkFormat() {
      // Given
      GuildChannel mockChannel =
          mock(GuildChannel.class, withSettings().extraInterfaces(IPermissionContainer.class));
      IPermissionContainer permissionContainer = (IPermissionContainer) mockChannel;

      when(mockChannel.getIdLong()).thenReturn(TEST_CHANNEL_ID);
      when(mockChannel.getName()).thenReturn("linked-channel");
      when(mockChannel.getType()).thenReturn(net.dv8tion.jda.api.entities.channel.ChannelType.TEXT);
      when(permissionContainer.getPermissionOverrides()).thenReturn(List.of());

      when(mockGuild.getGuildChannelById(TEST_CHANNEL_ID)).thenReturn(mockChannel);

      // When - 使用 <#ID> 格式
      String result =
          tool.getChannelPermissions(
              "<#" + TEST_CHANNEL_ID + ">", createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": true");
      assertThat(result).contains("\"channelName\": \"linked-channel\"");
    }
  }

  @Nested
  @DisplayName("參數驗證測試")
  class ParameterValidationTests {

    @Test
    @DisplayName("當頻道 ID 為 null 時，應返回錯誤")
    void shouldReturnErrorWhenChannelIdIsNull() {
      // When
      String result = tool.getChannelPermissions(null, createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("channelId 未提供");
    }

    @Test
    @DisplayName("當頻道 ID 為空白時，應返回錯誤")
    void shouldReturnErrorWhenChannelIdIsBlank() {
      // When
      String result = tool.getChannelPermissions("   ", createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("channelId 未提供");
    }

    @Test
    @DisplayName("當頻道 ID 格式無效時，應返回錯誤")
    void shouldReturnErrorWhenChannelIdIsInvalid() {
      // When
      String result = tool.getChannelPermissions("invalid-id", createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("無效的頻道 ID");
    }
  }

  @Nested
  @DisplayName("錯誤處理測試")
  class ErrorHandlingTests {

    @Test
    @DisplayName("當找不到頻道時，應返回錯誤")
    void shouldReturnErrorWhenChannelNotFound() {
      // Given
      when(mockGuild.getGuildChannelById(TEST_CHANNEL_ID)).thenReturn(null);

      // When
      String result =
          tool.getChannelPermissions(
              String.valueOf(TEST_CHANNEL_ID), createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("找不到頻道");
    }

    @Test
    @DisplayName("當找不到伺服器時，應返回錯誤")
    void shouldReturnErrorWhenGuildNotFound() {
      // Given
      when(mockJda.getGuildById(TEST_GUILD_ID)).thenReturn(null);

      // When
      String result =
          tool.getChannelPermissions(
              String.valueOf(TEST_CHANNEL_ID), createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("找不到伺服器");
    }

    @Test
    @DisplayName("當工具執行上下文未設置時，應返回錯誤")
    void shouldReturnErrorWhenContextNotSet() {
      // Given - guildId 為 null
      InvocationParameters mockParams = mock(InvocationParameters.class);
      when(mockParams.get("guildId")).thenReturn(null);

      // When
      String result = tool.getChannelPermissions(String.valueOf(TEST_CHANNEL_ID), mockParams);

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("guildId 未設置");
    }
  }

  @Nested
  @DisplayName("結果格式測試")
  class ResultFormattingTests {

    @Test
    @DisplayName("結果應包含頻道 ID 和名稱")
    void resultShouldContainChannelIdAndName() {
      // Given
      GuildChannel mockChannel =
          mock(GuildChannel.class, withSettings().extraInterfaces(IPermissionContainer.class));
      IPermissionContainer permissionContainer = (IPermissionContainer) mockChannel;

      when(mockChannel.getIdLong()).thenReturn(TEST_CHANNEL_ID);
      when(mockChannel.getName()).thenReturn("test");
      when(mockChannel.getType()).thenReturn(net.dv8tion.jda.api.entities.channel.ChannelType.TEXT);
      when(permissionContainer.getPermissionOverrides()).thenReturn(List.of());

      when(mockGuild.getGuildChannelById(TEST_CHANNEL_ID)).thenReturn(mockChannel);

      // When
      String result =
          tool.getChannelPermissions(
              String.valueOf(TEST_CHANNEL_ID), createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"channelId\": \"" + TEST_CHANNEL_ID + "\"");
      assertThat(result).contains("\"channelName\": \"test\"");
    }

    @Test
    @DisplayName("結果應為有效的 JSON 格式")
    void resultShouldBeValidJsonFormat() {
      // Given
      GuildChannel mockChannel =
          mock(GuildChannel.class, withSettings().extraInterfaces(IPermissionContainer.class));
      IPermissionContainer permissionContainer = (IPermissionContainer) mockChannel;

      when(mockChannel.getIdLong()).thenReturn(TEST_CHANNEL_ID);
      when(mockChannel.getName()).thenReturn("test");
      when(mockChannel.getType()).thenReturn(net.dv8tion.jda.api.entities.channel.ChannelType.TEXT);
      when(permissionContainer.getPermissionOverrides()).thenReturn(List.of());

      when(mockGuild.getGuildChannelById(TEST_CHANNEL_ID)).thenReturn(mockChannel);

      // When
      String result =
          tool.getChannelPermissions(
              String.valueOf(TEST_CHANNEL_ID), createMockInvocationParameters());

      // Then
      assertThat(result).startsWith("{");
      assertThat(result).endsWith("}");
      assertThat(result).contains("\"overrides\":");
      assertThat(result).contains("\"count\":");
    }

    @Test
    @DisplayName("應正確轉義 JSON 特殊字符")
    void shouldEscapeJsonSpecialCharacters() {
      // Given
      GuildChannel mockChannel =
          mock(GuildChannel.class, withSettings().extraInterfaces(IPermissionContainer.class));
      IPermissionContainer permissionContainer = (IPermissionContainer) mockChannel;

      when(mockChannel.getIdLong()).thenReturn(TEST_CHANNEL_ID);
      when(mockChannel.getName()).thenReturn("test\"channel\nwith\\special");
      when(mockChannel.getType()).thenReturn(net.dv8tion.jda.api.entities.channel.ChannelType.TEXT);
      when(permissionContainer.getPermissionOverrides()).thenReturn(List.of());

      when(mockGuild.getGuildChannelById(TEST_CHANNEL_ID)).thenReturn(mockChannel);

      // When
      String result =
          tool.getChannelPermissions(
              String.valueOf(TEST_CHANNEL_ID), createMockInvocationParameters());

      // Then
      assertThat(result).contains("\\\"");
      assertThat(result).contains("\\n");
      assertThat(result).contains("\\\\");
    }
  }
}
