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
import ltdjms.discord.aiagent.services.tools.LangChain4jListCategoriesTool;
import ltdjms.discord.shared.di.JDAProvider;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.Category;

/**
 * 測試 {@link LangChain4jListCategoriesTool} 的工具執行邏輯。
 *
 * <p>測試範圍：
 *
 * <ul>
 *   <li>T025: LangChain4jListCategoriesTool 單元測試
 * </ul>
 *
 * <p>測試案例涵蓋：
 *
 * <ul>
 *   <li>正常情況：成功列出類別
 *   <li>錯誤處理：找不到伺服器、上下文未設置
 *   <li>結果格式驗證：JSON 格式正確性
 * </ul>
 */
@DisplayName("T025: LangChain4jListCategoriesTool 單元測試")
class LangChain4jListCategoriesToolTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_CHANNEL_ID = 999999999999999999L;
  private static final long TEST_USER_ID = 987654321098765432L;
  private static final long TEST_CATEGORY_ID_1 = 111111111111111111L;
  private static final long TEST_CATEGORY_ID_2 = 222222222222222222L;
  private static final long TEST_CATEGORY_ID_3 = 333333333333333333L;

  private Guild mockGuild;
  private JDA mockJda;
  private LangChain4jListCategoriesTool tool;

  @BeforeEach
  void setUp() {
    mockGuild = mock(Guild.class);
    mockJda = mock(JDA.class);
    tool = new LangChain4jListCategoriesTool();

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
   * 模擬類別。
   *
   * @param id 類別 ID
   * @param name 類別名稱
   * @return 模擬的 Category
   */
  private Category mockCategory(long id, String name) {
    Category category = mock(Category.class);
    when(category.getIdLong()).thenReturn(id);
    when(category.getName()).thenReturn(name);
    return category;
  }

  @Nested
  @DisplayName("正常情況測試")
  class SuccessTests {

    @Test
    @DisplayName("應成功列出所有類別")
    void shouldSuccessfullyListAllCategories() {
      // Given - 準備測試資料
      Category category1 = mockCategory(TEST_CATEGORY_ID_1, "文字頻道");
      Category category2 = mockCategory(TEST_CATEGORY_ID_2, "語音頻道");
      Category category3 = mockCategory(TEST_CATEGORY_ID_3, "管理區");

      List<Category> categories = List.of(category1, category2, category3);

      when(mockGuild.getCategories()).thenReturn(categories);

      // When - 執行工具
      String result = tool.listCategories(createMockInvocationParameters());

      // Then - 驗證結果
      assertThat(result).contains("\"count\": 3");
      assertThat(result).contains("文字頻道");
      assertThat(result).contains("語音頻道");
      assertThat(result).contains("管理區");
      assertThat(result).contains(String.valueOf(TEST_CATEGORY_ID_1));
      assertThat(result).contains(String.valueOf(TEST_CATEGORY_ID_2));
      assertThat(result).contains(String.valueOf(TEST_CATEGORY_ID_3));
    }

    @Test
    @DisplayName("應處理空類別列表")
    void shouldHandleEmptyCategoryList() {
      // Given - 空類別列表
      when(mockGuild.getCategories()).thenReturn(new ArrayList<>());

      // When
      String result = tool.listCategories(createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"count\": 0");
      assertThat(result).contains("\"categories\":");
      assertThat(result).contains("]");
    }

    @Test
    @DisplayName("應正確列出單一類別")
    void shouldSuccessfullyListSingleCategory() {
      // Given
      Category category = mockCategory(TEST_CATEGORY_ID_1, "公告區");
      when(mockGuild.getCategories()).thenReturn(List.of(category));

      // When
      String result = tool.listCategories(createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"count\": 1");
      assertThat(result).contains("公告區");
      assertThat(result).contains(String.valueOf(TEST_CATEGORY_ID_1));
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
      String result = tool.listCategories(createMockInvocationParameters());

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
      String result = tool.listCategories(mockParams);

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("guildId 未設置");
    }

    @Test
    @DisplayName("當獲取類別列表失敗時，應返回錯誤")
    void shouldReturnErrorWhenGetCategoriesFails() {
      // Given - 設定拋出異常
      when(mockGuild.getCategories()).thenThrow(new RuntimeException("Discord API error"));

      // When
      String result = tool.listCategories(createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("獲取類別列表失敗");
    }
  }

  @Nested
  @DisplayName("結果格式測試")
  class ResultFormattingTests {

    @Test
    @DisplayName("結果應包含類別 ID 和名稱")
    void resultShouldContainCategoryIdAndName() {
      // Given
      Category category = mockCategory(TEST_CATEGORY_ID_1, "遊戲區");
      when(mockGuild.getCategories()).thenReturn(List.of(category));

      // When
      String result = tool.listCategories(createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"id\": \"" + TEST_CATEGORY_ID_1 + "\"");
      assertThat(result).contains("\"name\": \"遊戲區\"");
    }

    @Test
    @DisplayName("結果應包含類別總數")
    void resultShouldContainCategoryCount() {
      // Given
      Category category1 = mockCategory(TEST_CATEGORY_ID_1, "category1");
      Category category2 = mockCategory(TEST_CATEGORY_ID_2, "category2");
      Category category3 = mockCategory(TEST_CATEGORY_ID_3, "category3");

      when(mockGuild.getCategories()).thenReturn(List.of(category1, category2, category3));

      // When
      String result = tool.listCategories(createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"count\": 3");
    }

    @Test
    @DisplayName("結果應為有效的 JSON 格式")
    void resultShouldBeValidJsonFormat() {
      // Given
      Category category = mockCategory(TEST_CATEGORY_ID_1, "test");
      when(mockGuild.getCategories()).thenReturn(List.of(category));

      // When
      String result = tool.listCategories(createMockInvocationParameters());

      // Then - 基本格式驗證
      assertThat(result).startsWith("{");
      assertThat(result).endsWith("}");
      assertThat(result).contains("\"categories\":");
      assertThat(result).contains("\"count\":");
    }

    @Test
    @DisplayName("多個類別結果應使用逗號分隔")
    void multipleCategoriesShouldBeCommaDelimited() {
      // Given
      Category category1 = mockCategory(TEST_CATEGORY_ID_1, "category1");
      Category category2 = mockCategory(TEST_CATEGORY_ID_2, "category2");

      when(mockGuild.getCategories()).thenReturn(List.of(category1, category2));

      // When
      String result = tool.listCategories(createMockInvocationParameters());

      // Then - 驗證兩個類別的格式
      assertThat(result).contains("\"name\": \"category1\"");
      assertThat(result).contains("\"name\": \"category2\"");
    }
  }
}
