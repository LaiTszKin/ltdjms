package ltdjms.discord.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link DotEnvLoader}. Verifies .env file loading behavior as specified in
 * BOT-CONFIG-ENV requirements.
 */
class DotEnvLoaderTest {

  @TempDir Path tempDir;

  @Test
  @DisplayName("should load key-value pairs from .env file")
  void shouldLoadKeyValuePairsFromDotEnvFile() throws IOException {
    // Given a .env file with key-value pairs
    Path envFile = tempDir.resolve(".env");
    Files.writeString(
        envFile,
        """
        DB_URL=jdbc:postgresql://localhost:5432/testdb
        DB_USERNAME=testuser
        DB_PASSWORD=testpass
        """);

    // When loading the .env file
    DotEnvLoader loader = new DotEnvLoader(tempDir);
    Map<String, String> values = loader.load();

    // Then all values should be loaded
    assertThat(values)
        .containsEntry("DB_URL", "jdbc:postgresql://localhost:5432/testdb")
        .containsEntry("DB_USERNAME", "testuser")
        .containsEntry("DB_PASSWORD", "testpass");
  }

  @Test
  @DisplayName("should return empty map when .env file does not exist")
  void shouldReturnEmptyMapWhenDotEnvFileDoesNotExist() {
    // Given no .env file exists
    DotEnvLoader loader = new DotEnvLoader(tempDir);

    // When loading
    Map<String, String> values = loader.load();

    // Then empty map should be returned
    assertThat(values).isEmpty();
  }

  @Test
  @DisplayName("should ignore empty lines and comments in .env file")
  void shouldIgnoreEmptyLinesAndComments() throws IOException {
    // Given a .env file with comments and empty lines
    Path envFile = tempDir.resolve(".env");
    Files.writeString(
        envFile,
        """
        # This is a comment
        DB_URL=jdbc:postgresql://localhost:5432/testdb

        # Another comment
        DB_USERNAME=testuser
        """);

    // When loading the .env file
    DotEnvLoader loader = new DotEnvLoader(tempDir);
    Map<String, String> values = loader.load();

    // Then only key-value pairs should be loaded
    assertThat(values)
        .hasSize(2)
        .containsEntry("DB_URL", "jdbc:postgresql://localhost:5432/testdb")
        .containsEntry("DB_USERNAME", "testuser");
  }

  @Test
  @DisplayName("should handle values with equals signs")
  void shouldHandleValuesWithEqualsSigns() throws IOException {
    // Given a .env file with values containing '='
    Path envFile = tempDir.resolve(".env");
    Files.writeString(
        envFile,
        """
        CONNECTION_STRING=host=localhost;port=5432;db=test
        """);

    // When loading the .env file
    DotEnvLoader loader = new DotEnvLoader(tempDir);
    Map<String, String> values = loader.load();

    // Then value should preserve all equals signs
    assertThat(values).containsEntry("CONNECTION_STRING", "host=localhost;port=5432;db=test");
  }

  @Test
  @DisplayName("should trim whitespace from keys and values")
  void shouldTrimWhitespaceFromKeysAndValues() throws IOException {
    // Given a .env file with whitespace around keys and values
    Path envFile = tempDir.resolve(".env");
    Files.writeString(
        envFile,
        """
          DB_URL  =  jdbc:postgresql://localhost:5432/testdb
        DB_USERNAME =testuser
        """);

    // When loading the .env file
    DotEnvLoader loader = new DotEnvLoader(tempDir);
    Map<String, String> values = loader.load();

    // Then whitespace should be trimmed
    assertThat(values)
        .containsEntry("DB_URL", "jdbc:postgresql://localhost:5432/testdb")
        .containsEntry("DB_USERNAME", "testuser");
  }

  @Test
  @DisplayName("should handle quoted values")
  void shouldHandleQuotedValues() throws IOException {
    // Given a .env file with quoted values
    Path envFile = tempDir.resolve(".env");
    Files.writeString(
        envFile,
        """
        DB_PASSWORD="password with spaces"
        API_KEY='single quoted'
        """);

    // When loading the .env file
    DotEnvLoader loader = new DotEnvLoader(tempDir);
    Map<String, String> values = loader.load();

    // Then quotes should be stripped and value preserved
    assertThat(values)
        .containsEntry("DB_PASSWORD", "password with spaces")
        .containsEntry("API_KEY", "single quoted");
  }

  @Test
  @DisplayName("should skip lines without equals sign")
  void shouldSkipLinesWithoutEqualsSign() throws IOException {
    // Given a .env file with invalid lines
    Path envFile = tempDir.resolve(".env");
    Files.writeString(
        envFile,
        """
        VALID_KEY=valid_value
        invalid line without equals
        ANOTHER_KEY=another_value
        """);

    // When loading the .env file
    DotEnvLoader loader = new DotEnvLoader(tempDir);
    Map<String, String> values = loader.load();

    // Then only valid lines should be loaded
    assertThat(values)
        .hasSize(2)
        .containsEntry("VALID_KEY", "valid_value")
        .containsEntry("ANOTHER_KEY", "another_value");
  }
}
