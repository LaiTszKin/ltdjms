package ltdjms.discord.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Tests for .env integration in {@link EnvironmentConfig}. Verifies the priority order: Environment
 * variables > .env > application.properties > defaults.
 *
 * <p>Note: System environment variables cannot be easily controlled in tests, so these tests focus
 * on verifying the .env > application.properties > defaults chain. The environment variable
 * priority is documented and assumed to work based on code review.
 */
class EnvironmentConfigDotEnvIntegrationTest {

  private static final String DOC_PATH = "docs/development/configuration.md";

  @TempDir Path tempDir;

  @Nested
  @DisplayName("Priority Order Tests")
  class PriorityOrderTests {

    @Test
    @DisplayName(
        "should use .env value when both .env and application.properties have the same key")
    void dotEnvTakesPrecedenceOverApplicationProperties() throws IOException {
      // Skip if environment variable is set (would override both)
      if (System.getenv("DB_POOL_MAX_SIZE") != null) {
        return;
      }

      // Given: .env has DB_POOL_MAX_SIZE=99 (different from app.properties default of 10)
      Path envFile = tempDir.resolve(".env");
      Files.writeString(envFile, "DB_POOL_MAX_SIZE=99\n");

      // When
      EnvironmentConfig config = new EnvironmentConfig(tempDir);

      // Then: .env value should be used
      assertThat(config.getPoolMaxSize()).isEqualTo(99);
    }

    @Test
    @DisplayName("should use application.properties value when .env does not have the key")
    void applicationPropertiesUsedWhenDotEnvMissingKey() throws IOException {
      // Skip if environment variable is set
      if (System.getenv("DB_POOL_MIN_IDLE") != null) {
        return;
      }

      // Given: .env exists but doesn't have DB_POOL_MIN_IDLE
      Path envFile = tempDir.resolve(".env");
      Files.writeString(envFile, "SOME_OTHER_KEY=value\n");

      // When
      EnvironmentConfig config = new EnvironmentConfig(tempDir);

      // Then: application.properties value (2) or default should be used
      // Note: main/resources/application.properties has db.pool.minimum-idle=2
      assertThat(config.getPoolMinIdle()).isEqualTo(2);
    }

    @Test
    @DisplayName(
        "should use default value when neither .env nor application.properties has the key")
    void defaultUsedWhenNothingConfigured() {
      // Skip if environment variables are set
      if (System.getenv("DB_POOL_CONNECTION_TIMEOUT") != null) {
        return;
      }

      // Given: empty .env directory (no .env file)
      // and application.properties has the same value as default

      // When
      EnvironmentConfig config = new EnvironmentConfig(tempDir);

      // Then: default value should be used
      // Default connection timeout is 30000ms
      assertThat(config.getPoolConnectionTimeout()).isEqualTo(30000L);
    }

    @Test
    @DisplayName("should use .env database URL over application.properties")
    void dotEnvDatabaseUrlTakesPrecedence() throws IOException {
      // Skip if environment variable is set
      if (System.getenv("DB_URL") != null) {
        return;
      }

      // Given: .env has custom DB_URL
      Path envFile = tempDir.resolve(".env");
      Files.writeString(envFile, "DB_URL=jdbc:postgresql://custom-host:9999/custom_db\n");

      // When
      EnvironmentConfig config = new EnvironmentConfig(tempDir);

      // Then: .env value should be used
      assertThat(config.getDatabaseUrl()).isEqualTo("jdbc:postgresql://custom-host:9999/custom_db");
    }

    @Test
    @DisplayName("should use application.properties database URL when .env is missing")
    void applicationPropertiesDatabaseUrlWhenNoDotEnv() {
      // Skip if environment variable is set
      if (System.getenv("DB_URL") != null) {
        return;
      }

      // Given: no .env file in temp directory

      // When
      EnvironmentConfig config = new EnvironmentConfig(tempDir);

      // Then: application.properties value should be used
      // main/resources/application.properties has:
      // db.url=jdbc:postgresql://localhost:5432/currency_bot
      assertThat(config.getDatabaseUrl())
          .isEqualTo("jdbc:postgresql://localhost:5432/currency_bot");
    }

    @Test
    @DisplayName("should build database URL from DATABASE_* values in .env when DB_URL is missing")
    void buildsDatabaseUrlFromDatabaseEnvValues() throws IOException {
      if (System.getenv("DB_URL") != null
          || System.getenv("DATABASE_HOST") != null
          || System.getenv("DATABASE_PORT") != null
          || System.getenv("DATABASE_NAME") != null) {
        return;
      }

      Path envFile = tempDir.resolve(".env");
      Files.writeString(
          envFile,
          """
          DATABASE_HOST=custom-host
          DATABASE_PORT=5439
          DATABASE_NAME=ltdjms
          """);

      EnvironmentConfig config = new EnvironmentConfig(tempDir);

      assertThat(config.getDatabaseUrl()).isEqualTo("jdbc:postgresql://custom-host:5439/ltdjms");
    }

    @Test
    @DisplayName("should preserve explicit ECPAY_RETURN_URL over derived APP_PUBLIC_BASE_URL")
    void explicitEcpayReturnUrlOverridesDerivedBaseUrl() throws IOException {
      if (System.getenv("ECPAY_RETURN_URL") != null
          || System.getenv("APP_PUBLIC_BASE_URL") != null) {
        return;
      }

      Path envFile = tempDir.resolve(".env");
      Files.writeString(
          envFile,
          """
          APP_PUBLIC_BASE_URL=pay.example.com/base/
          ECPAY_RETURN_URL=https://override.example.com/custom/callback
          ECPAY_CALLBACK_PATH=/ecpay/callback
          """);

      EnvironmentConfig config = new EnvironmentConfig(tempDir);

      assertThat(config.getAppPublicBaseUrl()).isEqualTo("https://pay.example.com/base");
      assertThat(config.getEcpayReturnUrl())
          .isEqualTo("https://override.example.com/custom/callback");
    }

    @Test
    @DisplayName(
        "should derive callback URL from APP_PUBLIC_BASE_URL when explicit return URL is missing")
    void derivesCallbackUrlFromPublicBaseUrlWhenExplicitMissing() throws IOException {
      if (System.getenv("ECPAY_RETURN_URL") != null
          || System.getenv("APP_PUBLIC_BASE_URL") != null) {
        return;
      }

      Path envFile = tempDir.resolve(".env");
      Files.writeString(
          envFile,
          """
          APP_PUBLIC_BASE_URL=https://pay.example.com/root/
          ECPAY_CALLBACK_PATH=ecpay/callback
          """);

      EnvironmentConfig config = new EnvironmentConfig(tempDir);

      assertThat(config.getAppPublicBaseUrl()).isEqualTo("https://pay.example.com/root");
      assertThat(config.getEcpayReturnUrl())
          .isEqualTo("https://pay.example.com/root/ecpay/callback");
    }
  }

  @Nested
  @DisplayName("Canonical Schema Regression Tests")
  class CanonicalSchemaRegressionTests {

    @Test
    @DisplayName("should keep application.properties as the only live packaged defaults schema")
    void shouldKeepApplicationPropertiesAsOnlyLivePackagedDefaultsSchema() {
      Config propertiesConfig = ConfigFactory.parseResources("application.properties");
      Config compatibilityShim = ConfigFactory.parseResources("application.conf");

      assertThat(propertiesConfig.hasPath("discord.bot.token")).isTrue();
      assertThat(propertiesConfig.hasPath("db.url")).isTrue();
      assertThat(propertiesConfig.hasPath("database.host")).isTrue();
      assertThat(propertiesConfig.hasPath("ai.service.base-url")).isTrue();
      assertThat(propertiesConfig.hasPath("payment.ecpay.callback.shared-secret")).isTrue();
      assertThat(propertiesConfig.hasPath("shop.fulfillment.signing-secret")).isTrue();
      assertThat(propertiesConfig.hasPath("discord.bot-token")).isFalse();
      assertThat(propertiesConfig.hasPath("aichat.base-url")).isFalse();
      assertThat(propertiesConfig.hasPath("shop.ecpay.callback.shared-secret")).isFalse();
      assertThat(propertiesConfig.hasPath("product.fulfillment.signing-secret")).isFalse();
      assertThat(compatibilityShim.entrySet()).isEmpty();
    }

    @Test
    @DisplayName("should document the same canonical schema and fallback chain")
    void shouldDocumentTheSameCanonicalSchemaAndFallbackChain() throws IOException {
      String documentation = Files.readString(Path.of(DOC_PATH));

      assertThat(documentation)
          .contains("1. **系統環境變數**")
          .contains("2. **`.env` 檔案**")
          .contains("3. **`application.properties`**")
          .contains("4. **程式內建預設值**");
      assertThat(documentation)
          .contains("`application.conf` 僅保留為 compatibility shim")
          .contains("`discord.bot.token`")
          .contains("`ai.service.base-url`")
          .contains("`payment.ecpay.callback.shared-secret`")
          .contains("`shop.fulfillment.signing-secret`")
          .contains("`prompts.dir.path`")
          .contains("`prompts.max-size`");
      assertThat(documentation)
          .doesNotContain("`aichat.base-url`")
          .doesNotContain("`shop.ecpay.callback.shared-secret`")
          .doesNotContain("`product.fulfillment.signing-secret`");
    }
  }

  @Nested
  @DisplayName("Fallback and Resilience Tests")
  class FallbackAndResilienceTests {

    @Test
    @DisplayName("should gracefully handle malformed .env file and use defaults")
    void shouldGracefullyHandleMalformedDotEnvFile() throws IOException {
      // Given a malformed .env file
      Path envFile = tempDir.resolve(".env");
      Files.writeString(
          envFile,
          """
          not a valid env file format
          also invalid
          """);

      // When creating EnvironmentConfig
      EnvironmentConfig config = new EnvironmentConfig(tempDir);

      // Then it should not throw and use defaults
      assertThat(config.getPoolMaxSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("should handle empty .env file gracefully")
    void shouldHandleEmptyDotEnvFile() throws IOException {
      // Given an empty .env file
      Path envFile = tempDir.resolve(".env");
      Files.writeString(envFile, "");

      // When
      EnvironmentConfig config = new EnvironmentConfig(tempDir);

      // Then: should use application.properties/defaults
      assertThat(config.getPoolMaxSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("should handle .env with only comments")
    void shouldHandleDotEnvWithOnlyComments() throws IOException {
      // Given a .env file with only comments
      Path envFile = tempDir.resolve(".env");
      Files.writeString(
          envFile,
          """
          # This is a comment
          # Another comment
          """);

      // When
      EnvironmentConfig config = new EnvironmentConfig(tempDir);

      // Then: should use application.properties/defaults
      assertThat(config.getPoolMaxSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("should handle non-existent .env directory gracefully")
    void shouldHandleNonExistentDotEnvDirectory() {
      // Given a non-existent directory
      Path nonExistent = Path.of("/non/existent/path/that/does/not/exist");

      // When
      EnvironmentConfig config = new EnvironmentConfig(nonExistent);

      // Then: should use defaults
      assertThat(config.getPoolMaxSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("should handle invalid numeric values in .env gracefully")
    void shouldHandleInvalidNumericValues() throws IOException {
      // Given a .env file with invalid numeric value
      Path envFile = tempDir.resolve(".env");
      Files.writeString(envFile, "DB_POOL_MAX_SIZE=not_a_number\n");

      // When
      EnvironmentConfig config = new EnvironmentConfig(tempDir);

      // Then: should use default value
      assertThat(config.getPoolMaxSize()).isEqualTo(10);
    }
  }

  @Nested
  @DisplayName("Discord Bot Token Tests")
  class DiscordBotTokenTests {

    @Test
    @DisplayName("should require Discord bot token to be configured")
    void shouldRequireDiscordBotTokenToBeConfigured() throws IOException {
      // Given a .env file without DISCORD_BOT_TOKEN
      Path envFile = tempDir.resolve(".env");
      Files.writeString(envFile, "DB_URL=jdbc:postgresql://localhost:5432/testdb\n");

      // When creating EnvironmentConfig and getting token
      EnvironmentConfig config = new EnvironmentConfig(tempDir);

      // Then it should throw if token is not set via env var
      if (System.getenv("DISCORD_BOT_TOKEN") == null) {
        assertThatThrownBy(config::getDiscordBotToken)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DISCORD_BOT_TOKEN");
      }
    }

    @Test
    @DisplayName("should load Discord bot token from .env file")
    void shouldLoadDiscordBotTokenFromDotEnv() throws IOException {
      // Skip if env var is set (would override .env)
      if (System.getenv("DISCORD_BOT_TOKEN") != null) {
        return;
      }

      // Given a .env file with DISCORD_BOT_TOKEN
      Path envFile = tempDir.resolve(".env");
      Files.writeString(envFile, "DISCORD_BOT_TOKEN=test-token-from-env\n");

      // When
      EnvironmentConfig config = new EnvironmentConfig(tempDir);

      // Then
      assertThat(config.getDiscordBotToken()).isEqualTo("test-token-from-env");
    }
  }

  @Nested
  @DisplayName("All Configuration Keys Tests")
  class AllConfigurationKeysTests {

    @Test
    @DisplayName("should load all database configuration from .env")
    void shouldLoadAllDatabaseConfigFromDotEnv() throws IOException {
      // Skip if any relevant env vars are set
      if (System.getenv("DB_URL") != null
          || System.getenv("DB_USERNAME") != null
          || System.getenv("DB_PASSWORD") != null) {
        return;
      }

      // Given .env with all database settings
      Path envFile = tempDir.resolve(".env");
      Files.writeString(
          envFile,
          """
          DB_URL=jdbc:postgresql://envhost:5432/envdb
          DB_USERNAME=env_user
          DB_PASSWORD=env_password
          """);

      // When
      EnvironmentConfig config = new EnvironmentConfig(tempDir);

      // Then
      assertThat(config.getDatabaseUrl()).isEqualTo("jdbc:postgresql://envhost:5432/envdb");
      assertThat(config.getDatabaseUsername()).isEqualTo("env_user");
      assertThat(config.getDatabasePassword()).isEqualTo("env_password");
    }

    @Test
    @DisplayName("should load all pool configuration from .env")
    void shouldLoadAllPoolConfigFromDotEnv() throws IOException {
      // Skip if any relevant env vars are set
      if (System.getenv("DB_POOL_MAX_SIZE") != null
          || System.getenv("DB_POOL_MIN_IDLE") != null
          || System.getenv("DB_POOL_CONNECTION_TIMEOUT") != null
          || System.getenv("DB_POOL_IDLE_TIMEOUT") != null
          || System.getenv("DB_POOL_MAX_LIFETIME") != null) {
        return;
      }

      // Given .env with all pool settings
      Path envFile = tempDir.resolve(".env");
      Files.writeString(
          envFile,
          """
          DB_POOL_MAX_SIZE=25
          DB_POOL_MIN_IDLE=5
          DB_POOL_CONNECTION_TIMEOUT=60000
          DB_POOL_IDLE_TIMEOUT=120000
          DB_POOL_MAX_LIFETIME=3600000
          """);

      // When
      EnvironmentConfig config = new EnvironmentConfig(tempDir);

      // Then
      assertThat(config.getPoolMaxSize()).isEqualTo(25);
      assertThat(config.getPoolMinIdle()).isEqualTo(5);
      assertThat(config.getPoolConnectionTimeout()).isEqualTo(60000L);
      assertThat(config.getPoolIdleTimeout()).isEqualTo(120000L);
      assertThat(config.getPoolMaxLifetime()).isEqualTo(3600000L);
    }

    @Test
    @DisplayName("should load AI service api key from .env")
    void shouldLoadAIServiceApiKeyFromDotEnv() throws IOException {
      // Skip if env var is set (would override .env)
      if (System.getenv("AI_SERVICE_API_KEY") != null) {
        return;
      }

      // Given .env with AI service API key
      Path envFile = tempDir.resolve(".env");
      Files.writeString(envFile, "AI_SERVICE_API_KEY=test-ai-key\n");

      // When
      EnvironmentConfig config = new EnvironmentConfig(tempDir);

      // Then
      assertThat(config.getAIServiceApiKey()).isEqualTo("test-ai-key");
    }
  }
}
