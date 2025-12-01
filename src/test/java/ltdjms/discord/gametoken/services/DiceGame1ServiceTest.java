package ltdjms.discord.gametoken.services;

import ltdjms.discord.currency.domain.MemberCurrencyAccount;
import ltdjms.discord.currency.persistence.MemberCurrencyAccountRepository;
import ltdjms.discord.gametoken.domain.DiceGame1Config;
import ltdjms.discord.gametoken.services.DiceGame1Service.DiceGameResult;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for DiceGame1Service.
 * Uses a predictable Random implementation to avoid Java 25 mocking restrictions.
 */
class DiceGame1ServiceTest {

    private static final long TEST_GUILD_ID = 123456789012345678L;
    private static final long TEST_USER_ID = 987654321098765432L;

    /**
     * A predictable Random that returns values from a predefined sequence.
     */
    static class PredictableRandom extends Random {
        private final Iterator<Integer> values;

        PredictableRandom(List<Integer> values) {
            this.values = values.iterator();
        }

        @Override
        public int nextInt(int bound) {
            return values.next();
        }
    }

    /**
     * A stub repository for testing.
     */
    static class StubCurrencyRepository implements MemberCurrencyAccountRepository {
        private MemberCurrencyAccount account;
        private int adjustCallCount = 0;

        StubCurrencyRepository(long initialBalance) {
            Instant now = Instant.now();
            this.account = new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, initialBalance, now, now);
        }

        @Override
        public Optional<MemberCurrencyAccount> findByGuildIdAndUserId(long guildId, long userId) {
            return Optional.of(account);
        }

        @Override
        public MemberCurrencyAccount save(MemberCurrencyAccount account) {
            this.account = account;
            return account;
        }

        @Override
        public MemberCurrencyAccount findOrCreate(long guildId, long userId) {
            return account;
        }

        @Override
        public MemberCurrencyAccount adjustBalance(long guildId, long userId, long amount) {
            adjustCallCount++;
            Instant now = Instant.now();
            this.account = new MemberCurrencyAccount(guildId, userId, account.balance() + amount, account.createdAt(), now);
            return account;
        }

        @Override
        public Result<MemberCurrencyAccount, DomainError> tryAdjustBalance(long guildId, long userId, long amount) {
            MemberCurrencyAccount updated = adjustBalance(guildId, userId, amount);
            return Result.ok(updated);
        }

        @Override
        public MemberCurrencyAccount setBalance(long guildId, long userId, long newBalance) {
            Instant now = Instant.now();
            this.account = new MemberCurrencyAccount(guildId, userId, newBalance, account.createdAt(), now);
            return account;
        }

        @Override
        public boolean deleteByGuildIdAndUserId(long guildId, long userId) {
            return true;
        }

        int getAdjustCallCount() {
            return adjustCallCount;
        }
    }

    @Nested
    @DisplayName("Roll dice")
    class RollDice {

        @Test
        @DisplayName("should roll specified number of dice")
        void shouldRollSpecifiedNumberOfDice() {
            // Given - predictable random that returns 0, 1, 2, 3, 4 (resulting in dice values 1, 2, 3, 4, 5)
            PredictableRandom random = new PredictableRandom(List.of(0, 1, 2, 3, 4));
            StubCurrencyRepository repository = new StubCurrencyRepository(0L);
            DiceGame1Service service = new DiceGame1Service(repository, random);

            // When
            List<Integer> rolls = service.rollDice(5);

            // Then
            assertThat(rolls).hasSize(5);
            assertThat(rolls).containsExactly(1, 2, 3, 4, 5);
        }

        @Test
        @DisplayName("should roll different number of dice based on parameter")
        void shouldRollDifferentNumberBasedOnParameter() {
            // Given
            PredictableRandom random = new PredictableRandom(List.of(0, 1, 2));
            StubCurrencyRepository repository = new StubCurrencyRepository(0L);
            DiceGame1Service service = new DiceGame1Service(repository, random);

            // When
            List<Integer> rolls = service.rollDice(3);

            // Then
            assertThat(rolls).hasSize(3);
            assertThat(rolls).containsExactly(1, 2, 3);
        }
    }

    @Nested
    @DisplayName("Calculate total reward")
    class CalculateTotalReward {

        @Test
        @DisplayName("should calculate correct total reward with default multiplier")
        void shouldCalculateCorrectTotalReward() {
            // Given
            StubCurrencyRepository repository = new StubCurrencyRepository(0L);
            DiceGame1Service service = new DiceGame1Service(repository);
            List<Integer> rolls = List.of(1, 2, 3, 4, 5);
            // Expected: 1*250000 + 2*250000 + 3*250000 + 4*250000 + 5*250000 = 15*250000 = 3,750,000

            // When
            long totalReward = service.calculateTotalReward(rolls, DiceGame1Config.DEFAULT_REWARD_PER_DICE_VALUE);

            // Then
            assertThat(totalReward).isEqualTo(3_750_000L);
        }

        @Test
        @DisplayName("should calculate max reward when all dice are 6")
        void shouldCalculateMaxReward() {
            // Given
            StubCurrencyRepository repository = new StubCurrencyRepository(0L);
            DiceGame1Service service = new DiceGame1Service(repository);
            List<Integer> rolls = List.of(6, 6, 6, 6, 6);
            // Expected: 6*250000 * 5 = 7,500,000

            // When
            long totalReward = service.calculateTotalReward(rolls, DiceGame1Config.DEFAULT_REWARD_PER_DICE_VALUE);

            // Then
            assertThat(totalReward).isEqualTo(7_500_000L);
        }

        @Test
        @DisplayName("should calculate min reward when all dice are 1")
        void shouldCalculateMinReward() {
            // Given
            StubCurrencyRepository repository = new StubCurrencyRepository(0L);
            DiceGame1Service service = new DiceGame1Service(repository);
            List<Integer> rolls = List.of(1, 1, 1, 1, 1);
            // Expected: 1*250000 * 5 = 1,250,000

            // When
            long totalReward = service.calculateTotalReward(rolls, DiceGame1Config.DEFAULT_REWARD_PER_DICE_VALUE);

            // Then
            assertThat(totalReward).isEqualTo(1_250_000L);
        }

        @Test
        @DisplayName("should calculate reward using configured multiplier")
        void shouldCalculateRewardWithConfiguredMultiplier() {
            // Given
            StubCurrencyRepository repository = new StubCurrencyRepository(0L);
            DiceGame1Service service = new DiceGame1Service(repository);
            List<Integer> rolls = List.of(1, 2, 3, 4, 5);
            long customMultiplier = 500_000L;  // Double the default
            // Expected: (1+2+3+4+5) * 500000 = 15 * 500000 = 7,500,000

            // When
            long totalReward = service.calculateTotalReward(rolls, customMultiplier);

            // Then
            assertThat(totalReward).isEqualTo(7_500_000L);
        }
    }

    @Nested
    @DisplayName("Play game")
    class PlayGame {

        @Test
        @DisplayName("should play game and return result with default config")
        void shouldPlayGameAndReturnResult() {
            // Given - predictable random returns 0,1,2,3,4 resulting in dice 1,2,3,4,5
            PredictableRandom random = new PredictableRandom(List.of(0, 1, 2, 3, 4));
            StubCurrencyRepository repository = new StubCurrencyRepository(1000L);
            DiceGame1Service service = new DiceGame1Service(repository, random);
            DiceGame1Config config = DiceGame1Config.createDefault(TEST_GUILD_ID);

            // When
            DiceGameResult result = service.play(TEST_GUILD_ID, TEST_USER_ID, config, 5);

            // Then
            assertThat(result.diceRolls()).containsExactly(1, 2, 3, 4, 5);
            assertThat(result.totalReward()).isEqualTo(3_750_000L);
            assertThat(result.previousBalance()).isEqualTo(1000L);
        }

        @Test
        @DisplayName("should apply reward in single adjustment when MAX_ADJUSTMENT_AMOUNT is very large")
        void shouldApplyRewardInSingleAdjustment() {
            // Given - max reward is 7,500,000 which is less than Long.MAX_VALUE
            // Random returns all 5s, resulting in dice all showing 6
            PredictableRandom random = new PredictableRandom(List.of(5, 5, 5, 5, 5));
            StubCurrencyRepository repository = new StubCurrencyRepository(0L);
            DiceGame1Service service = new DiceGame1Service(repository, random);
            DiceGame1Config config = DiceGame1Config.createDefault(TEST_GUILD_ID);

            // When
            service.play(TEST_GUILD_ID, TEST_USER_ID, config, 5);

            // Then - should call adjustBalance just once since MAX_ADJUSTMENT_AMOUNT is Long.MAX_VALUE
            assertThat(repository.getAdjustCallCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should play game with custom configuration")
        void shouldPlayGameWithCustomConfig() {
            // Given - predictable random returns 0,1,2,3,4 resulting in dice 1,2,3,4,5
            PredictableRandom random = new PredictableRandom(List.of(0, 1, 2, 3, 4));
            StubCurrencyRepository repository = new StubCurrencyRepository(1000L);
            DiceGame1Service service = new DiceGame1Service(repository, random);

            DiceGame1Config config = DiceGame1Config.createDefault(TEST_GUILD_ID)
                    .withRewardPerDiceValue(100_000L);  // Lower than default

            // When
            DiceGameResult result = service.play(TEST_GUILD_ID, TEST_USER_ID, config, 5);

            // Then
            assertThat(result.diceRolls()).containsExactly(1, 2, 3, 4, 5);
            // Expected: (1+2+3+4+5) * 100000 = 15 * 100000 = 1,500,000
            assertThat(result.totalReward()).isEqualTo(1_500_000L);
            assertThat(result.previousBalance()).isEqualTo(1000L);
        }

        @Test
        @DisplayName("should use configured multiplier from DiceGame1Config")
        void shouldUseConfiguredMultiplier() {
            // Given - high roller configuration with 1M per dice value
            PredictableRandom random = new PredictableRandom(List.of(5, 5, 5, 5, 5)); // all 6s
            StubCurrencyRepository repository = new StubCurrencyRepository(0L);
            DiceGame1Service service = new DiceGame1Service(repository, random);

            DiceGame1Config highRollerConfig = DiceGame1Config.createDefault(TEST_GUILD_ID)
                    .withRewardPerDiceValue(1_000_000L);

            // When
            DiceGameResult result = service.play(TEST_GUILD_ID, TEST_USER_ID, highRollerConfig, 5);

            // Then
            // Expected: 6 * 5 * 1_000_000 = 30,000,000
            assertThat(result.totalReward()).isEqualTo(30_000_000L);
        }

        @Test
        @DisplayName("should handle zero reward multiplier")
        void shouldHandleZeroRewardMultiplier() {
            // Given - special event with free plays but no reward
            PredictableRandom random = new PredictableRandom(List.of(0, 1, 2, 3, 4));
            StubCurrencyRepository repository = new StubCurrencyRepository(5000L);
            DiceGame1Service service = new DiceGame1Service(repository, random);

            DiceGame1Config zeroRewardConfig = new DiceGame1Config(
                    TEST_GUILD_ID, 1L, 10L, 0L,
                    Instant.now(), Instant.now()
            );

            // When
            DiceGameResult result = service.play(TEST_GUILD_ID, TEST_USER_ID, zeroRewardConfig, 5);

            // Then
            assertThat(result.totalReward()).isEqualTo(0L);
            assertThat(result.previousBalance()).isEqualTo(5000L);
            assertThat(result.newBalance()).isEqualTo(5000L);
        }

        @Test
        @DisplayName("should roll number of dice equal to tokens spent")
        void shouldRollDiceEqualToTokensSpent() {
            // Given - 7 tokens = 7 dice
            PredictableRandom random = new PredictableRandom(List.of(0, 1, 2, 3, 4, 5, 0)); // 7 values
            StubCurrencyRepository repository = new StubCurrencyRepository(1000L);
            DiceGame1Service service = new DiceGame1Service(repository, random);
            DiceGame1Config config = DiceGame1Config.createDefault(TEST_GUILD_ID);

            // When
            DiceGameResult result = service.play(TEST_GUILD_ID, TEST_USER_ID, config, 7);

            // Then
            assertThat(result.diceRolls()).hasSize(7);
            assertThat(result.diceRolls()).containsExactly(1, 2, 3, 4, 5, 6, 1);
        }
    }

    @Nested
    @DisplayName("Result formatting")
    class ResultFormatting {

        @Test
        @DisplayName("should format game result message correctly")
        void shouldFormatGameResultMessageCorrectly() {
            // Given
            DiceGameResult result = new DiceGameResult(
                    TEST_GUILD_ID, TEST_USER_ID,
                    List.of(1, 2, 3, 4, 5),
                    3_750_000L, 0L, 3_750_000L
            );

            // When
            String message = result.formatMessage("💰", "Gold");

            // Then
            assertThat(message).contains("Dice Game Results");
            assertThat(message).contains(":one:");
            assertThat(message).contains(":two:");
            assertThat(message).contains(":three:");
            assertThat(message).contains(":four:");
            assertThat(message).contains(":five:");
            assertThat(message).contains("3,750,000");
            assertThat(message).contains("💰");
            assertThat(message).contains("Gold");
        }
    }
}
