package ltdjms.discord.currency.contract;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ltdjms.discord.currency.domain.MemberCurrencyAccount;
import ltdjms.discord.currency.services.BalanceAdjustmentService.BalanceAdjustmentResult;

/**
 * Contract tests for balance adjustment operations. Verifies that adjustment responses conform to
 * the OpenAPI contract.
 */
class BalanceAdjustmentContractTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_USER_ID = 987654321098765432L;

  @Test
  @DisplayName("BalanceAdjustmentResult should contain all required fields per contract")
  void adjustmentResultShouldContainRequiredFields() {
    // Given - create a result per contract requirements
    BalanceAdjustmentResult result =
        new BalanceAdjustmentResult(
            TEST_GUILD_ID,
            TEST_USER_ID,
            100L, // previousBalance
            150L, // newBalance
            50L, // adjustment
            "Gold",
            "💰");

    // Then - verify all contract fields are present
    assertThat(result.guildId()).isEqualTo(TEST_GUILD_ID);
    assertThat(result.userId()).isEqualTo(TEST_USER_ID);
    assertThat(result.previousBalance()).isEqualTo(100L);
    assertThat(result.newBalance()).isEqualTo(150L);
    assertThat(result.adjustment()).isEqualTo(50L);
    assertThat(result.currencyName()).isEqualTo("Gold");
    assertThat(result.currencyIcon()).isEqualTo("💰");
  }

  @Test
  @DisplayName("Balance values should be non-negative per contract")
  void balanceValuesShouldBeNonNegative() {
    // Given - contract specifies minimum: 0 for balances
    BalanceAdjustmentResult result =
        new BalanceAdjustmentResult(TEST_GUILD_ID, TEST_USER_ID, 0L, 0L, 0L, "Coins", "🪙");

    // Then
    assertThat(result.previousBalance()).isGreaterThanOrEqualTo(0L);
    assertThat(result.newBalance()).isGreaterThanOrEqualTo(0L);
  }

  @Test
  @DisplayName("Adjustment amount can be negative (for debit)")
  void adjustmentAmountCanBeNegative() {
    // Given - negative adjustment represents a debit
    BalanceAdjustmentResult result =
        new BalanceAdjustmentResult(TEST_GUILD_ID, TEST_USER_ID, 100L, 50L, -50L, "Coins", "🪙");

    // Then
    assertThat(result.adjustment()).isNegative();
    assertThat(result.newBalance()).isEqualTo(result.previousBalance() + result.adjustment());
  }

  @Test
  @DisplayName("MemberCurrencyAccount should reject negative balance")
  void accountShouldRejectNegativeBalance() {
    assertThatThrownBy(
            () ->
                new MemberCurrencyAccount(
                    TEST_GUILD_ID,
                    TEST_USER_ID,
                    -100L,
                    java.time.Instant.now(),
                    java.time.Instant.now()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("negative");
  }

  @Test
  @DisplayName("Account adjustment validation rejects Long.MIN_VALUE")
  void accountAdjustmentValidationRejectsLongMinValue() {
    // Then - validation should accept all values except Long.MIN_VALUE (abs overflow)
    assertThat(MemberCurrencyAccount.isValidAdjustmentAmount(1L)).isTrue();
    assertThat(MemberCurrencyAccount.isValidAdjustmentAmount(-1L)).isTrue();
    assertThat(MemberCurrencyAccount.isValidAdjustmentAmount(Long.MAX_VALUE)).isTrue();
    // Note: Long.MIN_VALUE + 1 is used instead of -Long.MAX_VALUE to avoid overflow
    assertThat(MemberCurrencyAccount.isValidAdjustmentAmount(Long.MIN_VALUE + 1)).isTrue();
    assertThat(MemberCurrencyAccount.isValidAdjustmentAmount(Long.MIN_VALUE)).isFalse();
  }

  @Test
  @DisplayName("Account withAdjustedBalance should protect against overflow")
  void accountWithAdjustedBalanceShouldProtectAgainstOverflow() {
    // Given - an account at max balance
    MemberCurrencyAccount account =
        new MemberCurrencyAccount(
            TEST_GUILD_ID,
            TEST_USER_ID,
            Long.MAX_VALUE,
            java.time.Instant.now(),
            java.time.Instant.now());

    // When/Then - attempting to add more should fail with overflow protection
    assertThatThrownBy(() -> account.withAdjustedBalance(1L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("overflow");
  }

  @Test
  @DisplayName("Adjustment result should format message correctly")
  void adjustmentResultShouldFormatMessageCorrectly() {
    // Given - positive adjustment (credit)
    BalanceAdjustmentResult creditResult =
        new BalanceAdjustmentResult(TEST_GUILD_ID, TEST_USER_ID, 100L, 150L, 50L, "Gold", "💰");

    // When
    String message = creditResult.formatMessage("<@" + TEST_USER_ID + ">");

    // Then
    assertThat(message).contains("Added");
    assertThat(message).contains("💰");
    assertThat(message).contains("50");
    assertThat(message).contains("150"); // new balance

    // Given - negative adjustment (debit)
    BalanceAdjustmentResult debitResult =
        new BalanceAdjustmentResult(TEST_GUILD_ID, TEST_USER_ID, 150L, 100L, -50L, "Gold", "💰");

    // When
    String debitMessage = debitResult.formatMessage("<@" + TEST_USER_ID + ">");

    // Then
    assertThat(debitMessage).contains("Removed");
    assertThat(debitMessage).contains("50");
    assertThat(debitMessage).contains("100"); // new balance
  }
}
