package ltdjms.discord.aichat.unit.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import ltdjms.discord.aiagent.services.AIAgentChannelConfigService;
import ltdjms.discord.aichat.commands.AIChatMentionListener;
import ltdjms.discord.aichat.services.AIChannelRestrictionService;
import ltdjms.discord.aichat.services.AIChatService;
import ltdjms.discord.aichat.services.StreamingResponseHandler;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;

@DisplayName("AIChatMentionListener Agent 結論訊息")
class AIChatMentionListenerAgentConclusionTest {

  @Test
  @DisplayName("應刪除 thinking 訊息並只送出最後整段結論")
  void shouldDeleteThinkingAndSendOnlyFinalConclusionInAgentMode() {
    AIChatService aiChatService = mock(AIChatService.class);
    AIChannelRestrictionService channelRestrictionService = mock(AIChannelRestrictionService.class);
    AIAgentChannelConfigService agentConfigService = mock(AIAgentChannelConfigService.class);
    AIChatMentionListener listener =
        new AIChatMentionListener(
            aiChatService, channelRestrictionService, agentConfigService, false, true, false);

    MessageReceivedEvent event = mock(MessageReceivedEvent.class);
    JDA jda = mock(JDA.class);
    Guild guild = mock(Guild.class);
    SelfUser botUser = mock(SelfUser.class);
    User regularUser = mock(User.class);
    MessageChannelUnion messageChannel = mock(MessageChannelUnion.class);
    Message userMessage = mock(Message.class);

    when(event.isFromGuild()).thenReturn(true);
    when(event.getJDA()).thenReturn(jda);
    when(event.getGuild()).thenReturn(guild);
    when(event.getAuthor()).thenReturn(regularUser);
    when(event.getChannel()).thenReturn(messageChannel);
    when(event.getMessage()).thenReturn(userMessage);

    when(guild.getIdLong()).thenReturn(123L);
    when(regularUser.isBot()).thenReturn(false);
    when(regularUser.getId()).thenReturn("789");
    when(regularUser.getIdLong()).thenReturn(789L);
    when(messageChannel.getId()).thenReturn("456");
    when(messageChannel.getIdLong()).thenReturn(456L);
    when(userMessage.getIdLong()).thenReturn(111L);
    when(userMessage.getContentRaw()).thenReturn("<@999> 請處理這件事");

    when(jda.getSelfUser()).thenReturn(botUser);
    when(botUser.getId()).thenReturn("999");

    when(channelRestrictionService.isChannelAllowed(123L, 456L, 0L)).thenReturn(true);
    when(agentConfigService.isAgentEnabled(123L, 456L)).thenReturn(true);

    Message thinkingMessage = mock(Message.class);
    AuditableRestAction<Void> deleteAction = mock(AuditableRestAction.class);
    when(thinkingMessage.delete()).thenReturn(deleteAction);
    doAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              Consumer<Void> onSuccess = invocation.getArgument(0);
              if (onSuccess != null) {
                onSuccess.accept(null);
              }
              return null;
            })
        .when(deleteAction)
        .queue(any(), any());

    MessageCreateAction thinkingCreateAction = mock(MessageCreateAction.class);
    MessageCreateAction responseCreateAction = mock(MessageCreateAction.class);

    doAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              Consumer<Message> onSuccess = invocation.getArgument(0);
              if (onSuccess != null) {
                onSuccess.accept(thinkingMessage);
              }
              return null;
            })
        .when(thinkingCreateAction)
        .queue(any());
    doNothing().when(responseCreateAction).queue();

    doAnswer(
            invocation -> {
              CharSequence content = invocation.getArgument(0);
              if (":thought_balloon: AI 正在思考...".contentEquals(content)) {
                return thinkingCreateAction;
              }
              return responseCreateAction;
            })
        .when(messageChannel)
        .sendMessage(any(CharSequence.class));

    doAnswer(
            invocation -> {
              StreamingResponseHandler handler = invocation.getArgument(5);
              handler.onChunk("第一段工作中內容", false, null, StreamingResponseHandler.ChunkType.CONTENT);
              handler.onChunk("最後總結", true, null, StreamingResponseHandler.ChunkType.CONTENT);
              return null;
            })
        .when(aiChatService)
        .generateStreamingResponse(anyLong(), any(), any(), any(), anyLong(), any());

    listener.onMessageReceived(event);

    ArgumentCaptor<CharSequence> sentMessages = ArgumentCaptor.forClass(CharSequence.class);
    verify(messageChannel, times(2)).sendMessage(sentMessages.capture());
    assertThat(sentMessages.getAllValues())
        .containsExactly(":thought_balloon: AI 正在思考...", "第一段工作中內容最後總結");
    verify(thinkingMessage).delete();
    verify(thinkingMessage, never()).editMessage(any(CharSequence.class));
  }
}
