package ltdjms.discord.gametoken.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DiceGame1ConfigTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;

  @Nested
  @DisplayName("Constructor validation")
  class ConstructorValidation {

    @Test
    @DisplayName("should reject negative minTokensPerPlay")
    void shouldRejectNegativeMinTokens() {
      assertThatThrownBy(
              () ->
                  new DiceGame1Config(
                      TEST_GUILD_ID,
                      -1L, // minTokensPerPlay
                      10L, // maxTokensPerPlay
                      250_000L, // rewardPerDiceValue
                      Instant.now(),
                      Instant.now()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("minTokensPerPlay cannot be negative");
    }

    @Test
    @DisplayName("should reject negative maxTokensPerPlay")
    void shouldRejectNegativeMaxTokens() {
      assertThatThrownBy(
              () ->
                  new DiceGame1Config(
                      TEST_GUILD_ID,
                      1L, // minTokensPerPlay
                      -1L, // maxTokensPerPlay
                      250_000L,
                      Instant.now(),
                      Instant.now()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("maxTokensPerPlay cannot be negative");
    }

    @Test
    @DisplayName("should reject minTokensPerPlay greater than maxTokensPerPlay")
    void shouldRejectMinGreaterThanMax() {
      assertThatThrownBy(
              () ->
                  new DiceGame1Config(
                      TEST_GUILD_ID,
                      10L, // minTokensPerPlay
                      5L, // maxTokensPerPlay (less than min)
                      250_000L,
                      Instant.now(),
                      Instant.now()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("minTokensPerPlay cannot be greater than maxTokensPerPlay");
    }

    @Test
    @DisplayName("should reject negative rewardPerDiceValue")
    void shouldRejectNegativeReward() {
      assertThatThrownBy(
              () ->
                  new DiceGame1Config(
                      TEST_GUILD_ID,
                      1L,
                      10L,
                      -1L, // negative rewardPerDiceValue
                      Instant.now(),
                      Instant.now()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("rewardPerDiceValue cannot be negative");
    }

    @Test
    @DisplayName("should accept valid configuration")
    void shouldAcceptValidConfig() {
      DiceGame1Config config =
          new DiceGame1Config(TEST_GUILD_ID, 2L, 8L, 300_000L, Instant.now(), Instant.now());

      assertThat(config.guildId()).isEqualTo(TEST_GUILD_ID);
      assertThat(config.minTokensPerPlay()).isEqualTo(2L);
      assertThat(config.maxTokensPerPlay()).isEqualTo(8L);
      assertThat(config.rewardPerDiceValue()).isEqualTo(300_000L);
    }
  }

  @Nested
  @DisplayName("createDefault")
  class CreateDefault {

    @Test
    @DisplayName("should use default values")
    void shouldUseDefaults() {
      DiceGame1Config config = DiceGame1Config.createDefault(TEST_GUILD_ID);

      assertThat(config.guildId()).isEqualTo(TEST_GUILD_ID);
      assertThat(config.minTokensPerPlay()).isEqualTo(DiceGame1Config.DEFAULT_MIN_TOKENS_PER_PLAY);
      assertThat(config.maxTokensPerPlay()).isEqualTo(DiceGame1Config.DEFAULT_MAX_TOKENS_PER_PLAY);
      assertThat(config.rewardPerDiceValue())
          .isEqualTo(DiceGame1Config.DEFAULT_REWARD_PER_DICE_VALUE);
      assertThat(config.createdAt()).isNotNull();
      assertThat(config.updatedAt()).isNotNull();
    }
  }

  @Nested
  @DisplayName("with* methods")
  class WithMethods {

    @Test
    @DisplayName("withTokensPerPlayRange should update range and validate")
    void withTokensPerPlayRangeShouldUpdate() {
      DiceGame1Config original = DiceGame1Config.createDefault(TEST_GUILD_ID);
      Instant originalUpdatedAt = original.updatedAt();

      DiceGame1Config updated = original.withTokensPerPlayRange(2L, 8L);

      assertThat(updated.minTokensPerPlay()).isEqualTo(2L);
      assertThat(updated.maxTokensPerPlay()).isEqualTo(8L);
      assertThat(updated.rewardPerDiceValue()).isEqualTo(original.rewardPerDiceValue());
      assertThat(updated.createdAt()).isEqualTo(original.createdAt());
      assertThat(updated.updatedAt()).isAfterOrEqualTo(originalUpdatedAt);
    }

    @Test
    @DisplayName("withRewardPerDiceValue should update reward")
    void withRewardPerDiceValueShouldUpdate() {
      DiceGame1Config original = DiceGame1Config.createDefault(TEST_GUILD_ID);
      Instant originalUpdatedAt = original.updatedAt();

      DiceGame1Config updated = original.withRewardPerDiceValue(300_000L);

      assertThat(updated.rewardPerDiceValue()).isEqualTo(300_000L);
      assertThat(updated.minTokensPerPlay()).isEqualTo(original.minTokensPerPlay());
      assertThat(updated.maxTokensPerPlay()).isEqualTo(original.maxTokensPerPlay());
      assertThat(updated.createdAt()).isEqualTo(original.createdAt());
      assertThat(updated.updatedAt()).isAfterOrEqualTo(originalUpdatedAt);
    }
  }

  @Nested
  @DisplayName("isValidTokenAmount")
  class IsValidTokenAmount {

    @Test
    @DisplayName("should return true for amount within range")
    void shouldReturnTrueForValidAmount() {
      DiceGame1Config config =
          new DiceGame1Config(TEST_GUILD_ID, 2L, 8L, 250_000L, Instant.now(), Instant.now());

      assertThat(config.isValidTokenAmount(2L)).isTrue();
      assertThat(config.isValidTokenAmount(5L)).isTrue();
      assertThat(config.isValidTokenAmount(8L)).isTrue();
    }

    @Test
    @DisplayName("should return false for amount outside range")
    void shouldReturnFalseForInvalidAmount() {
      DiceGame1Config config =
          new DiceGame1Config(TEST_GUILD_ID, 2L, 8L, 250_000L, Instant.now(), Instant.now());

      assertThat(config.isValidTokenAmount(1L)).isFalse();
      assertThat(config.isValidTokenAmount(9L)).isFalse();
      assertThat(config.isValidTokenAmount(0L)).isFalse();
    }
  }
}
