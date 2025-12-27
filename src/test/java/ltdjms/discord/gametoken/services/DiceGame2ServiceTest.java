package ltdjms.discord.gametoken.services;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import ltdjms.discord.currency.domain.CurrencyTransaction;
import ltdjms.discord.currency.domain.MemberCurrencyAccount;
import ltdjms.discord.currency.persistence.MemberCurrencyAccountRepository;
import ltdjms.discord.currency.services.CurrencyTransactionService;
import ltdjms.discord.gametoken.domain.DiceGame2Config;
import ltdjms.discord.gametoken.services.DiceGame2Service.DiceGame2Result;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.events.DomainEvent;
import ltdjms.discord.shared.events.DomainEventPublisher;

/**
 * Unit tests for DiceGame2Service. Uses a predictable Random implementation to avoid mocking
 * restrictions.
 *
 * <p>Reward rules: - Dice count is determined by tokens spent (1 token = 3 dice) - If no straights
 * (length >= 3), total sum × 20,000 - Straights (consecutive increasing, length >= 3): sum of dice
 * in straight × 100,000 - Non-straight dice (excluding triples): sum × 20,000 - Triples (exactly 3
 * consecutive same values): - Sum < 10 (values 1-3): 1,500,000 each - Sum >= 10 (values 4-6):
 * 2,500,000 each - 4+ consecutive same values: NOT a triple, treated as non-straight
 */
class DiceGame2ServiceTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_USER_ID = 987654321098765432L;
  private static final int DEFAULT_DICE_COUNT = 15; // 5 tokens × 3 dice per token

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

  /** A stub transaction service for testing. */
  static class StubCurrencyTransactionService extends CurrencyTransactionService {
    private CurrencyTransaction lastTransaction;
    private int recordCallCount = 0;

    StubCurrencyTransactionService() {
      super(null); // No repository needed for stub
    }

    @Override
    public CurrencyTransaction recordTransaction(
        long guildId,
        long userId,
        long amount,
        long balanceAfter,
        CurrencyTransaction.Source source,
        String description) {
      recordCallCount++;
      lastTransaction =
          new CurrencyTransaction(
              1L, guildId, userId, amount, balanceAfter, source, description, Instant.now());
      return lastTransaction;
    }

    CurrencyTransaction getLastTransaction() {
      return lastTransaction;
    }

    int getRecordCallCount() {
      return recordCallCount;
    }
  }

  /** A stub event publisher for testing. */
  static class StubDomainEventPublisher extends DomainEventPublisher {
    @Override
    public void publish(DomainEvent event) {
      // no-op
    }
  }

  /** A stub repository for testing. */
  static class StubCurrencyRepository implements MemberCurrencyAccountRepository {
    private MemberCurrencyAccount account;
    private int adjustCallCount = 0;

    StubCurrencyRepository(long initialBalance) {
      Instant now = Instant.now();
      this.account =
          new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, initialBalance, now, now);
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
      this.account =
          new MemberCurrencyAccount(
              guildId, userId, account.balance() + amount, account.createdAt(), now);
      return account;
    }

    @Override
    public Result<MemberCurrencyAccount, DomainError> tryAdjustBalance(
        long guildId, long userId, long amount) {
      MemberCurrencyAccount updated = adjustBalance(guildId, userId, amount);
      return Result.ok(updated);
    }

    @Override
    public MemberCurrencyAccount setBalance(long guildId, long userId, long newBalance) {
      Instant now = Instant.now();
      this.account =
          new MemberCurrencyAccount(guildId, userId, newBalance, account.createdAt(), now);
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
  @DisplayName("Basic dice rolling")
  class BasicDiceRolling {

    @Test
    @DisplayName("should roll specified number of dice")
    void shouldRollSpecifiedNumberOfDice() {
      // Given - predictable random that returns 15 values
      List<Integer> randomValues = List.of(0, 1, 2, 3, 4, 5, 0, 1, 2, 3, 4, 5, 0, 1, 2);
      PredictableRandom random = new PredictableRandom(randomValues);
      StubCurrencyRepository repository = new StubCurrencyRepository(0L);
      DiceGame2Service service =
          new DiceGame2Service(
              repository,
              new StubCurrencyTransactionService(),
              new StubDomainEventPublisher(),
              random);

      // When
      List<Integer> rolls = service.rollDice(15);

      // Then
      assertThat(rolls).hasSize(15);
    }

    @Test
    @DisplayName("should convert random values to dice values 1-6")
    void shouldConvertRandomValuesToDiceValues() {
      // Given - random returns 0-5 which should map to dice 1-6
      List<Integer> randomValues = List.of(0, 1, 2, 3, 4, 5, 0, 0, 0, 0, 0, 0, 0, 0, 0);
      PredictableRandom random = new PredictableRandom(randomValues);
      StubCurrencyRepository repository = new StubCurrencyRepository(0L);
      DiceGame2Service service =
          new DiceGame2Service(
              repository,
              new StubCurrencyTransactionService(),
              new StubDomainEventPublisher(),
              random);

      // When
      List<Integer> rolls = service.rollDice(15);

      // Then
      assertThat(rolls.subList(0, 6)).containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    @DisplayName("should roll different number of dice based on parameter")
    void shouldRollDifferentNumberBasedOnParameter() {
      // Given
      List<Integer> randomValues = List.of(0, 1, 2, 3, 4, 5, 0, 1, 2);
      PredictableRandom random = new PredictableRandom(randomValues);
      StubCurrencyRepository repository = new StubCurrencyRepository(0L);
      DiceGame2Service service =
          new DiceGame2Service(
              repository,
              new StubCurrencyTransactionService(),
              new StubDomainEventPublisher(),
              random);

      // When - 3 tokens = 9 dice
      List<Integer> rolls = service.rollDice(9);

      // Then
      assertThat(rolls).hasSize(9);
      assertThat(rolls).containsExactly(1, 2, 3, 4, 5, 6, 1, 2, 3);
    }
  }

  @Nested
  @DisplayName("No straights and no triples - base reward only")
  class NoStraightsNoTriples {

    @Test
    @DisplayName("4.1.1: should calculate total sum × 20,000 when no straights or triples")
    void shouldCalculateBasicRewardWithNoStraightsOrTriples() {
      // Given - rolls: 1,3,5,1,3,5,1,3,5,1,3,5,1,3,6 (no consecutive increasing sequences of 3+)
      // Random values for dice: 0,2,4,0,2,4,0,2,4,0,2,4,0,2,5
      List<Integer> randomValues = List.of(0, 2, 4, 0, 2, 4, 0, 2, 4, 0, 2, 4, 0, 2, 5);
      PredictableRandom random = new PredictableRandom(randomValues);
      StubCurrencyRepository repository = new StubCurrencyRepository(0L);
      StubCurrencyTransactionService transactionService = new StubCurrencyTransactionService();
      DiceGame2Service service =
          new DiceGame2Service(
              repository, transactionService, new StubDomainEventPublisher(), random);
      DiceGame2Config config = DiceGame2Config.createDefault(TEST_GUILD_ID);

      // When
      DiceGame2Result result =
          service.play(TEST_GUILD_ID, TEST_USER_ID, config, DEFAULT_DICE_COUNT);

      // Then - total = 1+3+5+1+3+5+1+3+5+1+3+5+1+3+6 = 46, reward = 46 × 20,000 = 920,000
      assertThat(result.diceRolls()).containsExactly(1, 3, 5, 1, 3, 5, 1, 3, 5, 1, 3, 5, 1, 3, 6);
      assertThat(result.totalReward()).isEqualTo(920_000L);

      // Verify transaction
      assertThat(transactionService.getRecordCallCount()).isEqualTo(1);
      assertThat(transactionService.getLastTransaction().source())
          .isEqualTo(CurrencyTransaction.Source.DICE_GAME_2_WIN);
    }

    @Test
    @DisplayName("should calculate reward for all 1s")
    void shouldCalculateRewardForAllOnes() {
      // Given - rolls: all 1s (no straights, but potential triples)
      // However, 15 consecutive 1s means 5 groups of 3, but since length > 3 in each segment,
      // they don't count as triples. Actually, 1,1,1,1,1... is one long segment of >3 same values.
      // So none count as triples, all are non-straight.
      List<Integer> randomValues = List.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
      PredictableRandom random = new PredictableRandom(randomValues);
      StubCurrencyRepository repository = new StubCurrencyRepository(0L);
      DiceGame2Service service =
          new DiceGame2Service(
              repository,
              new StubCurrencyTransactionService(),
              new StubDomainEventPublisher(),
              random);
      DiceGame2Config config = DiceGame2Config.createDefault(TEST_GUILD_ID);

      // When
      DiceGame2Result result =
          service.play(TEST_GUILD_ID, TEST_USER_ID, config, DEFAULT_DICE_COUNT);

      // Then - all 15 ones form a single group of length 15 (>3), so no triples
      // Total = 15 × 1 = 15, reward = 15 × 20,000 = 300,000
      assertThat(result.totalReward()).isEqualTo(300_000L);
    }
  }

  @Nested
  @DisplayName("Single straight detection and reward")
  class SingleStraight {

    @Test
    @DisplayName("4.1.2: should detect single straight (1,2,3) and calculate correctly")
    void shouldDetectSingleStraight123() {
      // Given - rolls: 1,2,3,6,6,5,4,1,6,5,4,1,6,5,4
      // Straight: 1,2,3 at positions 0-2, sum = 6
      // Non-straight: rest = 6+6+5+4+1+6+5+4+1+6+5+4 = 53
      // Reward: 6 × 100,000 + 53 × 20,000 = 600,000 + 1,060,000 = 1,660,000
      List<Integer> randomValues = List.of(0, 1, 2, 5, 5, 4, 3, 0, 5, 4, 3, 0, 5, 4, 3);
      PredictableRandom random = new PredictableRandom(randomValues);
      StubCurrencyRepository repository = new StubCurrencyRepository(0L);
      DiceGame2Service service =
          new DiceGame2Service(
              repository,
              new StubCurrencyTransactionService(),
              new StubDomainEventPublisher(),
              random);
      DiceGame2Config config = DiceGame2Config.createDefault(TEST_GUILD_ID);

      // When
      DiceGame2Result result =
          service.play(TEST_GUILD_ID, TEST_USER_ID, config, DEFAULT_DICE_COUNT);

      // Then
      assertThat(result.diceRolls()).containsExactly(1, 2, 3, 6, 6, 5, 4, 1, 6, 5, 4, 1, 6, 5, 4);
      assertThat(result.totalReward()).isEqualTo(1_660_000L);
      assertThat(result.straightSegments()).hasSize(1);
      assertThat(result.straightSegments().get(0)).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("should detect longer straight (1,2,3,4,5) and calculate correctly")
    void shouldDetectLongerStraight() {
      // Given - rolls: 1,2,3,4,5,1,1,1,1,1,1,1,1,1,1 (but 1,1,1... is >3 same)
      // Straight: 1,2,3,4,5 at positions 0-4, sum = 15
      // Non-straight: 10 × 1 = 10 (the long run of 1s, >3 same, not a triple)
      // Reward: 15 × 100,000 + 10 × 20,000 = 1,500,000 + 200,000 = 1,700,000
      List<Integer> randomValues = List.of(0, 1, 2, 3, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
      PredictableRandom random = new PredictableRandom(randomValues);
      StubCurrencyRepository repository = new StubCurrencyRepository(0L);
      DiceGame2Service service =
          new DiceGame2Service(
              repository,
              new StubCurrencyTransactionService(),
              new StubDomainEventPublisher(),
              random);
      DiceGame2Config config = DiceGame2Config.createDefault(TEST_GUILD_ID);

      // When
      DiceGame2Result result =
          service.play(TEST_GUILD_ID, TEST_USER_ID, config, DEFAULT_DICE_COUNT);

      // Then
      assertThat(result.totalReward()).isEqualTo(1_700_000L);
    }

    @Test
    @DisplayName("should detect straight (4,5,6)")
    void shouldDetectStraight456() {
      // Given - rolls: 4,5,6,1,3,5,1,3,5,1,3,5,1,3,5
      // Straight: 4,5,6 at positions 0-2, sum = 15
      // Non-straight: 1+3+5+1+3+5+1+3+5+1+3+5 = 36
      // Reward: 15 × 100,000 + 36 × 20,000 = 1,500,000 + 720,000 = 2,220,000
      List<Integer> randomValues = List.of(3, 4, 5, 0, 2, 4, 0, 2, 4, 0, 2, 4, 0, 2, 4);
      PredictableRandom random = new PredictableRandom(randomValues);
      StubCurrencyRepository repository = new StubCurrencyRepository(0L);
      DiceGame2Service service =
          new DiceGame2Service(
              repository,
              new StubCurrencyTransactionService(),
              new StubDomainEventPublisher(),
              random);
      DiceGame2Config config = DiceGame2Config.createDefault(TEST_GUILD_ID);

      // When
      DiceGame2Result result =
          service.play(TEST_GUILD_ID, TEST_USER_ID, config, DEFAULT_DICE_COUNT);

      // Then
      assertThat(result.totalReward()).isEqualTo(2_220_000L);
    }
  }

  @Nested
  @DisplayName("Multiple straights")
  class MultipleStraights {

    @Test
    @DisplayName("4.1.3: should detect multiple straights and calculate correctly")
    void shouldDetectMultipleStraights() {
      // Given - rolls: 1,2,3,6,4,5,6,1,3,5,1,3,5,2,4
      // Straights: 1,2,3 (sum=6), 4,5,6 (sum=15)
      // Non-straight: 6+1+3+5+1+3+5+2+4 = 30
      // Reward: (6+15) × 100,000 + 30 × 20,000 = 2,100,000 + 600,000 = 2,700,000
      List<Integer> randomValues = List.of(0, 1, 2, 5, 3, 4, 5, 0, 2, 4, 0, 2, 4, 1, 3);
      PredictableRandom random = new PredictableRandom(randomValues);
      StubCurrencyRepository repository = new StubCurrencyRepository(0L);
      DiceGame2Service service =
          new DiceGame2Service(
              repository,
              new StubCurrencyTransactionService(),
              new StubDomainEventPublisher(),
              random);
      DiceGame2Config config = DiceGame2Config.createDefault(TEST_GUILD_ID);

      // When
      DiceGame2Result result =
          service.play(TEST_GUILD_ID, TEST_USER_ID, config, DEFAULT_DICE_COUNT);

      // Then
      assertThat(result.totalReward()).isEqualTo(2_700_000L);
      assertThat(result.straightSegments()).hasSize(2);
    }
  }

  @Nested
  @DisplayName("Triple (exactly 3 consecutive same values) detection")
  class TripleDetection {

    @Test
    @DisplayName("4.1.4: should detect single triple (1,1,1) with bonus 1,500,000")
    void shouldDetectSingleTriple111() {
      // Given - rolls: 1,1,1,6,5,4,3,6,5,4,3,6,5,4,3
      // Triple: 1,1,1 at positions 0-2, sum = 3 < 10 → 1,500,000
      // Non-straight (excluding triple): 6+5+4+3+6+5+4+3+6+5+4+3 = 54
      // Reward: 54 × 20,000 + 1,500,000 = 1,080,000 + 1,500,000 = 2,580,000
      List<Integer> randomValues = List.of(0, 0, 0, 5, 4, 3, 2, 5, 4, 3, 2, 5, 4, 3, 2);
      PredictableRandom random = new PredictableRandom(randomValues);
      StubCurrencyRepository repository = new StubCurrencyRepository(0L);
      DiceGame2Service service =
          new DiceGame2Service(
              repository,
              new StubCurrencyTransactionService(),
              new StubDomainEventPublisher(),
              random);
      DiceGame2Config config = DiceGame2Config.createDefault(TEST_GUILD_ID);

      // When
      DiceGame2Result result =
          service.play(TEST_GUILD_ID, TEST_USER_ID, config, DEFAULT_DICE_COUNT);

      // Then
      assertThat(result.diceRolls()).containsExactly(1, 1, 1, 6, 5, 4, 3, 6, 5, 4, 3, 6, 5, 4, 3);
      assertThat(result.totalReward()).isEqualTo(2_580_000L);
      assertThat(result.tripleSegments()).hasSize(1);
    }

    @Test
    @DisplayName("should detect single triple (5,5,5) with bonus 2,500,000")
    void shouldDetectSingleTriple555() {
      // Given - rolls: 5,5,5,1,3,6,1,3,6,1,3,6,1,3,6
      // Triple: 5,5,5 at positions 0-2, sum = 15 >= 10 → 2,500,000
      // Non-straight: 1+3+6+1+3+6+1+3+6+1+3+6 = 40
      // Reward: 40 × 20,000 + 2,500,000 = 800,000 + 2,500,000 = 3,300,000
      List<Integer> randomValues = List.of(4, 4, 4, 0, 2, 5, 0, 2, 5, 0, 2, 5, 0, 2, 5);
      PredictableRandom random = new PredictableRandom(randomValues);
      StubCurrencyRepository repository = new StubCurrencyRepository(0L);
      DiceGame2Service service =
          new DiceGame2Service(
              repository,
              new StubCurrencyTransactionService(),
              new StubDomainEventPublisher(),
              random);
      DiceGame2Config config = DiceGame2Config.createDefault(TEST_GUILD_ID);

      // When
      DiceGame2Result result =
          service.play(TEST_GUILD_ID, TEST_USER_ID, config, DEFAULT_DICE_COUNT);

      // Then
      assertThat(result.totalReward()).isEqualTo(3_300_000L);
    }

    @Test
    @DisplayName("4.1.5: should detect multiple triples and sum their bonuses")
    void shouldDetectMultipleTriples() {
      // Given - rolls: 1,1,1,6,2,2,2,6,3,6,4,6,5,6,6
      // Triples: 1,1,1 (sum=3<10 → 1,500,000), 2,2,2 (sum=6<10 → 1,500,000)
      // Non-straight: 6+6+3+6+4+6+5+6+6 = 48
      // Reward: 48 × 20,000 + 1,500,000 + 1,500,000 = 960,000 + 3,000,000 = 3,960,000
      List<Integer> randomValues = List.of(0, 0, 0, 5, 1, 1, 1, 5, 2, 5, 3, 5, 4, 5, 5);
      PredictableRandom random = new PredictableRandom(randomValues);
      StubCurrencyRepository repository = new StubCurrencyRepository(0L);
      DiceGame2Service service =
          new DiceGame2Service(
              repository,
              new StubCurrencyTransactionService(),
              new StubDomainEventPublisher(),
              random);
      DiceGame2Config config = DiceGame2Config.createDefault(TEST_GUILD_ID);

      // When
      DiceGame2Result result =
          service.play(TEST_GUILD_ID, TEST_USER_ID, config, DEFAULT_DICE_COUNT);

      // Then
      assertThat(result.totalReward()).isEqualTo(3_960_000L);
      assertThat(result.tripleSegments()).hasSize(2);
    }
  }

  @Nested
  @DisplayName("Four or more consecutive same values (NOT a triple)")
  class FourOrMoreConsecutive {

    @Test
    @DisplayName("4.1.6: four consecutive same values should NOT be a triple")
    void fourConsecutiveShouldNotBeTriple() {
      // Given - rolls: 1,1,1,1,3,5,3,5,3,5,3,5,3,5,3
      List<Integer> randomValues = List.of(0, 0, 0, 0, 2, 4, 2, 4, 2, 4, 2, 4, 2, 4, 2);
      PredictableRandom random = new PredictableRandom(randomValues);
      StubCurrencyRepository repository = new StubCurrencyRepository(0L);
      DiceGame2Service service =
          new DiceGame2Service(
              repository,
              new StubCurrencyTransactionService(),
              new StubDomainEventPublisher(),
              random);
      DiceGame2Config config = DiceGame2Config.createDefault(TEST_GUILD_ID);

      // When
      DiceGame2Result result =
          service.play(TEST_GUILD_ID, TEST_USER_ID, config, DEFAULT_DICE_COUNT);

      // Then - rolls: 1,1,1,1,3,5,3,5,3,5,3,5,3,5,3
      // 1,1,1,1 is length 4, not a triple
      // No straights
      // Total sum = 4×1 + 5×3 + 4×5 + 2×3 = 4 + 15 + 20 + 6 = ... let me recount
      // 1+1+1+1+3+5+3+5+3+5+3+5+3+5+3 = 4 + 43 = 47
      // Reward = 47 × 20,000 = 940,000
      assertThat(result.tripleSegments()).isEmpty();
      assertThat(result.totalReward()).isEqualTo(940_000L);
    }

    @Test
    @DisplayName("five consecutive same values should NOT be a triple")
    void fiveConsecutiveShouldNotBeTriple() {
      // Given - rolls: 2,2,2,2,2,3,5,3,5,3,5,3,5,3,5
      List<Integer> randomValues = List.of(1, 1, 1, 1, 1, 2, 4, 2, 4, 2, 4, 2, 4, 2, 4);
      PredictableRandom random = new PredictableRandom(randomValues);
      StubCurrencyRepository repository = new StubCurrencyRepository(0L);
      DiceGame2Service service =
          new DiceGame2Service(
              repository,
              new StubCurrencyTransactionService(),
              new StubDomainEventPublisher(),
              random);
      DiceGame2Config config = DiceGame2Config.createDefault(TEST_GUILD_ID);

      // When
      DiceGame2Result result =
          service.play(TEST_GUILD_ID, TEST_USER_ID, config, DEFAULT_DICE_COUNT);

      // Then - no triples (5 consecutive 2s is >3)
      // Total = 2×5 + 3×5 + 5×5 = 10+15+25 = 50
      // Reward = 50 × 20,000 = 1,000,000
      assertThat(result.tripleSegments()).isEmpty();
      assertThat(result.totalReward()).isEqualTo(1_000_000L);
    }
  }

  @Nested
  @DisplayName("Comprehensive scenarios")
  class ComprehensiveScenarios {

    @Test
    @DisplayName("4.1.7: comprehensive case with straights and triples")
    void comprehensiveCaseWithStraightsAndTriples() {
      // Given - rolls: 1,2,3,4,5,6,1,1,1,2,2,2,1,3,6
      // Straight: 1,2,3,4,5,6 at positions 0-5, sum = 21
      // Triples: 1,1,1 at positions 6-8 (sum=3<10 → 1,500,000)
      //          2,2,2 at positions 9-11 (sum=6<10 → 1,500,000)
      // Non-straight (excluding straights and triples): 1+3+6 = 10
      // Reward: 21 × 100,000 + 10 × 20,000 + 1,500,000 + 1,500,000
      //       = 2,100,000 + 200,000 + 3,000,000 = 5,300,000
      List<Integer> randomValues = List.of(0, 1, 2, 3, 4, 5, 0, 0, 0, 1, 1, 1, 0, 2, 5);
      PredictableRandom random = new PredictableRandom(randomValues);
      StubCurrencyRepository repository = new StubCurrencyRepository(0L);
      DiceGame2Service service =
          new DiceGame2Service(
              repository,
              new StubCurrencyTransactionService(),
              new StubDomainEventPublisher(),
              random);
      DiceGame2Config config = DiceGame2Config.createDefault(TEST_GUILD_ID);

      // When
      DiceGame2Result result =
          service.play(TEST_GUILD_ID, TEST_USER_ID, config, DEFAULT_DICE_COUNT);

      // Then
      assertThat(result.diceRolls()).containsExactly(1, 2, 3, 4, 5, 6, 1, 1, 1, 2, 2, 2, 1, 3, 6);
      assertThat(result.totalReward()).isEqualTo(5_300_000L);
      assertThat(result.straightSegments()).hasSize(1);
      assertThat(result.tripleSegments()).hasSize(2);
    }

    @Test
    @DisplayName("should handle straight followed immediately by triple")
    void shouldHandleStraightFollowedByTriple() {
      // Given - rolls: 1,2,3,5,5,5,6,5,4,6,5,4,6,5,4
      List<Integer> randomValues = List.of(0, 1, 2, 4, 4, 4, 5, 4, 3, 5, 4, 3, 5, 4, 3);
      PredictableRandom random = new PredictableRandom(randomValues);
      StubCurrencyRepository repository = new StubCurrencyRepository(0L);
      DiceGame2Service service =
          new DiceGame2Service(
              repository,
              new StubCurrencyTransactionService(),
              new StubDomainEventPublisher(),
              random);
      DiceGame2Config config = DiceGame2Config.createDefault(TEST_GUILD_ID);

      // When
      DiceGame2Result result =
          service.play(TEST_GUILD_ID, TEST_USER_ID, config, DEFAULT_DICE_COUNT);

      // Then - rolls: 1,2,3,5,5,5,6,5,4,6,5,4,6,5,4
      // Straight: 1,2,3 (sum=6)
      // Triple: 5,5,5 at indices 3-5 (sum=15>=10 → 2,500,000)
      // Non-straight: 6+5+4+6+5+4+6+5+4 = 45
      // Reward: 6 × 100,000 + 45 × 20,000 + 2,500,000 = 600,000 + 900,000 + 2,500,000 = 4,000,000
      assertThat(result.totalReward()).isEqualTo(4_000_000L);
    }

    @Test
    @DisplayName("should handle mixed scenario with high and low triples")
    void shouldHandleMixedTriples() {
      // Given - rolls: 1,1,1,3,5,6,6,6,3,5,1,3,5,1,3
      // Triple: 1,1,1 at indices 0-2 (sum=3<10 → 1,500,000)
      // Triple: 6,6,6 at indices 5-7 (sum=18>=10 → 2,500,000)
      // Non-straight: 3+5+3+5+1+3+5+1+3 = 29
      // Reward: 29 × 20,000 + 1,500,000 + 2,500,000 = 580,000 + 4,000,000 = 4,580,000
      List<Integer> randomValues = List.of(0, 0, 0, 2, 4, 5, 5, 5, 2, 4, 0, 2, 4, 0, 2);
      PredictableRandom random = new PredictableRandom(randomValues);
      StubCurrencyRepository repository = new StubCurrencyRepository(0L);
      DiceGame2Service service =
          new DiceGame2Service(
              repository,
              new StubCurrencyTransactionService(),
              new StubDomainEventPublisher(),
              random);
      DiceGame2Config config = DiceGame2Config.createDefault(TEST_GUILD_ID);

      // When
      DiceGame2Result result =
          service.play(TEST_GUILD_ID, TEST_USER_ID, config, DEFAULT_DICE_COUNT);

      // Then
      assertThat(result.totalReward()).isEqualTo(4_580_000L);
      assertThat(result.tripleSegments()).hasSize(2);
    }
  }

  @Nested
  @DisplayName("Edge cases")
  class EdgeCases {

    @Test
    @DisplayName("should handle all 6s (no straights, one long run)")
    void shouldHandleAllSixes() {
      // Given - all 6s
      List<Integer> randomValues = List.of(5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5);
      PredictableRandom random = new PredictableRandom(randomValues);
      StubCurrencyRepository repository = new StubCurrencyRepository(0L);
      DiceGame2Service service =
          new DiceGame2Service(
              repository,
              new StubCurrencyTransactionService(),
              new StubDomainEventPublisher(),
              random);
      DiceGame2Config config = DiceGame2Config.createDefault(TEST_GUILD_ID);

      // When
      DiceGame2Result result =
          service.play(TEST_GUILD_ID, TEST_USER_ID, config, DEFAULT_DICE_COUNT);

      // Then - 15 consecutive 6s is length 15 (>3), not a triple
      // Total = 15 × 6 = 90, reward = 90 × 20,000 = 1,800,000
      assertThat(result.tripleSegments()).isEmpty();
      assertThat(result.totalReward()).isEqualTo(1_800_000L);
    }

    @Test
    @DisplayName("should handle maximum possible reward scenario")
    void shouldHandleMaximumReward() {
      // Given - rolls: 1,2,3,4,5,6,6,6,6,6,6,6,6,6,6 (straight + long run of 6s)
      // Straight: 1,2,3,4,5,6 at indices 0-5, sum = 21
      // 6,6,6,6,6,6,6,6,6 at indices 6-14 is length 9 (>3), not a triple
      // Non-straight: 9 × 6 = 54
      // Reward: 21 × 100,000 + 54 × 20,000 = 2,100,000 + 1,080,000 = 3,180,000
      List<Integer> randomValues = List.of(0, 1, 2, 3, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5);
      PredictableRandom random = new PredictableRandom(randomValues);
      StubCurrencyRepository repository = new StubCurrencyRepository(0L);
      DiceGame2Service service =
          new DiceGame2Service(
              repository,
              new StubCurrencyTransactionService(),
              new StubDomainEventPublisher(),
              random);
      DiceGame2Config config = DiceGame2Config.createDefault(TEST_GUILD_ID);

      // When
      DiceGame2Result result =
          service.play(TEST_GUILD_ID, TEST_USER_ID, config, DEFAULT_DICE_COUNT);

      // Then
      assertThat(result.totalReward()).isEqualTo(3_180_000L);
    }

    @Test
    @DisplayName("triple boundary: exactly 3 same values triggers triple")
    void tripleBoundaryExactlyThree() {
      // Given - rolls: 4,4,4,1,3,5,1,3,5,1,3,5,1,3,5
      // Triple: 4,4,4 at indices 0-2 (sum=12>=10 → 2,500,000)
      // Non-straight: 1+3+5+1+3+5+1+3+5+1+3+5 = 36
      // Reward: 36 × 20,000 + 2,500,000 = 720,000 + 2,500,000 = 3,220,000
      List<Integer> randomValues = List.of(3, 3, 3, 0, 2, 4, 0, 2, 4, 0, 2, 4, 0, 2, 4);
      PredictableRandom random = new PredictableRandom(randomValues);
      StubCurrencyRepository repository = new StubCurrencyRepository(0L);
      DiceGame2Service service =
          new DiceGame2Service(
              repository,
              new StubCurrencyTransactionService(),
              new StubDomainEventPublisher(),
              random);
      DiceGame2Config config = DiceGame2Config.createDefault(TEST_GUILD_ID);

      // When
      DiceGame2Result result =
          service.play(TEST_GUILD_ID, TEST_USER_ID, config, DEFAULT_DICE_COUNT);

      // Then
      assertThat(result.tripleSegments()).hasSize(1);
      assertThat(result.totalReward()).isEqualTo(3_220_000L);
    }
  }

  @Nested
  @DisplayName("Variable dice count")
  class VariableDiceCount {

    @Test
    @DisplayName("should roll 9 dice for 3 tokens (1 token = 3 dice)")
    void shouldRollNineDiceForThreeTokens() {
      // Given
      List<Integer> randomValues = List.of(0, 1, 2, 3, 4, 5, 0, 1, 2);
      PredictableRandom random = new PredictableRandom(randomValues);
      StubCurrencyRepository repository = new StubCurrencyRepository(0L);
      DiceGame2Service service =
          new DiceGame2Service(
              repository,
              new StubCurrencyTransactionService(),
              new StubDomainEventPublisher(),
              random);
      DiceGame2Config config = DiceGame2Config.createDefault(TEST_GUILD_ID);

      // When - 3 tokens = 9 dice
      DiceGame2Result result = service.play(TEST_GUILD_ID, TEST_USER_ID, config, 9);

      // Then
      assertThat(result.diceRolls()).hasSize(9);
      assertThat(result.diceRolls()).containsExactly(1, 2, 3, 4, 5, 6, 1, 2, 3);
    }

    @Test
    @DisplayName("should calculate rewards correctly with variable dice count")
    void shouldCalculateRewardsWithVariableDiceCount() {
      // Given - 6 dice (2 tokens)
      List<Integer> randomValues = List.of(0, 1, 2, 3, 4, 5);
      PredictableRandom random = new PredictableRandom(randomValues);
      StubCurrencyRepository repository = new StubCurrencyRepository(0L);
      DiceGame2Service service =
          new DiceGame2Service(
              repository,
              new StubCurrencyTransactionService(),
              new StubDomainEventPublisher(),
              random);
      DiceGame2Config config = DiceGame2Config.createDefault(TEST_GUILD_ID);

      // When
      DiceGame2Result result = service.play(TEST_GUILD_ID, TEST_USER_ID, config, 6);

      // Then - straight 1,2,3,4,5,6 (sum=21)
      // Reward: 21 × 100,000 = 2,100,000
      assertThat(result.diceRolls()).containsExactly(1, 2, 3, 4, 5, 6);
      assertThat(result.totalReward()).isEqualTo(2_100_000L);
      assertThat(result.straightSegments()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("Result formatting")
  class ResultFormatting {

    @Test
    @DisplayName("should format game result message correctly")
    void shouldFormatGameResultMessageCorrectly() {
      // Given
      DiceGame2Result result =
          new DiceGame2Result(
              TEST_GUILD_ID,
              TEST_USER_ID,
              List.of(1, 2, 3, 4, 5, 6, 1, 1, 1, 2, 2, 2, 1, 3, 6),
              5_300_000L,
              0L,
              5_300_000L,
              List.of(List.of(1, 2, 3, 4, 5, 6)),
              List.of(List.of(1, 1, 1), List.of(2, 2, 2)),
              2_100_000L, // straight reward
              200_000L, // non-straight reward
              3_000_000L // triple reward
              );

      // When
      String message = result.formatMessage("💰", "Gold");

      // Then
      assertThat(message).contains("Dice Game 2 Results");
      assertThat(message).contains(":one:");
      assertThat(message).contains(":six:");
      assertThat(message).contains("5,300,000");
      assertThat(message).contains("💰");
      assertThat(message).contains("Gold");
    }
  }

  @Nested
  @DisplayName("Currency account integration")
  class CurrencyIntegration {

    @Test
    @DisplayName("should update currency balance after game")
    void shouldUpdateCurrencyBalance() {
      // Given
      List<Integer> randomValues = List.of(0, 2, 4, 0, 2, 4, 0, 2, 4, 0, 2, 4, 0, 2, 5);
      PredictableRandom random = new PredictableRandom(randomValues);
      StubCurrencyRepository repository = new StubCurrencyRepository(1_000_000L);
      DiceGame2Service service =
          new DiceGame2Service(
              repository,
              new StubCurrencyTransactionService(),
              new StubDomainEventPublisher(),
              random);
      DiceGame2Config config = DiceGame2Config.createDefault(TEST_GUILD_ID);

      // When
      DiceGame2Result result =
          service.play(TEST_GUILD_ID, TEST_USER_ID, config, DEFAULT_DICE_COUNT);

      // Then
      assertThat(result.previousBalance()).isEqualTo(1_000_000L);
      assertThat(result.newBalance()).isEqualTo(1_000_000L + result.totalReward());
    }
  }

  @Nested
  @DisplayName("Performance regression tests")
  class PerformanceRegression {

    @Test
    @DisplayName("should complete 500 games within 200ms")
    void shouldComplete500GamesWithin200ms() {
      // Given
      StubCurrencyRepository repository = new StubCurrencyRepository(0L);
      DiceGame2Service service =
          new DiceGame2Service(
              repository, new StubCurrencyTransactionService(), new StubDomainEventPublisher());
      DiceGame2Config config = DiceGame2Config.createDefault(TEST_GUILD_ID);

      // When
      long startTime = System.nanoTime();
      for (int i = 0; i < 500; i++) {
        service.play(TEST_GUILD_ID, TEST_USER_ID, config, DEFAULT_DICE_COUNT);
      }
      long endTime = System.nanoTime();

      // Then - should complete within 200ms (200,000,000 ns)
      long durationNs = endTime - startTime;
      assertThat(durationNs).as("500 games should complete within 200ms").isLessThan(200_000_000L);
    }

    @Test
    @DisplayName("should analyze rewards efficiently for large dice counts")
    void shouldAnalyzeRewardsEfficientlyForLargeDiceCounts() {
      // Given
      StubCurrencyRepository repository = new StubCurrencyRepository(0L);
      DiceGame2Service service =
          new DiceGame2Service(
              repository, new StubCurrencyTransactionService(), new StubDomainEventPublisher());
      DiceGame2Config config = DiceGame2Config.createDefault(TEST_GUILD_ID);

      // When - play with 60 dice (simulating 20 tokens spent)
      long startTime = System.nanoTime();
      for (int i = 0; i < 100; i++) {
        service.play(TEST_GUILD_ID, TEST_USER_ID, config, 60);
      }
      long endTime = System.nanoTime();

      // Then - should complete within 100ms
      long durationNs = endTime - startTime;
      assertThat(durationNs)
          .as("100 games with 60 dice each should complete within 100ms")
          .isLessThan(100_000_000L);
    }
  }
}
