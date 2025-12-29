package ltdjms.discord.aiagent.unit.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import ltdjms.discord.aiagent.domain.AIAgentChannelConfig;

/**
 * 測試 {@link AIAgentChannelConfig} 的領域邏輯。
 *
 * <p>測試範圍：
 *
 * <ul>
 *   <li>T022: AIAgentChannelConfig 單元測試
 * </ul>
 */
@DisplayName("T022: AIAgentChannelConfig 單元測試")
class AIAgentChannelConfigTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_CHANNEL_ID = 111111111111111111L;

  @Nested
  @DisplayName("建立配置")
  class CreateTests {

    @Test
    @DisplayName("新建立的配置預設啟用 Agent 模式")
    void newConfigShouldEnableAgentByDefault() {
      // When
      AIAgentChannelConfig config = AIAgentChannelConfig.create(TEST_GUILD_ID, TEST_CHANNEL_ID);

      // Then
      assertThat(config.id()).isEqualTo(0L);
      assertThat(config.guildId()).isEqualTo(TEST_GUILD_ID);
      assertThat(config.channelId()).isEqualTo(TEST_CHANNEL_ID);
      assertThat(config.agentEnabled()).isTrue();
      assertThat(config.createdAt()).isNotNull();
      assertThat(config.updatedAt()).isNotNull();
    }

    @Test
    @DisplayName("建立時間與更新時間應該相同")
    void createdAtShouldEqualUpdatedAtForNewConfig() {
      // When
      AIAgentChannelConfig config = AIAgentChannelConfig.create(TEST_GUILD_ID, TEST_CHANNEL_ID);

      // Then
      assertThat(config.createdAt()).isEqualTo(config.updatedAt());
    }
  }

  @Nested
  @DisplayName("切換狀態")
  class ToggleTests {

    @Test
    @DisplayName("從啟用狀態切換應該變為停用")
    void toggleFromEnabledShouldDisable() {
      // Given
      AIAgentChannelConfig config = AIAgentChannelConfig.create(TEST_GUILD_ID, TEST_CHANNEL_ID);

      // When
      AIAgentChannelConfig toggled = config.toggleAgentMode();

      // Then
      assertThat(toggled.agentEnabled()).isFalse();
      assertThat(toggled.id()).isEqualTo(config.id());
      assertThat(toggled.guildId()).isEqualTo(config.guildId());
      assertThat(toggled.channelId()).isEqualTo(config.channelId());
      assertThat(toggled.createdAt()).isEqualTo(config.createdAt());
      assertThat(toggled.updatedAt()).isAfter(config.updatedAt());
    }

    @Test
    @DisplayName("從停用狀態切換應該變為啟用")
    void toggleFromDisabledShouldEnable() {
      // Given
      AIAgentChannelConfig config =
          AIAgentChannelConfig.create(TEST_GUILD_ID, TEST_CHANNEL_ID).withAgentEnabled(false);

      // When
      AIAgentChannelConfig toggled = config.toggleAgentMode();

      // Then
      assertThat(toggled.agentEnabled()).isTrue();
    }

    @Test
    @DisplayName("連續切換應該正確切換狀態")
    void multipleTogglesShouldWorkCorrectly() {
      // Given
      AIAgentChannelConfig config = AIAgentChannelConfig.create(TEST_GUILD_ID, TEST_CHANNEL_ID);

      // When
      AIAgentChannelConfig after1 = config.toggleAgentMode(); // false
      AIAgentChannelConfig after2 = after1.toggleAgentMode(); // true
      AIAgentChannelConfig after3 = after2.toggleAgentMode(); // false

      // Then
      assertThat(after1.agentEnabled()).isFalse();
      assertThat(after2.agentEnabled()).isTrue();
      assertThat(after3.agentEnabled()).isFalse();
    }
  }

  @Nested
  @DisplayName("設定狀態")
  class WithAgentEnabledTests {

    @Test
    @DisplayName("應該能夠設定為啟用")
    void shouldSetToEnabled() {
      // Given
      AIAgentChannelConfig config =
          AIAgentChannelConfig.create(TEST_GUILD_ID, TEST_CHANNEL_ID).withAgentEnabled(false);

      // When
      AIAgentChannelConfig updated = config.withAgentEnabled(true);

      // Then
      assertThat(updated.agentEnabled()).isTrue();
    }

    @Test
    @DisplayName("應該能夠設定為停用")
    void shouldSetToDisabled() {
      // Given
      AIAgentChannelConfig config = AIAgentChannelConfig.create(TEST_GUILD_ID, TEST_CHANNEL_ID);

      // When
      AIAgentChannelConfig updated = config.withAgentEnabled(false);

      // Then
      assertThat(updated.agentEnabled()).isFalse();
    }

    @Test
    @DisplayName("設定狀態應該更新時間戳")
    void settingStateShouldUpdateTimestamp() {
      // Given
      AIAgentChannelConfig config = AIAgentChannelConfig.create(TEST_GUILD_ID, TEST_CHANNEL_ID);

      // When
      AIAgentChannelConfig updated = config.withAgentEnabled(false);

      // Then
      assertThat(updated.updatedAt()).isAfter(config.updatedAt());
    }
  }

  @Nested
  @DisplayName("驗證")
  class ValidationTests {

    @Test
    @DisplayName("createdAt 為 null 應該拋出例外")
    void nullCreatedAtShouldThrowException() {
      // When & Then
      assertThatThrownBy(
              () ->
                  new AIAgentChannelConfig(
                      0L, TEST_GUILD_ID, TEST_CHANNEL_ID, true, null, LocalDateTime.now()))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("createdAt");
    }

    @Test
    @DisplayName("updatedAt 為 null 應該拋出例外")
    void nullUpdatedAtShouldThrowException() {
      // When & Then
      assertThatThrownBy(
              () ->
                  new AIAgentChannelConfig(
                      0L, TEST_GUILD_ID, TEST_CHANNEL_ID, true, LocalDateTime.now(), null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("updatedAt");
    }
  }
}
