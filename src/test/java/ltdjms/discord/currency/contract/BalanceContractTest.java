package ltdjms.discord.currency.contract;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ltdjms.discord.currency.domain.BalanceView;

/**
 * Contract tests for balance retrieval operations. Verifies that balance responses conform to the
 * OpenAPI contract.
 */
class BalanceContractTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_USER_ID = 987654321098765432L;

  @Test
  @DisplayName("BalanceView should contain all required fields per contract")
  void balanceViewShouldContainRequiredFields() {
    // Given - create a balance view per contract requirements
    BalanceView balanceView = new BalanceView(TEST_GUILD_ID, TEST_USER_ID, 100L, "Gold", "💰");

    // Then - verify all contract fields are present
    assertThat(balanceView.guildId()).isEqualTo(TEST_GUILD_ID);
    assertThat(balanceView.userId()).isEqualTo(TEST_USER_ID);
    assertThat(balanceView.balance()).isEqualTo(100L);
    assertThat(balanceView.currencyName()).isEqualTo("Gold");
    assertThat(balanceView.currencyIcon()).isEqualTo("💰");
  }

  @Test
  @DisplayName("Balance should be non-negative per contract")
  void balanceShouldBeNonNegative() {
    // Given - contract specifies minimum: 0
    BalanceView balanceView = new BalanceView(TEST_GUILD_ID, TEST_USER_ID, 0L, "Coins", "🪙");

    // Then
    assertThat(balanceView.balance()).isGreaterThanOrEqualTo(0L);
  }

  @Test
  @DisplayName("Currency name should respect maximum length per contract")
  void currencyNameShouldRespectMaxLength() {
    // Given - contract specifies maxLength: 50
    String maxLengthName = "A".repeat(50);
    BalanceView balanceView = new BalanceView(TEST_GUILD_ID, TEST_USER_ID, 0L, maxLengthName, "🪙");

    // Then
    assertThat(balanceView.currencyName().length()).isLessThanOrEqualTo(50);
  }

  @Test
  @DisplayName("Currency icon should respect maximum length per contract")
  void currencyIconShouldRespectMaxLength() {
    // Given - contract specifies maxLength: 10
    String maxLengthIcon = "🏆🎖️"; // Multi-codepoint emoji
    BalanceView balanceView =
        new BalanceView(TEST_GUILD_ID, TEST_USER_ID, 0L, "Coins", maxLengthIcon);

    // Then
    assertThat(balanceView.currencyIcon().length()).isLessThanOrEqualTo(10);
  }

  @Test
  @DisplayName("BalanceView should format display correctly")
  void shouldFormatDisplayCorrectly() {
    // Given
    BalanceView balanceView = new BalanceView(TEST_GUILD_ID, TEST_USER_ID, 1234L, "Gold", "💰");

    // When
    String display = balanceView.formatDisplay();

    // Then - should include icon, formatted amount, and name
    assertThat(display).contains("💰");
    assertThat(display).contains("1,234"); // Formatted with comma
    assertThat(display).contains("Gold");
  }

  @Test
  @DisplayName("BalanceView should format message correctly")
  void shouldFormatMessageCorrectly() {
    // Given
    BalanceView balanceView = new BalanceView(TEST_GUILD_ID, TEST_USER_ID, 500L, "Coins", "🪙");

    // When
    String message = balanceView.formatMessage();

    // Then - should be a complete message
    assertThat(message).startsWith("Your balance:");
    assertThat(message).contains("🪙");
    assertThat(message).contains("500");
    assertThat(message).contains("Coins");
  }
}
