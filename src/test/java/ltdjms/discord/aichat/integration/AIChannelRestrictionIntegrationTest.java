package ltdjms.discord.aichat.integration;

import static org.assertj.core.api.Assertions.*;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Set;
import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import ltdjms.discord.aichat.domain.AllowedChannel;
import ltdjms.discord.currency.integration.PostgresIntegrationTestBase;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.Unit;

/**
 * 整合測試，使用真實 PostgreSQL 資料庫測試 AI 頻道限制功能的完整流程。 測試範圍包含：
 *
 * <ul>
 *   <li>T038: add-remove-flow - 新增與移除頻道的完整流程
 *   <li>T039: channel-check-flow - 頻道檢查流程
 *   <li>T040: deleted-channel-cleanup - 已刪除頻道的清理流程
 *   <li>T041: unrestricted-mode - 無限制模式（空清單）測試
 * </ul>
 */
@DisplayName("AI 頻道限制整合測試")
class AIChannelRestrictionIntegrationTest extends PostgresIntegrationTestBase {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_CHANNEL_1 = 111111111111111111L;
  private static final long TEST_CHANNEL_2 = 222222222222222222L;
  private static final long TEST_CHANNEL_3 = 333333333333333333L;

  private ltdjms.discord.aichat.persistence.AIChannelRestrictionRepository repository;
  private ltdjms.discord.aichat.services.DefaultAIChannelRestrictionService service;

  @BeforeEach
  void setUp() {
    DataSource dataSource = getDataSource();

    // 清理 ai_channel_restriction 資料表
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("TRUNCATE TABLE ai_channel_restriction CASCADE");
    } catch (Exception e) {
      // 資料表可能尚未建立，嘗試建立
      createTableIfNotExists(dataSource);
    }

    repository =
        new ltdjms.discord.aichat.persistence.JdbcAIChannelRestrictionRepository(dataSource);
    service = new ltdjms.discord.aichat.services.DefaultAIChannelRestrictionService(repository);
  }

  private void createTableIfNotExists(DataSource dataSource) {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(
          """
          CREATE TABLE IF NOT EXISTS ai_channel_restriction (
              guild_id BIGINT NOT NULL,
              channel_id BIGINT NOT NULL,
              channel_name TEXT NOT NULL,
              created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
              updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
              PRIMARY KEY (guild_id, channel_id)
          )
          """);
      stmt.execute(
          """
          CREATE INDEX IF NOT EXISTS idx_ai_channel_restriction_guild_id
              ON ai_channel_restriction(guild_id)
          """);
    } catch (Exception e) {
      throw new RuntimeException("Failed to create ai_channel_restriction table", e);
    }
  }

  @Nested
  @DisplayName("T038: add-remove-flow 整合測試")
  class AddRemoveFlowTests {

    @Test
    @DisplayName("應該能夠新增頻道並成功儲存")
    void shouldAddChannelAndPersist() {
      // Given
      AllowedChannel channel = new AllowedChannel(TEST_CHANNEL_1, "test-channel-1");

      // When
      Result<AllowedChannel, DomainError> result =
          service.addAllowedChannel(TEST_GUILD_ID, channel);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().channelId()).isEqualTo(TEST_CHANNEL_1);
      assertThat(result.getValue().channelName()).isEqualTo("test-channel-1");

      // 驗證資料庫儲存
      Result<Set<AllowedChannel>, DomainError> found = service.getAllowedChannels(TEST_GUILD_ID);
      assertThat(found.isOk()).isTrue();
      assertThat(found.getValue()).hasSize(1);
      assertThat(found.getValue().iterator().next().channelId()).isEqualTo(TEST_CHANNEL_1);
    }

    @Test
    @DisplayName("應該能夠新增多個頻道")
    void shouldAddMultipleChannels() {
      // Given
      AllowedChannel channel1 = new AllowedChannel(TEST_CHANNEL_1, "test-channel-1");
      AllowedChannel channel2 = new AllowedChannel(TEST_CHANNEL_2, "test-channel-2");

      // When
      service.addAllowedChannel(TEST_GUILD_ID, channel1);
      service.addAllowedChannel(TEST_GUILD_ID, channel2);

      // Then
      Result<Set<AllowedChannel>, DomainError> result = service.getAllowedChannels(TEST_GUILD_ID);
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue()).hasSize(2);
    }

    @Test
    @DisplayName("新增重複頻道應該回傳 DUPLICATE_CHANNEL 錯誤")
    void shouldReturnDuplicateErrorForDuplicateChannel() {
      // Given
      AllowedChannel channel = new AllowedChannel(TEST_CHANNEL_1, "test-channel-1");
      service.addAllowedChannel(TEST_GUILD_ID, channel);

      // When
      Result<AllowedChannel, DomainError> result =
          service.addAllowedChannel(TEST_GUILD_ID, channel);

      // Then
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().category()).isEqualTo(DomainError.Category.DUPLICATE_CHANNEL);
    }

    @Test
    @DisplayName("應該能夠移除已存在的頻道")
    void shouldRemoveExistingChannel() {
      // Given
      AllowedChannel channel1 = new AllowedChannel(TEST_CHANNEL_1, "test-channel-1");
      AllowedChannel channel2 = new AllowedChannel(TEST_CHANNEL_2, "test-channel-2");
      service.addAllowedChannel(TEST_GUILD_ID, channel1);
      service.addAllowedChannel(TEST_GUILD_ID, channel2);

      // When
      Result<Unit, DomainError> result =
          service.removeAllowedChannel(TEST_GUILD_ID, TEST_CHANNEL_1);

      // Then
      assertThat(result.isOk()).isTrue();

      Result<Set<AllowedChannel>, DomainError> remaining =
          service.getAllowedChannels(TEST_GUILD_ID);
      assertThat(remaining.isOk()).isTrue();
      assertThat(remaining.getValue()).hasSize(1);
      assertThat(remaining.getValue().iterator().next().channelId()).isEqualTo(TEST_CHANNEL_2);
    }

    @Test
    @DisplayName("移除不存在的頻道應該回傳 CHANNEL_NOT_FOUND 錯誤")
    void shouldReturnNotFoundErrorForNonExistentChannel() {
      // When
      Result<Unit, DomainError> result = service.removeAllowedChannel(TEST_GUILD_ID, 999L);

      // Then
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().category()).isEqualTo(DomainError.Category.CHANNEL_NOT_FOUND);
    }

    @Test
    @DisplayName("清空所有頻道應該恢復無限制模式")
    void shouldReturnToUnrestrictedModeWhenAllChannelsRemoved() {
      // Given
      AllowedChannel channel1 = new AllowedChannel(TEST_CHANNEL_1, "test-channel-1");
      service.addAllowedChannel(TEST_GUILD_ID, channel1);
      assertThat(service.isChannelAllowed(TEST_GUILD_ID, TEST_CHANNEL_2)).isFalse();

      // When
      service.removeAllowedChannel(TEST_GUILD_ID, TEST_CHANNEL_1);

      // Then - 無限制模式，所有頻道都允許
      assertThat(service.isChannelAllowed(TEST_GUILD_ID, TEST_CHANNEL_2)).isTrue();
    }
  }

  @Nested
  @DisplayName("T039: channel-check-flow 整合測試")
  class ChannelCheckFlowTests {

    @Test
    @DisplayName("在無限制模式下，所有頻道都應該被允許")
    void shouldAllowAllChannelsInUnrestrictedMode() {
      // When - 未設定任何頻道限制
      boolean allowed1 = service.isChannelAllowed(TEST_GUILD_ID, TEST_CHANNEL_1);
      boolean allowed2 = service.isChannelAllowed(TEST_GUILD_ID, TEST_CHANNEL_2);
      boolean allowed3 = service.isChannelAllowed(TEST_GUILD_ID, 999L);

      // Then
      assertThat(allowed1).isTrue();
      assertThat(allowed2).isTrue();
      assertThat(allowed3).isTrue();
    }

    @Test
    @DisplayName("在限制模式下，只有允許清單中的頻道能被使用")
    void shouldOnlyAllowChannelsInAllowlist() {
      // Given
      AllowedChannel channel1 = new AllowedChannel(TEST_CHANNEL_1, "test-channel-1");
      AllowedChannel channel2 = new AllowedChannel(TEST_CHANNEL_2, "test-channel-2");
      service.addAllowedChannel(TEST_GUILD_ID, channel1);
      service.addAllowedChannel(TEST_GUILD_ID, channel2);

      // When
      boolean allowed1 = service.isChannelAllowed(TEST_GUILD_ID, TEST_CHANNEL_1);
      boolean allowed2 = service.isChannelAllowed(TEST_GUILD_ID, TEST_CHANNEL_2);
      boolean allowed3 = service.isChannelAllowed(TEST_GUILD_ID, TEST_CHANNEL_3);

      // Then
      assertThat(allowed1).isTrue();
      assertThat(allowed2).isTrue();
      assertThat(allowed3).isFalse();
    }

    @Test
    @DisplayName("頻道檢查效能應該小於 100ms")
    void shouldCheckChannelPerformanceUnder100ms() {
      // Given
      AllowedChannel channel1 = new AllowedChannel(TEST_CHANNEL_1, "test-channel-1");
      service.addAllowedChannel(TEST_GUILD_ID, channel1);

      // When
      long startTime = System.nanoTime();
      for (int i = 0; i < 1000; i++) {
        service.isChannelAllowed(TEST_GUILD_ID, TEST_CHANNEL_1);
      }
      long endTime = System.nanoTime();

      // Then - 平均每次檢查應該遠小於 100ms
      double avgTimeMs = (endTime - startTime) / 1_000_000.0 / 1000;
      assertThat(avgTimeMs).isLessThan(100.0);
    }
  }

  @Nested
  @DisplayName("T040: deleted-channel-cleanup 整合測試")
  class DeletedChannelCleanupTests {

    @Test
    @DisplayName("批次刪除應該移除指定的頻道")
    void shouldBatchRemoveChannels() {
      // Given
      AllowedChannel channel1 = new AllowedChannel(TEST_CHANNEL_1, "test-channel-1");
      AllowedChannel channel2 = new AllowedChannel(TEST_CHANNEL_2, "test-channel-2");
      AllowedChannel channel3 = new AllowedChannel(TEST_CHANNEL_3, "test-channel-3");
      service.addAllowedChannel(TEST_GUILD_ID, channel1);
      service.addAllowedChannel(TEST_GUILD_ID, channel2);
      service.addAllowedChannel(TEST_GUILD_ID, channel3);

      // When
      Set<Long> validChannelIds = Set.of(TEST_CHANNEL_1, TEST_CHANNEL_3);
      repository.deleteRemovedChannels(TEST_GUILD_ID, validChannelIds);

      // Then
      Result<Set<AllowedChannel>, DomainError> remaining =
          service.getAllowedChannels(TEST_GUILD_ID);
      assertThat(remaining.isOk()).isTrue();
      assertThat(remaining.getValue()).hasSize(2);
      assertThat(remaining.getValue().stream().map(AllowedChannel::channelId))
          .containsExactlyInAnyOrder(TEST_CHANNEL_1, TEST_CHANNEL_3);
    }

    @Test
    @DisplayName("清理所有頻道應該恢復無限制模式")
    void shouldReturnToUnrestrictedModeAfterCleanup() {
      // Given
      AllowedChannel channel1 = new AllowedChannel(TEST_CHANNEL_1, "test-channel-1");
      AllowedChannel channel2 = new AllowedChannel(TEST_CHANNEL_2, "test-channel-2");
      service.addAllowedChannel(TEST_GUILD_ID, channel1);
      service.addAllowedChannel(TEST_GUILD_ID, channel2);
      assertThat(service.isChannelAllowed(TEST_GUILD_ID, 999L)).isFalse();

      // When - 清空所有頻道
      repository.deleteRemovedChannels(TEST_GUILD_ID, Set.of());

      // Then - 應該恢復無限制模式
      assertThat(service.isChannelAllowed(TEST_GUILD_ID, 999L)).isTrue();
      Result<Set<AllowedChannel>, DomainError> channels = service.getAllowedChannels(TEST_GUILD_ID);
      assertThat(channels.isOk()).isTrue();
      assertThat(channels.getValue()).isEmpty();
    }
  }

  @Nested
  @DisplayName("T041: unrestricted-mode (empty list) 整合測試")
  class UnrestrictedModeTests {

    @Test
    @DisplayName("空清單應該被視為無限制模式")
    void emptyListShouldBeUnrestrictedMode() {
      // When - 未新增任何頻道
      Result<Set<AllowedChannel>, DomainError> result = service.getAllowedChannels(TEST_GUILD_ID);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue()).isEmpty();
      assertThat(service.isChannelAllowed(TEST_GUILD_ID, 123L)).isTrue();
      assertThat(service.isChannelAllowed(TEST_GUILD_ID, 456L)).isTrue();
      assertThat(service.isChannelAllowed(TEST_GUILD_ID, 789L)).isTrue();
    }

    @Test
    @DisplayName("從無限制模式切換到限制模式")
    void shouldSwitchFromUnrestrictedToRestricted() {
      // Given - 無限制模式
      assertThat(service.isChannelAllowed(TEST_GUILD_ID, 999L)).isTrue();

      // When - 新增第一個頻道
      AllowedChannel channel1 = new AllowedChannel(TEST_CHANNEL_1, "test-channel-1");
      service.addAllowedChannel(TEST_GUILD_ID, channel1);

      // Then - 應該切換到限制模式
      assertThat(service.isChannelAllowed(TEST_GUILD_ID, TEST_CHANNEL_1)).isTrue();
      assertThat(service.isChannelAllowed(TEST_GUILD_ID, 999L)).isFalse();
    }

    @Test
    @DisplayName("從限制模式恢復到無限制模式")
    void shouldSwitchFromRestrictedToUnrestricted() {
      // Given - 限制模式
      AllowedChannel channel1 = new AllowedChannel(TEST_CHANNEL_1, "test-channel-1");
      service.addAllowedChannel(TEST_GUILD_ID, channel1);
      assertThat(service.isChannelAllowed(TEST_GUILD_ID, 999L)).isFalse();

      // When - 移除所有頻道
      service.removeAllowedChannel(TEST_GUILD_ID, TEST_CHANNEL_1);

      // Then - 應該恢復無限制模式
      assertThat(service.isChannelAllowed(TEST_GUILD_ID, 999L)).isTrue();
      Result<Set<AllowedChannel>, DomainError> result = service.getAllowedChannels(TEST_GUILD_ID);
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue()).isEmpty();
    }

    @Test
    @DisplayName("多個伺服器應該有獨立的頻道限制設定")
    void shouldHaveIndependentSettingsPerGuild() {
      // Given
      long guild1 = 111L;
      long guild2 = 222L;
      AllowedChannel channel1 = new AllowedChannel(TEST_CHANNEL_1, "test-channel-1");

      // When - Guild 1 設定頻道限制
      service.addAllowedChannel(guild1, channel1);

      // Then - Guild 1 應該是限制模式，Guild 2 應該是無限制模式
      assertThat(service.isChannelAllowed(guild1, TEST_CHANNEL_1)).isTrue();
      assertThat(service.isChannelAllowed(guild1, 999L)).isFalse();

      // Guild 2 是無限制模式，所有頻道都允許
      assertThat(service.isChannelAllowed(guild2, TEST_CHANNEL_1)).isTrue();
      assertThat(service.isChannelAllowed(guild2, 999L)).isTrue();
    }
  }
}
