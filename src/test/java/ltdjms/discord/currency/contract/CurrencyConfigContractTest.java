package ltdjms.discord.currency.contract;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ltdjms.discord.currency.domain.GuildCurrencyConfig;

/**
 * Contract tests for currency configuration operations. Verifies that configuration responses
 * conform to the OpenAPI contract.
 */
class CurrencyConfigContractTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;

  @Test
  @DisplayName("GuildCurrencyConfig should contain all required fields per contract")
  void configShouldContainRequiredFields() {
    // Given - create a config per contract requirements
    Instant now = Instant.now();
    GuildCurrencyConfig config = new GuildCurrencyConfig(TEST_GUILD_ID, "Gold", "💰", now, now);

    // Then - verify all contract fields are present
    assertThat(config.guildId()).isEqualTo(TEST_GUILD_ID);
    assertThat(config.currencyName()).isEqualTo("Gold");
    assertThat(config.currencyIcon()).isEqualTo("💰");
  }

  @Test
  @DisplayName("Currency name should respect maximum length per contract")
  void currencyNameShouldRespectMaxLength() {
    // Given - contract specifies maxLength: 50
    String maxLengthName = "A".repeat(50);
    Instant now = Instant.now();

    // When/Then - should not throw
    GuildCurrencyConfig config =
        new GuildCurrencyConfig(TEST_GUILD_ID, maxLengthName, "🪙", now, now);
    assertThat(config.currencyName().length()).isLessThanOrEqualTo(50);
  }

  @Test
  @DisplayName("Currency name exceeding max length should be rejected")
  void currencyNameExceedingMaxLengthShouldBeRejected() {
    // Given - contract specifies maxLength: 50
    String tooLongName = "A".repeat(51);
    Instant now = Instant.now();

    // When/Then
    assertThatThrownBy(() -> new GuildCurrencyConfig(TEST_GUILD_ID, tooLongName, "🪙", now, now))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Currency icon should respect maximum length per contract")
  void currencyIconShouldRespectMaxLength() {
    // Given - contract specifies maxLength: 64
    String validIcon = "💎 Points"; // Text + emoji within limit
    Instant now = Instant.now();

    // When/Then - should not throw
    GuildCurrencyConfig config =
        new GuildCurrencyConfig(TEST_GUILD_ID, "Coins", validIcon, now, now);
    assertThat(config.currencyIcon().length()).isLessThanOrEqualTo(64);
  }

  @Test
  @DisplayName("Currency icon exceeding max length should be rejected")
  void currencyIconExceedingMaxLengthShouldBeRejected() {
    // Given - contract specifies maxLength: 64
    String tooLongIcon = "A".repeat(65);
    Instant now = Instant.now();

    // When/Then
    assertThatThrownBy(() -> new GuildCurrencyConfig(TEST_GUILD_ID, "Coins", tooLongIcon, now, now))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Currency name must not be blank")
  void currencyNameMustNotBeBlank() {
    Instant now = Instant.now();

    assertThatThrownBy(() -> new GuildCurrencyConfig(TEST_GUILD_ID, "", "🪙", now, now))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> new GuildCurrencyConfig(TEST_GUILD_ID, "   ", "🪙", now, now))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Currency icon must not be blank")
  void currencyIconMustNotBeBlank() {
    Instant now = Instant.now();

    assertThatThrownBy(() -> new GuildCurrencyConfig(TEST_GUILD_ID, "Coins", "", now, now))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> new GuildCurrencyConfig(TEST_GUILD_ID, "Coins", "   ", now, now))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Default configuration should have valid values")
  void defaultConfigShouldHaveValidValues() {
    // When
    GuildCurrencyConfig config = GuildCurrencyConfig.createDefault(TEST_GUILD_ID);

    // Then
    assertThat(config.guildId()).isEqualTo(TEST_GUILD_ID);
    assertThat(config.currencyName()).isEqualTo(GuildCurrencyConfig.DEFAULT_NAME);
    assertThat(config.currencyIcon()).isEqualTo(GuildCurrencyConfig.DEFAULT_ICON);
    assertThat(config.createdAt()).isNotNull();
    assertThat(config.updatedAt()).isNotNull();
  }

  @Test
  @DisplayName("withUpdates should create new config with updated values")
  void withUpdatesShouldCreateNewConfig() {
    // Given
    GuildCurrencyConfig original = GuildCurrencyConfig.createDefault(TEST_GUILD_ID);

    // When
    GuildCurrencyConfig updated = original.withUpdates("Gold", "💰");

    // Then
    assertThat(updated.guildId()).isEqualTo(original.guildId());
    assertThat(updated.currencyName()).isEqualTo("Gold");
    assertThat(updated.currencyIcon()).isEqualTo("💰");
    assertThat(updated.createdAt()).isEqualTo(original.createdAt());
    // updatedAt should be newer
    assertThat(updated.updatedAt()).isAfterOrEqualTo(original.updatedAt());
  }

  @Test
  @DisplayName("withUpdates with null values should preserve existing values")
  void withUpdatesWithNullShouldPreserveExisting() {
    // Given
    Instant now = Instant.now();
    GuildCurrencyConfig original = new GuildCurrencyConfig(TEST_GUILD_ID, "Gold", "💰", now, now);

    // When - only update name
    GuildCurrencyConfig updatedName = original.withUpdates("Silver", null);

    // Then
    assertThat(updatedName.currencyName()).isEqualTo("Silver");
    assertThat(updatedName.currencyIcon()).isEqualTo("💰"); // Preserved

    // When - only update icon
    GuildCurrencyConfig updatedIcon = original.withUpdates(null, "🥈");

    // Then
    assertThat(updatedIcon.currencyName()).isEqualTo("Gold"); // Preserved
    assertThat(updatedIcon.currencyIcon()).isEqualTo("🥈");
  }
}
