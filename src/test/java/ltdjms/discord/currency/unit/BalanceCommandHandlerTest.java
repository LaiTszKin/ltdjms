package ltdjms.discord.currency.unit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ltdjms.discord.currency.domain.BalanceView;

/**
 * Unit tests for BalanceCommandHandler response formatting. Tests the formatting logic without
 * mocking JDA events (due to Java 25 restrictions). The actual command handler integration is
 * tested via integration tests.
 */
class BalanceCommandHandlerTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_USER_ID = 987654321098765432L;

  @Test
  @DisplayName("should format balance display with icon, amount, and name")
  void shouldFormatBalanceDisplayCorrectly() {
    // Given
    BalanceView balanceView = new BalanceView(TEST_GUILD_ID, TEST_USER_ID, 500L, "Gold", "💰");

    // When
    String display = balanceView.formatDisplay();

    // Then
    assertThat(display).contains("💰");
    assertThat(display).contains("500");
    assertThat(display).contains("Gold");
  }

  @Test
  @DisplayName("should format balance message as user-friendly text")
  void shouldFormatBalanceMessageCorrectly() {
    // Given
    BalanceView balanceView = new BalanceView(TEST_GUILD_ID, TEST_USER_ID, 100L, "Diamonds", "💎");

    // When
    String message = balanceView.formatMessage();

    // Then
    assertThat(message).startsWith("Your balance:");
    assertThat(message).contains("💎");
    assertThat(message).contains("100");
    assertThat(message).contains("Diamonds");
  }

  @Test
  @DisplayName("should format zero balance correctly")
  void shouldFormatZeroBalanceCorrectly() {
    // Given
    BalanceView balanceView = new BalanceView(TEST_GUILD_ID, TEST_USER_ID, 0L, "Coins", "🪙");

    // When
    String display = balanceView.formatDisplay();

    // Then
    assertThat(display).contains("🪙");
    assertThat(display).contains("0");
    assertThat(display).contains("Coins");
  }

  @Test
  @DisplayName("should format large balance with comma separators")
  void shouldFormatLargeBalanceWithCommaSeparators() {
    // Given
    BalanceView balanceView = new BalanceView(TEST_GUILD_ID, TEST_USER_ID, 1234567L, "Gold", "💰");

    // When
    String display = balanceView.formatDisplay();

    // Then
    assertThat(display).contains("1,234,567");
  }

  @Test
  @DisplayName("should include all required fields in BalanceView")
  void shouldIncludeAllRequiredFields() {
    // Given
    BalanceView balanceView = new BalanceView(TEST_GUILD_ID, TEST_USER_ID, 42L, "Stars", "⭐");

    // Then
    assertThat(balanceView.guildId()).isEqualTo(TEST_GUILD_ID);
    assertThat(balanceView.userId()).isEqualTo(TEST_USER_ID);
    assertThat(balanceView.balance()).isEqualTo(42L);
    assertThat(balanceView.currencyName()).isEqualTo("Stars");
    assertThat(balanceView.currencyIcon()).isEqualTo("⭐");
  }

  @Test
  @DisplayName("should format emoji icons correctly")
  void shouldFormatEmojiIconsCorrectly() {
    // Given - multi-codepoint emoji
    BalanceView balanceView = new BalanceView(TEST_GUILD_ID, TEST_USER_ID, 100L, "Trophies", "🏆");

    // When
    String display = balanceView.formatDisplay();

    // Then
    assertThat(display).contains("🏆");
  }
}
