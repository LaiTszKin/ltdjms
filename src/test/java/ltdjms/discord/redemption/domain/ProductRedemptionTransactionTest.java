package ltdjms.discord.redemption.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ProductRedemptionTransaction domain model.
 */
class ProductRedemptionTransactionTest {

    private static final long TEST_GUILD_ID = 123456789012345678L;
    private static final long TEST_USER_ID = 987654321098765432L;
    private static final long TEST_PRODUCT_ID = 1L;

    @Nested
    @DisplayName("ProductRedemptionTransaction creation")
    class TransactionCreationTests {

        @Test
        @DisplayName("should create transaction without reward")
        void shouldCreateTransactionWithoutReward() {
            // When
            ProductRedemptionTransaction transaction = ProductRedemptionTransaction.create(
                    TEST_GUILD_ID,
                    TEST_USER_ID,
                    TEST_PRODUCT_ID,
                    "VIP 服務",
                    "ABCD1234EFGH5678",
                    1,
                    null,
                    null
            );

            // Then
            assertThat(transaction.id()).isNull();
            assertThat(transaction.guildId()).isEqualTo(TEST_GUILD_ID);
            assertThat(transaction.userId()).isEqualTo(TEST_USER_ID);
            assertThat(transaction.productId()).isEqualTo(TEST_PRODUCT_ID);
            assertThat(transaction.productName()).isEqualTo("VIP 服務");
            assertThat(transaction.redemptionCode()).isEqualTo("ABCD1234EFGH5678");
            assertThat(transaction.quantity()).isEqualTo(1);
            assertThat(transaction.hasReward()).isFalse();
            assertThat(transaction.rewardType()).isNull();
            assertThat(transaction.rewardAmount()).isNull();
            assertThat(transaction.createdAt()).isNotNull();
        }

        @Test
        @DisplayName("should create transaction with currency reward")
        void shouldCreateTransactionWithCurrencyReward() {
            // When
            ProductRedemptionTransaction transaction = ProductRedemptionTransaction.create(
                    TEST_GUILD_ID,
                    TEST_USER_ID,
                    TEST_PRODUCT_ID,
                    "禮包",
                    "ABCD1234EFGH5678",
                    5,
                    ProductRedemptionTransaction.RewardType.CURRENCY,
                    5000L
            );

            // Then
            assertThat(transaction.hasReward()).isTrue();
            assertThat(transaction.rewardType()).isEqualTo(ProductRedemptionTransaction.RewardType.CURRENCY);
            assertThat(transaction.rewardAmount()).isEqualTo(5000L);
        }

        @Test
        @DisplayName("should create transaction with token reward")
        void shouldCreateTransactionWithTokenReward() {
            // When
            ProductRedemptionTransaction transaction = ProductRedemptionTransaction.create(
                    TEST_GUILD_ID,
                    TEST_USER_ID,
                    TEST_PRODUCT_ID,
                    "代幣包",
                    "ABCD1234EFGH5678",
                    2,
                    ProductRedemptionTransaction.RewardType.TOKEN,
                    100L
            );

            // Then
            assertThat(transaction.hasReward()).isTrue();
            assertThat(transaction.rewardType()).isEqualTo(ProductRedemptionTransaction.RewardType.TOKEN);
            assertThat(transaction.rewardAmount()).isEqualTo(100L);
        }

        @Test
        @DisplayName("should reject null product name")
        void shouldRejectNullProductName() {
            assertThatThrownBy(() ->
                    ProductRedemptionTransaction.create(
                            TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID,
                            null, "ABCD1234EFGH5678", 1, null, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject blank product name")
        void shouldRejectBlankProductName() {
            assertThatThrownBy(() ->
                    ProductRedemptionTransaction.create(
                            TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID,
                            "   ", "ABCD1234EFGH5678", 1, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("blank");
        }

        @Test
        @DisplayName("should reject product name exceeding max length")
        void shouldRejectProductNameExceedingMaxLength() {
            String longName = "A".repeat(101);
            assertThatThrownBy(() ->
                    ProductRedemptionTransaction.create(
                            TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID,
                            longName, "ABCD1234EFGH5678", 1, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("100");
        }

        @Test
        @DisplayName("should reject zero quantity")
        void shouldRejectZeroQuantity() {
            assertThatThrownBy(() ->
                    ProductRedemptionTransaction.create(
                            TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID,
                            "Test", "ABCD1234EFGH5678", 0, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positive");
        }

        @Test
        @DisplayName("should reject quantity exceeding maximum")
        void shouldRejectQuantityExceedingMaximum() {
            assertThatThrownBy(() ->
                    ProductRedemptionTransaction.create(
                            TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID,
                            "Test", "ABCD1234EFGH5678", 1001, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("1000");
        }

        @Test
        @DisplayName("should reject reward type without reward amount")
        void shouldRejectRewardTypeWithoutRewardAmount() {
            assertThatThrownBy(() ->
                    ProductRedemptionTransaction.create(
                            TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID,
                            "Test", "ABCD1234EFGH5678", 1,
                            ProductRedemptionTransaction.RewardType.CURRENCY, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should reject negative reward amount")
        void shouldRejectNegativeRewardAmount() {
            assertThatThrownBy(() ->
                    ProductRedemptionTransaction.create(
                            TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID,
                            "Test", "ABCD1234EFGH5678", 1,
                            ProductRedemptionTransaction.RewardType.CURRENCY, -1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("negative");
        }
    }

    @Nested
    @DisplayName("formatForDisplay")
    class FormatForDisplayTests {

        @Test
        @DisplayName("should format transaction without reward")
        void shouldFormatTransactionWithoutReward() {
            // Given
            ProductRedemptionTransaction transaction = ProductRedemptionTransaction.create(
                    TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID,
                    "VIP 服務", "ABCD1234EFGH5678", 1, null, null);

            // When
            String formatted = transaction.formatForDisplay();

            // Then
            assertThat(formatted).contains("**VIP 服務**");
            assertThat(formatted).contains("無自動獎勵");
            assertThat(formatted).contains("ABCD****5678");
        }

        @Test
        @DisplayName("should format transaction with quantity > 1")
        void shouldFormatTransactionWithQuantity() {
            // Given
            ProductRedemptionTransaction transaction = ProductRedemptionTransaction.create(
                    TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID,
                    "禮包", "ABCD1234EFGH5678", 5, null, null);

            // When
            String formatted = transaction.formatForDisplay();

            // Then
            assertThat(formatted).contains("**禮包** x5");
        }

        @Test
        @DisplayName("should format transaction with currency reward")
        void shouldFormatTransactionWithCurrencyReward() {
            // Given
            ProductRedemptionTransaction transaction = ProductRedemptionTransaction.create(
                    TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID,
                    "禮包", "ABCD1234EFGH5678", 1,
                    ProductRedemptionTransaction.RewardType.CURRENCY, 1000L);

            // When
            String formatted = transaction.formatForDisplay();

            // Then
            assertThat(formatted).contains("貨幣");
            assertThat(formatted).contains("+1,000");
        }

        @Test
        @DisplayName("should format transaction with token reward")
        void shouldFormatTransactionWithTokenReward() {
            // Given
            ProductRedemptionTransaction transaction = ProductRedemptionTransaction.create(
                    TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID,
                    "代幣包", "ABCD1234EFGH5678", 1,
                    ProductRedemptionTransaction.RewardType.TOKEN, 50L);

            // When
            String formatted = transaction.formatForDisplay();

            // Then
            assertThat(formatted).contains("代幣");
            assertThat(formatted).contains("+50");
        }
    }

    @Nested
    @DisplayName("getMaskedCode")
    class GetMaskedCodeTests {

        @Test
        @DisplayName("should mask code properly")
        void shouldMaskCodeProperly() {
            // Given
            ProductRedemptionTransaction transaction = ProductRedemptionTransaction.create(
                    TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID,
                    "Test", "ABCD1234EFGH5678", 1, null, null);

            // When
            String masked = transaction.getMaskedCode();

            // Then
            assertThat(masked).isEqualTo("ABCD****5678");
        }

        @Test
        @DisplayName("should not mask short code")
        void shouldNotMaskShortCode() {
            // Given
            Instant now = Instant.now();
            ProductRedemptionTransaction transaction = new ProductRedemptionTransaction(
                    1L, TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID,
                    "Test", "ABCD1234", 1, null, null, now);

            // When
            String masked = transaction.getMaskedCode();

            // Then
            assertThat(masked).isEqualTo("ABCD1234");
        }
    }

    @Nested
    @DisplayName("getShortTimestamp")
    class GetShortTimestampTests {

        @Test
        @DisplayName("should format timestamp as Discord relative time")
        void shouldFormatTimestampAsDiscordRelativeTime() {
            // Given
            ProductRedemptionTransaction transaction = ProductRedemptionTransaction.create(
                    TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID,
                    "Test", "ABCD1234EFGH5678", 1, null, null);

            // When
            String timestamp = transaction.getShortTimestamp();

            // Then
            assertThat(timestamp).startsWith("<t:");
            assertThat(timestamp).endsWith(":R>");
        }
    }

    @Nested
    @DisplayName("RewardType")
    class RewardTypeTests {

        @Test
        @DisplayName("CURRENCY should have correct display name")
        void currencyShouldHaveCorrectDisplayName() {
            assertThat(ProductRedemptionTransaction.RewardType.CURRENCY.getDisplayName())
                    .isEqualTo("貨幣");
        }

        @Test
        @DisplayName("TOKEN should have correct display name")
        void tokenShouldHaveCorrectDisplayName() {
            assertThat(ProductRedemptionTransaction.RewardType.TOKEN.getDisplayName())
                    .isEqualTo("代幣");
        }
    }
}
