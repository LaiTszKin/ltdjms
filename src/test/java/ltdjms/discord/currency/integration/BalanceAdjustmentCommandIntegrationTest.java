package ltdjms.discord.currency.integration;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ltdjms.discord.currency.domain.BalanceView;
import ltdjms.discord.currency.domain.CurrencyTransactionRepository;
import ltdjms.discord.currency.persistence.GuildCurrencyConfigRepository;
import ltdjms.discord.currency.persistence.JdbcCurrencyTransactionRepository;
import ltdjms.discord.currency.persistence.JooqGuildCurrencyConfigRepository;
import ltdjms.discord.currency.persistence.JooqMemberCurrencyAccountRepository;
import ltdjms.discord.currency.persistence.MemberCurrencyAccountRepository;
import ltdjms.discord.currency.persistence.NegativeBalanceException;
import ltdjms.discord.currency.services.BalanceAdjustmentService;
import ltdjms.discord.currency.services.BalanceAdjustmentService.BalanceAdjustmentResult;
import ltdjms.discord.currency.services.CurrencyConfigService;
import ltdjms.discord.currency.services.CurrencyTransactionService;
import ltdjms.discord.currency.services.DefaultBalanceService;
import ltdjms.discord.currency.services.EmojiValidator;
import ltdjms.discord.currency.services.NoOpEmojiValidator;
import ltdjms.discord.shared.cache.CacheKeyGenerator;
import ltdjms.discord.shared.cache.CacheService;
import ltdjms.discord.shared.cache.DefaultCacheKeyGenerator;
import ltdjms.discord.shared.cache.NoOpCacheService;
import ltdjms.discord.shared.events.DomainEventPublisher;

/**
 * Integration tests for balance adjustment commands. Verifies admin balance adjustments and their
 * effect on member balances.
 */
@SuppressWarnings(
    "deprecation") // intentionally exercise deprecated service APIs for backward-compat coverage
class BalanceAdjustmentCommandIntegrationTest extends PostgresIntegrationTestBase {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_USER_ID = 987654321098765432L;

  private BalanceAdjustmentService adjustmentService;
  private DefaultBalanceService balanceService;
  private CurrencyConfigService configService;
  private GuildCurrencyConfigRepository configRepository;
  private MemberCurrencyAccountRepository accountRepository;

  @BeforeEach
  void setUp() {
    configRepository = new JooqGuildCurrencyConfigRepository(dslContext);
    accountRepository = new JooqMemberCurrencyAccountRepository(dslContext);
    CurrencyTransactionRepository transactionRepository =
        new JdbcCurrencyTransactionRepository(dataSource);
    CurrencyTransactionService transactionService =
        new CurrencyTransactionService(transactionRepository);
    DomainEventPublisher eventPublisher = new DomainEventPublisher();

    // Create cache dependencies
    CacheService cacheService =
        NoOpCacheService.getInstance(); // Use no-op cache for integration tests
    CacheKeyGenerator cacheKeyGenerator = new DefaultCacheKeyGenerator();

    adjustmentService =
        new BalanceAdjustmentService(
            accountRepository,
            configRepository,
            transactionService,
            eventPublisher,
            cacheService,
            cacheKeyGenerator);
    balanceService =
        new DefaultBalanceService(
            accountRepository, configRepository, cacheService, cacheKeyGenerator);
    EmojiValidator emojiValidator = new NoOpEmojiValidator();
    configService = new CurrencyConfigService(configRepository, emojiValidator, eventPublisher);
  }

  @Test
  @DisplayName("should credit balance and verify with balance command")
  void shouldCreditBalanceAndVerifyWithBalanceCommand() {
    // Given - initial balance is 0
    BalanceView initialBalance = balanceService.getBalance(TEST_GUILD_ID, TEST_USER_ID);
    assertThat(initialBalance.balance()).isEqualTo(0L);

    // When - admin credits 100
    BalanceAdjustmentResult result =
        adjustmentService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, 100L);

    // Then - result shows adjustment
    assertThat(result.previousBalance()).isEqualTo(0L);
    assertThat(result.newBalance()).isEqualTo(100L);

    // And - balance command shows updated value
    BalanceView updatedBalance = balanceService.getBalance(TEST_GUILD_ID, TEST_USER_ID);
    assertThat(updatedBalance.balance()).isEqualTo(100L);
  }

  @Test
  @DisplayName("should debit balance within limits")
  void shouldDebitBalanceWithinLimits() {
    // Given - member has 200
    adjustmentService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, 200L);

    // When - admin debits 50
    BalanceAdjustmentResult result =
        adjustmentService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, -50L);

    // Then
    assertThat(result.previousBalance()).isEqualTo(200L);
    assertThat(result.newBalance()).isEqualTo(150L);

    // Verify with balance command
    BalanceView balance = balanceService.getBalance(TEST_GUILD_ID, TEST_USER_ID);
    assertThat(balance.balance()).isEqualTo(150L);
  }

  @Test
  @DisplayName("should prevent negative balance")
  void shouldPreventNegativeBalance() {
    // Given - member has 50
    adjustmentService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, 50L);

    // When/Then - debit 100 should fail
    assertThatThrownBy(() -> adjustmentService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, -100L))
        .isInstanceOf(NegativeBalanceException.class);

    // Verify balance unchanged
    BalanceView balance = balanceService.getBalance(TEST_GUILD_ID, TEST_USER_ID);
    assertThat(balance.balance()).isEqualTo(50L);
  }

  @Test
  @DisplayName("should handle multiple adjustments correctly")
  void shouldHandleMultipleAdjustmentsCorrectly() {
    // When - multiple adjustments
    adjustmentService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, 100L);
    adjustmentService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, 50L);
    adjustmentService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, -30L);

    // Then
    BalanceView balance = balanceService.getBalance(TEST_GUILD_ID, TEST_USER_ID);
    assertThat(balance.balance()).isEqualTo(120L); // 100 + 50 - 30
  }

  @Test
  @DisplayName("should isolate adjustments between guilds")
  void shouldIsolateAdjustmentsBetweenGuilds() {
    // Given - two guilds
    long guild1 = TEST_GUILD_ID;
    long guild2 = TEST_GUILD_ID + 1;

    // When - adjust same user in different guilds
    adjustmentService.adjustBalance(guild1, TEST_USER_ID, 100L);
    adjustmentService.adjustBalance(guild2, TEST_USER_ID, 200L);

    // Then - balances are isolated
    assertThat(balanceService.getBalance(guild1, TEST_USER_ID).balance()).isEqualTo(100L);
    assertThat(balanceService.getBalance(guild2, TEST_USER_ID).balance()).isEqualTo(200L);
  }

  @Test
  @DisplayName("should use custom currency config in adjustment result")
  void shouldUseCustomCurrencyConfigInAdjustmentResult() {
    // Given - custom currency config
    configService.updateConfig(TEST_GUILD_ID, "Gold", "💰");

    // When
    BalanceAdjustmentResult result =
        adjustmentService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, 100L);

    // Then
    assertThat(result.currencyName()).isEqualTo("Gold");
    assertThat(result.currencyIcon()).isEqualTo("💰");
  }

  @Test
  @DisplayName("should handle debit to exactly zero")
  void shouldHandleDebitToExactlyZero() {
    // Given - member has 100
    adjustmentService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, 100L);

    // When - debit exactly 100
    BalanceAdjustmentResult result =
        adjustmentService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, -100L);

    // Then
    assertThat(result.newBalance()).isEqualTo(0L);

    // Verify with balance command
    BalanceView balance = balanceService.getBalance(TEST_GUILD_ID, TEST_USER_ID);
    assertThat(balance.balance()).isEqualTo(0L);
  }

  @Test
  @DisplayName("should create account automatically when adjusting non-existent user")
  void shouldCreateAccountAutomaticallyWhenAdjustingNonExistentUser() {
    // Given - no account exists
    assertThat(accountRepository.findByGuildIdAndUserId(TEST_GUILD_ID, TEST_USER_ID)).isEmpty();

    // When - adjust balance
    BalanceAdjustmentResult result =
        adjustmentService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, 100L);

    // Then - account created with adjustment
    assertThat(result.previousBalance()).isEqualTo(0L);
    assertThat(result.newBalance()).isEqualTo(100L);
    assertThat(accountRepository.findByGuildIdAndUserId(TEST_GUILD_ID, TEST_USER_ID)).isPresent();
  }

  // Tests for adjustBalanceTo (adjust mode)

  @Test
  @DisplayName("should adjust balance to target value (increase)")
  void shouldAdjustBalanceToTargetValueIncrease() {
    // Given - member has 100
    adjustmentService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, 100L);

    // When - adjust to 500
    BalanceAdjustmentResult result =
        adjustmentService.adjustBalanceTo(TEST_GUILD_ID, TEST_USER_ID, 500L);

    // Then
    assertThat(result.previousBalance()).isEqualTo(100L);
    assertThat(result.newBalance()).isEqualTo(500L);
    assertThat(result.adjustment()).isEqualTo(400L);

    // Verify with balance command
    BalanceView balance = balanceService.getBalance(TEST_GUILD_ID, TEST_USER_ID);
    assertThat(balance.balance()).isEqualTo(500L);
  }

  @Test
  @DisplayName("should adjust balance to target value (decrease)")
  void shouldAdjustBalanceToTargetValueDecrease() {
    // Given - member has 500
    adjustmentService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, 500L);

    // When - adjust to 200
    BalanceAdjustmentResult result =
        adjustmentService.adjustBalanceTo(TEST_GUILD_ID, TEST_USER_ID, 200L);

    // Then
    assertThat(result.previousBalance()).isEqualTo(500L);
    assertThat(result.newBalance()).isEqualTo(200L);
    assertThat(result.adjustment()).isEqualTo(-300L);

    // Verify with balance command
    BalanceView balance = balanceService.getBalance(TEST_GUILD_ID, TEST_USER_ID);
    assertThat(balance.balance()).isEqualTo(200L);
  }

  @Test
  @DisplayName("should adjust balance to zero")
  void shouldAdjustBalanceToZero() {
    // Given - member has 100
    adjustmentService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, 100L);

    // When - adjust to 0
    BalanceAdjustmentResult result =
        adjustmentService.adjustBalanceTo(TEST_GUILD_ID, TEST_USER_ID, 0L);

    // Then
    assertThat(result.previousBalance()).isEqualTo(100L);
    assertThat(result.newBalance()).isEqualTo(0L);
    assertThat(result.adjustment()).isEqualTo(-100L);

    // Verify with balance command
    BalanceView balance = balanceService.getBalance(TEST_GUILD_ID, TEST_USER_ID);
    assertThat(balance.balance()).isEqualTo(0L);
  }

  @Test
  @DisplayName("should reject negative target balance")
  void shouldRejectNegativeTargetBalance() {
    // Given - member has 100
    adjustmentService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, 100L);

    // When/Then - adjust to -1 should fail
    assertThatThrownBy(() -> adjustmentService.adjustBalanceTo(TEST_GUILD_ID, TEST_USER_ID, -1L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("negative");

    // Verify balance unchanged
    BalanceView balance = balanceService.getBalance(TEST_GUILD_ID, TEST_USER_ID);
    assertThat(balance.balance()).isEqualTo(100L);
  }

  @Test
  @DisplayName("should handle no-op adjustment (same balance)")
  void shouldHandleNoOpAdjustment() {
    // Given - member has 100
    adjustmentService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, 100L);

    // When - adjust to same value
    BalanceAdjustmentResult result =
        adjustmentService.adjustBalanceTo(TEST_GUILD_ID, TEST_USER_ID, 100L);

    // Then
    assertThat(result.previousBalance()).isEqualTo(100L);
    assertThat(result.newBalance()).isEqualTo(100L);
    assertThat(result.adjustment()).isEqualTo(0L);
  }
}
