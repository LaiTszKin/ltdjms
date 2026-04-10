package ltdjms.discord.currency.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import ltdjms.discord.currency.domain.GuildCurrencyConfig;
import ltdjms.discord.currency.domain.MemberCurrencyAccount;
import ltdjms.discord.currency.persistence.GuildCurrencyConfigRepository;
import ltdjms.discord.currency.persistence.JooqGuildCurrencyConfigRepository;
import ltdjms.discord.currency.persistence.JooqMemberCurrencyAccountRepository;
import ltdjms.discord.currency.persistence.MemberCurrencyAccountRepository;
import ltdjms.discord.currency.persistence.NegativeBalanceException;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;

/**
 * Integration tests for JOOQ-based repository implementations. Tests repository operations against
 * a real PostgreSQL database using JOOQ DSLContext.
 */
class JooqRepositoryIntegrationTest extends PostgresIntegrationTestBase {

  private MemberCurrencyAccountRepository accountRepository;
  private GuildCurrencyConfigRepository configRepository;

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_USER_ID = 987654321098765432L;

  @BeforeEach
  void setUp() {
    accountRepository = new JooqMemberCurrencyAccountRepository(dslContext);
    configRepository = new JooqGuildCurrencyConfigRepository(dslContext);
  }

  @Nested
  @DisplayName("JooqGuildCurrencyConfigRepository Tests")
  class JooqGuildCurrencyConfigRepositoryTests {

    @Test
    @DisplayName("should save and find guild currency config")
    void shouldSaveAndFindConfig() {
      GuildCurrencyConfig config = GuildCurrencyConfig.createDefault(TEST_GUILD_ID);

      configRepository.save(config);
      Optional<GuildCurrencyConfig> found = configRepository.findByGuildId(TEST_GUILD_ID);

      assertThat(found).isPresent();
      assertThat(found.get().guildId()).isEqualTo(TEST_GUILD_ID);
      assertThat(found.get().currencyName()).isEqualTo(GuildCurrencyConfig.DEFAULT_NAME);
      assertThat(found.get().currencyIcon()).isEqualTo(GuildCurrencyConfig.DEFAULT_ICON);
    }

    @Test
    @DisplayName("should update existing guild currency config")
    void shouldUpdateConfig() {
      GuildCurrencyConfig original = GuildCurrencyConfig.createDefault(TEST_GUILD_ID);
      configRepository.save(original);

      GuildCurrencyConfig updated = original.withUpdates("Gold", "💰");
      configRepository.update(updated);

      Optional<GuildCurrencyConfig> found = configRepository.findByGuildId(TEST_GUILD_ID);
      assertThat(found).isPresent();
      assertThat(found.get().currencyName()).isEqualTo("Gold");
      assertThat(found.get().currencyIcon()).isEqualTo("💰");
    }

    @Test
    @DisplayName("should save or update guild currency config")
    void shouldSaveOrUpdateConfig() {
      GuildCurrencyConfig config = GuildCurrencyConfig.createDefault(TEST_GUILD_ID);

      configRepository.saveOrUpdate(config);
      configRepository.saveOrUpdate(config.withUpdates("Silver", "🥈"));

      Optional<GuildCurrencyConfig> found = configRepository.findByGuildId(TEST_GUILD_ID);
      assertThat(found).isPresent();
      assertThat(found.get().currencyName()).isEqualTo("Silver");
      assertThat(found.get().currencyIcon()).isEqualTo("🥈");
    }

    @Test
    @DisplayName("should delete guild currency config")
    void shouldDeleteConfig() {
      configRepository.save(GuildCurrencyConfig.createDefault(TEST_GUILD_ID));

      boolean deleted = configRepository.deleteByGuildId(TEST_GUILD_ID);

      assertThat(deleted).isTrue();
      assertThat(configRepository.findByGuildId(TEST_GUILD_ID)).isEmpty();
    }
  }

  @Nested
  @DisplayName("JooqMemberCurrencyAccountRepository Tests")
  class JooqMemberCurrencyAccountRepositoryTests {

    @Test
    @DisplayName("should save and find member account")
    void shouldSaveAndFindAccount() {
      // Given
      MemberCurrencyAccount account = MemberCurrencyAccount.createNew(TEST_GUILD_ID, TEST_USER_ID);

      // When
      accountRepository.save(account);
      Optional<MemberCurrencyAccount> found =
          accountRepository.findByGuildIdAndUserId(TEST_GUILD_ID, TEST_USER_ID);

      // Then
      assertThat(found).isPresent();
      assertThat(found.get().guildId()).isEqualTo(TEST_GUILD_ID);
      assertThat(found.get().userId()).isEqualTo(TEST_USER_ID);
      assertThat(found.get().balance()).isEqualTo(0L);
    }

    @Test
    @DisplayName("should return empty when account not found")
    void shouldReturnEmptyWhenNotFound() {
      // When
      Optional<MemberCurrencyAccount> found = accountRepository.findByGuildIdAndUserId(999L, 999L);

      // Then
      assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("should find or create account")
    void shouldFindOrCreate() {
      // When - first call creates
      MemberCurrencyAccount created = accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID);

      // Then
      assertThat(created.guildId()).isEqualTo(TEST_GUILD_ID);
      assertThat(created.userId()).isEqualTo(TEST_USER_ID);
      assertThat(created.balance()).isEqualTo(0L);

      // When - second call finds existing
      MemberCurrencyAccount found = accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID);

      // Then
      assertThat(found.guildId()).isEqualTo(TEST_GUILD_ID);
      assertThat(found.userId()).isEqualTo(TEST_USER_ID);
    }

    @Test
    @DisplayName("should adjust balance positively")
    void shouldAdjustBalancePositively() {
      // Given
      accountRepository.save(MemberCurrencyAccount.createNew(TEST_GUILD_ID, TEST_USER_ID));

      // When
      MemberCurrencyAccount updated =
          accountRepository.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, 100L);

      // Then
      assertThat(updated.balance()).isEqualTo(100L);

      // Verify persisted
      Optional<MemberCurrencyAccount> found =
          accountRepository.findByGuildIdAndUserId(TEST_GUILD_ID, TEST_USER_ID);
      assertThat(found).isPresent();
      assertThat(found.get().balance()).isEqualTo(100L);
    }

    @Test
    @DisplayName("should adjust balance negatively within limits")
    void shouldAdjustBalanceNegatively() {
      // Given - start with 100
      accountRepository.save(MemberCurrencyAccount.createNew(TEST_GUILD_ID, TEST_USER_ID));
      accountRepository.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, 100L);

      // When
      MemberCurrencyAccount updated =
          accountRepository.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, -50L);

      // Then
      assertThat(updated.balance()).isEqualTo(50L);
    }

    @Test
    @DisplayName("should prevent negative balance")
    void shouldPreventNegativeBalance() {
      // Given - start with 50
      accountRepository.save(MemberCurrencyAccount.createNew(TEST_GUILD_ID, TEST_USER_ID));
      accountRepository.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, 50L);

      // When/Then
      assertThatThrownBy(() -> accountRepository.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, -100L))
          .isInstanceOf(NegativeBalanceException.class)
          .hasMessageContaining("Insufficient balance");
    }

    @Test
    @DisplayName("should prevent negative balance via database constraint")
    void shouldPreventNegativeBalanceViaConstraint() {
      // Given - start with 0
      accountRepository.save(MemberCurrencyAccount.createNew(TEST_GUILD_ID, TEST_USER_ID));

      // When/Then
      assertThatThrownBy(() -> accountRepository.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, -1L))
          .isInstanceOf(NegativeBalanceException.class);
    }

    @Test
    @DisplayName("should set balance to specific value")
    void shouldSetBalance() {
      // Given
      accountRepository.save(MemberCurrencyAccount.createNew(TEST_GUILD_ID, TEST_USER_ID));

      // When
      MemberCurrencyAccount updated =
          accountRepository.setBalance(TEST_GUILD_ID, TEST_USER_ID, 500L);

      // Then
      assertThat(updated.balance()).isEqualTo(500L);
    }

    @Test
    @DisplayName("should reject negative balance in setBalance")
    void shouldRejectNegativeBalanceInSet() {
      // Given
      accountRepository.save(MemberCurrencyAccount.createNew(TEST_GUILD_ID, TEST_USER_ID));

      // When/Then
      assertThatThrownBy(() -> accountRepository.setBalance(TEST_GUILD_ID, TEST_USER_ID, -100L))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("negative");
    }

    @Test
    @DisplayName("should isolate balances between guilds")
    void shouldIsolateBalancesBetweenGuilds() {
      // Given - same user in two guilds
      long guild1 = TEST_GUILD_ID;
      long guild2 = TEST_GUILD_ID + 1;

      accountRepository.save(MemberCurrencyAccount.createNew(guild1, TEST_USER_ID));
      accountRepository.save(MemberCurrencyAccount.createNew(guild2, TEST_USER_ID));

      // When - adjust only guild1
      accountRepository.adjustBalance(guild1, TEST_USER_ID, 100L);

      // Then
      assertThat(accountRepository.findByGuildIdAndUserId(guild1, TEST_USER_ID).get().balance())
          .isEqualTo(100L);
      assertThat(accountRepository.findByGuildIdAndUserId(guild2, TEST_USER_ID).get().balance())
          .isEqualTo(0L);
    }

    @Test
    @DisplayName("should delete account")
    void shouldDeleteAccount() {
      // Given
      accountRepository.save(MemberCurrencyAccount.createNew(TEST_GUILD_ID, TEST_USER_ID));

      // When
      boolean deleted = accountRepository.deleteByGuildIdAndUserId(TEST_GUILD_ID, TEST_USER_ID);

      // Then
      assertThat(deleted).isTrue();
      assertThat(accountRepository.findByGuildIdAndUserId(TEST_GUILD_ID, TEST_USER_ID)).isEmpty();
    }

    @Test
    @DisplayName("tryAdjustBalance should return Ok with updated account on success")
    void tryAdjustBalanceShouldReturnOkOnSuccess() {
      // Given
      accountRepository.save(MemberCurrencyAccount.createNew(TEST_GUILD_ID, TEST_USER_ID));
      accountRepository.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, 100L);

      // When
      Result<MemberCurrencyAccount, DomainError> result =
          accountRepository.tryAdjustBalance(TEST_GUILD_ID, TEST_USER_ID, -50L);

      // Then
      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().balance()).isEqualTo(50L);
    }

    @Test
    @DisplayName(
        "tryAdjustBalance should return Err with INSUFFICIENT_BALANCE on insufficient funds")
    void tryAdjustBalanceShouldReturnErrOnInsufficientBalance() {
      // Given
      accountRepository.save(MemberCurrencyAccount.createNew(TEST_GUILD_ID, TEST_USER_ID));

      // When
      Result<MemberCurrencyAccount, DomainError> result =
          accountRepository.tryAdjustBalance(TEST_GUILD_ID, TEST_USER_ID, -100L);

      // Then
      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().category()).isEqualTo(DomainError.Category.INSUFFICIENT_BALANCE);
    }
  }
}
