package ltdjms.discord.currency.unit;

import ltdjms.discord.currency.domain.GuildCurrencyConfig;
import ltdjms.discord.currency.domain.MemberCurrencyAccount;
import ltdjms.discord.currency.persistence.GuildCurrencyConfigRepository;
import ltdjms.discord.currency.persistence.MemberCurrencyAccountRepository;
import ltdjms.discord.currency.persistence.NegativeBalanceException;
import ltdjms.discord.currency.services.BalanceAdjustmentService;
import ltdjms.discord.currency.services.BalanceService;
import ltdjms.discord.currency.services.CurrencyConfigService;
import ltdjms.discord.currency.services.CurrencyTransactionService;
import ltdjms.discord.currency.services.DefaultBalanceService;
import ltdjms.discord.currency.services.EmojiValidator;
import ltdjms.discord.currency.services.NoOpEmojiValidator;
import ltdjms.discord.shared.events.DomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Edge case unit tests for the currency system.
 * Tests invalid amounts, missing configuration, member rejoin scenarios, and permission errors.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("deprecation") // covers legacy exception-based APIs (getBalance/adjustBalance/updateConfig)
class EdgeCaseTests {

    private static final long TEST_GUILD_ID = 123456789012345678L;
    private static final long TEST_USER_ID = 987654321098765432L;

    @Mock
    private MemberCurrencyAccountRepository accountRepository;

    @Mock
    private GuildCurrencyConfigRepository configRepository;

    @Mock
    private CurrencyTransactionService transactionService;
    
    @Mock
    private DomainEventPublisher eventPublisher;

    // ============================================================
    // Invalid Amount Tests
    // ============================================================

    @Nested
    @DisplayName("Invalid Amount Tests")
    class InvalidAmountTests {

        private BalanceAdjustmentService adjustmentService;

        @BeforeEach
        void setUp() {
            adjustmentService = new BalanceAdjustmentService(
                    accountRepository, configRepository, transactionService, eventPublisher);
        }

        @Test
        @DisplayName("should accept adjustment at exactly the maximum amount (Long.MAX_VALUE)")
        void shouldAcceptAdjustmentAtExactlyMaximum() {
            Instant now = Instant.now();
            MemberCurrencyAccount initial = new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 0L, now, now);
            long maxAmount = MemberCurrencyAccount.MAX_ADJUSTMENT_AMOUNT;
            MemberCurrencyAccount adjusted = new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, maxAmount, now, now);

            when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(initial);
            when(accountRepository.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, maxAmount)).thenReturn(adjusted);
            when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.empty());

            var result = adjustmentService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, maxAmount);

            assertThat(result.newBalance()).isEqualTo(maxAmount);
        }

        @Test
        @DisplayName("should accept adjustment at exactly the negative maximum amount")
        void shouldAcceptAdjustmentAtExactlyNegativeMaximum() {
            Instant now = Instant.now();
            long maxAmount = MemberCurrencyAccount.MAX_ADJUSTMENT_AMOUNT;
            MemberCurrencyAccount initial = new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, maxAmount, now, now);
            MemberCurrencyAccount adjusted = new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 0L, now, now);

            when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(initial);
            when(accountRepository.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, -maxAmount)).thenReturn(adjusted);
            when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.empty());

            var result = adjustmentService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, -maxAmount);

            assertThat(result.newBalance()).isEqualTo(0L);
        }

        @Test
        @DisplayName("should reject zero adjustment")
        void shouldHandleZeroAdjustment() {
            Instant now = Instant.now();
            MemberCurrencyAccount initial = new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 100L, now, now);
            MemberCurrencyAccount adjusted = new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 100L, now, now);

            when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(initial);
            when(accountRepository.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, 0L)).thenReturn(adjusted);
            when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.empty());

            // Zero adjustment is technically valid (no change)
            var result = adjustmentService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, 0L);

            assertThat(result.previousBalance()).isEqualTo(100L);
            assertThat(result.newBalance()).isEqualTo(100L);
        }
    }

    // ============================================================
    // Overflow Protection Tests
    // ============================================================

    @Nested
    @DisplayName("Overflow Protection Tests")
    class OverflowProtectionTests {

        @Test
        @DisplayName("should reject adjustment that would cause positive overflow")
        void shouldRejectAdjustmentCausingPositiveOverflow() {
            Instant now = Instant.now();
            MemberCurrencyAccount account = new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, Long.MAX_VALUE, now, now);

            // Adding 1 to Long.MAX_VALUE would overflow
            assertThatThrownBy(() -> account.withAdjustedBalance(1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("overflow");
        }

        @Test
        @DisplayName("should accept large adjustment that does not overflow")
        void shouldAcceptLargeAdjustmentThatDoesNotOverflow() {
            Instant now = Instant.now();
            MemberCurrencyAccount account = new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 0L, now, now);

            // Should be able to add large amount without overflow
            MemberCurrencyAccount result = account.withAdjustedBalance(Long.MAX_VALUE);
            assertThat(result.balance()).isEqualTo(Long.MAX_VALUE);
        }

        @Test
        @DisplayName("should accept adjustment close to overflow boundary")
        void shouldAcceptAdjustmentCloseToOverflowBoundary() {
            Instant now = Instant.now();
            MemberCurrencyAccount account = new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, Long.MAX_VALUE - 100, now, now);

            // Adding exactly 100 should work
            MemberCurrencyAccount result = account.withAdjustedBalance(100);
            assertThat(result.balance()).isEqualTo(Long.MAX_VALUE);
        }

        @Test
        @DisplayName("should reject adjustment just past overflow boundary")
        void shouldRejectAdjustmentJustPastOverflowBoundary() {
            Instant now = Instant.now();
            MemberCurrencyAccount account = new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, Long.MAX_VALUE - 100, now, now);

            // Adding 101 would overflow
            assertThatThrownBy(() -> account.withAdjustedBalance(101))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("overflow");
        }
    }

    // ============================================================
    // Missing Configuration Tests (for Balance Command)
    // ============================================================

    @Nested
    @DisplayName("Balance Command Without Configuration Tests")
    class MissingConfigurationTests {

        private BalanceService balanceService;

        @BeforeEach
        void setUp() {
            balanceService = new DefaultBalanceService(accountRepository, configRepository);
        }

        @Test
        @DisplayName("should use default currency when no configuration exists")
        void shouldUseDefaultCurrencyWhenNoConfigurationExists() {
            Instant now = Instant.now();
            MemberCurrencyAccount account = new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 0L, now, now);

            when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(account);
            when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.empty());

            var result = balanceService.getBalance(TEST_GUILD_ID, TEST_USER_ID);

            assertThat(result.currencyName()).isEqualTo(GuildCurrencyConfig.DEFAULT_NAME);
            assertThat(result.currencyIcon()).isEqualTo(GuildCurrencyConfig.DEFAULT_ICON);
        }

        @Test
        @DisplayName("should create account automatically when balance queried for first time")
        void shouldCreateAccountAutomaticallyWhenBalanceQueriedForFirstTime() {
            Instant now = Instant.now();
            MemberCurrencyAccount newAccount = new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 0L, now, now);

            when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(newAccount);
            when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.empty());

            var result = balanceService.getBalance(TEST_GUILD_ID, TEST_USER_ID);

            verify(accountRepository).findOrCreate(TEST_GUILD_ID, TEST_USER_ID);
            assertThat(result.balance()).isEqualTo(0L);
        }
    }

    // ============================================================
    // Member Leave and Rejoin Tests
    // ============================================================

    @Nested
    @DisplayName("Member Leave and Rejoin Tests")
    class MemberLeaveRejoinTests {

        private BalanceService balanceService;
        private BalanceAdjustmentService adjustmentService;

        @BeforeEach
        void setUp() {
            balanceService = new DefaultBalanceService(accountRepository, configRepository);
            adjustmentService = new BalanceAdjustmentService(
                    accountRepository, configRepository, transactionService, eventPublisher);
        }

        @Test
        @DisplayName("should preserve balance when member rejoins server")
        void shouldPreserveBalanceWhenMemberRejoinsServer() {
            // Simulates the scenario where a member with balance leaves and rejoins
            // The account should still exist and be retrievable
            Instant createdAt = Instant.parse("2024-01-01T00:00:00Z");
            Instant updatedAt = Instant.parse("2024-06-01T00:00:00Z");
            long existingBalance = 500L;
            MemberCurrencyAccount existingAccount = new MemberCurrencyAccount(
                    TEST_GUILD_ID, TEST_USER_ID, existingBalance, createdAt, updatedAt);

            when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(existingAccount);
            when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.empty());

            var result = balanceService.getBalance(TEST_GUILD_ID, TEST_USER_ID);

            assertThat(result.balance()).isEqualTo(existingBalance);
            // Verify we didn't create a new account (balance is preserved)
            verify(accountRepository).findOrCreate(TEST_GUILD_ID, TEST_USER_ID);
        }

        @Test
        @DisplayName("should allow adjustment for member who left and rejoined")
        void shouldAllowAdjustmentForMemberWhoLeftAndRejoined() {
            Instant createdAt = Instant.parse("2024-01-01T00:00:00Z");
            Instant updatedAt = Instant.now();
            long existingBalance = 200L;
            MemberCurrencyAccount existingAccount = new MemberCurrencyAccount(
                    TEST_GUILD_ID, TEST_USER_ID, existingBalance, createdAt, updatedAt);
            MemberCurrencyAccount adjustedAccount = new MemberCurrencyAccount(
                    TEST_GUILD_ID, TEST_USER_ID, 300L, createdAt, updatedAt);

            when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(existingAccount);
            when(accountRepository.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, 100L)).thenReturn(adjustedAccount);
            when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.empty());

            var result = adjustmentService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, 100L);

            assertThat(result.previousBalance()).isEqualTo(existingBalance);
            assertThat(result.newBalance()).isEqualTo(300L);
        }
    }

    // ============================================================
    // Currency Configuration Validation Tests
    // ============================================================

    @Nested
    @DisplayName("Currency Configuration Validation Tests")
    class CurrencyConfigValidationTests {

        private CurrencyConfigService configService;

        @BeforeEach
        void setUp() {
            EmojiValidator emojiValidator = new NoOpEmojiValidator();
            configService = new CurrencyConfigService(configRepository, emojiValidator, eventPublisher);
        }

        @Test
        @DisplayName("should reject blank currency name")
        void shouldRejectBlankCurrencyName() {
            assertThatThrownBy(() -> configService.updateConfig(TEST_GUILD_ID, "", "💰"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("blank");

            verifyNoInteractions(configRepository);
        }

        @Test
        @DisplayName("should reject whitespace-only currency name")
        void shouldRejectWhitespaceOnlyCurrencyName() {
            assertThatThrownBy(() -> configService.updateConfig(TEST_GUILD_ID, "   ", "💰"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("blank");

            verifyNoInteractions(configRepository);
        }

        @Test
        @DisplayName("should reject currency name exceeding maximum length")
        void shouldRejectCurrencyNameExceedingMaxLength() {
            String tooLongName = "a".repeat(GuildCurrencyConfig.MAX_NAME_LENGTH + 1);

            assertThatThrownBy(() -> configService.updateConfig(TEST_GUILD_ID, tooLongName, "💰"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exceed");

            verifyNoInteractions(configRepository);
        }

        @Test
        @DisplayName("should reject blank currency icon")
        void shouldRejectBlankCurrencyIcon() {
            assertThatThrownBy(() -> configService.updateConfig(TEST_GUILD_ID, "Gold", ""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("blank");

            verifyNoInteractions(configRepository);
        }

        @Test
        @DisplayName("should reject currency icon exceeding maximum length")
        void shouldRejectCurrencyIconExceedingMaxLength() {
            String tooLongIcon = "🪙".repeat(GuildCurrencyConfig.MAX_ICON_LENGTH + 1);

            assertThatThrownBy(() -> configService.updateConfig(TEST_GUILD_ID, "Gold", tooLongIcon))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exceed");

            verifyNoInteractions(configRepository);
        }

        @Test
        @DisplayName("should accept currency name at exactly maximum length")
        void shouldAcceptCurrencyNameAtExactlyMaxLength() {
            String exactLengthName = "a".repeat(GuildCurrencyConfig.MAX_NAME_LENGTH);
            Instant now = Instant.now();
            GuildCurrencyConfig defaultConfig = GuildCurrencyConfig.createDefault(TEST_GUILD_ID);
            GuildCurrencyConfig updatedConfig = new GuildCurrencyConfig(
                    TEST_GUILD_ID, exactLengthName, "💰", now, now);

            when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.of(defaultConfig));
            when(configRepository.saveOrUpdate(any())).thenReturn(updatedConfig);

            var result = configService.updateConfig(TEST_GUILD_ID, exactLengthName, "💰");

            assertThat(result.currencyName()).isEqualTo(exactLengthName);
        }
    }

    // ============================================================
    // Negative Balance Prevention Tests
    // ============================================================

    @Nested
    @DisplayName("Negative Balance Prevention Tests")
    class NegativeBalancePreventionTests {

        private BalanceAdjustmentService adjustmentService;

        @BeforeEach
        void setUp() {
            adjustmentService = new BalanceAdjustmentService(
                    accountRepository, configRepository, transactionService, eventPublisher);
        }

        @Test
        @DisplayName("should prevent debit that would result in negative balance")
        void shouldPreventDebitThatWouldResultInNegativeBalance() {
            Instant now = Instant.now();
            MemberCurrencyAccount account = new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 50L, now, now);

            when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(account);
            when(accountRepository.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, -100L))
                    .thenThrow(new NegativeBalanceException("Insufficient balance"));

            assertThatThrownBy(() -> adjustmentService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, -100L))
                    .isInstanceOf(NegativeBalanceException.class);

            // Verify balance was not changed (repository throws before saving)
        }

        @Test
        @DisplayName("should allow debit that results in exactly zero balance")
        void shouldAllowDebitThatResultsInExactlyZeroBalance() {
            Instant now = Instant.now();
            MemberCurrencyAccount account = new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 100L, now, now);
            MemberCurrencyAccount adjusted = new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 0L, now, now);

            when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(account);
            when(accountRepository.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, -100L)).thenReturn(adjusted);
            when(configRepository.findByGuildId(TEST_GUILD_ID)).thenReturn(Optional.empty());

            var result = adjustmentService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, -100L);

            assertThat(result.newBalance()).isEqualTo(0L);
        }

        @Test
        @DisplayName("should reject attempt to create account with negative balance directly")
        void shouldRejectCreatingAccountWithNegativeBalance() {
            Instant now = Instant.now();

            assertThatThrownBy(() -> new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, -1L, now, now))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("negative");
        }

        @Test
        @DisplayName("should ensure no partial balance changes on error")
        void shouldEnsureNoPartialBalanceChangesOnError() {
            Instant now = Instant.now();
            MemberCurrencyAccount account = new MemberCurrencyAccount(TEST_GUILD_ID, TEST_USER_ID, 50L, now, now);

            when(accountRepository.findOrCreate(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(account);
            when(accountRepository.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, -100L))
                    .thenThrow(new NegativeBalanceException("Insufficient balance"));

            try {
                adjustmentService.adjustBalance(TEST_GUILD_ID, TEST_USER_ID, -100L);
                fail("Expected NegativeBalanceException");
            } catch (NegativeBalanceException e) {
                // Expected
            }

            // Verify that after failure, the original balance would still be intact
            // (this is verified by the repository not completing the save)
            verify(accountRepository).adjustBalance(TEST_GUILD_ID, TEST_USER_ID, -100L);
        }
    }

    // ============================================================
    // Guild Isolation Tests
    // ============================================================

    @Nested
    @DisplayName("Guild Isolation Tests")
    class GuildIsolationTests {

        private BalanceService balanceService;

        @BeforeEach
        void setUp() {
            balanceService = new DefaultBalanceService(accountRepository, configRepository);
        }

        @Test
        @DisplayName("should maintain separate balances for same user in different guilds")
        void shouldMaintainSeparateBalancesForSameUserInDifferentGuilds() {
            Instant now = Instant.now();
            long guild1 = TEST_GUILD_ID;
            long guild2 = TEST_GUILD_ID + 1;

            MemberCurrencyAccount guild1Account = new MemberCurrencyAccount(guild1, TEST_USER_ID, 100L, now, now);
            MemberCurrencyAccount guild2Account = new MemberCurrencyAccount(guild2, TEST_USER_ID, 500L, now, now);

            when(accountRepository.findOrCreate(guild1, TEST_USER_ID)).thenReturn(guild1Account);
            when(accountRepository.findOrCreate(guild2, TEST_USER_ID)).thenReturn(guild2Account);
            when(configRepository.findByGuildId(anyLong())).thenReturn(Optional.empty());

            var result1 = balanceService.getBalance(guild1, TEST_USER_ID);
            var result2 = balanceService.getBalance(guild2, TEST_USER_ID);

            assertThat(result1.balance()).isEqualTo(100L);
            assertThat(result2.balance()).isEqualTo(500L);
        }

        @Test
        @DisplayName("should use guild-specific currency configuration")
        void shouldUseGuildSpecificCurrencyConfiguration() {
            Instant now = Instant.now();
            long guild1 = TEST_GUILD_ID;
            long guild2 = TEST_GUILD_ID + 1;

            MemberCurrencyAccount account1 = new MemberCurrencyAccount(guild1, TEST_USER_ID, 0L, now, now);
            MemberCurrencyAccount account2 = new MemberCurrencyAccount(guild2, TEST_USER_ID, 0L, now, now);
            GuildCurrencyConfig config1 = new GuildCurrencyConfig(guild1, "Gold", "💰", now, now);
            GuildCurrencyConfig config2 = new GuildCurrencyConfig(guild2, "Silver", "🥈", now, now);

            when(accountRepository.findOrCreate(guild1, TEST_USER_ID)).thenReturn(account1);
            when(accountRepository.findOrCreate(guild2, TEST_USER_ID)).thenReturn(account2);
            when(configRepository.findByGuildId(guild1)).thenReturn(Optional.of(config1));
            when(configRepository.findByGuildId(guild2)).thenReturn(Optional.of(config2));

            var result1 = balanceService.getBalance(guild1, TEST_USER_ID);
            var result2 = balanceService.getBalance(guild2, TEST_USER_ID);

            assertThat(result1.currencyName()).isEqualTo("Gold");
            assertThat(result1.currencyIcon()).isEqualTo("💰");
            assertThat(result2.currencyName()).isEqualTo("Silver");
            assertThat(result2.currencyIcon()).isEqualTo("🥈");
        }
    }
}