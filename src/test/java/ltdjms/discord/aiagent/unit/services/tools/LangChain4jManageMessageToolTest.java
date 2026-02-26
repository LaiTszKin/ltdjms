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
        tool.manageMessage(String.valueOf(TEST_MESSAGE_ID), "pin", null, null, null, parameters);

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
        tool.manageMessage(String.valueOf(TEST_MESSAGE_ID), "delete", null, null, null, parameters);

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
            String.valueOf(TEST_MESSAGE_ID),
            "edit",
            null,
            "updated content",
            "replace",
            parameters);

    assertThat(result).contains("\"success\": true");
    assertThat(result).contains("\"action\": \"edit\"");
    assertThat(result).contains("\"editMode\": \"replace\"");
    assertThat(result).contains("updated content");
  }

  @Test
  @DisplayName("未提供 editMode 且內容較短時，應預設 append")
  void shouldDefaultToAppendWhenEditContentLooksIncremental() {
    @SuppressWarnings("unchecked")
    RestAction<Message> retrieveAction = mock(RestAction.class);
    Message originalMessage = mock(Message.class);
    MessageEditAction editAction = mock(MessageEditAction.class);
    Message editedMessage = mock(Message.class);

    when(mockChannel.retrieveMessageById(TEST_MESSAGE_ID)).thenReturn(retrieveAction);
    when(retrieveAction.complete()).thenReturn(originalMessage);
    when(originalMessage.getContentRaw()).thenReturn("原始訊息內容");
    when(mockChannel.editMessageById(TEST_MESSAGE_ID, "原始訊息內容\n測試")).thenReturn(editAction);
    when(editAction.complete()).thenReturn(editedMessage);

    String result =
        tool.manageMessage(String.valueOf(TEST_MESSAGE_ID), "edit", null, "測試", null, parameters);

    assertThat(result).contains("\"success\": true");
    assertThat(result).contains("\"action\": \"edit\"");
    assertThat(result).contains("\"editMode\": \"append\"");
    assertThat(result).contains("原始訊息內容 測試");
  }

  @Test
  @DisplayName("editMode=prepend 時應保留原文並加在前方")
  void shouldPrependContentWhenEditModeIsPrepend() {
    @SuppressWarnings("unchecked")
    RestAction<Message> retrieveAction = mock(RestAction.class);
    Message originalMessage = mock(Message.class);
    MessageEditAction editAction = mock(MessageEditAction.class);
    Message editedMessage = mock(Message.class);

    when(mockChannel.retrieveMessageById(TEST_MESSAGE_ID)).thenReturn(retrieveAction);
    when(retrieveAction.complete()).thenReturn(originalMessage);
    when(originalMessage.getContentRaw()).thenReturn("原始內容");
    when(mockChannel.editMessageById(TEST_MESSAGE_ID, "前置說明\n原始內容")).thenReturn(editAction);
    when(editAction.complete()).thenReturn(editedMessage);

    String result =
        tool.manageMessage(
            String.valueOf(TEST_MESSAGE_ID), "edit", null, "前置說明", "prepend", parameters);

    assertThat(result).contains("\"success\": true");
    assertThat(result).contains("\"editMode\": \"prepend\"");
    assertThat(result).contains("前置說明 原始內容");
  }

  @Test
  @DisplayName("editMode=diff 時應套用 unified diff")
  void shouldApplyUnifiedDiffWhenEditModeIsDiff() {
    @SuppressWarnings("unchecked")
    RestAction<Message> retrieveAction = mock(RestAction.class);
    Message originalMessage = mock(Message.class);
    MessageEditAction editAction = mock(MessageEditAction.class);
    Message editedMessage = mock(Message.class);

    when(mockChannel.retrieveMessageById(TEST_MESSAGE_ID)).thenReturn(retrieveAction);
    when(retrieveAction.complete()).thenReturn(originalMessage);
    when(originalMessage.getContentRaw()).thenReturn("第一行\n第二行\n第三行");
    when(mockChannel.editMessageById(TEST_MESSAGE_ID, "第一行\n第二行（已更新）\n第三行")).thenReturn(editAction);
    when(editAction.complete()).thenReturn(editedMessage);

    String diffPatch = "@@ -1,3 +1,3 @@\n" + " 第一行\n" + "-第二行\n" + "+第二行（已更新）\n" + " 第三行";

    String result =
        tool.manageMessage(
            String.valueOf(TEST_MESSAGE_ID), "edit", null, diffPatch, "diff", parameters);

    assertThat(result).contains("\"success\": true");
    assertThat(result).contains("\"editMode\": \"diff\"");
    assertThat(result).contains("第一行 第二行（已更新） 第三行");
  }

  @Test
  @DisplayName("未提供 editMode 且內容為 unified diff 時應自動套用 diff")
  void shouldAutoDetectDiffModeWhenEditModeMissing() {
    @SuppressWarnings("unchecked")
    RestAction<Message> retrieveAction = mock(RestAction.class);
    Message originalMessage = mock(Message.class);
    MessageEditAction editAction = mock(MessageEditAction.class);
    Message editedMessage = mock(Message.class);

    when(mockChannel.retrieveMessageById(TEST_MESSAGE_ID)).thenReturn(retrieveAction);
    when(retrieveAction.complete()).thenReturn(originalMessage);
    when(originalMessage.getContentRaw()).thenReturn("A\nB\nC");
    when(mockChannel.editMessageById(TEST_MESSAGE_ID, "A\nB-2\nC")).thenReturn(editAction);
    when(editAction.complete()).thenReturn(editedMessage);

    String diffPatch = "@@ -1,3 +1,3 @@\n A\n-B\n+B-2\n C";

    String result =
        tool.manageMessage(
            String.valueOf(TEST_MESSAGE_ID), "edit", null, diffPatch, null, parameters);

    assertThat(result).contains("\"success\": true");
    assertThat(result).contains("\"editMode\": \"diff\"");
    assertThat(result).contains("A B-2 C");
  }

  @Test
  @DisplayName("diff 內容包在 markdown code fence 時應可正常套用")
  void shouldApplyDiffWhenWrappedInCodeFence() {
    @SuppressWarnings("unchecked")
    RestAction<Message> retrieveAction = mock(RestAction.class);
    Message originalMessage = mock(Message.class);
    MessageEditAction editAction = mock(MessageEditAction.class);
    Message editedMessage = mock(Message.class);

    when(mockChannel.retrieveMessageById(TEST_MESSAGE_ID)).thenReturn(retrieveAction);
    when(retrieveAction.complete()).thenReturn(originalMessage);
    when(originalMessage.getContentRaw()).thenReturn("alpha\nbeta\ngamma");
    when(mockChannel.editMessageById(TEST_MESSAGE_ID, "alpha\nbeta-2\ngamma"))
        .thenReturn(editAction);
    when(editAction.complete()).thenReturn(editedMessage);

    String diffPatch = "```diff\n@@ -1,3 +1,3 @@\n alpha\n-beta\n+beta-2\n gamma\n```";

    String result =
        tool.manageMessage(
            String.valueOf(TEST_MESSAGE_ID), "edit", null, diffPatch, "diff", parameters);

    assertThat(result).contains("\"success\": true");
    assertThat(result).contains("\"editMode\": \"diff\"");
    assertThat(result).contains("alpha beta-2 gamma");
  }

  @Test
  @DisplayName("diff 內容與原文不符時應回傳錯誤")
  void shouldReturnErrorWhenDiffContextDoesNotMatchOriginal() {
    @SuppressWarnings("unchecked")
    RestAction<Message> retrieveAction = mock(RestAction.class);
    Message originalMessage = mock(Message.class);

    when(mockChannel.retrieveMessageById(TEST_MESSAGE_ID)).thenReturn(retrieveAction);
    when(retrieveAction.complete()).thenReturn(originalMessage);
    when(originalMessage.getContentRaw()).thenReturn("A\nB\nC");

    String result =
        tool.manageMessage(
            String.valueOf(TEST_MESSAGE_ID),
            "edit",
            null,
            "@@ -1,3 +1,3 @@\n A\n-X\n+Y\n C",
            "diff",
            parameters);

    assertThat(result).contains("\"success\": false");
    assertThat(result).contains("diff 套用失敗：第 2 行內容不符");
  }

  @Test
  @DisplayName("editMode 非法時應回傳錯誤")
  void shouldReturnErrorWhenEditModeInvalid() {
    String result =
        tool.manageMessage(
            String.valueOf(TEST_MESSAGE_ID), "edit", null, "updated content", "merge", parameters);

    assertThat(result).contains("\"success\": false");
    assertThat(result).contains("editMode 必須是 replace、append、prepend 或 diff");
  }

  @Test
  @DisplayName("diff 格式缺少 hunk 時應回傳錯誤")
  void shouldReturnErrorWhenDiffFormatIsInvalid() {
    @SuppressWarnings("unchecked")
    RestAction<Message> retrieveAction = mock(RestAction.class);
    Message originalMessage = mock(Message.class);

    when(mockChannel.retrieveMessageById(TEST_MESSAGE_ID)).thenReturn(retrieveAction);
    when(retrieveAction.complete()).thenReturn(originalMessage);
    when(originalMessage.getContentRaw()).thenReturn("原始內容");

    String result =
        tool.manageMessage(
            String.valueOf(TEST_MESSAGE_ID),
            "edit",
            null,
            "--- a/message\n+++ b/message\n-原始內容\n+更新內容",
            "diff",
            parameters);

    assertThat(result).contains("\"success\": false");
    assertThat(result).contains("diff 格式無效：找不到 hunk 標頭");
  }

  @Test
  @DisplayName("append 後超過長度上限時應回傳錯誤")
  void shouldReturnErrorWhenAppendedContentExceedsLimit() {
    @SuppressWarnings("unchecked")
    RestAction<Message> retrieveAction = mock(RestAction.class);
    Message originalMessage = mock(Message.class);

    when(mockChannel.retrieveMessageById(TEST_MESSAGE_ID)).thenReturn(retrieveAction);
    when(retrieveAction.complete()).thenReturn(originalMessage);
    when(originalMessage.getContentRaw()).thenReturn("a".repeat(1995));

    String result =
        tool.manageMessage(
            String.valueOf(TEST_MESSAGE_ID), "edit", null, "1234567890", "append", parameters);

    assertThat(result).contains("\"success\": false");
    assertThat(result).contains("編輯後內容長度不可超過 2000 字元");
  }

  @Test
  @DisplayName("action=edit 且 newContent 為空時應回傳錯誤")
  void shouldReturnErrorWhenEditContentMissing() {
    String result =
        tool.manageMessage(String.valueOf(TEST_MESSAGE_ID), "edit", null, "   ", null, parameters);

    assertThat(result).contains("\"success\": false");
    assertThat(result).contains("newContent 不能為空");
  }

  @Test
  @DisplayName("不支援的 action 應回傳錯誤")
  void shouldReturnErrorForUnsupportedAction() {
    String result =
        tool.manageMessage(
            String.valueOf(TEST_MESSAGE_ID), "archive", null, null, null, parameters);

    assertThat(result).contains("\"success\": false");
    assertThat(result).contains("action 必須是 pin、delete 或 edit");
  }

  @Test
  @DisplayName("無效 messageId 應回傳錯誤")
  void shouldReturnErrorForInvalidMessageId() {
    String result = tool.manageMessage("invalid-id", "pin", null, null, null, parameters);

    assertThat(result).contains("\"success\": false");
    assertThat(result).contains("無效的 messageId 格式");
  }
}
