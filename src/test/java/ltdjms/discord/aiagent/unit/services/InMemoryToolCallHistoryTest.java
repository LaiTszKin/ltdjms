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
}
