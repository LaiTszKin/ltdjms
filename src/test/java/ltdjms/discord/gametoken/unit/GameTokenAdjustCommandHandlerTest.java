package ltdjms.discord.gametoken.unit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ltdjms.discord.gametoken.services.GameTokenService.TokenAdjustmentResult;

/**
 * Unit tests for GameTokenAdjustCommandHandler response formatting. Tests the formatting logic
 * without mocking JDA events (due to Java 25 restrictions). The actual command handler integration
 * is tested via integration tests.
 */
class GameTokenAdjustCommandHandlerTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_USER_ID = 987654321098765432L;

  @Test
  @DisplayName("should format token adjustment message for addition")
  void shouldFormatTokenAdjustmentMessageForAddition() {
    // Given
    TokenAdjustmentResult result =
        new TokenAdjustmentResult(TEST_GUILD_ID, TEST_USER_ID, 100L, 150L, 50L);

    // When
    String message = result.formatMessage("<@123456>");

    // Then
    assertThat(message).contains("Added");
    assertThat(message).contains("50");
    assertThat(message).contains("<@123456>");
    assertThat(message).contains("150");
    assertThat(message).contains("game tokens");
  }

  @Test
  @DisplayName("should format token adjustment message for removal")
  void shouldFormatTokenAdjustmentMessageForRemoval() {
    // Given
    TokenAdjustmentResult result =
        new TokenAdjustmentResult(TEST_GUILD_ID, TEST_USER_ID, 100L, 50L, -50L);

    // When
    String message = result.formatMessage("<@123456>");

    // Then
    assertThat(message).contains("Removed");
    assertThat(message).contains("50");
    assertThat(message).contains("<@123456>");
    assertThat(message).contains("50");
  }

  @Test
  @DisplayName("should format large token amounts with comma separators")
  void shouldFormatLargeTokenAmountsWithCommaSeparators() {
    // Given
    TokenAdjustmentResult result =
        new TokenAdjustmentResult(TEST_GUILD_ID, TEST_USER_ID, 0L, 1234567L, 1234567L);

    // When
    String message = result.formatMessage("<@123456>");

    // Then
    assertThat(message).contains("1,234,567");
  }

  @Test
  @DisplayName("should include all required fields in TokenAdjustmentResult")
  void shouldIncludeAllRequiredFields() {
    // Given
    TokenAdjustmentResult result =
        new TokenAdjustmentResult(TEST_GUILD_ID, TEST_USER_ID, 100L, 150L, 50L);

    // Then
    assertThat(result.guildId()).isEqualTo(TEST_GUILD_ID);
    assertThat(result.userId()).isEqualTo(TEST_USER_ID);
    assertThat(result.previousTokens()).isEqualTo(100L);
    assertThat(result.newTokens()).isEqualTo(150L);
    assertThat(result.adjustment()).isEqualTo(50L);
  }

  @Test
  @DisplayName("should handle zero previous tokens correctly")
  void shouldHandleZeroPreviousTokensCorrectly() {
    // Given
    TokenAdjustmentResult result =
        new TokenAdjustmentResult(TEST_GUILD_ID, TEST_USER_ID, 0L, 100L, 100L);

    // When
    String message = result.formatMessage("<@123456>");

    // Then
    assertThat(message).contains("Added");
    assertThat(message).contains("100");
  }
}
