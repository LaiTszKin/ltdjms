package ltdjms.discord.currency.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import ltdjms.discord.currency.domain.BalanceView;
import ltdjms.discord.currency.domain.GuildCurrencyConfig;
import ltdjms.discord.currency.domain.MemberCurrencyAccount;
import ltdjms.discord.currency.persistence.GuildCurrencyConfigRepository;
import ltdjms.discord.currency.persistence.MemberCurrencyAccountRepository;
import ltdjms.discord.currency.persistence.RepositoryException;
import ltdjms.discord.currency.services.BalanceService;
import ltdjms.discord.currency.services.DefaultBalanceService;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.cache.CacheKeyGenerator;
import ltdjms.discord.shared.cache.CacheService;

/**
 * Unit tests for BalanceService. Tests balance retrieval logic, auto-creation of accounts, and
 * currency configuration handling.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("deprecation") // exercises deprecated getBalance alongside new tryGetBalance API
class BalanceServiceTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_USER_ID = 987654321098765432L;

  @Mock private MemberCurrencyAccountRepository accountRepository;

  @Mock private GuildCurrencyConfigRepository configRepository;

  @Mock private CacheService cacheService;

  @Mock private CacheKeyGenerator cacheKeyGenerator;

  private BalanceService balanceService;

  @BeforeEach
  void setUp() {
    // CacheService returns empty by default (cache miss)
    when(cacheService.get(any(), any(Class.class))).thenReturn(Optional.empty());

    balanceService =
        new DefaultBalanceService(
            accountRepository, configRepository, cacheService, cacheKeyGenerator);
  }

  @Test
  @DisplayName("should return zero balance for new member")
  void shouldReturnZeroBalanceForNewMember() {
    // Given - new member with no account
    MemberCurrencyAccount newAccount = MemberCurrencyAccount.createNew(TEST_GUILD_ID, TEST_USER_ID);
    when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(newAccount);
    when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.empty());

    // When
    BalanceView result = balanceService.getBalance(TEST_GUILD_ID, TEST_USER_ID);

    // Then
    assertThat(result.balance()).isEqualTo(0L);
    assertThat(result.guildId()).isEqualTo(TEST_GUILD_ID);
    assertThat(result.userId()).isEqualTo(TEST_USER_ID);
  }

  @Test
  @DisplayName("should auto-create account for new member")
  void shouldAutoCreateAccountForNewMember() {
    // Given
    MemberCurrencyAccount newAccount = MemberCurrencyAccount.createNew(TEST_GUILD_ID, TEST_USER_ID);
    when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(newAccount);
    when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.empty());

    // When
    balanceService.getBalance(TEST_GUILD_ID, TEST_USER_ID);

    // Then
    verify(accountRepository).findOrCreate(TEST_GUILD_ID, TEST_USER_ID);
  }

  @Test
  @DisplayName("should return existing balance for existing member")
  void shouldReturnExistingBalanceForExistingMember() {
    // Given - existing member with balance
    Instant now = Instant.now();
    MemberCurrencyAccount existingAccount =
        new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 500L, now, now);
    when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(existingAccount);
    when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.empty());

    // When
    BalanceView result = balanceService.getBalance(TEST_GUILD_ID, TEST_USER_ID);

    // Then
    assertThat(result.balance()).isEqualTo(500L);
  }

  @Test
  @DisplayName("should use default currency config when none exists")
  void shouldUseDefaultCurrencyConfigWhenNoneExists() {
    // Given
    MemberCurrencyAccount account = MemberCurrencyAccount.createNew(TEST_GUILD_ID, TEST_USER_ID);
    when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(account);
    when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.empty());

    // When
    BalanceView result = balanceService.getBalance(TEST_GUILD_ID, TEST_USER_ID);

    // Then
    assertThat(result.currencyName()).isEqualTo(GuildCurrencyConfig.DEFAULT_NAME);
    assertThat(result.currencyIcon()).isEqualTo(GuildCurrencyConfig.DEFAULT_ICON);
  }

  @Test
  @DisplayName("should use custom currency config when exists")
  void shouldUseCustomCurrencyConfigWhenExists() {
    // Given
    MemberCurrencyAccount account = MemberCurrencyAccount.createNew(TEST_GUILD_ID, TEST_USER_ID);
    Instant now = Instant.now();
    GuildCurrencyConfig customConfig =
        new GuildCurrencyConfig(TEST_GUILD_ID, "Gold", "💰", now, now);

    when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(account);
    when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.of(customConfig));

    // When
    BalanceView result = balanceService.getBalance(TEST_GUILD_ID, TEST_USER_ID);

    // Then
    assertThat(result.currencyName()).isEqualTo("Gold");
    assertThat(result.currencyIcon()).isEqualTo("💰");
  }

  @Test
  @DisplayName("should return correct guild and user IDs")
  void shouldReturnCorrectGuildAndUserIds() {
    // Given
    MemberCurrencyAccount account = MemberCurrencyAccount.createNew(TEST_GUILD_ID, TEST_USER_ID);
    when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(account);
    when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.empty());

    // When
    BalanceView result = balanceService.getBalance(TEST_GUILD_ID, TEST_USER_ID);

    // Then
    assertThat(result.guildId()).isEqualTo(TEST_GUILD_ID);
    assertThat(result.userId()).isEqualTo(TEST_USER_ID);
  }

  @Test
  @DisplayName("should isolate balances between different guilds")
  void shouldIsolateBalancesBetweenGuilds() {
    // Given - same user in different guilds
    long guild1 = TEST_GUILD_ID;
    long guild2 = TEST_GUILD_ID + 1;

    Instant now = Instant.now();
    MemberCurrencyAccount account1 =
        new MemberCurrencyAccount(guild1, TEST_USER_ID, 100L, now, now);
    MemberCurrencyAccount account2 =
        new MemberCurrencyAccount(guild2, TEST_USER_ID, 200L, now, now);

    when(accountRepository.findOrCreate(guild1, TEST_USER_ID)).thenReturn(account1);
    when(accountRepository.findOrCreate(guild2, TEST_USER_ID)).thenReturn(account2);
    when(configRepository.findByGuildId(anyLong())).thenReturn(Optional.empty());

    // When
    BalanceView result1 = balanceService.getBalance(guild1, TEST_USER_ID);
    BalanceView result2 = balanceService.getBalance(guild2, TEST_USER_ID);

    // Then
    assertThat(result1.balance()).isEqualTo(100L);
    assertThat(result2.balance()).isEqualTo(200L);
    assertThat(result1.guildId()).isEqualTo(guild1);
    assertThat(result2.guildId()).isEqualTo(guild2);
  }

  @Test
  @DisplayName("tryGetBalance 應在成功時回傳 Ok")
  void tryGetBalanceShouldReturnOkOnSuccess() {
    MemberCurrencyAccount account = MemberCurrencyAccount.createNew(TEST_GUILD_ID, TEST_USER_ID);
    when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(account);
    when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.empty());

    Result<BalanceView, DomainError> result =
        balanceService.tryGetBalance(TEST_GUILD_ID, TEST_USER_ID);

    assertThat(result.isOk()).isTrue();
    BalanceView view = result.getValue();
    assertThat(view.guildId()).isEqualTo(TEST_GUILD_ID);
    assertThat(view.userId()).isEqualTo(TEST_USER_ID);
    assertThat(view.balance()).isEqualTo(0L);
  }

  @Test
  @DisplayName("tryGetBalance 應將 RepositoryException 映射為 PERSISTENCE_FAILURE")
  void tryGetBalanceShouldMapRepositoryExceptionToPersistenceFailure() {
    when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID))
        .thenThrow(new RepositoryException("DB error"));

    Result<BalanceView, DomainError> result =
        balanceService.tryGetBalance(TEST_GUILD_ID, TEST_USER_ID);

    assertThat(result.isErr()).isTrue();
    DomainError error = result.getError();
    assertThat(error.category()).isEqualTo(DomainError.Category.PERSISTENCE_FAILURE);
    assertThat(error.message()).contains("Failed to retrieve balance");
  }
}
