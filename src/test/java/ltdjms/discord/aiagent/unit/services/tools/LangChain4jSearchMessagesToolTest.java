package ltdjms.discord.aiagent.unit.services.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.langchain4j.invocation.InvocationParameters;
import ltdjms.discord.aiagent.services.tools.LangChain4jSearchMessagesTool;
import ltdjms.discord.shared.di.JDAProvider;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.requests.RestAction;

@DisplayName("LangChain4jSearchMessagesTool")
class LangChain4jSearchMessagesToolTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_CURRENT_CHANNEL_ID = 223456789012345678L;
  private static final long TEST_USER_ID = 323456789012345678L;

  private LangChain4jSearchMessagesTool tool;
  private JDA mockJda;
  private Guild mockGuild;
  private InvocationParameters parameters;

  @BeforeEach
  void setUp() {
    tool = new LangChain4jSearchMessagesTool();
    mockJda = mock(JDA.class);
    mockGuild = mock(Guild.class);
    parameters = new InvocationParameters();

    parameters.put("guildId", TEST_GUILD_ID);
    parameters.put("channelId", TEST_CURRENT_CHANNEL_ID);
    parameters.put("userId", TEST_USER_ID);

    JDAProvider.setJda(mockJda);
    when(mockJda.getGuildById(TEST_GUILD_ID)).thenReturn(mockGuild);

    Member caller = mock(Member.class);
    when(mockGuild.getMemberById(TEST_USER_ID)).thenReturn(caller);
    when(caller.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);
  }

  @AfterEach
  void tearDown() {
    JDAProvider.clear();
  }

  @Test
  @DisplayName("應可在指定頻道找到關鍵字訊息")
  void shouldFindMessagesByKeyword() {
    GuildMessageChannel channel = mock(GuildMessageChannel.class);
    when(channel.getName()).thenReturn("search-channel");
    when(mockGuild.getGuildChannelById(TEST_CURRENT_CHANNEL_ID)).thenReturn(channel);

    Message matched = mockMessage(9001L, "urgent deploy completed");
    Message unmatched = mockMessage(9002L, "casual chat");
    mockHistory(channel, List.of(matched, unmatched));

    String result = tool.searchMessages("urgent", null, 10, 100, parameters);

    assertThat(result).contains("\"success\": true");
    assertThat(result).contains("\"matchedCount\": 1");
    assertThat(result).contains("9001");
    assertThat(result).contains("urgent deploy completed");
  }

  @Test
  @DisplayName("未提供 channelIds 時應使用當前頻道")
  void shouldUseCurrentChannelWhenChannelIdsMissing() {
    GuildMessageChannel channel = mock(GuildMessageChannel.class);
    when(channel.getName()).thenReturn("current-channel");
    when(mockGuild.getGuildChannelById(TEST_CURRENT_CHANNEL_ID)).thenReturn(channel);

    Message matched = mockMessage(9101L, "weekly report ready");
    mockHistory(channel, List.of(matched));

    String result = tool.searchMessages("weekly", null, null, null, parameters);

    assertThat(result).contains("\"success\": true");
    assertThat(result).contains("\"channels\":");
    assertThat(result).contains("\"channelId\": \"" + TEST_CURRENT_CHANNEL_ID + "\"");
  }

  @Test
  @DisplayName("keywords 為空時應回傳錯誤")
  void shouldReturnErrorWhenKeywordsBlank() {
    String result = tool.searchMessages("   ", null, null, null, parameters);

    assertThat(result).contains("\"success\": false");
    assertThat(result).contains("keywords 不能為空");
  }

  @Test
  @DisplayName("無效頻道 ID 應在頻道結果中標示錯誤")
  void shouldReportInvalidChannelId() {
    String result = tool.searchMessages("urgent", List.of("not-a-channel-id"), 5, 50, parameters);

    assertThat(result).contains("\"success\": false");
    assertThat(result).contains("無效的頻道 ID 格式");
  }

  @Test
  @DisplayName("多關鍵字應採用 AND 條件匹配")
  void shouldUseAndConditionForMultipleKeywords() {
    GuildMessageChannel channel = mock(GuildMessageChannel.class);
    when(channel.getName()).thenReturn("support");
    when(mockGuild.getGuildChannelById(TEST_CURRENT_CHANNEL_ID)).thenReturn(channel);

    Message bothKeywords = mockMessage(9201L, "invoice issue resolved");
    Message onlyOneKeyword = mockMessage(9202L, "invoice sent");
    mockHistory(channel, List.of(bothKeywords, onlyOneKeyword));

    String result = tool.searchMessages("invoice issue", null, 10, 100, parameters);

    assertThat(result).contains("\"matchedCount\": 1");
    assertThat(result).contains("9201");
    assertThat(result).doesNotContain("9202");
  }

  private Message mockMessage(long messageId, String content) {
    Message message = mock(Message.class);
    User author = mock(User.class);
    when(author.getIdLong()).thenReturn(777777777777777777L);
    when(author.getName()).thenReturn("tester");

    when(message.getIdLong()).thenReturn(messageId);
    when(message.getContentDisplay()).thenReturn(content);
    when(message.getAuthor()).thenReturn(author);
    when(message.getTimeCreated()).thenReturn(OffsetDateTime.parse("2026-01-01T00:00:00Z"));
    when(message.getJumpUrl())
        .thenReturn("https://discord.com/channels/guild/channel/" + messageId);
    return message;
  }

  private void mockHistory(GuildMessageChannel channel, List<Message> messages) {
    MessageHistory history = mock(MessageHistory.class);
    @SuppressWarnings("unchecked")
    RestAction<List<Message>> retrieveAction = mock(RestAction.class);

    when(channel.getHistory()).thenReturn(history);
    when(history.retrievePast(anyInt())).thenReturn(retrieveAction);
    when(retrieveAction.submit()).thenReturn(CompletableFuture.completedFuture(messages));
  }
}
