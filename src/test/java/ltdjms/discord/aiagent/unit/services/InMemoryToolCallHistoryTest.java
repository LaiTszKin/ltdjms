package ltdjms.discord.aiagent.unit.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import ltdjms.discord.aiagent.services.InMemoryToolCallHistory;

class InMemoryToolCallHistoryTest {

  @Test
  @DisplayName("應將工具執行結果保存為助手訊息而非 tool 訊息")
  void shouldStoreToolResultsAsAssistantMessages() {
    InMemoryToolCallHistory history = new InMemoryToolCallHistory();
    long threadId = 42L;

    history.addToolCall(
        threadId,
        new InMemoryToolCallHistory.ToolCallEntry(
            Instant.parse("2026-01-01T00:00:00Z"),
            "listChannels",
            Map.of("limit", 3),
            true,
            "列出 3 個頻道"));

    List<ChatMessage> messages = history.getToolCallMessages(threadId);

    assertThat(messages).hasSize(1);
    assertThat(messages.get(0)).isInstanceOf(AiMessage.class);
    AiMessage msg = (AiMessage) messages.get(0);
    assertThat(msg.text()).isEqualTo("工具「listChannels」執行結果：✅ 列出 3 個頻道");
  }

  @Test
  @DisplayName("同一 Thread 不同使用者的工具歷史應隔離")
  void shouldIsolateHistoryByUserWithinSameThread() {
    InMemoryToolCallHistory history = new InMemoryToolCallHistory();
    long threadId = 42L;
    long userA = 1001L;
    long userB = 1002L;

    history.addToolCall(
        threadId,
        userA,
        new InMemoryToolCallHistory.ToolCallEntry(
            Instant.parse("2026-01-01T00:00:00Z"), "toolA", Map.of(), true, "A"));
    history.addToolCall(
        threadId,
        userB,
        new InMemoryToolCallHistory.ToolCallEntry(
            Instant.parse("2026-01-01T00:00:01Z"), "toolB", Map.of(), true, "B"));

    List<ChatMessage> userAMessages = history.getToolCallMessages(threadId, userA);
    List<ChatMessage> userBMessages = history.getToolCallMessages(threadId, userB);

    assertThat(userAMessages).hasSize(1);
    assertThat(((AiMessage) userAMessages.get(0)).text()).contains("toolA");
    assertThat(userBMessages).hasSize(1);
    assertThat(((AiMessage) userBMessages.get(0)).text()).contains("toolB");
  }

  @Test
  @DisplayName("清理特定使用者歷史時不應影響其他使用者")
  void shouldOnlyClearSpecifiedUserHistory() {
    InMemoryToolCallHistory history = new InMemoryToolCallHistory();
    long threadId = 42L;
    long userA = 1001L;
    long userB = 1002L;

    history.addToolCall(
        threadId,
        userA,
        new InMemoryToolCallHistory.ToolCallEntry(
            Instant.parse("2026-01-01T00:00:00Z"), "toolA", Map.of(), true, "A"));
    history.addToolCall(
        threadId,
        userB,
        new InMemoryToolCallHistory.ToolCallEntry(
            Instant.parse("2026-01-01T00:00:01Z"), "toolB", Map.of(), true, "B"));

    history.clearHistory(threadId, userA);

    assertThat(history.getToolCallMessages(threadId, userA)).isEmpty();
    assertThat(history.getToolCallMessages(threadId, userB)).hasSize(1);
  }

  @Test
  @DisplayName("舊版 API 應維持與 userId=0 相同語意")
  void shouldKeepBackwardCompatibilityForLegacyApi() {
    InMemoryToolCallHistory history = new InMemoryToolCallHistory();
    long threadId = 42L;

    history.addToolCall(
        threadId,
        new InMemoryToolCallHistory.ToolCallEntry(
            Instant.parse("2026-01-01T00:00:00Z"), "legacyTool", Map.of(), true, "ok"));

    List<ChatMessage> legacyMessages = history.getToolCallMessages(threadId);
    List<ChatMessage> scopedMessages = history.getToolCallMessages(threadId, 0L);

    assertThat(legacyMessages).hasSize(1);
    assertThat(scopedMessages).hasSize(1);
    assertThat(((AiMessage) legacyMessages.get(0)).text())
        .isEqualTo(((AiMessage) scopedMessages.get(0)).text());
  }
}
