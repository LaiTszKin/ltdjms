package ltdjms.discord.aiagent.integration.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.langchain4j.invocation.InvocationParameters;
import ltdjms.discord.aiagent.services.ToolExecutionContext;
import ltdjms.discord.aiagent.services.tools.LangChain4jListRolesTool;
import ltdjms.discord.shared.di.JDAProvider;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;

/**
 * 整合測試：LangChain4jListRolesTool 端對端測試。
 *
 * <p>測試範圍：
 *
 * <ul>
 *   <li>T025: LangChain4jListRolesTool 整合測試
 * </ul>
 *
 * <p>測試案例涵蓋：
 *
 * <ul>
 *   <li>完整工具調用流程
 *   <li>JSON 輸出格式驗證
 *   <li>與其他工具的一致性
 * </ul>
 */
@DisplayName("T025: LangChain4jListRolesTool 整合測試")
class LangChain4jListRolesToolIntegrationTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_CHANNEL_ID = 999999999999999999L;
  private static final long TEST_USER_ID = 987654321098765432L;

  private LangChain4jListRolesTool tool;
  private JDA mockJda;
  private Guild mockGuild;

  @BeforeEach
  void setUp() {
    mockJda = mock(JDA.class);
    mockGuild = mock(Guild.class);

    tool = new LangChain4jListRolesTool();

    // 設置 JDAProvider
    JDAProvider.setJda(mockJda);
    when(mockJda.getGuildById(TEST_GUILD_ID)).thenReturn(mockGuild);

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
   * @param guildId 伺服器 ID
   * @return InvocationParameters 實例
   */
  private InvocationParameters createInvocationParameters(long guildId) {
    return new InvocationParameters() {
      @Override
      @SuppressWarnings("unchecked")
      public <T> T get(String key) {
        if ("guildId".equals(key)) {
          return (T) Long.valueOf(guildId);
        }
        if ("channelId".equals(key)) {
          return (T) Long.valueOf(TEST_CHANNEL_ID);
        }
        if ("userId".equals(key)) {
          return (T) Long.valueOf(TEST_USER_ID);
        }
        return null;
      }
    };
  }

  /**
   * 模擬角色。
   *
   * @param id 角色 ID
   * @param name 角色名稱
   * @param position 角色位置
   * @return 模擬的 Role
   */
  private Role mockRole(long id, String name, int position) {
    Role role = mock(Role.class);
    when(role.getIdLong()).thenReturn(id);
    when(role.getName()).thenReturn(name);
    when(role.getPosition()).thenReturn(position);
    when(role.compareTo(role)).thenReturn(0);
    return role;
  }

  @Nested
  @DisplayName("端對端流程測試")
  class EndToEndTests {

    @Test
    @DisplayName("完整工具調用應返回正確的 JSON 格式")
    void shouldReturnCorrectJsonFormat() {
      // Given
      Role adminRole = mockRole(111111111111111111L, "管理員", 10);
      Role memberRole = mockRole(222222222222222222L, "成員", 5);
      Role everyoneRole = mockRole(333333333333333333L, "@everyone", 0);

      // 設定比較邏輯
      when(adminRole.compareTo(memberRole)).thenReturn(-1);
      when(adminRole.compareTo(everyoneRole)).thenReturn(-1);
      when(memberRole.compareTo(adminRole)).thenReturn(1);
      when(memberRole.compareTo(everyoneRole)).thenReturn(-1);
      when(everyoneRole.compareTo(adminRole)).thenReturn(1);
      when(everyoneRole.compareTo(memberRole)).thenReturn(1);

      List<Role> roles = List.of(adminRole, memberRole, everyoneRole);
      when(mockGuild.getRoles()).thenReturn(roles);

      // When
      String result = tool.listRoles(createInvocationParameters(TEST_GUILD_ID));

      // Then
      assertThat(result).contains("\"count\": 3");
      assertThat(result).contains("\"roles\": [");
      assertThat(result).contains("\"id\": 111111111111111111");
      assertThat(result).contains("\"name\": \"管理員\"");
      assertThat(result).contains("\"id\": 222222222222222222");
      assertThat(result).contains("\"name\": \"成員\"");
      assertThat(result).contains("\"id\": 333333333333333333");
      assertThat(result).contains("\"name\": \"@everyone\"");
    }

    @Test
    @DisplayName("空角色列表應返回正確的 JSON 格式")
    void shouldReturnCorrectJsonForEmptyList() {
      // Given
      when(mockGuild.getRoles()).thenReturn(new ArrayList<>());

      // When
      String result = tool.listRoles(createInvocationParameters(TEST_GUILD_ID));

      // Then
      assertThat(result).contains("\"count\": 0");
      assertThat(result).contains("\"roles\": [");
      assertThat(result).contains("]");
    }
  }

  @Nested
  @DisplayName("錯誤場景整合測試")
  class ErrorScenarioTests {

    @Test
    @DisplayName("找不到伺服器應返回標準錯誤格式")
    void shouldReturnStandardErrorForGuildNotFound() {
      // Given
      long invalidGuildId = 999999999999999999L;
      when(mockJda.getGuildById(invalidGuildId)).thenReturn(null);

      // When
      String result = tool.listRoles(createInvocationParameters(invalidGuildId));

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("\"error\":");
      assertThat(result).contains("找不到伺服器");
    }

    @Test
    @DisplayName("guildId 未設置應返回標準錯誤格式")
    void shouldReturnStandardErrorForMissingGuildId() {
      // Given
      InvocationParameters nullGuildParams =
          new InvocationParameters() {
            @Override
            public <T> T get(String key) {
              if ("channelId".equals(key)) {
                @SuppressWarnings("unchecked")
                T channelId = (T) Long.valueOf(TEST_CHANNEL_ID);
                return channelId;
              }
              if ("userId".equals(key)) {
                @SuppressWarnings("unchecked")
                T userId = (T) Long.valueOf(TEST_USER_ID);
                return userId;
              }
              return null;
            }
          };

      // When
      String result = tool.listRoles(nullGuildParams);

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("\"error\":");
      assertThat(result).contains("guildId 未設置");
    }
  }

  @Nested
  @DisplayName("與其他工具的一致性測試")
  class ConsistencyTests {

    @Test
    @DisplayName("錯誤格式應與 ListChannelsTool 一致")
    void errorFormatShouldMatchListChannelsTool() {
      // Given
      InvocationParameters nullGuildParams =
          new InvocationParameters() {
            @Override
            public <T> T get(String key) {
              return null;
            }
          };

      // When
      String result = tool.listRoles(nullGuildParams);

      // Then - 驗證錯誤格式包含必要的欄位
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("\"error\":");
      // 不應包含成功時的欄位
      assertThat(result).doesNotContain("\"roles\":");
      assertThat(result).doesNotContain("\"count\":");
    }

    @Test
    @DisplayName("成功格式應與 ListChannelsTool 結構一致")
    void successFormatShouldMatchListChannelsToolStructure() {
      // Given
      Role role = mockRole(111111111111111111L, "Test Role", 5);
      when(role.compareTo(role)).thenReturn(0);
      when(mockGuild.getRoles()).thenReturn(List.of(role));

      // When
      String result = tool.listRoles(createInvocationParameters(TEST_GUILD_ID));

      // Then - 驗證結構一致性
      assertThat(result).contains("\"count\":");
      assertThat(result).contains("\"roles\": [");
      assertThat(result).contains("\"id\":");
      assertThat(result).contains("\"name\":");
      // 不應包含錯誤欄位
      assertThat(result).doesNotContain("\"success\":");
      assertThat(result).doesNotContain("\"error\":");
    }
  }
}
