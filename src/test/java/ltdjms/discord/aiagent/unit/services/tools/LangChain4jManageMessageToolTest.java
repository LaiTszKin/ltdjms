package ltdjms.discord.aiagent.unit.services.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.langchain4j.invocation.InvocationParameters;
import ltdjms.discord.aiagent.services.tools.LangChain4jManageMessageTool;
import ltdjms.discord.shared.di.JDAProvider;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.requests.restaction.MessageEditAction;

@DisplayName("LangChain4jManageMessageTool")
class LangChain4jManageMessageToolTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_CHANNEL_ID = 223456789012345678L;
  private static final long TEST_USER_ID = 323456789012345678L;
  private static final long TEST_MESSAGE_ID = 423456789012345678L;

  private LangChain4jManageMessageTool tool;
  private JDA mockJda;
  private Guild mockGuild;
  private GuildMessageChannel mockChannel;
  private InvocationParameters parameters;

  @BeforeEach
  void setUp() {
    tool = new LangChain4jManageMessageTool();
    mockJda = mock(JDA.class);
    mockGuild = mock(Guild.class);
    mockChannel = mock(GuildMessageChannel.class);
    parameters = new InvocationParameters();

    parameters.put("guildId", TEST_GUILD_ID);
    parameters.put("channelId", TEST_CHANNEL_ID);
    parameters.put("userId", TEST_USER_ID);

    JDAProvider.setJda(mockJda);
    when(mockJda.getGuildById(TEST_GUILD_ID)).thenReturn(mockGuild);
    when(mockGuild.getGuildChannelById(TEST_CHANNEL_ID)).thenReturn(mockChannel);

    Member caller = mock(Member.class);
    when(mockGuild.getMemberById(TEST_USER_ID)).thenReturn(caller);
    when(caller.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);
  }

  @AfterEach
  void tearDown() {
    JDAProvider.clear();
  }

  @Test
  @DisplayName("pin 操作應成功")
  void shouldPinMessageSuccessfully() {
    @SuppressWarnings("unchecked")
    RestAction<Void> action = mock(RestAction.class);
    when(mockChannel.pinMessageById(TEST_MESSAGE_ID)).thenReturn(action);
    when(action.complete()).thenReturn(null);

    String result =
        tool.manageMessage(String.valueOf(TEST_MESSAGE_ID), "pin", null, null, parameters);

    assertThat(result).contains("\"success\": true");
    assertThat(result).contains("\"action\": \"pin\"");
  }

  @Test
  @DisplayName("delete 操作應成功")
  void shouldDeleteMessageSuccessfully() {
    @SuppressWarnings("unchecked")
    AuditableRestAction<Void> action = mock(AuditableRestAction.class);
    when(mockChannel.deleteMessageById(TEST_MESSAGE_ID)).thenReturn(action);
    when(action.complete()).thenReturn(null);

    String result =
        tool.manageMessage(String.valueOf(TEST_MESSAGE_ID), "delete", null, null, parameters);

    assertThat(result).contains("\"success\": true");
    assertThat(result).contains("\"action\": \"delete\"");
  }

  @Test
  @DisplayName("edit 操作應成功")
  void shouldEditMessageSuccessfully() {
    MessageEditAction action = mock(MessageEditAction.class);
    Message editedMessage = mock(Message.class);
    when(mockChannel.editMessageById(TEST_MESSAGE_ID, "updated content")).thenReturn(action);
    when(action.complete()).thenReturn(editedMessage);

    String result =
        tool.manageMessage(
            String.valueOf(TEST_MESSAGE_ID), "edit", null, "updated content", parameters);

    assertThat(result).contains("\"success\": true");
    assertThat(result).contains("\"action\": \"edit\"");
    assertThat(result).contains("updated content");
  }

  @Test
  @DisplayName("action=edit 且 newContent 為空時應回傳錯誤")
  void shouldReturnErrorWhenEditContentMissing() {
    String result =
        tool.manageMessage(String.valueOf(TEST_MESSAGE_ID), "edit", null, "   ", parameters);

    assertThat(result).contains("\"success\": false");
    assertThat(result).contains("newContent 不能為空");
  }

  @Test
  @DisplayName("不支援的 action 應回傳錯誤")
  void shouldReturnErrorForUnsupportedAction() {
    String result =
        tool.manageMessage(String.valueOf(TEST_MESSAGE_ID), "archive", null, null, parameters);

    assertThat(result).contains("\"success\": false");
    assertThat(result).contains("action 必須是 pin、delete 或 edit");
  }

  @Test
  @DisplayName("無效 messageId 應回傳錯誤")
  void shouldReturnErrorForInvalidMessageId() {
    String result = tool.manageMessage("invalid-id", "pin", null, null, parameters);

    assertThat(result).contains("\"success\": false");
    assertThat(result).contains("無效的 messageId 格式");
  }
}
