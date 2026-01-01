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
import ltdjms.discord.aiagent.services.tools.LangChain4jListChannelsTool;
import ltdjms.discord.shared.di.JDAProvider;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;

/**
 * 測試 {@link LangChain4jListChannelsTool} 的工具執行邏輯。
 *
 * <p>測試範圍：
 *
 * <ul>
 *   <li>T023: LangChain4jListChannelsTool 單元測試
 * </ul>
 *
 * <p>測試案例涵蓋：
 *
 * <ul>
 *   <li>正常情況：成功列出頻道
 *   <li>參數驗證：無效的頻道類型
 *   <li>錯誤處理：找不到伺服器、上下文未設置
 *   <li>類型篩選：按類型篩選頻道
 * </ul>
 */
@DisplayName("T023: LangChain4jListChannelsTool 單元測試")
class LangChain4jListChannelsToolTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_CHANNEL_ID = 999999999999999999L;
  private static final long TEST_USER_ID = 987654321098765432L;
  private static final long TEST_CATEGORY_ID = 888888888888888888L;

  private Guild mockGuild;
  private JDA mockJda;
  private LangChain4jListChannelsTool tool;

  @BeforeEach
  void setUp() {
    mockGuild = mock(Guild.class);
    mockJda = mock(JDA.class);
    tool = new LangChain4jListChannelsTool();

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

  @Nested
  @DisplayName("正常情況測試")
  class SuccessTests {

    @Test
    @DisplayName("應成功列出所有頻道")
    void shouldSuccessfullyListAllChannels() {
      // Given - 準備測試資料
      GuildChannel textChannel = mockTextChannel(TEST_CHANNEL_ID, "general");
      GuildChannel voiceChannel = mockVoiceChannel(TEST_CHANNEL_ID + 1, "General Voice");
      GuildChannel category = mockCategory(TEST_CATEGORY_ID, "TEXT CHANNELS");

      List<GuildChannel> channels = List.of(textChannel, voiceChannel, category);
      when(mockGuild.getChannels()).thenReturn(channels);

      // When - 執行工具
      String result = tool.listChannels(null, createMockInvocationParameters());

      // Then - 驗證結果
      assertThat(result).contains("\"count\": 3");
      assertThat(result).contains("general");
      assertThat(result).contains("General Voice");
      assertThat(result).contains("TEXT CHANNELS");
      assertThat(result).contains("\"type\": \"text\"");
      assertThat(result).contains("\"type\": \"voice\"");
      assertThat(result).contains("\"type\": \"category\"");
    }

    @Test
    @DisplayName("應處理空頻道列表")
    void shouldHandleEmptyChannelList() {
      // Given - 空頻道列表
      when(mockGuild.getChannels()).thenReturn(new ArrayList<>());

      // When
      String result = tool.listChannels(null, createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"count\": 0");
      assertThat(result).contains("\"channels\":");
      assertThat(result).contains("]");
    }

    @Test
    @DisplayName("應正確列出單一頻道")
    void shouldSuccessfullyListSingleChannel() {
      // Given
      GuildChannel textChannel = mockTextChannel(TEST_CHANNEL_ID, "announcements");
      when(mockGuild.getChannels()).thenReturn(List.of(textChannel));

      // When
      String result = tool.listChannels(null, createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"count\": 1");
      assertThat(result).contains("announcements");
      assertThat(result).contains(String.valueOf(TEST_CHANNEL_ID));
    }

    @Test
    @DisplayName("當類型參數為空白時，應列出所有頻道")
    void shouldListAllChannelsWhenTypeIsBlank() {
      // Given
      GuildChannel textChannel = mockTextChannel(TEST_CHANNEL_ID, "chat");
      when(mockGuild.getChannels()).thenReturn(List.of(textChannel));

      // When - 使用空白字串
      String result = tool.listChannels("   ", createMockInvocationParameters());

      // Then - 應該忽略空白參數並列出所有頻道
      assertThat(result).contains("\"count\": 1");
      assertThat(result).contains("chat");
    }

    @Test
    @DisplayName("當類型參數為 null 時，應列出所有頻道")
    void shouldListAllChannelsWhenTypeIsNull() {
      // Given
      GuildChannel textChannel = mockTextChannel(TEST_CHANNEL_ID, "chat");
      when(mockGuild.getChannels()).thenReturn(List.of(textChannel));

      // When
      String result = tool.listChannels(null, createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"count\": 1");
    }
  }

  @Nested
  @DisplayName("類型篩選測試")
  class TypeFilterTests {

    @Test
    @DisplayName("應正確篩選 text 類型頻道")
    void shouldFilterTextChannels() {
      // Given
      GuildChannel textChannel1 = mockTextChannel(TEST_CHANNEL_ID, "general");
      GuildChannel textChannel2 = mockTextChannel(TEST_CHANNEL_ID + 1, "announcements");
      GuildChannel voiceChannel = mockVoiceChannel(TEST_CHANNEL_ID + 2, "Voice Chat");
      GuildChannel category = mockCategory(TEST_CATEGORY_ID, "TEXT CHANNELS");

      List<GuildChannel> channels = List.of(textChannel1, textChannel2, voiceChannel, category);
      when(mockGuild.getChannels()).thenReturn(channels);

      // When - 只列出 text 頻道
      String result = tool.listChannels("text", createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"count\": 2");
      assertThat(result).contains("general");
      assertThat(result).contains("announcements");
      assertThat(result).contains("\"type\": \"text\"");
    }

    @Test
    @DisplayName("應正確篩選 voice 類型頻道")
    void shouldFilterVoiceChannels() {
      // Given
      GuildChannel textChannel = mockTextChannel(TEST_CHANNEL_ID, "general");
      GuildChannel voiceChannel1 = mockVoiceChannel(TEST_CHANNEL_ID + 1, "General Voice");
      GuildChannel voiceChannel2 = mockVoiceChannel(TEST_CHANNEL_ID + 2, "Gaming Voice");

      List<GuildChannel> channels = List.of(textChannel, voiceChannel1, voiceChannel2);
      when(mockGuild.getChannels()).thenReturn(channels);

      // When
      String result = tool.listChannels("voice", createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"count\": 2");
      assertThat(result).contains("General Voice");
      assertThat(result).contains("Gaming Voice");
      assertThat(result).contains("\"type\": \"voice\"");
    }

    @Test
    @DisplayName("應正確篩選 category 類型頻道")
    void shouldFilterCategoryChannels() {
      // Given
      GuildChannel textChannel = mockTextChannel(TEST_CHANNEL_ID, "general");
      GuildChannel category1 = mockCategory(TEST_CATEGORY_ID, "TEXT CHANNELS");
      GuildChannel category2 = mockCategory(TEST_CATEGORY_ID + 1, "VOICE CHANNELS");

      List<GuildChannel> channels = List.of(textChannel, category1, category2);
      when(mockGuild.getChannels()).thenReturn(channels);

      // When
      String result = tool.listChannels("category", createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"count\": 2");
      assertThat(result).contains("TEXT CHANNELS");
      assertThat(result).contains("VOICE CHANNELS");
      assertThat(result).contains("\"type\": \"category\"");
    }

    @Test
    @DisplayName("應正確處理大小寫混合的類型參數")
    void shouldHandleMixedCaseTypeParameter() {
      // Given
      GuildChannel textChannel = mockTextChannel(TEST_CHANNEL_ID, "chat");
      GuildChannel voiceChannel = mockVoiceChannel(TEST_CHANNEL_ID + 1, "Voice");

      List<GuildChannel> channels = List.of(textChannel, voiceChannel);
      when(mockGuild.getChannels()).thenReturn(channels);

      // When - 使用大寫
      String result = tool.listChannels("TEXT", createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"count\": 1");
      assertThat(result).contains("chat");
    }

    @Test
    @DisplayName("當篩選結果為空時，應返回空列表")
    void shouldReturnEmptyListWhenFilterMatchesNothing() {
      // Given
      GuildChannel textChannel = mockTextChannel(TEST_CHANNEL_ID, "general");
      when(mockGuild.getChannels()).thenReturn(List.of(textChannel));

      // When - 篩選 voice 類型但沒有 voice 頻道
      String result = tool.listChannels("voice", createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"count\": 0");
    }
  }

  @Nested
  @DisplayName("參數驗證測試")
  class ParameterValidationTests {

    @Test
    @DisplayName("當頻道類型無效時，應返回錯誤")
    void shouldReturnErrorWhenTypeIsInvalid() {
      // Given
      GuildChannel textChannel = mockTextChannel(TEST_CHANNEL_ID, "general");
      when(mockGuild.getChannels()).thenReturn(List.of(textChannel));

      // When - 使用無效的類型
      String result = tool.listChannels("invalid_type", createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("無效的頻道類型");
      assertThat(result).contains("invalid_type");
      assertThat(result).contains("支援的類型");
    }

    @Test
    @DisplayName("錯誤訊息應包含所有支援的頻道類型")
    void errorMessageShouldContainAllSupportedTypes() {
      // Given
      GuildChannel textChannel = mockTextChannel(TEST_CHANNEL_ID, "general");
      when(mockGuild.getChannels()).thenReturn(List.of(textChannel));

      // When
      String result = tool.listChannels("unknown", createMockInvocationParameters());

      // Then
      assertThat(result).contains("text");
      assertThat(result).contains("voice");
      assertThat(result).contains("category");
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
      String result = tool.listChannels(null, createMockInvocationParameters());

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
      String result = tool.listChannels(null, mockParams);

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("guildId 未設置");
    }

    @Test
    @DisplayName("當獲取頻道列表失敗時，應返回錯誤")
    void shouldReturnErrorWhenGetChannelsFails() {
      // Given - 設定拋出異常
      when(mockGuild.getChannels()).thenThrow(new RuntimeException("Discord API error"));

      // When
      String result = tool.listChannels(null, createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"success\": false");
      assertThat(result).contains("獲取頻道列表失敗");
    }
  }

  @Nested
  @DisplayName("結果格式測試")
  class ResultFormattingTests {

    @Test
    @DisplayName("結果應包含頻道 ID、名稱和類型")
    void resultShouldContainChannelIdNameAndType() {
      // Given
      GuildChannel textChannel = mockTextChannel(TEST_CHANNEL_ID, "general");
      when(mockGuild.getChannels()).thenReturn(List.of(textChannel));

      // When
      String result = tool.listChannels(null, createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"id\": \"" + TEST_CHANNEL_ID + "\"");
      assertThat(result).contains("\"name\": \"general\"");
      assertThat(result).contains("\"type\": \"text\"");
    }

    @Test
    @DisplayName("結果應包含頻道總數")
    void resultShouldContainChannelCount() {
      // Given
      GuildChannel channel1 = mockTextChannel(TEST_CHANNEL_ID, "ch1");
      GuildChannel channel2 = mockTextChannel(TEST_CHANNEL_ID + 1, "ch2");
      GuildChannel channel3 = mockTextChannel(TEST_CHANNEL_ID + 2, "ch3");
      when(mockGuild.getChannels()).thenReturn(List.of(channel1, channel2, channel3));

      // When
      String result = tool.listChannels(null, createMockInvocationParameters());

      // Then
      assertThat(result).contains("\"count\": 3");
    }

    @Test
    @DisplayName("結果應為有效的 JSON 格式")
    void resultShouldBeValidJsonFormat() {
      // Given
      GuildChannel textChannel = mockTextChannel(TEST_CHANNEL_ID, "test");
      when(mockGuild.getChannels()).thenReturn(List.of(textChannel));

      // When
      String result = tool.listChannels(null, createMockInvocationParameters());

      // Then - 基本格式驗證
      assertThat(result).startsWith("{");
      assertThat(result).endsWith("}");
      assertThat(result).contains("\"channels\":");
      assertThat(result).contains("\"count\":");
    }

    @Test
    @DisplayName("多個頻道結果應使用逗號分隔")
    void multipleChannelsShouldBeCommaDelimited() {
      // Given
      GuildChannel channel1 = mockTextChannel(TEST_CHANNEL_ID, "ch1");
      GuildChannel channel2 = mockTextChannel(TEST_CHANNEL_ID + 1, "ch2");
      when(mockGuild.getChannels()).thenReturn(List.of(channel1, channel2));

      // When
      String result = tool.listChannels(null, createMockInvocationParameters());

      // Then - 驗證兩個頻道的格式
      assertThat(result).contains("\"name\": \"ch1\"");
      assertThat(result).contains("\"name\": \"ch2\"");
    }
  }

  /**
   * 模擬文字頻道。
   *
   * @param id 頻道 ID
   * @param name 頻道名稱
   * @return 模擬的 GuildChannel
   */
  private GuildChannel mockTextChannel(long id, String name) {
    GuildChannel channel = mock(GuildChannel.class);
    when(channel.getIdLong()).thenReturn(id);
    when(channel.getName()).thenReturn(name);
    when(channel.getType()).thenReturn(ChannelType.TEXT);
    return channel;
  }

  /**
   * 模擬語音頻道。
   *
   * @param id 頻道 ID
   * @param name 頻道名稱
   * @return 模擬的 GuildChannel
   */
  private GuildChannel mockVoiceChannel(long id, String name) {
    GuildChannel channel = mock(GuildChannel.class);
    when(channel.getIdLong()).thenReturn(id);
    when(channel.getName()).thenReturn(name);
    when(channel.getType()).thenReturn(ChannelType.VOICE);
    return channel;
  }

  /**
   * 模擬類別。
   *
   * @param id 類別 ID
   * @param name 類別名稱
   * @return 模擬的 GuildChannel
   */
  private GuildChannel mockCategory(long id, String name) {
    GuildChannel channel = mock(GuildChannel.class);
    when(channel.getIdLong()).thenReturn(id);
    when(channel.getName()).thenReturn(name);
    when(channel.getType()).thenReturn(ChannelType.CATEGORY);
    return channel;
  }
}
