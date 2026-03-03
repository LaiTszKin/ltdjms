package ltdjms.discord.aiagent.unit.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.UserMessage;
import ltdjms.discord.aiagent.services.DiscordThreadHistoryProvider;
import ltdjms.discord.aiagent.services.InMemoryToolCallHistory;
import ltdjms.discord.aiagent.services.SimplifiedChatMemoryProvider;
import ltdjms.discord.shared.di.JDAProvider;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.SelfUser;

@DisplayName("SimplifiedChatMemoryProvider")
class SimplifiedChatMemoryProviderTest {

  private DiscordThreadHistoryProvider threadHistoryProvider;
  private InMemoryToolCallHistory toolCallHistory;
  private SimplifiedChatMemoryProvider provider;

  @BeforeEach
  void setUp() {
    threadHistoryProvider = mock(DiscordThreadHistoryProvider.class);
    toolCallHistory = mock(InMemoryToolCallHistory.class);
    provider = new SimplifiedChatMemoryProvider(threadHistoryProvider, toolCallHistory);
  }

  @AfterEach
  void tearDown() {
    JDAProvider.clear();
  }

  @Test
  @DisplayName("當 memoryId 不是字串時應回退為非 Thread 記憶體")
  void shouldFallbackToNonThreadMemoryWhenMemoryIdIsNotString() {
    var memory = provider.get(12345L);

    assertThat(memory).isNotNull();
    verify(threadHistoryProvider, never())
        .getThreadHistory(anyLong(), anyLong(), anyLong(), anyLong());
    verify(toolCallHistory, never()).getToolCallMessages(anyLong(), anyLong());
  }

  @Test
  @DisplayName("當 Thread 級別 conversationId 非數字時應回退為非 Thread 記憶體")
  void shouldFallbackToNonThreadMemoryWhenThreadConversationIdIsMalformed() {
    var memory = provider.get("guild:thread:user");

    assertThat(memory).isNotNull();
    verify(threadHistoryProvider, never())
        .getThreadHistory(anyLong(), anyLong(), anyLong(), anyLong());
    verify(toolCallHistory, never()).getToolCallMessages(anyLong(), anyLong());
  }

  @Test
  @DisplayName("合法 Thread 級別 conversationId 應正常載入歷史")
  void shouldLoadThreadHistoryForValidThreadConversationId() {
    JDA jda = mock(JDA.class);
    SelfUser selfUser = mock(SelfUser.class);
    when(selfUser.getIdLong()).thenReturn(900L);
    when(jda.getSelfUser()).thenReturn(selfUser);
    JDAProvider.setJda(jda);

    when(threadHistoryProvider.getThreadHistory(100L, 200L, 300L, 900L))
        .thenReturn(List.of(UserMessage.from("hello")));
    when(toolCallHistory.getToolCallMessages(200L, 300L)).thenReturn(List.of());

    var memory = provider.get("100:200:300");

    assertThat(memory).isNotNull();
    verify(threadHistoryProvider).getThreadHistory(100L, 200L, 300L, 900L);
    verify(toolCallHistory).getToolCallMessages(200L, 300L);
  }
}
