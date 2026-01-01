package ltdjms.discord.aiagent.unit.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import ltdjms.discord.aiagent.services.DiscordThreadHistoryProvider;
import ltdjms.discord.aiagent.services.TokenEstimator;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.entities.User;

@DisplayName("DiscordThreadHistoryProvider 單元測試")
class DiscordThreadHistoryProviderTest {

  @Test
  @DisplayName("Thread 歷史應保留機器人自身的訊息")
  void shouldIncludeBotMessagesInHistory() throws Exception {
    // Given
    DiscordThreadHistoryProvider provider =
        new DiscordThreadHistoryProvider(100, new TokenEstimator(4000));
    long botUserId = 999L;

    Message userMsg = mock(Message.class);
    User user = mock(User.class);
    when(user.getIdLong()).thenReturn(111L);
    when(user.isBot()).thenReturn(false);
    when(userMsg.getAuthor()).thenReturn(user);
    when(userMsg.getType()).thenReturn(MessageType.DEFAULT);
    when(userMsg.getContentDisplay()).thenReturn("使用者訊息");

    Message botMsg = mock(Message.class);
    User botUser = mock(User.class);
    when(botUser.getIdLong()).thenReturn(botUserId);
    when(botUser.isBot()).thenReturn(true);
    when(botMsg.getAuthor()).thenReturn(botUser);
    when(botMsg.getType()).thenReturn(MessageType.DEFAULT);
    when(botMsg.getContentDisplay()).thenReturn("機器人回覆");

    List<Message> discordMessages = List.of(userMsg, botMsg);

    Method convertMethod =
        DiscordThreadHistoryProvider.class.getDeclaredMethod(
            "convertToChatMessages", List.class, long.class);
    convertMethod.setAccessible(true);

    @SuppressWarnings("unchecked")
    List<ChatMessage> chatMessages =
        (List<ChatMessage>) convertMethod.invoke(provider, discordMessages, botUserId);

    // Then
    assertThat(chatMessages).hasSize(2);
    assertThat(chatMessages.get(0)).isInstanceOf(UserMessage.class);
    assertThat(((UserMessage) chatMessages.get(0)).singleText()).isEqualTo("使用者訊息");
    assertThat(chatMessages.get(1)).isInstanceOf(AiMessage.class);
    assertThat(((AiMessage) chatMessages.get(1)).text()).isEqualTo("機器人回覆");
  }
}
