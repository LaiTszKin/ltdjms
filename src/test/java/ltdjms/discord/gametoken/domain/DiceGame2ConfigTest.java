package ltdjms.discord.gametoken.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiceGame2ConfigTest {

    private static final long TEST_GUILD_ID = 123456789012345678L;

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("should reject negative minTokensPerPlay")
        void shouldRejectNegativeMinTokens() {
            assertThatThrownBy(() -> new DiceGame2Config(
                    TEST_GUILD_ID,
                    -1L,       // minTokensPerPlay
                    50L,       // maxTokensPerPlay
                    100_000L,  // straightMultiplier
                    20_000L,   // baseMultiplier
                    1_500_000L, // tripleLowBonus
                    2_500_000L, // tripleHighBonus
                    Instant.now(),
                    Instant.now()
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("minTokensPerPlay cannot be negative");
        }

        @Test
        @DisplayName("should reject negative maxTokensPerPlay")
        void shouldRejectNegativeMaxTokens() {
            assertThatThrownBy(() -> new DiceGame2Config(
                    TEST_GUILD_ID,
                    5L,
                    -1L,
                    100_000L,
                    20_000L,
                    1_500_000L,
                    2_500_000L,
                    Instant.now(),
                    Instant.now()
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxTokensPerPlay cannot be negative");
        }

        @Test
        @DisplayName("should reject minTokensPerPlay greater than maxTokensPerPlay")
        void shouldRejectMinGreaterThanMax() {
            assertThatThrownBy(() -> new DiceGame2Config(
                    TEST_GUILD_ID,
                    50L,
                    10L,
                    100_000L,
                    20_000L,
                    1_500_000L,
                    2_500_000L,
                    Instant.now(),
                    Instant.now()
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("minTokensPerPlay cannot be greater than maxTokensPerPlay");
        }

        @Test
        @DisplayName("should reject negative straightMultiplier")
        void shouldRejectNegativeStraightMultiplier() {
            assertThatThrownBy(() -> new DiceGame2Config(
                    TEST_GUILD_ID,
                    5L,
                    50L,
                    -1L,
                    20_000L,
                    1_500_000L,
                    2_500_000L,
                    Instant.now(),
                    Instant.now()
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("straightMultiplier cannot be negative");
        }

        @Test
        @DisplayName("should reject negative baseMultiplier")
        void shouldRejectNegativeBaseMultiplier() {
            assertThatThrownBy(() -> new DiceGame2Config(
                    TEST_GUILD_ID,
                    5L,
                    50L,
                    100_000L,
                    -1L,
                    1_500_000L,
                    2_500_000L,
                    Instant.now(),
                    Instant.now()
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("baseMultiplier cannot be negative");
        }

        @Test
        @DisplayName("should reject negative tripleLowBonus")
        void shouldRejectNegativeTripleLowBonus() {
            assertThatThrownBy(() -> new DiceGame2Config(
                    TEST_GUILD_ID,
                    5L,
                    50L,
                    100_000L,
                    20_000L,
                    -1L,
                    2_500_000L,
                    Instant.now(),
                    Instant.now()
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tripleLowBonus cannot be negative");
        }

        @Test
        @DisplayName("should reject negative tripleHighBonus")
        void shouldRejectNegativeTripleHighBonus() {
            assertThatThrownBy(() -> new DiceGame2Config(
                    TEST_GUILD_ID,
                    5L,
                    50L,
                    100_000L,
                    20_000L,
                    1_500_000L,
                    -1L,
                    Instant.now(),
                    Instant.now()
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tripleHighBonus cannot be negative");
        }

        @Test
        @DisplayName("should accept valid configuration")
        void shouldAcceptValidConfig() {
            DiceGame2Config config = new DiceGame2Config(
                    TEST_GUILD_ID,
                    10L,
                    40L,
                    200_000L,
                    50_000L,
                    2_000_000L,
                    3_000_000L,
                    Instant.now(),
                    Instant.now()
            );

            assertThat(config.guildId()).isEqualTo(TEST_GUILD_ID);
            assertThat(config.minTokensPerPlay()).isEqualTo(10L);
            assertThat(config.maxTokensPerPlay()).isEqualTo(40L);
            assertThat(config.straightMultiplier()).isEqualTo(200_000L);
            assertThat(config.baseMultiplier()).isEqualTo(50_000L);
            assertThat(config.tripleLowBonus()).isEqualTo(2_000_000L);
            assertThat(config.tripleHighBonus()).isEqualTo(3_000_000L);
        }
    }

    @Nested
    @DisplayName("createDefault")
    class CreateDefault {

        @Test
        @DisplayName("should use default values")
        void shouldUseDefaults() {
            DiceGame2Config config = DiceGame2Config.createDefault(TEST_GUILD_ID);

            assertThat(config.guildId()).isEqualTo(TEST_GUILD_ID);
            assertThat(config.minTokensPerPlay()).isEqualTo(DiceGame2Config.DEFAULT_MIN_TOKENS_PER_PLAY);
            assertThat(config.maxTokensPerPlay()).isEqualTo(DiceGame2Config.DEFAULT_MAX_TOKENS_PER_PLAY);
            assertThat(config.straightMultiplier()).isEqualTo(DiceGame2Config.DEFAULT_STRAIGHT_MULTIPLIER);
            assertThat(config.baseMultiplier()).isEqualTo(DiceGame2Config.DEFAULT_BASE_MULTIPLIER);
            assertThat(config.tripleLowBonus()).isEqualTo(DiceGame2Config.DEFAULT_TRIPLE_LOW_BONUS);
            assertThat(config.tripleHighBonus()).isEqualTo(DiceGame2Config.DEFAULT_TRIPLE_HIGH_BONUS);
            assertThat(config.createdAt()).isNotNull();
            assertThat(config.updatedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("with* methods")
    class WithMethods {

        @Test
        @DisplayName("withTokensPerPlayRange should update range")
        void withTokensPerPlayRangeShouldUpdate() {
            DiceGame2Config original = DiceGame2Config.createDefault(TEST_GUILD_ID);
            Instant originalUpdatedAt = original.updatedAt();

            DiceGame2Config updated = original.withTokensPerPlayRange(10L, 40L);

            assertThat(updated.minTokensPerPlay()).isEqualTo(10L);
            assertThat(updated.maxTokensPerPlay()).isEqualTo(40L);
            assertThat(updated.straightMultiplier()).isEqualTo(original.straightMultiplier());
            assertThat(updated.createdAt()).isEqualTo(original.createdAt());
            assertThat(updated.updatedAt()).isAfterOrEqualTo(originalUpdatedAt);
        }

        @Test
        @DisplayName("withMultipliers should update multipliers")
        void withMultipliersShouldUpdate() {
            DiceGame2Config original = DiceGame2Config.createDefault(TEST_GUILD_ID);

            DiceGame2Config updated = original.withMultipliers(200_000L, 50_000L);

            assertThat(updated.straightMultiplier()).isEqualTo(200_000L);
            assertThat(updated.baseMultiplier()).isEqualTo(50_000L);
            assertThat(updated.tripleLowBonus()).isEqualTo(original.tripleLowBonus());
            assertThat(updated.tripleHighBonus()).isEqualTo(original.tripleHighBonus());
        }

        @Test
        @DisplayName("withTripleBonuses should update bonuses")
        void withTripleBonusesShouldUpdate() {
            DiceGame2Config original = DiceGame2Config.createDefault(TEST_GUILD_ID);

            DiceGame2Config updated = original.withTripleBonuses(2_000_000L, 3_000_000L);

            assertThat(updated.tripleLowBonus()).isEqualTo(2_000_000L);
            assertThat(updated.tripleHighBonus()).isEqualTo(3_000_000L);
            assertThat(updated.straightMultiplier()).isEqualTo(original.straightMultiplier());
            assertThat(updated.baseMultiplier()).isEqualTo(original.baseMultiplier());
        }
    }

    @Nested
    @DisplayName("isValidTokenAmount")
    class IsValidTokenAmount {

        @Test
        @DisplayName("should return true for amount within range")
        void shouldReturnTrueForValidAmount() {
            DiceGame2Config config = DiceGame2Config.createDefault(TEST_GUILD_ID);

            assertThat(config.isValidTokenAmount(5L)).isTrue();
            assertThat(config.isValidTokenAmount(25L)).isTrue();
            assertThat(config.isValidTokenAmount(50L)).isTrue();
        }

        @Test
        @DisplayName("should return false for amount outside range")
        void shouldReturnFalseForInvalidAmount() {
            DiceGame2Config config = DiceGame2Config.createDefault(TEST_GUILD_ID);

            assertThat(config.isValidTokenAmount(4L)).isFalse();
            assertThat(config.isValidTokenAmount(51L)).isFalse();
            assertThat(config.isValidTokenAmount(0L)).isFalse();
        }
    }

    @Nested
    @DisplayName("calculateDiceCount")
    class CalculateDiceCount {

        @Test
        @DisplayName("should calculate dice count as 3 times tokens")
        void shouldCalculateDiceCount() {
            DiceGame2Config config = DiceGame2Config.createDefault(TEST_GUILD_ID);

            assertThat(config.calculateDiceCount(5)).isEqualTo(15);
            assertThat(config.calculateDiceCount(10)).isEqualTo(30);
            assertThat(config.calculateDiceCount(1)).isEqualTo(3);
        }
    }
}
