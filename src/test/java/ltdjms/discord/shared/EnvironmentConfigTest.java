package ltdjms.discord.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link EnvironmentConfig}.
 *
 * <p>These tests focus on default values when no environment overrides are present.
 */
class EnvironmentConfigTest {

  @Test
  void defaultDatabaseUrlUsesLocalCurrencyBotDatabase() {
    // Use a test directory without .env so we only see defaults and application.properties,
    // independent of the developer's local .env file.
    EnvironmentConfig config = new EnvironmentConfig(Path.of("src/test/resources"));

    // When DB_URL is not set in the environment, we should fall back to the
    // hardcoded default JDBC URL instead of a placeholder literal.
    assertThat(config.getDatabaseUrl()).isEqualTo("jdbc:postgresql://localhost:5432/currency_bot");
  }
}
