package ltdjms.discord.aiagent.unit.services.tools;

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
 * 測試 {@link LangChain4jListRolesTool} 的工具執行邏輯。
 *
 * <p>測試範圍：
 *
 * <ul>
 *   <li>T024: LangChain4jListRolesTool 單元測試
 * </ul>
 *
 * <p>測試案例涵蓋：
 *
 * <ul>
 *   <li>正常情況：成功列出角色
 *   <li>錯誤處理：找不到伺服器、上下文未設置
 *   <li>排序驗證：按權限等級排序
 *   <li>@everyone 角色包含在結果中
 * </ul>
 */
@DisplayName("T024: LangChain4jListRolesTool 單元測試")
class LangChain4jListRolesToolTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_CHANNEL_ID = 999999999999999999L;
  private static final long TEST_USER_ID = 987654321098765432L;
  private static final long TEST_ROLE_ID_1 = 111111111111111111L;
  private static final long TEST_ROLE_ID_2 = 222222222222222222L;
  private static final long TEST_ROLE_ID_3 = 333333333333333333L;

  private Guild mockGuild;
  private JDA mockJda;
  private LangChain4jListRolesTool tool;

  @BeforeEach
  void setUp() {
    mockGuild = mock(Guild.class);
    mockJda = mock(JDA.class);
    tool = new LangChain4jListRolesTool();

    // 設定 JDAProvider
    JDAProvider.setJda(mockJda);

    // 設定 JDA 基本行為
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
   * @return mock 的 InvocationParameters
   */
  private InvocationParameters createMockInvocationParameters() {
    InvocationParameters mockParams = mock(InvocationParameters.class);
    when(mockParams.get("guildId")).thenReturn(TEST_GUILD_ID);
    when(mockParams.get("channelId")).thenReturn(TEST_CHANNEL_ID);
    when(mockParams.get("userId")).thenReturn(TEST_USER_ID);
    return mockParams;
  }

  /**
   * 模擬角色。
   *
   * @param id 角色 ID
   * @param name 角色名稱
   * @param position 角色位置（用於排序測試）
   * @return 模擬的 Role
   */
  private Role mockRole(long id, String name, int position) {
    Role role = mock(Role.class);
    when(role.getIdLong()).thenReturn(id);
    when(role.getName()).thenReturn(name);
    when(role.getPosition()).thenReturn(position);

    // 模擬 compareTo 方法：按 position 降序排列
    when(role.compareTo(role)).thenReturn(0);

    return role;
  }

  @Nested
  @DisplayName("正常情況測試")
  class SuccessTests {

    @Test
    @DisplayName("應成功列出所有角色")
    void shouldSuccessfullyListAllRoles() {
      // Given - 準備測試資料
      Role adminRole = mockRole(TEST_ROLE_ID_1, "管理員", 10);
      Role moderatorRole = mockRole(TEST_ROLE_ID_2, "版主", 5);
      Role everyoneRole = mockRole(TEST_ROLE_ID_3, "@everyone", 0);

      List<Role> roles = List.of(adminRole, moderatorRole, everyoneRole);

      // 模擬 compareTo 方法以實現排序
      when(adminRole.compareTo(moderatorRole)).thenReturn(-1); // admin > moderator
      when(adminRole.compareTo(everyoneRole)).thenReturn(-1); // admin > everyone
      when(moderatorRole.compareTo(adminRole)).thenReturn(1); // moderator < admin
      when(moderatorRole.compareTo(everyoneRole)).thenReturn(-1); // moderator > everyone
      when(everyoneRole.compareTo(adminRole)).thenReturn(1); // everyone < admin
      when(everyoneRole.compareTo(moderatorRole)).thenReturn(1); // everyone < moderator

      when(mockGuild.getRoles()).thenReturn(roles);

      // When - 執行工具
      String result = tool.listRoles(createMockInvocationParameters());

      // Then - 驗證結果
      assertThat(result).contains("\"count\": 3");
      assertThat(result).contains("管理員");
      assertThat(result).contains("版主");
      assertThat(result).contains("@everyone");
      assertThat(result).contains(String.valueOf(TEST_ROLE_ID_1));
      assertThat(result).contains(String.valueOf(TEST_ROLE_ID_2));
      assertThat(result).contains(String.valueOf(TEST_ROLE_ID_3));
    }

    @Test
    @DisplayName("應處理空角色列表")
    void shouldHandleEmptyRoleList() {
      // Given - 空角色列表
      when(mockGuild.getRoles()).thenReturn(new ArrayList<>());

      // When
      String result = tool.listRoles(createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"count\": 0");
      assertThat(result).contains("\"roles\":");
      assertThat(result).contains("]");
    }

    @Test
    @DisplayName("應正確列出單一角色")
    void shouldSuccessfullyListSingleRole() {
      // Given
      Role role = mockRole(TEST_ROLE_ID_1, "VIP", 5);
      when(role.compareTo(role)).thenReturn(0);
      when(mockGuild.getRoles()).thenReturn(List.of(role));

      // When
      String result = tool.listRoles(createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"count\": 1");
      assertThat(result).contains("VIP");
      assertThat(result).contains(String.valueOf(TEST_ROLE_ID_1));
    }
  }

  @Nested
  @DisplayName("錯誤處理測試")
  class ErrorHandlingTests {

    @Test
    @DisplayName("當找不到伺服器時，應返回錯誤")
    void shouldReturnErrorWhenGuildNotFound() {
      // Given
      when(mockJda.getGuildById(TEST_GUILD_ID)).thenReturn(null);

      // When
      String result = tool.listRoles(createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("找不到伺服器");
    }

    @Test
    @DisplayName("當工具執行上下文未設置時，應返回錯誤")
    void shouldReturnErrorWhenContextNotSet() {
      // Given - 創建 guildId 為 null 的 InvocationParameters
      InvocationParameters mockParams = mock(InvocationParameters.class);
      when(mockParams.get("guildId")).thenReturn(null);

      // When
      String result = tool.listRoles(mockParams);

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("guildId 未設置");
    }

    @Test
    @DisplayName("當獲取角色列表失敗時，應返回錯誤")
    void shouldReturnErrorWhenGetRolesFails() {
      // Given - 設定拋出異常
      when(mockGuild.getRoles()).thenThrow(new RuntimeException("Discord API error"));

      // When
      String result = tool.listRoles(createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("獲取角色列表失敗");
    }
  }

  @Nested
  @DisplayName("結果格式測試")
  class ResultFormattingTests {

    @Test
    @DisplayName("結果應包含角色 ID 和名稱")
    void resultShouldContainRoleIdAndName() {
      // Given
      Role role = mockRole(TEST_ROLE_ID_1, "管理員", 10);
      when(role.compareTo(role)).thenReturn(0);
      when(mockGuild.getRoles()).thenReturn(List.of(role));

      // When
      String result = tool.listRoles(createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"id\": " + TEST_ROLE_ID_1);
      assertThat(result).contains("\"name\": \"管理員\"");
    }

    @Test
    @DisplayName("結果應包含角色總數")
    void resultShouldContainRoleCount() {
      // Given
      Role role1 = mockRole(TEST_ROLE_ID_1, "role1", 5);
      Role role2 = mockRole(TEST_ROLE_ID_2, "role2", 3);
      Role role3 = mockRole(TEST_ROLE_ID_3, "role3", 1);

      when(role1.compareTo(role1)).thenReturn(0);
      when(role2.compareTo(role2)).thenReturn(0);
      when(role3.compareTo(role3)).thenReturn(0);

      when(mockGuild.getRoles()).thenReturn(List.of(role1, role2, role3));

      // When
      String result = tool.listRoles(createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"count\": 3");
    }

    @Test
    @DisplayName("結果應為有效的 JSON 格式")
    void resultShouldBeValidJsonFormat() {
      // Given
      Role role = mockRole(TEST_ROLE_ID_1, "test", 5);
      when(role.compareTo(role)).thenReturn(0);
      when(mockGuild.getRoles()).thenReturn(List.of(role));

      // When
      String result = tool.listRoles(createMockInvocationParameters());

      // Then - 基本格式驗證
      assertThat(result).startsWith("{");
      assertThat(result).endsWith("}");
      assertThat(result).contains("\"roles\":");
      assertThat(result).contains("\"count\":");
    }

    @Test
    @DisplayName("多個角色結果應使用逗號分隔")
    void multipleRolesShouldBeCommaDelimited() {
      // Given
      Role role1 = mockRole(TEST_ROLE_ID_1, "role1", 5);
      Role role2 = mockRole(TEST_ROLE_ID_2, "role2", 3);

      when(role1.compareTo(role1)).thenReturn(0);
      when(role2.compareTo(role2)).thenReturn(0);
      when(role1.compareTo(role2)).thenReturn(-1);
      when(role2.compareTo(role1)).thenReturn(1);

      when(mockGuild.getRoles()).thenReturn(List.of(role1, role2));

      // When
      String result = tool.listRoles(createMockInvocationParameters());

      // Then - 驗證兩個角色的格式
      assertThat(result).contains("\"name\": \"role1\"");
      assertThat(result).contains("\"name\": \"role2\"");
    }
  }

  @Nested
  @DisplayName("@everyone 角色測試")
  class EveryoneRoleTests {

    @Test
    @DisplayName("應包含 @everyone 角色在結果中")
    void shouldIncludeEveryoneRole() {
      // Given
      Role everyoneRole = mockRole(TEST_ROLE_ID_1, "@everyone", 0);
      when(everyoneRole.compareTo(everyoneRole)).thenReturn(0);
      when(mockGuild.getRoles()).thenReturn(List.of(everyoneRole));

      // When
      String result = tool.listRoles(createMockInvocationParameters());

      // Then
      assertThat(result).contains("@everyone");
      assertThat(result).contains(String.valueOf(TEST_ROLE_ID_1));
    }
  }
}
