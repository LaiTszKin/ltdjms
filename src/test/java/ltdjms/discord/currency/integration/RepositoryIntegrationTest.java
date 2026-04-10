package ltdjms.discord.currency.integration;

import static org.assertj.core.api.Assertions.*;

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

/**
 * Production-path integration tests for guild and member repositories. Tests JOOQ repository
 * operations against a real PostgreSQL database.
 */
class RepositoryIntegrationTest extends PostgresIntegrationTestBase {

  private GuildCurrencyConfigRepository configRepository;
  private MemberCurrencyAccountRepository accountRepository;

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_USER_ID = 987654321098765432L;

  @BeforeEach
  void setUp() {
    configRepository = new JooqGuildCurrencyConfigRepository(dslContext);
    accountRepository = new JooqMemberCurrencyAccountRepository(dslContext);
  }

  @Nested
  @DisplayName("Production JOOQ GuildCurrencyConfigRepository Tests")
  class GuildCurrencyConfigRepositoryTests {

    @Test
    @DisplayName("should save and find guild currency config")
    void shouldSaveAndFindConfig() {
      // Given
      GuildCurrencyConfig config = GuildCurrencyConfig.createDefault(TEST_GUILD_ID);

      // When
      configRepository.save(config);
      Optional<GuildCurrencyConfig> found = configRepository.findByGuildId(TEST_GUILD_ID);

      // Then
      assertThat(found).isPresent();
      assertThat(found.get().guildId()).isEqualTo(TEST_GUILD_ID);
      assertThat(found.get().currencyName()).isEqualTo(GuildCurrencyConfig.DEFAULT_NAME);
      assertThat(found.get().currencyIcon()).isEqualTo(GuildCurrencyConfig.DEFAULT_ICON);
    }

    @Test
    @DisplayName("should return empty when config not found")
    void shouldReturnEmptyWhenNotFound() {
      // When
      Optional<GuildCurrencyConfig> found = configRepository.findByGuildId(999L);

      // Then
      assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("should update existing config")
    void shouldUpdateConfig() {
      // Given
      GuildCurrencyConfig original = GuildCurrencyConfig.createDefault(TEST_GUILD_ID);
      configRepository.save(original);

      // When
      GuildCurrencyConfig updated = original.withUpdates("Gold", "💰");
      configRepository.update(updated);

      // Then
      Optional<GuildCurrencyConfig> found = configRepository.findByGuildId(TEST_GUILD_ID);
      assertThat(found).isPresent();
      assertThat(found.get().currencyName()).isEqualTo("Gold");
      assertThat(found.get().currencyIcon()).isEqualTo("💰");
    }

    @Test
    @DisplayName("should upsert config (save or update)")
    void shouldUpsertConfig() {
      // Given - no existing config
      GuildCurrencyConfig config1 = GuildCurrencyConfig.createDefault(TEST_GUILD_ID);

      // When - first save
      configRepository.saveOrUpdate(config1);

      // Then
      Optional<GuildCurrencyConfig> found1 = configRepository.findByGuildId(TEST_GUILD_ID);
      assertThat(found1).isPresent();
      assertThat(found1.get().currencyName()).isEqualTo(GuildCurrencyConfig.DEFAULT_NAME);

      // When - update
      GuildCurrencyConfig config2 = config1.withUpdates("Silver", "🥈");
      configRepository.saveOrUpdate(config2);

      // Then
      Optional<GuildCurrencyConfig> found2 = configRepository.findByGuildId(TEST_GUILD_ID);
      assertThat(found2).isPresent();
      assertThat(found2.get().currencyName()).isEqualTo("Silver");
      assertThat(found2.get().currencyIcon()).isEqualTo("🥈");
    }

    @Test
    @DisplayName("should delete config")
    void shouldDeleteConfig() {
      // Given
      GuildCurrencyConfig config = GuildCurrencyConfig.createDefault(TEST_GUILD_ID);
      configRepository.save(config);

      // When
      boolean deleted = configRepository.deleteByGuildId(TEST_GUILD_ID);

      // Then
      assertThat(deleted).isTrue();
      assertThat(configRepository.findByGuildId(TEST_GUILD_ID)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Production JOOQ MemberCurrencyAccountRepository Tests")
  class MemberCurrencyAccountRepositoryTests {

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
  }
}
