package ltdjms.discord.gametoken.services;

import static org.assertj.core.api.Assertions.*;

import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ltdjms.discord.currency.domain.CurrencyTransaction;
import ltdjms.discord.currency.services.GameRewardService;
import ltdjms.discord.gametoken.domain.DiceGame1Config;
import ltdjms.discord.gametoken.services.DiceGame1Service.DiceGameResult;

/**
 * Unit tests for DiceGame1Service. Uses a predictable Random implementation and stub
 * GameRewardService to test the game logic in isolation.
 */
class DiceGame1ServiceTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_USER_ID = 987654321098765432L;

  /** A predictable Random that returns values from a predefined sequence. */
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

  /** A stub GameRewardService for testing that tracks balance changes. */
  static class StubGameRewardService extends GameRewardService {
    private long currentBalance = 1000L;
    private int creditCallCount = 0;
    private long lastRewardAmount = 0;
    private CurrencyTransaction.Source lastSource = null;

    public StubGameRewardService() {
      super(null, null, null);
    }

    public void setCurrentBalance(long balance) {
      this.currentBalance = balance;
    }

    @Override
    public long creditReward(
        long guildId, long userId, long amount, CurrencyTransaction.Source source) {
      creditCallCount++;
      lastRewardAmount = amount;
      lastSource = source;

      if (amount > 0) {
        long previousBalance = currentBalance;
        currentBalance += amount;
        return currentBalance;
      }
      return currentBalance;
    }

    public int getCreditCallCount() {
      return creditCallCount;
    }

    public long getLastRewardAmount() {
      return lastRewardAmount;
    }

    public CurrencyTransaction.Source getLastSource() {
      return lastSource;
    }

    public long getCurrentBalance() {
      return currentBalance;
    }
  }

  @Nested
  @DisplayName("Roll dice")
  class RollDice {

    @Test
    @DisplayName("should roll specified number of dice")
    void shouldRollSpecifiedNumberOfDice() {
      // Given - predictable random that returns 0, 1, 2, 3, 4 (resulting in dice values 1, 2, 3, 4,
      // 5)
      PredictableRandom random = new PredictableRandom(List.of(0, 1, 2, 3, 4));
      StubGameRewardService rewardService = new StubGameRewardService();
      DefaultDiceGame1Service service = new DefaultDiceGame1Service(rewardService, random);

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
      StubGameRewardService rewardService = new StubGameRewardService();
      DefaultDiceGame1Service service = new DefaultDiceGame1Service(rewardService, random);

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
      StubGameRewardService rewardService = new StubGameRewardService();
      DefaultDiceGame1Service service = new DefaultDiceGame1Service(rewardService);
      List<Integer> rolls = List.of(1, 2, 3, 4, 5);
      // Expected: 1*250000 + 2*250000 + 3*250000 + 4*250000 + 5*250000 = 15*250000 = 3,750,000

      // When
      long totalReward =
          service.calculateTotalReward(rolls, DiceGame1Config.DEFAULT_REWARD_PER_DICE_VALUE);

      // Then
      assertThat(totalReward).isEqualTo(3_750_000L);
    }

    @Test
    @DisplayName("should calculate max reward when all dice are 6")
    void shouldCalculateMaxReward() {
      // Given
      StubGameRewardService rewardService = new StubGameRewardService();
      DefaultDiceGame1Service service = new DefaultDiceGame1Service(rewardService);
      List<Integer> rolls = List.of(6, 6, 6, 6, 6);
      // Expected: 6*250000 * 5 = 7,500,000

      // When
      long totalReward =
          service.calculateTotalReward(rolls, DiceGame1Config.DEFAULT_REWARD_PER_DICE_VALUE);

      // Then
      assertThat(totalReward).isEqualTo(7_500_000L);
    }

    @Test
    @DisplayName("should calculate min reward when all dice are 1")
    void shouldCalculateMinReward() {
      // Given
      StubGameRewardService rewardService = new StubGameRewardService();
      DefaultDiceGame1Service service = new DefaultDiceGame1Service(rewardService);
      List<Integer> rolls = List.of(1, 1, 1, 1, 1);
      // Expected: 1*250000 * 5 = 1,250,000

      // When
      long totalReward =
          service.calculateTotalReward(rolls, DiceGame1Config.DEFAULT_REWARD_PER_DICE_VALUE);

      // Then
      assertThat(totalReward).isEqualTo(1_250_000L);
    }

    @Test
    @DisplayName("should calculate reward using configured multiplier")
    void shouldCalculateRewardWithConfiguredMultiplier() {
      // Given
      StubGameRewardService rewardService = new StubGameRewardService();
      DefaultDiceGame1Service service = new DefaultDiceGame1Service(rewardService);
      List<Integer> rolls = List.of(1, 2, 3, 4, 5);
      long customMultiplier = 500_000L; // Double the default
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
      StubGameRewardService rewardService = new StubGameRewardService();
      rewardService.setCurrentBalance(1000L);
      DefaultDiceGame1Service service = new DefaultDiceGame1Service(rewardService, random);
      DiceGame1Config config = DiceGame1Config.createDefault(TEST_GUILD_ID);

      // When
      DiceGameResult result = service.play(TEST_GUILD_ID, TEST_USER_ID, config, 5);

      // Then
      assertThat(result.diceRolls()).containsExactly(1, 2, 3, 4, 5);
      assertThat(result.totalReward()).isEqualTo(3_750_000L);
      assertThat(result.previousBalance()).isEqualTo(1000L);

      // Verify reward was credited via GameRewardService
      assertThat(rewardService.getCreditCallCount()).isGreaterThanOrEqualTo(1);
      assertThat(rewardService.getLastRewardAmount()).isEqualTo(3_750_000L);
      assertThat(rewardService.getLastSource())
          .isEqualTo(CurrencyTransaction.Source.DICE_GAME_1_WIN);
    }

    @Test
    @DisplayName("should play game with custom configuration")
    void shouldPlayGameWithCustomConfig() {
      // Given - predictable random returns 0,1,2,3,4 resulting in dice 1,2,3,4,5
      PredictableRandom random = new PredictableRandom(List.of(0, 1, 2, 3, 4));
      StubGameRewardService rewardService = new StubGameRewardService();
      rewardService.setCurrentBalance(1000L);
      DefaultDiceGame1Service service = new DefaultDiceGame1Service(rewardService, random);

      DiceGame1Config config =
          DiceGame1Config.createDefault(TEST_GUILD_ID)
              .withRewardPerDiceValue(100_000L); // Lower than default

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
      StubGameRewardService rewardService = new StubGameRewardService();
      rewardService.setCurrentBalance(0L);
      DefaultDiceGame1Service service = new DefaultDiceGame1Service(rewardService, random);

      DiceGame1Config highRollerConfig =
          DiceGame1Config.createDefault(TEST_GUILD_ID).withRewardPerDiceValue(1_000_000L);

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
      StubGameRewardService rewardService = new StubGameRewardService();
      rewardService.setCurrentBalance(5000L);
      DefaultDiceGame1Service service = new DefaultDiceGame1Service(rewardService, random);

      DiceGame1Config zeroRewardConfig =
          new DiceGame1Config(
              TEST_GUILD_ID, 1L, 10L, 0L, java.time.Instant.now(), java.time.Instant.now());

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
      StubGameRewardService rewardService = new StubGameRewardService();
      rewardService.setCurrentBalance(1000L);
      DefaultDiceGame1Service service = new DefaultDiceGame1Service(rewardService, random);
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
      DiceGameResult result =
          new DiceGameResult(
              TEST_GUILD_ID, TEST_USER_ID, List.of(1, 2, 3, 4, 5), 3_750_000L, 0L, 3_750_000L);

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

  @Nested
  @DisplayName("Performance regression tests")
  class PerformanceRegression {
    private Logger diceGameLogger;
    private Level previousLevel;

    @BeforeEach
    void reduceLoggingNoiseForPerformanceChecks() {
      diceGameLogger = (Logger) LoggerFactory.getLogger(DiceGame1Service.class);
      previousLevel = diceGameLogger.getLevel();
      diceGameLogger.setLevel(Level.WARN);
    }

    @AfterEach
    void restoreLoggingLevel() {
      diceGameLogger.setLevel(previousLevel);
    }

    @Test
    @DisplayName("should complete 1000 games within 100ms")
    void shouldComplete1000GamesWithin100ms() {
      // Given
      StubGameRewardService rewardService = new StubGameRewardService();
      DefaultDiceGame1Service service = new DefaultDiceGame1Service(rewardService);
      DiceGame1Config config = DiceGame1Config.createDefault(TEST_GUILD_ID);

      // When
      long startTime = System.nanoTime();
      for (int i = 0; i < 1000; i++) {
        service.play(TEST_GUILD_ID, TEST_USER_ID, config, 5);
      }
      long endTime = System.nanoTime();

      // Then - should complete within 100ms (100,000,000 ns)
      long durationNs = endTime - startTime;
      assertThat(durationNs).as("1000 games should complete within 100ms").isLessThan(100_000_000L);
    }

    @Test
    @DisplayName("should calculate rewards efficiently for large dice counts")
    void shouldCalculateRewardsEfficientlyForLargeDiceCounts() {
      // Given
      StubGameRewardService rewardService = new StubGameRewardService();
      DefaultDiceGame1Service service = new DefaultDiceGame1Service(rewardService);
      DiceGame1Config config = DiceGame1Config.createDefault(TEST_GUILD_ID);

      // When - play with 100 dice (simulating high-roller scenario)
      long startTime = System.nanoTime();
      for (int i = 0; i < 100; i++) {
        service.play(TEST_GUILD_ID, TEST_USER_ID, config, 100);
      }
      long endTime = System.nanoTime();

      // Then - should complete within 50ms
      long durationNs = endTime - startTime;
      assertThat(durationNs)
          .as("100 games with 100 dice each should complete within 50ms")
          .isLessThan(50_000_000L);
    }
  }
}
