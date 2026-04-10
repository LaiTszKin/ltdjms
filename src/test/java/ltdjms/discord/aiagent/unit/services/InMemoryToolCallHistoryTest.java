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
  @DisplayName("應只將記憶安全摘要保存為助手訊息")
  void shouldStoreOnlyMemorySafeSummaryAsAssistantMessages() {
    InMemoryToolCallHistory history = new InMemoryToolCallHistory();
    long threadId = 42L;

    history.addToolCall(
        threadId,
        new InMemoryToolCallHistory.ToolCallEntry(
            Instant.parse("2026-01-01T00:00:00Z"),
            "listChannels",
            Map.of("limit", 3),
            true,
            "工具「listChannels」已成功執行；完整結果不會保留於跨回合記憶。",
            InMemoryToolCallHistory.RedactionMode.NONE));

    List<ChatMessage> messages = history.getToolCallMessages(threadId);

    assertThat(messages).hasSize(1);
    assertThat(messages.get(0)).isInstanceOf(AiMessage.class);
    AiMessage msg = (AiMessage) messages.get(0);
    assertThat(msg.text()).isEqualTo("工具「listChannels」已成功執行；完整結果不會保留於跨回合記憶。");
  }

  @Test
  @DisplayName("高風險工具摘要不應包含原始 snippet 或 jump URL")
  void shouldRedactSensitiveToolSummaries() {
    InMemoryToolCallHistory history = new InMemoryToolCallHistory();
    long threadId = 42L;

    history.addToolCall(
        threadId,
        1001L,
        new InMemoryToolCallHistory.ToolCallEntry(
            Instant.parse("2026-01-01T00:00:00Z"),
            "searchMessages",
            Map.of("keywords", "secret"),
            true,
            "工具「searchMessages」已執行，結果因敏感內容已從跨回合記憶隔離。",
            InMemoryToolCallHistory.RedactionMode.REDACTED));

    List<ChatMessage> messages = history.getToolCallMessages(threadId, 1001L);
    List<InMemoryToolCallHistory.ToolCallEntry> auditEntries =
        history.getAuditEntries(threadId, 1001L);

    assertThat(messages).hasSize(1);
    assertThat(((AiMessage) messages.get(0)).text())
        .contains("已從跨回合記憶隔離")
        .doesNotContain("jumpUrl")
        .doesNotContain("discord.com/channels/")
        .doesNotContain("敏感片段");
    assertThat(auditEntries).hasSize(1);
    assertThat(auditEntries.get(0).redactionMode())
        .isEqualTo(InMemoryToolCallHistory.RedactionMode.REDACTED);
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
            Instant.parse("2026-01-01T00:00:00Z"),
            "toolA",
            Map.of(),
            true,
            "工具「toolA」已成功執行；完整結果不會保留於跨回合記憶。",
            InMemoryToolCallHistory.RedactionMode.NONE));
    history.addToolCall(
        threadId,
        userB,
        new InMemoryToolCallHistory.ToolCallEntry(
            Instant.parse("2026-01-01T00:00:01Z"),
            "toolB",
            Map.of(),
            true,
            "工具「toolB」已成功執行；完整結果不會保留於跨回合記憶。",
            InMemoryToolCallHistory.RedactionMode.NONE));

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
            Instant.parse("2026-01-01T00:00:00Z"),
            "toolA",
            Map.of(),
            true,
            "工具「toolA」已成功執行；完整結果不會保留於跨回合記憶。",
            InMemoryToolCallHistory.RedactionMode.NONE));
    history.addToolCall(
        threadId,
        userB,
        new InMemoryToolCallHistory.ToolCallEntry(
            Instant.parse("2026-01-01T00:00:01Z"),
            "toolB",
            Map.of(),
            true,
            "工具「toolB」已成功執行；完整結果不會保留於跨回合記憶。",
            InMemoryToolCallHistory.RedactionMode.NONE));

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
            Instant.parse("2026-01-01T00:00:00Z"),
            "legacyTool",
            Map.of(),
            true,
            "工具「legacyTool」已成功執行；完整結果不會保留於跨回合記憶。",
            InMemoryToolCallHistory.RedactionMode.NONE));

    List<ChatMessage> legacyMessages = history.getToolCallMessages(threadId);
    List<ChatMessage> scopedMessages = history.getToolCallMessages(threadId, 0L);

    assertThat(legacyMessages).hasSize(1);
    assertThat(scopedMessages).hasSize(1);
    assertThat(((AiMessage) legacyMessages.get(0)).text())
        .isEqualTo(((AiMessage) scopedMessages.get(0)).text());
  }
}
