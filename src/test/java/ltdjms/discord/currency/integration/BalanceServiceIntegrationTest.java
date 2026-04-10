package ltdjms.discord.currency.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ltdjms.discord.currency.domain.BalanceView;
import ltdjms.discord.currency.domain.GuildCurrencyConfig;
import ltdjms.discord.currency.persistence.GuildCurrencyConfigRepository;
import ltdjms.discord.currency.persistence.JooqGuildCurrencyConfigRepository;
import ltdjms.discord.currency.persistence.JooqMemberCurrencyAccountRepository;
import ltdjms.discord.currency.persistence.MemberCurrencyAccountRepository;
import ltdjms.discord.currency.services.BalanceService;
import ltdjms.discord.currency.services.DefaultBalanceService;
import ltdjms.discord.shared.cache.CacheKeyGenerator;
import ltdjms.discord.shared.cache.CacheService;
import ltdjms.discord.shared.cache.DefaultCacheKeyGenerator;
import ltdjms.discord.shared.cache.NoOpCacheService;

/**
 * Integration tests for BalanceService. Tests balance retrieval with a real PostgreSQL database.
 */
@SuppressWarnings(
    "deprecation") // uses deprecated BalanceService#getBalance for compatibility verification
class BalanceServiceIntegrationTest extends PostgresIntegrationTestBase {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_USER_ID = 987654321098765432L;

  private BalanceService balanceService;
  private MemberCurrencyAccountRepository accountRepository;
  private GuildCurrencyConfigRepository configRepository;

  @BeforeEach
  void setUp() {
    accountRepository = new JooqMemberCurrencyAccountRepository(dslContext);
    configRepository = new JooqGuildCurrencyConfigRepository(dslContext);
    CacheService cacheService = NoOpCacheService.getInstance();
    CacheKeyGenerator cacheKeyGenerator = new DefaultCacheKeyGenerator();
    balanceService =
        new DefaultBalanceService(
            accountRepository, configRepository, cacheService, cacheKeyGenerator);
  }

  @Test
  @DisplayName("should return zero balance and auto-create account for new member")
  void shouldReturnZeroBalanceAndAutoCreateAccount() {
    // When
    BalanceView result = balanceService.getBalance(TEST_GUILD_ID, TEST_USER_ID);

    // Then
    assertThat(result.balance()).isEqualTo(0L);
    assertThat(result.guildId()).isEqualTo(TEST_GUILD_ID);
    assertThat(result.userId()).isEqualTo(TEST_USER_ID);

    // Verify account was created in database
    assertThat(accountRepository.findByGuildIdAndUserId(TEST_GUILD_ID, TEST_USER_ID))
        .isPresent()
        .hasValueSatisfying(account -> assertThat(account.balance()).isEqualTo(0L));
  }

  @Test
  @DisplayName("should return existing balance for returning member")
  void shouldReturnExistingBalanceForReturningMember() {
    // Given - create account with balance
    accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID);
    accountRepository.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, 500L);

    // When
    BalanceView result = balanceService.getBalance(TEST_GUILD_ID, TEST_USER_ID);

    // Then
    assertThat(result.balance()).isEqualTo(500L);
  }

  @Test
  @DisplayName("should use default currency when no config exists")
  void shouldUseDefaultCurrencyWhenNoConfigExists() {
    // When
    BalanceView result = balanceService.getBalance(TEST_GUILD_ID, TEST_USER_ID);

    // Then
    assertThat(result.currencyName()).isEqualTo(GuildCurrencyConfig.DEFAULT_NAME);
    assertThat(result.currencyIcon()).isEqualTo(GuildCurrencyConfig.DEFAULT_ICON);
  }

  @Test
  @DisplayName("should use custom currency config when exists")
  void shouldUseCustomCurrencyConfigWhenExists() {
    // Given - create custom currency config
    GuildCurrencyConfig customConfig =
        GuildCurrencyConfig.createDefault(TEST_GUILD_ID).withUpdates("Diamonds", "💎");
    configRepository.save(customConfig);

    // When
    BalanceView result = balanceService.getBalance(TEST_GUILD_ID, TEST_USER_ID);

    // Then
    assertThat(result.currencyName()).isEqualTo("Diamonds");
    assertThat(result.currencyIcon()).isEqualTo("💎");
  }

  @Test
  @DisplayName("should isolate balances between guilds")
  void shouldIsolateBalancesBetweenGuilds() {
    // Given - same user in two guilds with different balances
    long guild1 = TEST_GUILD_ID;
    long guild2 = TEST_GUILD_ID + 1;

    accountRepository.findOrCreate(guild1, TEST_USER_ID);
    accountRepository.adjustBalance(guild1, TEST_USER_ID, 100L);

    accountRepository.findOrCreate(guild2, TEST_USER_ID);
    accountRepository.adjustBalance(guild2, TEST_USER_ID, 200L);

    // When
    BalanceView result1 = balanceService.getBalance(guild1, TEST_USER_ID);
    BalanceView result2 = balanceService.getBalance(guild2, TEST_USER_ID);

    // Then
    assertThat(result1.balance()).isEqualTo(100L);
    assertThat(result2.balance()).isEqualTo(200L);
  }

  @Test
  @DisplayName("should isolate currency config between guilds")
  void shouldIsolateCurrencyConfigBetweenGuilds() {
    // Given - two guilds with different currency configs
    long guild1 = TEST_GUILD_ID;
    long guild2 = TEST_GUILD_ID + 1;

    configRepository.save(GuildCurrencyConfig.createDefault(guild1).withUpdates("Gold", "💰"));
    configRepository.save(GuildCurrencyConfig.createDefault(guild2).withUpdates("Silver", "🥈"));

    // When
    BalanceView result1 = balanceService.getBalance(guild1, TEST_USER_ID);
    BalanceView result2 = balanceService.getBalance(guild2, TEST_USER_ID);

    // Then
    assertThat(result1.currencyName()).isEqualTo("Gold");
    assertThat(result1.currencyIcon()).isEqualTo("💰");
    assertThat(result2.currencyName()).isEqualTo("Silver");
    assertThat(result2.currencyIcon()).isEqualTo("🥈");
  }

  @Test
  @DisplayName("should handle multiple calls for same user consistently")
  void shouldHandleMultipleCallsConsistently() {
    // Given - first call creates account
    BalanceView first = balanceService.getBalance(TEST_GUILD_ID, TEST_USER_ID);

    // When - subsequent calls should return same result
    BalanceView second = balanceService.getBalance(TEST_GUILD_ID, TEST_USER_ID);
    BalanceView third = balanceService.getBalance(TEST_GUILD_ID, TEST_USER_ID);

    // Then
    assertThat(second.balance()).isEqualTo(first.balance());
    assertThat(third.balance()).isEqualTo(first.balance());
  }
}
