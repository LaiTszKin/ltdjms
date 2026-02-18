package ltdjms.discord.aiagent.unit.services.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.langchain4j.invocation.InvocationParameters;
import ltdjms.discord.aiagent.services.tools.LangChain4jSendMessagesTool;
import ltdjms.discord.shared.di.JDAProvider;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;

@DisplayName("LangChain4jSendMessagesTool")
class LangChain4jSendMessagesToolTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_CURRENT_CHANNEL_ID = 223456789012345678L;
  private static final long TEST_USER_ID = 323456789012345678L;
  private static final long TEST_TARGET_CHANNEL_1 = 423456789012345678L;
  private static final long TEST_TARGET_CHANNEL_2 = 523456789012345678L;

  private LangChain4jSendMessagesTool tool;
  private JDA mockJda;
  private Guild mockGuild;
  private InvocationParameters parameters;

  @BeforeEach
  void setUp() {
    tool = new LangChain4jSendMessagesTool();
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
  @DisplayName("應可發送單則訊息到多個頻道")
  void shouldSendSingleMessageToMultipleChannels() {
    GuildMessageChannel channel1 = mock(GuildMessageChannel.class);
    GuildMessageChannel channel2 = mock(GuildMessageChannel.class);
    when(channel1.getName()).thenReturn("channel-1");
    when(channel2.getName()).thenReturn("channel-2");
    when(mockGuild.getGuildChannelById(TEST_TARGET_CHANNEL_1)).thenReturn(channel1);
    when(mockGuild.getGuildChannelById(TEST_TARGET_CHANNEL_2)).thenReturn(channel2);

    mockSendSuccess(channel1, 10001L);
    mockSendSuccess(channel2, 10002L);

    String result =
        tool.sendMessages(
            List.of(String.valueOf(TEST_TARGET_CHANNEL_1), String.valueOf(TEST_TARGET_CHANNEL_2)),
            "hello world",
            null,
            parameters);

    assertThat(result).contains("\"success\": true");
    assertThat(result).contains("\"successfulChannels\": 2");
    assertThat(result).contains("\"totalMessagesSent\": 2");
    assertThat(result).contains("10001");
    assertThat(result).contains("10002");
  }

  @Test
  @DisplayName("未提供 channelIds 時應使用當前頻道")
  void shouldUseCurrentChannelWhenChannelIdsMissing() {
    GuildMessageChannel currentChannel = mock(GuildMessageChannel.class);
    when(currentChannel.getName()).thenReturn("current-channel");
    when(mockGuild.getGuildChannelById(TEST_CURRENT_CHANNEL_ID)).thenReturn(currentChannel);
    mockSendSuccess(currentChannel, 20001L);

    String result = tool.sendMessages(null, "notify", null, parameters);

    assertThat(result).contains("\"success\": true");
    assertThat(result).contains("\"requestedChannels\": 1");
    assertThat(result).contains("\"totalMessagesSent\": 1");
  }

  @Test
  @DisplayName("未提供任何訊息內容時應回傳錯誤")
  void shouldReturnErrorWhenNoMessageProvided() {
    String result =
        tool.sendMessages(List.of(String.valueOf(TEST_TARGET_CHANNEL_1)), null, null, parameters);

    assertThat(result).contains("\"success\": false");
    assertThat(result).contains("請至少提供一則非空白訊息");
  }

  @Test
  @DisplayName("無效頻道 ID 應在結果中標示失敗")
  void shouldMarkInvalidChannelIdAsFailure() {
    String result = tool.sendMessages(List.of("invalid-channel-id"), "hello", null, parameters);

    assertThat(result).contains("\"success\": false");
    assertThat(result).contains("無效的頻道 ID 格式");
    assertThat(result).contains("\"failedChannels\": 1");
  }

  @Test
  @DisplayName("訊息超過長度上限時應回傳錯誤")
  void shouldReturnErrorWhenMessageTooLong() {
    String result =
        tool.sendMessages(
            List.of(String.valueOf(TEST_TARGET_CHANNEL_1)), "a".repeat(2001), null, parameters);

    assertThat(result).contains("\"success\": false");
    assertThat(result).contains("訊息長度不可超過 2000 字元");
  }

  private void mockSendSuccess(GuildMessageChannel channel, long messageId) {
    MessageCreateAction action = mock(MessageCreateAction.class);
    Message message = mock(Message.class);
    when(message.getIdLong()).thenReturn(messageId);
    when(channel.sendMessage("hello world")).thenReturn(action);
    when(channel.sendMessage("notify")).thenReturn(action);
    when(channel.sendMessage("hello")).thenReturn(action);
    when(action.submit()).thenReturn(CompletableFuture.completedFuture(message));
  }
}
