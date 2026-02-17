package ltdjms.discord.aiagent.integration.services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import dev.langchain4j.invocation.InvocationParameters;
import ltdjms.discord.aiagent.services.ToolExecutionContext;
import ltdjms.discord.aiagent.services.tools.LangChain4jModifyChannelPermissionsTool;
import ltdjms.discord.shared.di.JDAProvider;

/**
 * 整合測試 {@link LangChain4jModifyChannelPermissionsTool}。
 *
 * <p>測試範圍：
 *
 * <ul>
 *   <li>T025: LangChain4jModifyChannelPermissionsTool 整合測試
 * </ul>
 *
 * <p>使用真實的 Discord API 進行測試（需要有效的測試環境）。
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("T025: LangChain4jModifyChannelPermissionsTool 整合測試")
class LangChain4jModifyChannelPermissionsToolIntegrationTest {

  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
          .withDatabaseName("test_db")
          .withUsername("test")
          .withPassword("test");

  private LangChain4jModifyChannelPermissionsTool tool;
  private InvocationParameters parameters;

  @BeforeAll
  static void setUpBeforeAll() {
    // 確保 PostgreSQL 容器已啟動
    POSTGRES.start();
  }

  @BeforeEach
  void setUp() {
    tool = new LangChain4jModifyChannelPermissionsTool();
    parameters = new InvocationParameters();

    // 注意：整合測試需要真實的 JDA 實例
    // 在實際環境中，需要使用 Test Discord 伺服器
    // 這裡僅作為模板，實際執行需要配置真實的 Discord Token
  }

  @AfterEach
  void tearDown() {
    ToolExecutionContext.clearContext();
    JDAProvider.clear();
  }

  @Nested
  @DisplayName("角色權限修改測試")
  class RolePermissionTests {

    @Test
    @Disabled("需要真實的 Discord 環境和配置")
    @DisplayName("應成功為角色添加允許權限")
    void shouldSuccessfullyAddAllowedPermissionsToRole() {
      // Given
      // long guildId = ...;
      // long channelId = ...;
      // long userId = ...;
      // long roleId = ...;
      // ToolExecutionContext.setContext(guildId, channelId, userId);
      // parameters.put("guildId", guildId);
      // parameters.put("channelId", channelId);
      // parameters.put("userId", userId);

      // When
      // String result =
      //     tool.modifyChannelSettings(
      //         String.valueOf(channelId),
      //         String.valueOf(roleId),
      //         "role",
      //         List.of("VIEW_CHANNEL", "MESSAGE_SEND"),
      //         null,
      //         null,
      //         null,
      //         parameters);

      // Then
      // assertThat(result).contains("\"success\": true");
      // assertThat(result).contains("\"after\"");
      // assertThat(result).contains("VIEW_CHANNEL");
      // assertThat(result).contains("MESSAGE_SEND");
    }

    @Test
    @Disabled("需要真實的 Discord 環境和配置")
    @DisplayName("應成功移除角色的允許權限")
    void shouldSuccessfullyRemoveAllowedPermissionsFromRole() {
      // Given
      // long guildId = ...;
      // long channelId = ...;
      // long userId = ...;
      // long roleId = ...;
      // ToolExecutionContext.setContext(guildId, channelId, userId);
      // parameters.put("guildId", guildId);
      // parameters.put("channelId", channelId);
      // parameters.put("userId", userId);

      // When
      // String result =
      //     tool.modifyChannelSettings(
      //         String.valueOf(channelId),
      //         String.valueOf(roleId),
      //         "role",
      //         null,
      //         List.of("MESSAGE_SEND"),
      //         null,
      //         null,
      //         parameters);

      // Then
      // assertThat(result).contains("\"success\": true");
      // assertThat(result).contains("\"before\"");
      // assertThat(result).contains("\"after\"");
    }

    @Test
    @Disabled("需要真實的 Discord 環境和配置")
    @DisplayName("應成功為角色添加拒絕權限")
    void shouldSuccessfullyAddDeniedPermissionsToRole() {
      // Given
      // long guildId = ...;
      // long channelId = ...;
      // long userId = ...;
      // long roleId = ...;
      // ToolExecutionContext.setContext(guildId, channelId, userId);
      // parameters.put("guildId", guildId);
      // parameters.put("channelId", channelId);
      // parameters.put("userId", userId);

      // When
      // String result =
      //     tool.modifyChannelSettings(
      //         String.valueOf(channelId),
      //         String.valueOf(roleId),
      //         "role",
      //         null,
      //         null,
      //         List.of("VOICE_CONNECT"),
      //         null,
      //         parameters);

      // Then
      // assertThat(result).contains("\"success\": true");
      // assertThat(result).contains("\"denied\"");
      // assertThat(result).contains("VOICE_CONNECT");
    }

    @Test
    @Disabled("需要真實的 Discord 環境和配置")
    @DisplayName("應成功執行複雜的權限修改（添加和移除同時進行）")
    void shouldSuccessfullyHandleComplexPermissionChanges() {
      // Given
      // long guildId = ...;
      // long channelId = ...;
      // long userId = ...;
      // long roleId = ...;
      // ToolExecutionContext.setContext(guildId, channelId, userId);
      // parameters.put("guildId", guildId);
      // parameters.put("channelId", channelId);
      // parameters.put("userId", userId);

      // When - 同時添加和移除多個權限
      // String result =
      //     tool.modifyChannelSettings(
      //         String.valueOf(channelId),
      //         String.valueOf(roleId),
      //         "role",
      //         List.of("MESSAGE_SEND", "MESSAGE_ADD_REACTION"),
      //         List.of("MESSAGE_ATTACH_FILES"),
      //         List.of("VOICE_CONNECT"),
      //         List.of("PRIORITY_SPEAKER"),
      //         parameters);

      // Then
      // assertThat(result).contains("\"success\": true");
      // assertThat(result).contains("MESSAGE_SEND");
      // assertThat(result).contains("MESSAGE_ADD_REACTION");
      // assertThat(result).contains("VOICE_CONNECT");
    }
  }

  @Nested
  @DisplayName("成員權限修改測試")
  class MemberPermissionTests {

    @Test
    @Disabled("需要真實的 Discord 環境和配置")
    @DisplayName("應成功為成員添加允許權限")
    void shouldSuccessfullyAddAllowedPermissionsToMember() {
      // Given
      // long guildId = ...;
      // long channelId = ...;
      // long userId = ...;
      // long targetMemberId = ...;
      // ToolExecutionContext.setContext(guildId, channelId, userId);
      // parameters.put("guildId", guildId);
      // parameters.put("channelId", channelId);
      // parameters.put("userId", userId);

      // When
      // String result =
      //     tool.modifyChannelSettings(
      //         String.valueOf(channelId),
      //         String.valueOf(targetMemberId),
      //         "member",
      //         List.of("VIEW_CHANNEL", "MESSAGE_SEND"),
      //         null,
      //         null,
      //         null,
      //         parameters);

      // Then
      // assertThat(result).contains("\"success\": true");
      // assertThat(result).contains("\"targetType\": \"member\"");
    }

    @Test
    @Disabled("需要真實的 Discord 環境和配置")
    @DisplayName("應成功覆蓋成員的現有權限")
    void shouldSuccessfullyOverrideMemberExistingPermissions() {
      // Given
      // long guildId = ...;
      // long channelId = ...;
      // long userId = ...;
      // long targetMemberId = ...;
      // ToolExecutionContext.setContext(guildId, channelId, userId);
      // parameters.put("guildId", guildId);
      // parameters.put("channelId", channelId);
      // parameters.put("userId", userId);

      // When - 先添加權限
      // tool.modifyChannelSettings(
      //     String.valueOf(channelId),
      //     String.valueOf(targetMemberId),
      //     "member",
      //     List.of("VIEW_CHANNEL", "MESSAGE_SEND", "MESSAGE_ATTACH_FILES"),
      //     null,
      //     null,
      //     null,
      //     parameters);

      // Then - 移除部分權限
      // String result =
      //     tool.modifyChannelSettings(
      //         String.valueOf(channelId),
      //         String.valueOf(targetMemberId),
      //         "member",
      //         null,
      //         List.of("MESSAGE_ATTACH_FILES"),
      //         null,
      //         null,
      //         parameters);

      // assertThat(result).contains("\"success\": true");
      // assertThat(result).contains("\"before\"");
      // assertThat(result).contains("\"after\"");
    }
  }

  @Nested
  @DisplayName("權限互斥性測試")
  class PermissionMutualExclusionTests {

    @Test
    @Disabled("需要真實的 Discord 環境和配置")
    @DisplayName("添加允許權限應自動移除對應的拒絕權限")
    void shouldAutoRemoveDeniedWhenAddingAllowed() {
      // Given
      // long guildId = ...;
      // long channelId = ...;
      // long userId = ...;
      // long roleId = ...;
      // ToolExecutionContext.setContext(guildId, channelId, userId);
      // parameters.put("guildId", guildId);
      // parameters.put("channelId", channelId);
      // parameters.put("userId", userId);

      // 首先添加拒絕權限
      // tool.modifyChannelSettings(
      //     String.valueOf(channelId),
      //     String.valueOf(roleId),
      //     "role",
      //     null,
      //     null,
      //     List.of("MESSAGE_SEND"),
      //     null,
      //     parameters);

      // When - 添加相同的允許權限（應自動移除拒絕）
      // String result =
      //     tool.modifyChannelSettings(
      //         String.valueOf(channelId),
      //         String.valueOf(roleId),
      //         "role",
      //         List.of("MESSAGE_SEND"),
      //         null,
      //         null,
      //         null,
      //         parameters);

      // Then
      // assertThat(result).contains("\"success\": true");
      // 驗證 MESSAGE_SEND 在允許列表中，不在拒絕列表中
    }

    @Test
    @Disabled("需要真實的 Discord 環境和配置")
    @DisplayName("添加拒絕權限應自動移除對應的允許權限")
    void shouldAutoRemoveAllowedWhenAddingDenied() {
      // Given
      // long guildId = ...;
      // long channelId = ...;
      // long userId = ...;
      // long roleId = ...;
      // ToolExecutionContext.setContext(guildId, channelId, userId);
      // parameters.put("guildId", guildId);
      // parameters.put("channelId", channelId);
      // parameters.put("userId", userId);

      // 首先添加允許權限
      // tool.modifyChannelSettings(
      //     String.valueOf(channelId),
      //     String.valueOf(roleId),
      //     "role",
      //     List.of("VOICE_CONNECT"),
      //     null,
      //     null,
      //     null,
      //     parameters);

      // When - 添加相同的拒絕權限（應自動移除允許）
      // String result =
      //     tool.modifyChannelSettings(
      //         String.valueOf(channelId),
      //         String.valueOf(roleId),
      //         "role",
      //         null,
      //         null,
      //         List.of("VOICE_CONNECT"),
      //         null,
      //         parameters);

      // Then
      // assertThat(result).contains("\"success\": true");
      // 驗證 VOICE_CONNECT 在拒絕列表中，不在允許列表中
    }
  }

  @Nested
  @DisplayName("增量操作測試")
  class IncrementalOperationTests {

    @Test
    @Disabled("需要真實的 Discord 環境和配置")
    @DisplayName("多次操作應正確累積權限變更")
    void shouldAccumulatePermissionChangesCorrectly() {
      // Given
      // long guildId = ...;
      // long channelId = ...;
      // long userId = ...;
      // long roleId = ...;
      // ToolExecutionContext.setContext(guildId, channelId, userId);
      // parameters.put("guildId", guildId);
      // parameters.put("channelId", channelId);
      // parameters.put("userId", userId);

      // When - 第一次操作：添加兩個權限
      // String result1 =
      //     tool.modifyChannelSettings(
      //         String.valueOf(channelId),
      //         String.valueOf(roleId),
      //         "role",
      //         List.of("VIEW_CHANNEL", "MESSAGE_SEND"),
      //         null,
      //         null,
      //         null,
      //         parameters);

      // 第二次操作：再添加一個權限
      // String result2 =
      //     tool.modifyChannelSettings(
      //         String.valueOf(channelId),
      //         String.valueOf(roleId),
      //         "role",
      //         List.of("MESSAGE_ATTACH_FILES"),
      //         null,
      //         null,
      //         null,
      //         parameters);

      // Then
      // assertThat(result1).contains("\"success\": true");
      // assertThat(result2).contains("\"success\": true");
      // 最終應包含三個權限
    }
  }

  @Nested
  @DisplayName("ID 格式支援測試")
  class IdFormatSupportTests {

    @Test
    @Disabled("需要真實的 Discord 環境和配置")
    @DisplayName("應支援 Discord 頻道連結格式")
    void shouldSupportDiscordChannelLinkFormat() {
      // Given
      // long guildId = ...;
      // long channelId = ...;
      // long userId = ...;
      // long roleId = ...;
      // ToolExecutionContext.setContext(guildId, channelId, userId);
      // parameters.put("guildId", guildId);
      // parameters.put("channelId", channelId);
      // parameters.put("userId", userId);

      // When - 使用 <#ID> 格式
      // String result =
      //     tool.modifyChannelSettings(
      //         "<#" + channelId + ">",
      //         String.valueOf(roleId),
      //         "role",
      //         List.of("VIEW_CHANNEL"),
      //         null,
      //         null,
      //         null,
      //         parameters);

      // Then
      // assertThat(result).contains("\"success\": true");
    }

    @Test
    @Disabled("需要真實的 Discord 環境和配置")
    @DisplayName("應支援 Discord 角色提及格式")
    void shouldSupportDiscordRoleMentionFormat() {
      // Given
      // long guildId = ...;
      // long channelId = ...;
      // long userId = ...;
      // long roleId = ...;
      // ToolExecutionContext.setContext(guildId, channelId, userId);
      // parameters.put("guildId", guildId);
      // parameters.put("channelId", channelId);
      // parameters.put("userId", userId);

      // When - 使用 <@&ID> 格式
      // String result =
      //     tool.modifyChannelSettings(
      //         String.valueOf(channelId),
      //         "<@&" + roleId + ">",
      //         "role",
      //         List.of("VIEW_CHANNEL"),
      //         null,
      //         null,
      //         null,
      //         parameters);

      // Then
      // assertThat(result).contains("\"success\": true);
    }

    @Test
    @Disabled("需要真實的 Discord 環境和配置")
    @DisplayName("應支援 Discord 用戶提及格式")
    void shouldSupportDiscordUserMentionFormat() {
      // Given
      // long guildId = ...;
      // long channelId = ...;
      // long userId = ...;
      // long targetMemberId = ...;
      // ToolExecutionContext.setContext(guildId, channelId, userId);
      // parameters.put("guildId", guildId);
      // parameters.put("channelId", channelId);
      // parameters.put("userId", userId);

      // When - 使用 <@ID> 格式
      // String result =
      //     tool.modifyChannelSettings(
      //         String.valueOf(channelId),
      //         "<@" + targetMemberId + ">",
      //         "member",
      //         List.of("VIEW_CHANNEL"),
      //         null,
      //         null,
      //         null,
      //         parameters);

      // Then
      // assertThat(result).contains("\"success\": true");
    }
  }
}
