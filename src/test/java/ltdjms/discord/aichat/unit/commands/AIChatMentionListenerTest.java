package ltdjms.discord.aichat.unit.commands;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import ltdjms.discord.aichat.commands.AIChatMentionListener;
import ltdjms.discord.aichat.services.AIChannelRestrictionService;
import ltdjms.discord.aichat.services.AIChatService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;

/**
 * 測試 {@link AIChatMentionListener} 的頻道檢查功能。
 *
 * <p>這些測試專注於驗證頻道檢查邏輯，確保：
 *
 * <ul>
 *   <li>允許的頻道會觸發 AI 回應
 *   <li>不允許的頻道會安靜忽略
 *   <li>無限制模式（空清單）允許所有頻道
 * </ul>
 */
@DisplayName("AIChatMentionListener 頻道檢查測試")
class AIChatMentionListenerTest {

  private AIChatService aiChatService;
  private AIChannelRestrictionService channelRestrictionService;
  private AIChatMentionListener listener;
  private MessageReceivedEvent event;
  private JDA jda;
  private Guild guild;
  private SelfUser botUser;
  private User regularUser;
  private MessageChannelUnion messageChannel;
  private Message message;
  private MessageCreateAction messageCreateAction;

  @BeforeEach
  void setUp() {
    aiChatService = mock(AIChatService.class);
    channelRestrictionService = mock(AIChannelRestrictionService.class);
    listener = new AIChatMentionListener(aiChatService, channelRestrictionService);

    event = mock(MessageReceivedEvent.class);
    jda = mock(JDA.class);
    guild = mock(Guild.class);
    botUser = mock(SelfUser.class);
    regularUser = mock(User.class);
    messageChannel = mock(MessageChannelUnion.class);
    message = mock(Message.class);
    messageCreateAction = mock(MessageCreateAction.class);

    // 基本設定
    when(event.isFromGuild()).thenReturn(true);
    when(event.getChannel()).thenReturn(messageChannel);
    when(event.getAuthor()).thenReturn(regularUser);
    when(event.getGuild()).thenReturn(guild);
    when(event.getJDA()).thenReturn(jda);
    when(event.getMessage()).thenReturn(message);
    when(regularUser.isBot()).thenReturn(false);
    when(jda.getSelfUser()).thenReturn(botUser);
    when(botUser.getId()).thenReturn("999");

    // Mock sendMessage 返回一個可用的 MessageCreateAction
    when(messageChannel.sendMessage(any(CharSequence.class))).thenReturn(messageCreateAction);
    // 讓 queue() 方法不做任何事情（避免 NullPointerException）
    doNothing().when(messageCreateAction).queue();
  }

  @Nested
  @DisplayName("頻道檢查")
  class ChannelCheck {

    @Test
    @DisplayName("當頻道被允許時，應觸發 AI 回應")
    void shouldTriggerAIResponseWhenChannelAllowed() {
      // Arrange
      when(channelRestrictionService.isChannelAllowed(123L, 456L)).thenReturn(true);
      when(guild.getIdLong()).thenReturn(123L);
      when(messageChannel.getIdLong()).thenReturn(456L);
      when(regularUser.getId()).thenReturn("789");
      when(message.getContentRaw()).thenReturn("<@999> hello");

      // Act
      listener.onMessageReceived(event);

      // Assert - 應該觸發 AI 回應（驗證有呼叫 sendMessage）
      verify(messageChannel).sendMessage(any(CharSequence.class));
    }

    @Test
    @DisplayName("當頻道不被允許時，不應觸發 AI 回應")
    void shouldNotTriggerAIResponseWhenChannelNotAllowed() {
      // Arrange
      when(channelRestrictionService.isChannelAllowed(123L, 456L)).thenReturn(false);
      when(guild.getIdLong()).thenReturn(123L);
      when(messageChannel.getIdLong()).thenReturn(456L);
      when(message.getContentRaw()).thenReturn("<@999> hello");

      // Act
      listener.onMessageReceived(event);

      // Assert - 不應該觸發任何回應
      verify(messageChannel, never()).sendMessage(any(CharSequence.class));
    }

    @Test
    @DisplayName("當允許清單為空（無限制模式）時，應觸發 AI 回應")
    void shouldTriggerAIResponseWhenUnrestrictedMode() {
      // Arrange
      when(channelRestrictionService.isChannelAllowed(123L, 456L)).thenReturn(true);
      when(guild.getIdLong()).thenReturn(123L);
      when(messageChannel.getIdLong()).thenReturn(456L);
      when(regularUser.getId()).thenReturn("789");
      when(message.getContentRaw()).thenReturn("<@999> hello");

      // Act
      listener.onMessageReceived(event);

      // Assert - 無限制模式應該觸發 AI 回應
      verify(messageChannel).sendMessage(any(CharSequence.class));
    }

    @Test
    @DisplayName("當訊息不提及機器人時，不應觸發 AI 回應")
    void shouldNotTriggerWhenBotNotMentioned() {
      // Arrange
      when(channelRestrictionService.isChannelAllowed(123L, 456L)).thenReturn(true);
      when(guild.getIdLong()).thenReturn(123L);
      when(messageChannel.getIdLong()).thenReturn(456L);
      when(message.getContentRaw()).thenReturn("hello world");

      // Act
      listener.onMessageReceived(event);

      // Assert - 不應該觸發任何回應
      verify(messageChannel, never()).sendMessage(any(CharSequence.class));
    }
  }
}
