package ltdjms.discord.shared;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads environment variables from a .env file.
 *
 * <p>The loader reads key-value pairs from a .env file in the following format:
 *
 * <pre>
 * KEY=value
 * ANOTHER_KEY=another value
 * # Comments are ignored
 * </pre>
 *
 * <p>Features:
 *
 * <ul>
 *   <li>Lines starting with # are treated as comments
 *   <li>Empty lines are ignored
 *   <li>Whitespace around keys and values is trimmed
 *   <li>Quoted values (single or double) have quotes stripped
 *   <li>Values can contain = characters
 * </ul>
 */
public final class DotEnvLoader {

  private static final Logger LOG = LoggerFactory.getLogger(DotEnvLoader.class);

  private static final String DOT_ENV_FILE = ".env";

  private final Path directory;

  /**
   * Creates a DotEnvLoader for the specified directory.
   *
   * @param directory the directory containing the .env file
   */
  public DotEnvLoader(Path directory) {
    this.directory = directory;
  }

  /** Creates a DotEnvLoader for the current working directory. */
  public DotEnvLoader() {
    this(Path.of(System.getProperty("user.dir")));
  }

  /**
   * Loads environment variables from the .env file.
   *
   * @return an unmodifiable map of environment variables, or empty map if file doesn't exist
   */
  public Map<String, String> load() {
    Path envFile = directory.resolve(DOT_ENV_FILE);

    if (!Files.exists(envFile)) {
      LOG.debug("No {} file found at {}", DOT_ENV_FILE, envFile);
      return Collections.emptyMap();
    }

    try {
      Map<String, String> result = new HashMap<>();
      Files.lines(envFile).forEach(line -> parseLine(line, result));
      LOG.debug("Loaded {} variables from {}", result.size(), envFile);
      return Collections.unmodifiableMap(result);
    } catch (IOException e) {
      LOG.warn("Failed to read {} file at {}", DOT_ENV_FILE, envFile, e);
      return Collections.emptyMap();
    }
  }

  private void parseLine(String line, Map<String, String> result) {
    // Trim the line
    String trimmed = line.trim();

    // Skip empty lines and comments
    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
      return;
    }

    // Find the first equals sign
    int equalsIndex = trimmed.indexOf('=');
    if (equalsIndex <= 0) {
      // No equals sign or equals at start (invalid key)
      LOG.trace("Skipping invalid line: {}", trimmed);
      return;
    }

    // Extract key and value
    String key = trimmed.substring(0, equalsIndex).trim();
    String value = trimmed.substring(equalsIndex + 1).trim();

    // Strip quotes from value if present
    value = stripQuotes(value);

    if (!key.isEmpty()) {
      result.put(key, value);
    }
  }

  private String stripQuotes(String value) {
    if (value.length() >= 2) {
      char first = value.charAt(0);
      char last = value.charAt(value.length() - 1);

      // Handle double quotes
      if (first == '"' && last == '"') {
        return value.substring(1, value.length() - 1);
      }

      // Handle single quotes
      if (first == '\'' && last == '\'') {
        return value.substring(1, value.length() - 1);
      }
    }
    return value;
  }
}
