package ltdjms.discord.currency.unit;

import ltdjms.discord.currency.domain.CurrencyTransaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for CurrencyTransaction domain object.
 */
class CurrencyTransactionTest {

    private static final long TEST_GUILD_ID = 123456789012345678L;
    private static final long TEST_USER_ID = 987654321098765432L;

    @Nested
    @DisplayName("constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("should reject null source")
        void shouldRejectNullSource() {
            assertThatThrownBy(() ->
                    new CurrencyTransaction(1L, TEST_GUILD_ID, TEST_USER_ID, 100, 100, null, null, Instant.now())
            ).isInstanceOf(NullPointerException.class)
                    .hasMessage("source must not be null");
        }

        @Test
        @DisplayName("should reject negative balanceAfter")
        void shouldRejectNegativeBalanceAfter() {
            assertThatThrownBy(() ->
                    new CurrencyTransaction(1L, TEST_GUILD_ID, TEST_USER_ID, -100, -50,
                            CurrencyTransaction.Source.ADMIN_ADJUSTMENT, null, Instant.now())
            ).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("balanceAfter cannot be negative");
        }

        @Test
        @DisplayName("should allow zero balanceAfter")
        void shouldAllowZeroBalanceAfter() {
            CurrencyTransaction tx = new CurrencyTransaction(
                    1L, TEST_GUILD_ID, TEST_USER_ID, -100, 0,
                    CurrencyTransaction.Source.ADMIN_ADJUSTMENT, null, Instant.now());
            assertThat(tx.balanceAfter()).isZero();
        }
    }

    @Nested
    @DisplayName("create factory method")
    class CreateMethod {

        @Test
        @DisplayName("should create transaction with null id")
        void shouldCreateTransactionWithNullId() {
            CurrencyTransaction tx = CurrencyTransaction.create(
                    TEST_GUILD_ID, TEST_USER_ID, 50, 150,
                    CurrencyTransaction.Source.ADMIN_ADJUSTMENT, "Test adjustment");

            assertThat(tx.id()).isNull();
            assertThat(tx.guildId()).isEqualTo(TEST_GUILD_ID);
            assertThat(tx.userId()).isEqualTo(TEST_USER_ID);
            assertThat(tx.amount()).isEqualTo(50);
            assertThat(tx.balanceAfter()).isEqualTo(150);
            assertThat(tx.source()).isEqualTo(CurrencyTransaction.Source.ADMIN_ADJUSTMENT);
            assertThat(tx.description()).isEqualTo("Test adjustment");
            assertThat(tx.createdAt()).isNotNull();
        }

        @Test
        @DisplayName("should create transaction with null description")
        void shouldCreateTransactionWithNullDescription() {
            CurrencyTransaction tx = CurrencyTransaction.create(
                    TEST_GUILD_ID, TEST_USER_ID, -50, 50,
                    CurrencyTransaction.Source.ADMIN_ADJUSTMENT, null);

            assertThat(tx.description()).isNull();
        }
    }

    @Nested
    @DisplayName("formatForDisplay")
    class FormatForDisplay {

        @Test
        @DisplayName("should format positive amount correctly")
        void shouldFormatPositiveAmountCorrectly() {
            CurrencyTransaction tx = new CurrencyTransaction(
                    1L, TEST_GUILD_ID, TEST_USER_ID, 100, 200,
                    CurrencyTransaction.Source.ADMIN_ADJUSTMENT, null, Instant.now());

            String display = tx.formatForDisplay();

            assertThat(display).contains("+100");
            assertThat(display).contains("餘額: 200");
            assertThat(display).contains("管理員調整");
        }

        @Test
        @DisplayName("should format negative amount correctly")
        void shouldFormatNegativeAmountCorrectly() {
            CurrencyTransaction tx = new CurrencyTransaction(
                    1L, TEST_GUILD_ID, TEST_USER_ID, -50, 50,
                    CurrencyTransaction.Source.ADMIN_ADJUSTMENT, null, Instant.now());

            String display = tx.formatForDisplay();

            assertThat(display).contains("-50");
            assertThat(display).contains("餘額: 50");
        }

        @Test
        @DisplayName("should include description when present")
        void shouldIncludeDescriptionWhenPresent() {
            CurrencyTransaction tx = new CurrencyTransaction(
                    1L, TEST_GUILD_ID, TEST_USER_ID, 50, 150,
                    CurrencyTransaction.Source.ADMIN_ADJUSTMENT, "Birthday gift", Instant.now());

            String display = tx.formatForDisplay();

            assertThat(display).contains("Birthday gift");
        }

        @Test
        @DisplayName("should not include separator when description is null")
        void shouldNotIncludeSeparatorWhenDescriptionIsNull() {
            CurrencyTransaction tx = new CurrencyTransaction(
                    1L, TEST_GUILD_ID, TEST_USER_ID, 10, 110,
                    CurrencyTransaction.Source.ADMIN_ADJUSTMENT, null, Instant.now());

            String display = tx.formatForDisplay();

            assertThat(display).doesNotContain(" - ");
        }

        @Test
        @DisplayName("should not include separator when description is blank")
        void shouldNotIncludeSeparatorWhenDescriptionIsBlank() {
            CurrencyTransaction tx = new CurrencyTransaction(
                    1L, TEST_GUILD_ID, TEST_USER_ID, 10, 110,
                    CurrencyTransaction.Source.ADMIN_ADJUSTMENT, "   ", Instant.now());

            String display = tx.formatForDisplay();

            assertThat(display).doesNotContain(" - ");
        }
    }

    @Nested
    @DisplayName("Source enum")
    class SourceEnum {

        @Test
        @DisplayName("should return correct display names")
        void shouldReturnCorrectDisplayNames() {
            assertThat(CurrencyTransaction.Source.ADMIN_ADJUSTMENT.getDisplayName()).isEqualTo("管理員調整");
        }
    }

    @Nested
    @DisplayName("getShortTimestamp")
    class GetShortTimestamp {

        @Test
        @DisplayName("should return Discord relative timestamp format")
        void shouldReturnDiscordTimestampFormat() {
            Instant testTime = Instant.ofEpochSecond(1700000000L);
            CurrencyTransaction tx = new CurrencyTransaction(
                    1L, TEST_GUILD_ID, TEST_USER_ID, 10, 110,
                    CurrencyTransaction.Source.ADMIN_ADJUSTMENT, null, testTime);

            String timestamp = tx.getShortTimestamp();

            assertThat(timestamp).isEqualTo("<t:1700000000:R>");
        }
    }
}
