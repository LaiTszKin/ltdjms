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

import dev.langchain4j.data.message.AiMessage;
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

  @Test
  @DisplayName("rehydration 時只應加入安全摘要而非 raw tool result")
  void shouldOnlyInjectMemorySafeToolSummaries() {
    JDA jda = mock(JDA.class);
    SelfUser selfUser = mock(SelfUser.class);
    when(selfUser.getIdLong()).thenReturn(900L);
    when(jda.getSelfUser()).thenReturn(selfUser);
    JDAProvider.setJda(jda);

    InMemoryToolCallHistory realHistory = new InMemoryToolCallHistory();
    provider = new SimplifiedChatMemoryProvider(threadHistoryProvider, realHistory);

    when(threadHistoryProvider.getThreadHistory(100L, 200L, 300L, 900L))
        .thenReturn(List.of(UserMessage.from("請幫我找關鍵字訊息")));

    realHistory.addToolCall(
        200L,
        300L,
        new InMemoryToolCallHistory.ToolCallEntry(
            java.time.Instant.parse("2026-01-01T00:00:00Z"),
            "searchMessages",
            java.util.Map.of("keywords", "secret"),
            true,
            "工具「searchMessages」已執行，結果因敏感內容已從跨回合記憶隔離。",
            InMemoryToolCallHistory.RedactionMode.REDACTED));

    var memory = provider.get("100:200:300");
    var messages = memory.messages();

    assertThat(messages).hasSize(2);
    assertThat(((UserMessage) messages.get(0)).singleText()).isEqualTo("請幫我找關鍵字訊息");
    assertThat(messages.get(1)).isInstanceOf(AiMessage.class);
    assertThat(((AiMessage) messages.get(1)).text())
        .contains("已從跨回合記憶隔離")
        .doesNotContain("jumpUrl")
        .doesNotContain("discord.com/channels/")
        .doesNotContain("作者");
  }

  @Test
  @DisplayName("當 JDA 尚未初始化時 Thread 會話應回退為非 Thread 記憶體")
  void shouldFallbackToNonThreadMemoryWhenJdaIsNotInitialized() {
    var memory = provider.get("100:200:300");

    assertThat(memory).isNotNull();
    verify(threadHistoryProvider, never())
        .getThreadHistory(anyLong(), anyLong(), anyLong(), anyLong());
    verify(toolCallHistory, never()).getToolCallMessages(anyLong(), anyLong());
  }
}
