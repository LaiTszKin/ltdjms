package ltdjms.discord.currency.integration;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import ltdjms.discord.currency.domain.BalanceView;
import ltdjms.discord.currency.domain.CurrencyTransactionRepository;
import ltdjms.discord.currency.domain.GuildCurrencyConfig;
import ltdjms.discord.currency.persistence.GuildCurrencyConfigRepository;
import ltdjms.discord.currency.persistence.JdbcCurrencyTransactionRepository;
import ltdjms.discord.currency.persistence.JooqGuildCurrencyConfigRepository;
import ltdjms.discord.currency.persistence.JooqMemberCurrencyAccountRepository;
import ltdjms.discord.currency.persistence.MemberCurrencyAccountRepository;
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
 * Integration tests for bot restart and reconnection scenarios. Verifies that balances and
 * configurations are preserved across service restarts and that no partial or duplicated
 * adjustments occur.
 */
@SuppressWarnings(
    "deprecation") // uses deprecated balance/config APIs to ensure backward-compatible behaviour
class BotRestartIntegrationTest extends PostgresIntegrationTestBase {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_USER_ID = 987654321098765432L;

  private BalanceAdjustmentService createAdjustmentService(
      MemberCurrencyAccountRepository accountRepo, GuildCurrencyConfigRepository configRepo) {
    CurrencyTransactionRepository transactionRepo =
        new JdbcCurrencyTransactionRepository(dataSource);
    CurrencyTransactionService transactionService = new CurrencyTransactionService(transactionRepo);
    DomainEventPublisher eventPublisher = new DomainEventPublisher();
    CacheService cacheService = NoOpCacheService.getInstance();
    CacheKeyGenerator cacheKeyGenerator = new DefaultCacheKeyGenerator();
    return new BalanceAdjustmentService(
        accountRepo,
        configRepo,
        transactionService,
        eventPublisher,
        cacheService,
        cacheKeyGenerator);
  }

  // ============================================================
  // Balance Preservation Tests
  // ============================================================

  @Nested
  @DisplayName("Balance Preservation Across Restart Tests")
  class BalancePreservationTests {

    @Test
    @DisplayName("should preserve balances after simulated restart")
    void shouldPreserveBalancesAfterSimulatedRestart() {
      // Given - set up initial state with services
      GuildCurrencyConfigRepository configRepo1 = new JooqGuildCurrencyConfigRepository(dslContext);
      MemberCurrencyAccountRepository accountRepo1 =
          new JooqMemberCurrencyAccountRepository(dslContext);
      DefaultBalanceService balanceService1 =
          new DefaultBalanceService(
              accountRepo1,
              configRepo1,
              NoOpCacheService.getInstance(),
              new DefaultCacheKeyGenerator());
      BalanceAdjustmentService adjustmentService1 =
          createAdjustmentService(accountRepo1, configRepo1);

      // Create account and set balance
      balanceService1.getBalance(TEST_GUILD_ID, TEST_USER_ID); // Creates account
      adjustmentService1.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, 500L);

      // Verify initial balance
      BalanceView initialBalance = balanceService1.getBalance(TEST_GUILD_ID, TEST_USER_ID);
      assertThat(initialBalance.balance()).isEqualTo(500L);

      // When - simulate restart by creating new service instances (new connections)
      GuildCurrencyConfigRepository configRepo2 = new JooqGuildCurrencyConfigRepository(dslContext);
      MemberCurrencyAccountRepository accountRepo2 =
          new JooqMemberCurrencyAccountRepository(dslContext);
      DefaultBalanceService balanceService2 =
          new DefaultBalanceService(
              accountRepo2,
              configRepo2,
              NoOpCacheService.getInstance(),
              new DefaultCacheKeyGenerator());

      // Then - balance should be preserved
      BalanceView preservedBalance = balanceService2.getBalance(TEST_GUILD_ID, TEST_USER_ID);
      assertThat(preservedBalance.balance()).isEqualTo(500L);
    }

    @Test
    @DisplayName("should preserve balances for multiple users across restart")
    void shouldPreserveBalancesForMultipleUsersAcrossRestart() {
      // Given - multiple users with different balances
      long user1 = TEST_USER_ID;
      long user2 = TEST_USER_ID + 1;
      long user3 = TEST_USER_ID + 2;

      GuildCurrencyConfigRepository configRepo = new JooqGuildCurrencyConfigRepository(dslContext);
      MemberCurrencyAccountRepository accountRepo =
          new JooqMemberCurrencyAccountRepository(dslContext);
      BalanceAdjustmentService adjustmentService = createAdjustmentService(accountRepo, configRepo);

      adjustmentService.adjustBalance(TEST_GUILD_ID, user1, 100L);
      adjustmentService.adjustBalance(TEST_GUILD_ID, user2, 250L);
      adjustmentService.adjustBalance(TEST_GUILD_ID, user3, 750L);

      // When - simulate restart
      GuildCurrencyConfigRepository newConfigRepo =
          new JooqGuildCurrencyConfigRepository(dslContext);
      MemberCurrencyAccountRepository newAccountRepo =
          new JooqMemberCurrencyAccountRepository(dslContext);
      DefaultBalanceService newBalanceService =
          new DefaultBalanceService(
              newAccountRepo,
              newConfigRepo,
              NoOpCacheService.getInstance(),
              new DefaultCacheKeyGenerator());

      // Then - all balances preserved
      assertThat(newBalanceService.getBalance(TEST_GUILD_ID, user1).balance()).isEqualTo(100L);
      assertThat(newBalanceService.getBalance(TEST_GUILD_ID, user2).balance()).isEqualTo(250L);
      assertThat(newBalanceService.getBalance(TEST_GUILD_ID, user3).balance()).isEqualTo(750L);
    }

    @Test
    @DisplayName("should preserve balances across multiple guilds after restart")
    void shouldPreserveBalancesAcrossMultipleGuildsAfterRestart() {
      // Given
      long guild1 = TEST_GUILD_ID;
      long guild2 = TEST_GUILD_ID + 1;

      GuildCurrencyConfigRepository configRepo = new JooqGuildCurrencyConfigRepository(dslContext);
      MemberCurrencyAccountRepository accountRepo =
          new JooqMemberCurrencyAccountRepository(dslContext);
      BalanceAdjustmentService adjustmentService = createAdjustmentService(accountRepo, configRepo);

      adjustmentService.adjustBalance(guild1, TEST_USER_ID, 300L);
      adjustmentService.adjustBalance(guild2, TEST_USER_ID, 600L);

      // When - simulate restart
      GuildCurrencyConfigRepository newConfigRepo =
          new JooqGuildCurrencyConfigRepository(dslContext);
      MemberCurrencyAccountRepository newAccountRepo =
          new JooqMemberCurrencyAccountRepository(dslContext);
      DefaultBalanceService newBalanceService =
          new DefaultBalanceService(
              newAccountRepo,
              newConfigRepo,
              NoOpCacheService.getInstance(),
              new DefaultCacheKeyGenerator());

      // Then
      assertThat(newBalanceService.getBalance(guild1, TEST_USER_ID).balance()).isEqualTo(300L);
      assertThat(newBalanceService.getBalance(guild2, TEST_USER_ID).balance()).isEqualTo(600L);
    }
  }

  // ============================================================
  // Configuration Preservation Tests
  // ============================================================

  @Nested
  @DisplayName("Configuration Preservation Across Restart Tests")
  class ConfigurationPreservationTests {

    @Test
    @DisplayName("should preserve currency configuration after restart")
    void shouldPreserveCurrencyConfigurationAfterRestart() {
      // Given
      GuildCurrencyConfigRepository configRepo = new JooqGuildCurrencyConfigRepository(dslContext);
      EmojiValidator emojiValidator = new NoOpEmojiValidator();
      DomainEventPublisher eventPublisher = new DomainEventPublisher();
      CurrencyConfigService configService =
          new CurrencyConfigService(configRepo, emojiValidator, eventPublisher);

      configService.updateConfig(TEST_GUILD_ID, "龍幣", "🐉");

      // When - simulate restart
      GuildCurrencyConfigRepository newConfigRepo =
          new JooqGuildCurrencyConfigRepository(dslContext);
      DomainEventPublisher newEventPublisher = new DomainEventPublisher();
      CurrencyConfigService newConfigService =
          new CurrencyConfigService(newConfigRepo, emojiValidator, newEventPublisher);

      // Then
      GuildCurrencyConfig preserved = newConfigService.getConfig(TEST_GUILD_ID);
      assertThat(preserved.currencyName()).isEqualTo("龍幣");
      assertThat(preserved.currencyIcon()).isEqualTo("🐉");
    }

    @Test
    @DisplayName("should preserve configuration for multiple guilds after restart")
    void shouldPreserveConfigurationForMultipleGuildsAfterRestart() {
      // Given
      long guild1 = TEST_GUILD_ID;
      long guild2 = TEST_GUILD_ID + 1;

      GuildCurrencyConfigRepository configRepo = new JooqGuildCurrencyConfigRepository(dslContext);
      EmojiValidator emojiValidator = new NoOpEmojiValidator();
      DomainEventPublisher eventPublisher = new DomainEventPublisher();
      CurrencyConfigService configService =
          new CurrencyConfigService(configRepo, emojiValidator, eventPublisher);

      configService.updateConfig(guild1, "金幣", "💰");
      configService.updateConfig(guild2, "星星", "⭐");

      // When - simulate restart
      GuildCurrencyConfigRepository newConfigRepo =
          new JooqGuildCurrencyConfigRepository(dslContext);
      DomainEventPublisher newEventPublisher = new DomainEventPublisher();
      CurrencyConfigService newConfigService =
          new CurrencyConfigService(newConfigRepo, emojiValidator, newEventPublisher);

      // Then
      GuildCurrencyConfig config1 = newConfigService.getConfig(guild1);
      GuildCurrencyConfig config2 = newConfigService.getConfig(guild2);

      assertThat(config1.currencyName()).isEqualTo("金幣");
      assertThat(config1.currencyIcon()).isEqualTo("💰");
      assertThat(config2.currencyName()).isEqualTo("星星");
      assertThat(config2.currencyIcon()).isEqualTo("⭐");
    }
  }

  // ============================================================
  // Atomic Operation and No Duplication Tests
  // ============================================================

  @Nested
  @DisplayName("Atomic Operations and No Duplication Tests")
  class AtomicOperationTests {

    @Test
    @DisplayName("should not duplicate adjustment on successful complete operation")
    void shouldNotDuplicateAdjustmentOnSuccessfulOperation() {
      // Given
      GuildCurrencyConfigRepository configRepo = new JooqGuildCurrencyConfigRepository(dslContext);
      MemberCurrencyAccountRepository accountRepo =
          new JooqMemberCurrencyAccountRepository(dslContext);
      BalanceAdjustmentService adjustmentService = createAdjustmentService(accountRepo, configRepo);

      // Initial adjustment
      BalanceAdjustmentResult result1 =
          adjustmentService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, 100L);
      assertThat(result1.newBalance()).isEqualTo(100L);

      // When - simulate restart and verify
      GuildCurrencyConfigRepository newConfigRepo =
          new JooqGuildCurrencyConfigRepository(dslContext);
      MemberCurrencyAccountRepository newAccountRepo =
          new JooqMemberCurrencyAccountRepository(dslContext);
      DefaultBalanceService newBalanceService =
          new DefaultBalanceService(
              newAccountRepo,
              newConfigRepo,
              NoOpCacheService.getInstance(),
              new DefaultCacheKeyGenerator());

      // Then - balance should be exactly 100, not duplicated
      assertThat(newBalanceService.getBalance(TEST_GUILD_ID, TEST_USER_ID).balance())
          .isEqualTo(100L);
    }

    @Test
    @DisplayName("should handle sequential adjustments correctly without duplication")
    void shouldHandleSequentialAdjustmentsCorrectlyWithoutDuplication() {
      // Given
      GuildCurrencyConfigRepository configRepo = new JooqGuildCurrencyConfigRepository(dslContext);
      MemberCurrencyAccountRepository accountRepo =
          new JooqMemberCurrencyAccountRepository(dslContext);
      BalanceAdjustmentService adjustmentService = createAdjustmentService(accountRepo, configRepo);

      // Multiple sequential adjustments
      adjustmentService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, 100L);
      adjustmentService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, 50L);
      adjustmentService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, -30L);

      // Simulate restart between adjustments
      GuildCurrencyConfigRepository newConfigRepo =
          new JooqGuildCurrencyConfigRepository(dslContext);
      MemberCurrencyAccountRepository newAccountRepo =
          new JooqMemberCurrencyAccountRepository(dslContext);
      BalanceAdjustmentService newAdjustmentService =
          createAdjustmentService(newAccountRepo, newConfigRepo);
      ;

      // Continue adjustments after restart
      newAdjustmentService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, 80L);

      // Final verification
      DefaultBalanceService finalBalanceService =
          new DefaultBalanceService(
              newAccountRepo,
              newConfigRepo,
              NoOpCacheService.getInstance(),
              new DefaultCacheKeyGenerator());
      // Expected: 100 + 50 - 30 + 80 = 200
      assertThat(finalBalanceService.getBalance(TEST_GUILD_ID, TEST_USER_ID).balance())
          .isEqualTo(200L);
    }

    @Test
    @DisplayName(
        "should maintain balance consistency when balance view and adjustment interleaved with"
            + " restart")
    void shouldMaintainConsistencyWhenInterleavedWithRestart() {
      // Given - initial setup
      GuildCurrencyConfigRepository configRepo = new JooqGuildCurrencyConfigRepository(dslContext);
      MemberCurrencyAccountRepository accountRepo =
          new JooqMemberCurrencyAccountRepository(dslContext);
      BalanceAdjustmentService adjustmentService = createAdjustmentService(accountRepo, configRepo);
      DefaultBalanceService balanceService =
          new DefaultBalanceService(
              accountRepo,
              configRepo,
              NoOpCacheService.getInstance(),
              new DefaultCacheKeyGenerator());

      // Initial adjustment
      adjustmentService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, 1000L);

      // Check balance
      assertThat(balanceService.getBalance(TEST_GUILD_ID, TEST_USER_ID).balance()).isEqualTo(1000L);

      // Simulate restart
      GuildCurrencyConfigRepository newConfigRepo =
          new JooqGuildCurrencyConfigRepository(dslContext);
      MemberCurrencyAccountRepository newAccountRepo =
          new JooqMemberCurrencyAccountRepository(dslContext);
      DefaultBalanceService newBalanceService =
          new DefaultBalanceService(
              newAccountRepo,
              newConfigRepo,
              NoOpCacheService.getInstance(),
              new DefaultCacheKeyGenerator());
      BalanceAdjustmentService newAdjustmentService =
          createAdjustmentService(newAccountRepo, newConfigRepo);
      ;

      // Check balance after restart
      assertThat(newBalanceService.getBalance(TEST_GUILD_ID, TEST_USER_ID).balance())
          .isEqualTo(1000L);

      // Make another adjustment
      newAdjustmentService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, -500L);

      // Final check
      assertThat(newBalanceService.getBalance(TEST_GUILD_ID, TEST_USER_ID).balance())
          .isEqualTo(500L);
    }
  }

  // ============================================================
  // Combined Balance and Config Tests After Restart
  // ============================================================

  @Nested
  @DisplayName("Combined Balance and Config Preservation Tests")
  class CombinedPreservationTests {

    @Test
    @DisplayName("should preserve both balance and config together after restart")
    void shouldPreserveBothBalanceAndConfigAfterRestart() {
      // Given - set up config and balance
      GuildCurrencyConfigRepository configRepo = new JooqGuildCurrencyConfigRepository(dslContext);
      MemberCurrencyAccountRepository accountRepo =
          new JooqMemberCurrencyAccountRepository(dslContext);
      EmojiValidator emojiValidator = new NoOpEmojiValidator();
      DomainEventPublisher eventPublisher = new DomainEventPublisher();
      CurrencyConfigService configService =
          new CurrencyConfigService(configRepo, emojiValidator, eventPublisher);
      BalanceAdjustmentService adjustmentService = createAdjustmentService(accountRepo, configRepo);
      DefaultBalanceService balanceService =
          new DefaultBalanceService(
              accountRepo,
              configRepo,
              NoOpCacheService.getInstance(),
              new DefaultCacheKeyGenerator());

      configService.updateConfig(TEST_GUILD_ID, "鑽石", "💎");
      adjustmentService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, 999L);

      // Verify before restart
      BalanceView before = balanceService.getBalance(TEST_GUILD_ID, TEST_USER_ID);
      assertThat(before.balance()).isEqualTo(999L);
      assertThat(before.currencyName()).isEqualTo("鑽石");
      assertThat(before.currencyIcon()).isEqualTo("💎");

      // When - simulate restart
      GuildCurrencyConfigRepository newConfigRepo =
          new JooqGuildCurrencyConfigRepository(dslContext);
      MemberCurrencyAccountRepository newAccountRepo =
          new JooqMemberCurrencyAccountRepository(dslContext);
      DefaultBalanceService newBalanceService =
          new DefaultBalanceService(
              newAccountRepo,
              newConfigRepo,
              NoOpCacheService.getInstance(),
              new DefaultCacheKeyGenerator());

      // Then - both balance and config should be preserved
      BalanceView after = newBalanceService.getBalance(TEST_GUILD_ID, TEST_USER_ID);
      assertThat(after.balance()).isEqualTo(999L);
      assertThat(after.currencyName()).isEqualTo("鑽石");
      assertThat(after.currencyIcon()).isEqualTo("💎");
    }

    @Test
    @DisplayName("should display correct currency after config update and restart")
    void shouldDisplayCorrectCurrencyAfterConfigUpdateAndRestart() {
      // Given - initial setup
      GuildCurrencyConfigRepository configRepo = new JooqGuildCurrencyConfigRepository(dslContext);
      MemberCurrencyAccountRepository accountRepo =
          new JooqMemberCurrencyAccountRepository(dslContext);
      EmojiValidator emojiValidator = new NoOpEmojiValidator();
      DomainEventPublisher eventPublisher = new DomainEventPublisher();
      CurrencyConfigService configService =
          new CurrencyConfigService(configRepo, emojiValidator, eventPublisher);
      BalanceAdjustmentService adjustmentService = createAdjustmentService(accountRepo, configRepo);

      adjustmentService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, 500L);
      configService.updateConfig(TEST_GUILD_ID, "Ruby", "❤️");

      // Simulate restart
      GuildCurrencyConfigRepository newConfigRepo =
          new JooqGuildCurrencyConfigRepository(dslContext);
      MemberCurrencyAccountRepository newAccountRepo =
          new JooqMemberCurrencyAccountRepository(dslContext);
      DomainEventPublisher newEventPublisher = new DomainEventPublisher();
      CurrencyConfigService newConfigService =
          new CurrencyConfigService(newConfigRepo, emojiValidator, newEventPublisher);
      DefaultBalanceService newBalanceService =
          new DefaultBalanceService(
              newAccountRepo,
              newConfigRepo,
              NoOpCacheService.getInstance(),
              new DefaultCacheKeyGenerator());

      // Update config after restart
      newConfigService.updateConfig(TEST_GUILD_ID, "Emerald", "💚");

      // Then - should show updated currency name
      BalanceView balance = newBalanceService.getBalance(TEST_GUILD_ID, TEST_USER_ID);
      assertThat(balance.balance()).isEqualTo(500L);
      assertThat(balance.currencyName()).isEqualTo("Emerald");
      assertThat(balance.currencyIcon()).isEqualTo("💚");
    }
  }
}
