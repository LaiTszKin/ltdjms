package ltdjms.discord.shared;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Loads configuration from environment variables with fallback to .env file,
 * application.conf/properties, and built-in defaults.
 *
 * <p>This implementation uses Typesafe Config internally to manage configuration sources while
 * maintaining backward compatibility with the existing API.
 *
 * <p>Priority order (highest to lowest):
 *
 * <ol>
 *   <li>System environment variables
 *   <li>.env file in project root
 *   <li>application.conf / application.properties
 *   <li>Built-in defaults
 * </ol>
 */
public final class EnvironmentConfig {

  private static final Logger LOG = LoggerFactory.getLogger(EnvironmentConfig.class);

  // Environment variable names
  private static final String ENV_DISCORD_BOT_TOKEN = "DISCORD_BOT_TOKEN";
  private static final String ENV_DB_URL = "DB_URL";
  private static final String ENV_DB_USERNAME = "DB_USERNAME";
  private static final String ENV_DB_PASSWORD = "DB_PASSWORD";
  private static final String ENV_DATABASE_HOST = "DATABASE_HOST";
  private static final String ENV_DATABASE_PORT = "DATABASE_PORT";
  private static final String ENV_DATABASE_NAME = "DATABASE_NAME";
  private static final String ENV_DATABASE_USER = "DATABASE_USER";
  private static final String ENV_DATABASE_PASSWORD = "DATABASE_PASSWORD";
  private static final String ENV_DB_POOL_MAX_SIZE = "DB_POOL_MAX_SIZE";
  private static final String ENV_DB_POOL_MIN_IDLE = "DB_POOL_MIN_IDLE";
  private static final String ENV_DB_POOL_CONNECTION_TIMEOUT = "DB_POOL_CONNECTION_TIMEOUT";
  private static final String ENV_DB_POOL_IDLE_TIMEOUT = "DB_POOL_IDLE_TIMEOUT";
  private static final String ENV_DB_POOL_MAX_LIFETIME = "DB_POOL_MAX_LIFETIME";
  private static final String ENV_REDIS_URI = "REDIS_URI";
  private static final String ENV_AI_SERVICE_BASE_URL = "AI_SERVICE_BASE_URL";
  private static final String ENV_AI_SERVICE_API_KEY = "AI_SERVICE_API_KEY";
  private static final String ENV_AI_SERVICE_MODEL = "AI_SERVICE_MODEL";
  private static final String ENV_AI_SERVICE_TEMPERATURE = "AI_SERVICE_TEMPERATURE";
  private static final String ENV_AI_SERVICE_TIMEOUT_SECONDS = "AI_SERVICE_TIMEOUT_SECONDS";
  private static final String ENV_PROMPTS_DIR_PATH = "PROMPTS_DIR_PATH";
  private static final String ENV_PROMPT_MAX_SIZE_BYTES = "PROMPT_MAX_SIZE_BYTES";
  private static final String ENV_AI_SHOW_REASONING = "AI_SHOW_REASONING";
  private static final String ENV_AI_MARKDOWN_VALIDATION_ENABLED = "AI_MARKDOWN_VALIDATION_ENABLED";
  private static final String ENV_AI_MARKDOWN_VALIDATION_STREAMING_BYPASS =
      "AI_MARKDOWN_VALIDATION_STREAMING_BYPASS";
  private static final String ENV_ECPAY_MERCHANT_ID = "ECPAY_MERCHANT_ID";
  private static final String ENV_ECPAY_HASH_KEY = "ECPAY_HASH_KEY";
  private static final String ENV_ECPAY_HASH_IV = "ECPAY_HASH_IV";
  private static final String ENV_ECPAY_RETURN_URL = "ECPAY_RETURN_URL";
  private static final String ENV_ECPAY_STAGE_MODE = "ECPAY_STAGE_MODE";
  private static final String ENV_ECPAY_CVS_EXPIRE_MINUTES = "ECPAY_CVS_EXPIRE_MINUTES";

  // Config paths for Typesafe Config
  private static final String CFG_DISCORD_BOT_TOKEN = "discord.bot.token";
  private static final String CFG_DB_URL = "db.url";
  private static final String CFG_REDIS_URI = "redis.uri";
  private static final String CFG_DB_USERNAME = "db.username";
  private static final String CFG_DB_PASSWORD = "db.password";
  private static final String CFG_DATABASE_HOST = "database.host";
  private static final String CFG_DATABASE_PORT = "database.port";
  private static final String CFG_DATABASE_NAME = "database.name";
  private static final String CFG_DATABASE_USERNAME = "database.username";
  private static final String CFG_DATABASE_PASSWORD = "database.password";
  private static final String CFG_DB_POOL_MAX_SIZE = "db.pool.maximum-pool-size";
  private static final String CFG_DB_POOL_MIN_IDLE = "db.pool.minimum-idle";
  private static final String CFG_DB_POOL_CONNECTION_TIMEOUT = "db.pool.connection-timeout";
  private static final String CFG_DB_POOL_IDLE_TIMEOUT = "db.pool.idle-timeout";
  private static final String CFG_DB_POOL_MAX_LIFETIME = "db.pool.max-lifetime";
  private static final String CFG_AI_SERVICE_BASE_URL = "ai.service.base-url";
  private static final String CFG_AI_SERVICE_API_KEY = "ai.service.api-key";
  private static final String CFG_AI_SERVICE_MODEL = "ai.service.model";
  private static final String CFG_AI_SERVICE_TEMPERATURE = "ai.service.temperature";
  private static final String CFG_AI_SERVICE_TIMEOUT_SECONDS = "ai.service.timeout-seconds";
  private static final String CFG_PROMPTS_DIR_PATH = "prompts.dir.path";
  private static final String CFG_PROMPT_MAX_SIZE = "prompts.max-size";
  private static final String CFG_AI_SHOW_REASONING = "ai.show-reasoning";
  private static final String CFG_AI_MARKDOWN_VALIDATION_ENABLED = "ai.markdown-validation.enabled";
  private static final String CFG_AI_MARKDOWN_VALIDATION_STREAMING_BYPASS =
      "ai.markdown-validation.streaming-bypass";
  private static final String CFG_ECPAY_MERCHANT_ID = "payment.ecpay.merchant-id";
  private static final String CFG_ECPAY_HASH_KEY = "payment.ecpay.hash-key";
  private static final String CFG_ECPAY_HASH_IV = "payment.ecpay.hash-iv";
  private static final String CFG_ECPAY_RETURN_URL = "payment.ecpay.return-url";
  private static final String CFG_ECPAY_STAGE_MODE = "payment.ecpay.stage-mode";
  private static final String CFG_ECPAY_CVS_EXPIRE_MINUTES = "payment.ecpay.cvs-expire-minutes";

  // Default values
  private static final String DEFAULT_REDIS_URI = "redis://localhost:6379";
  private static final String DEFAULT_DB_URL = "jdbc:postgresql://localhost:5432/currency_bot";
  private static final String DEFAULT_DB_USERNAME = "postgres";
  private static final String DEFAULT_DB_PASSWORD = "postgres";
  private static final int DEFAULT_POOL_MAX_SIZE = 10;
  private static final int DEFAULT_POOL_MIN_IDLE = 2;
  private static final long DEFAULT_POOL_CONNECTION_TIMEOUT = 30000L;
  private static final long DEFAULT_POOL_IDLE_TIMEOUT = 600000L;
  private static final long DEFAULT_POOL_MAX_LIFETIME = 1800000L;
  private static final String DEFAULT_AI_SERVICE_BASE_URL = "https://api.openai.com/v1";
  private static final String DEFAULT_AI_SERVICE_MODEL = "gpt-3.5-turbo";
  private static final double DEFAULT_AI_SERVICE_TEMPERATURE = 0.7;
  private static final int DEFAULT_AI_SERVICE_TIMEOUT_SECONDS = 30;
  private static final String DEFAULT_PROMPTS_DIR_PATH = "./prompts";
  private static final long DEFAULT_PROMPT_MAX_SIZE_BYTES = 1048576L; // 1MB
  private static final boolean DEFAULT_AI_SHOW_REASONING = false;
  private static final boolean DEFAULT_AI_MARKDOWN_VALIDATION_ENABLED = true;
  private static final boolean DEFAULT_AI_MARKDOWN_VALIDATION_STREAMING_BYPASS = false;
  private static final String DEFAULT_ECPAY_MERCHANT_ID = "";
  private static final String DEFAULT_ECPAY_HASH_KEY = "";
  private static final String DEFAULT_ECPAY_HASH_IV = "";
  private static final String DEFAULT_ECPAY_RETURN_URL = "";
  private static final boolean DEFAULT_ECPAY_STAGE_MODE = true;
  private static final int DEFAULT_ECPAY_CVS_EXPIRE_MINUTES = 10080;

  private final Config config;
  private final Map<String, String> dotEnvValues;

  /** Creates an EnvironmentConfig loading .env from the current working directory. */
  public EnvironmentConfig() {
    this(Path.of(System.getProperty("user.dir")));
  }

  /**
   * Creates an EnvironmentConfig loading .env from the specified directory.
   *
   * @param dotEnvDirectory the directory containing the .env file
   */
  public EnvironmentConfig(Path dotEnvDirectory) {
    this.dotEnvValues = new DotEnvLoader(dotEnvDirectory).load();
    this.config = buildConfig();
    if (!dotEnvValues.isEmpty()) {
      LOG.info("Loaded {} values from .env file", dotEnvValues.size());
    }
  }

  /**
   * Builds the layered Typesafe Config with proper priority order. Priority: System env vars > .env
   * values > application.conf/properties > defaults
   */
  private Config buildConfig() {
    // Build defaults config
    Map<String, Object> defaults = new HashMap<>();
    defaults.put(CFG_REDIS_URI, DEFAULT_REDIS_URI);
    defaults.put(CFG_DB_URL, DEFAULT_DB_URL);
    defaults.put(CFG_DB_USERNAME, DEFAULT_DB_USERNAME);
    defaults.put(CFG_DB_PASSWORD, DEFAULT_DB_PASSWORD);
    defaults.put(CFG_DB_POOL_MAX_SIZE, DEFAULT_POOL_MAX_SIZE);
    defaults.put(CFG_DB_POOL_MIN_IDLE, DEFAULT_POOL_MIN_IDLE);
    defaults.put(CFG_DB_POOL_CONNECTION_TIMEOUT, DEFAULT_POOL_CONNECTION_TIMEOUT);
    defaults.put(CFG_DB_POOL_IDLE_TIMEOUT, DEFAULT_POOL_IDLE_TIMEOUT);
    defaults.put(CFG_DB_POOL_MAX_LIFETIME, DEFAULT_POOL_MAX_LIFETIME);
    defaults.put(CFG_AI_SERVICE_BASE_URL, DEFAULT_AI_SERVICE_BASE_URL);
    defaults.put(CFG_AI_SERVICE_MODEL, DEFAULT_AI_SERVICE_MODEL);
    defaults.put(CFG_AI_SERVICE_TEMPERATURE, DEFAULT_AI_SERVICE_TEMPERATURE);
    defaults.put(CFG_AI_SERVICE_TIMEOUT_SECONDS, DEFAULT_AI_SERVICE_TIMEOUT_SECONDS);
    defaults.put(CFG_PROMPTS_DIR_PATH, DEFAULT_PROMPTS_DIR_PATH);
    defaults.put(CFG_PROMPT_MAX_SIZE, DEFAULT_PROMPT_MAX_SIZE_BYTES);
    defaults.put(CFG_AI_SHOW_REASONING, DEFAULT_AI_SHOW_REASONING);
    defaults.put(CFG_AI_MARKDOWN_VALIDATION_ENABLED, DEFAULT_AI_MARKDOWN_VALIDATION_ENABLED);
    defaults.put(
        CFG_AI_MARKDOWN_VALIDATION_STREAMING_BYPASS,
        DEFAULT_AI_MARKDOWN_VALIDATION_STREAMING_BYPASS);
    defaults.put(CFG_ECPAY_MERCHANT_ID, DEFAULT_ECPAY_MERCHANT_ID);
    defaults.put(CFG_ECPAY_HASH_KEY, DEFAULT_ECPAY_HASH_KEY);
    defaults.put(CFG_ECPAY_HASH_IV, DEFAULT_ECPAY_HASH_IV);
    defaults.put(CFG_ECPAY_RETURN_URL, DEFAULT_ECPAY_RETURN_URL);
    defaults.put(CFG_ECPAY_STAGE_MODE, DEFAULT_ECPAY_STAGE_MODE);
    defaults.put(CFG_ECPAY_CVS_EXPIRE_MINUTES, DEFAULT_ECPAY_CVS_EXPIRE_MINUTES);
    Config defaultsConfig = ConfigFactory.parseMap(defaults);

    // Load application.conf/properties (standard Typesafe Config behavior)
    Config applicationConfig = ConfigFactory.load();

    // Build .env values as config (mapped to config paths)
    Map<String, Object> dotEnvMapped = new HashMap<>();
    mapEnvToConfig(dotEnvMapped, ENV_DISCORD_BOT_TOKEN, CFG_DISCORD_BOT_TOKEN);
    mapEnvToConfig(dotEnvMapped, ENV_REDIS_URI, CFG_REDIS_URI);
    mapEnvToConfig(dotEnvMapped, ENV_DB_URL, CFG_DB_URL);
    mapEnvToConfig(dotEnvMapped, ENV_DB_USERNAME, CFG_DB_USERNAME);
    mapEnvToConfig(dotEnvMapped, ENV_DB_PASSWORD, CFG_DB_PASSWORD);
    mapEnvToConfigInt(dotEnvMapped, ENV_DB_POOL_MAX_SIZE, CFG_DB_POOL_MAX_SIZE);
    mapEnvToConfigInt(dotEnvMapped, ENV_DB_POOL_MIN_IDLE, CFG_DB_POOL_MIN_IDLE);
    mapEnvToConfigLong(
        dotEnvMapped, ENV_DB_POOL_CONNECTION_TIMEOUT, CFG_DB_POOL_CONNECTION_TIMEOUT);
    mapEnvToConfigLong(dotEnvMapped, ENV_DB_POOL_IDLE_TIMEOUT, CFG_DB_POOL_IDLE_TIMEOUT);
    mapEnvToConfigLong(dotEnvMapped, ENV_DB_POOL_MAX_LIFETIME, CFG_DB_POOL_MAX_LIFETIME);
    mapEnvToConfig(dotEnvMapped, ENV_AI_SERVICE_BASE_URL, CFG_AI_SERVICE_BASE_URL);
    mapEnvToConfig(dotEnvMapped, ENV_AI_SERVICE_API_KEY, CFG_AI_SERVICE_API_KEY);
    mapEnvToConfig(dotEnvMapped, ENV_AI_SERVICE_MODEL, CFG_AI_SERVICE_MODEL);
    mapEnvToConfigDouble(dotEnvMapped, ENV_AI_SERVICE_TEMPERATURE, CFG_AI_SERVICE_TEMPERATURE);
    mapEnvToConfigInt(dotEnvMapped, ENV_AI_SERVICE_TIMEOUT_SECONDS, CFG_AI_SERVICE_TIMEOUT_SECONDS);
    mapEnvToConfig(dotEnvMapped, ENV_PROMPTS_DIR_PATH, CFG_PROMPTS_DIR_PATH);
    mapEnvToConfigLong(dotEnvMapped, ENV_PROMPT_MAX_SIZE_BYTES, CFG_PROMPT_MAX_SIZE);
    mapEnvToConfigBoolean(dotEnvMapped, ENV_AI_SHOW_REASONING, CFG_AI_SHOW_REASONING);
    mapEnvToConfigBoolean(
        dotEnvMapped, ENV_AI_MARKDOWN_VALIDATION_ENABLED, CFG_AI_MARKDOWN_VALIDATION_ENABLED);
    mapEnvToConfigBoolean(
        dotEnvMapped,
        ENV_AI_MARKDOWN_VALIDATION_STREAMING_BYPASS,
        CFG_AI_MARKDOWN_VALIDATION_STREAMING_BYPASS);
    mapEnvToConfig(dotEnvMapped, ENV_ECPAY_MERCHANT_ID, CFG_ECPAY_MERCHANT_ID);
    mapEnvToConfig(dotEnvMapped, ENV_ECPAY_HASH_KEY, CFG_ECPAY_HASH_KEY);
    mapEnvToConfig(dotEnvMapped, ENV_ECPAY_HASH_IV, CFG_ECPAY_HASH_IV);
    mapEnvToConfig(dotEnvMapped, ENV_ECPAY_RETURN_URL, CFG_ECPAY_RETURN_URL);
    mapEnvToConfigBoolean(dotEnvMapped, ENV_ECPAY_STAGE_MODE, CFG_ECPAY_STAGE_MODE);
    mapEnvToConfigInt(dotEnvMapped, ENV_ECPAY_CVS_EXPIRE_MINUTES, CFG_ECPAY_CVS_EXPIRE_MINUTES);
    Config dotEnvConfig = ConfigFactory.parseMap(dotEnvMapped);

    // Build system env vars as config (highest priority)
    Map<String, Object> sysEnvMapped = new HashMap<>();
    mapSysEnvToConfig(sysEnvMapped, ENV_DISCORD_BOT_TOKEN, CFG_DISCORD_BOT_TOKEN);
    mapSysEnvToConfig(sysEnvMapped, ENV_REDIS_URI, CFG_REDIS_URI);
    mapSysEnvToConfig(sysEnvMapped, ENV_DB_URL, CFG_DB_URL);
    mapSysEnvToConfig(sysEnvMapped, ENV_DB_USERNAME, CFG_DB_USERNAME);
    mapSysEnvToConfig(sysEnvMapped, ENV_DB_PASSWORD, CFG_DB_PASSWORD);
    mapSysEnvToConfigInt(sysEnvMapped, ENV_DB_POOL_MAX_SIZE, CFG_DB_POOL_MAX_SIZE);
    mapSysEnvToConfigInt(sysEnvMapped, ENV_DB_POOL_MIN_IDLE, CFG_DB_POOL_MIN_IDLE);
    mapSysEnvToConfigLong(
        sysEnvMapped, ENV_DB_POOL_CONNECTION_TIMEOUT, CFG_DB_POOL_CONNECTION_TIMEOUT);
    mapSysEnvToConfigLong(sysEnvMapped, ENV_DB_POOL_IDLE_TIMEOUT, CFG_DB_POOL_IDLE_TIMEOUT);
    mapSysEnvToConfigLong(sysEnvMapped, ENV_DB_POOL_MAX_LIFETIME, CFG_DB_POOL_MAX_LIFETIME);
    mapSysEnvToConfig(sysEnvMapped, ENV_AI_SERVICE_BASE_URL, CFG_AI_SERVICE_BASE_URL);
    mapSysEnvToConfig(sysEnvMapped, ENV_AI_SERVICE_API_KEY, CFG_AI_SERVICE_API_KEY);
    mapSysEnvToConfig(sysEnvMapped, ENV_AI_SERVICE_MODEL, CFG_AI_SERVICE_MODEL);
    mapSysEnvToConfigDouble(sysEnvMapped, ENV_AI_SERVICE_TEMPERATURE, CFG_AI_SERVICE_TEMPERATURE);
    mapSysEnvToConfigInt(
        sysEnvMapped, ENV_AI_SERVICE_TIMEOUT_SECONDS, CFG_AI_SERVICE_TIMEOUT_SECONDS);
    mapSysEnvToConfig(sysEnvMapped, ENV_PROMPTS_DIR_PATH, CFG_PROMPTS_DIR_PATH);
    mapSysEnvToConfigLong(sysEnvMapped, ENV_PROMPT_MAX_SIZE_BYTES, CFG_PROMPT_MAX_SIZE);
    mapSysEnvToConfigBoolean(sysEnvMapped, ENV_AI_SHOW_REASONING, CFG_AI_SHOW_REASONING);
    mapSysEnvToConfigBoolean(
        sysEnvMapped, ENV_AI_MARKDOWN_VALIDATION_ENABLED, CFG_AI_MARKDOWN_VALIDATION_ENABLED);
    mapSysEnvToConfigBoolean(
        sysEnvMapped,
        ENV_AI_MARKDOWN_VALIDATION_STREAMING_BYPASS,
        CFG_AI_MARKDOWN_VALIDATION_STREAMING_BYPASS);
    mapSysEnvToConfig(sysEnvMapped, ENV_ECPAY_MERCHANT_ID, CFG_ECPAY_MERCHANT_ID);
    mapSysEnvToConfig(sysEnvMapped, ENV_ECPAY_HASH_KEY, CFG_ECPAY_HASH_KEY);
    mapSysEnvToConfig(sysEnvMapped, ENV_ECPAY_HASH_IV, CFG_ECPAY_HASH_IV);
    mapSysEnvToConfig(sysEnvMapped, ENV_ECPAY_RETURN_URL, CFG_ECPAY_RETURN_URL);
    mapSysEnvToConfigBoolean(sysEnvMapped, ENV_ECPAY_STAGE_MODE, CFG_ECPAY_STAGE_MODE);
    mapSysEnvToConfigInt(sysEnvMapped, ENV_ECPAY_CVS_EXPIRE_MINUTES, CFG_ECPAY_CVS_EXPIRE_MINUTES);
    Config sysEnvConfig = ConfigFactory.parseMap(sysEnvMapped);

    // Layer configs: sysEnv > dotEnv > application > defaults
    return sysEnvConfig
        .withFallback(dotEnvConfig)
        .withFallback(applicationConfig)
        .withFallback(defaultsConfig);
  }

  private void mapEnvToConfig(Map<String, Object> target, String envKey, String configPath) {
    String value = dotEnvValues.get(envKey);
    if (value != null && !value.isBlank()) {
      target.put(configPath, value);
    }
  }

  private void mapEnvToConfigInt(Map<String, Object> target, String envKey, String configPath) {
    String value = dotEnvValues.get(envKey);
    if (value != null && !value.isBlank()) {
      try {
        target.put(configPath, Integer.parseInt(value));
      } catch (NumberFormatException e) {
        LOG.warn("Invalid integer value for {}: {}", envKey, value);
      }
    }
  }

  private void mapEnvToConfigDouble(Map<String, Object> target, String envKey, String configPath) {
    String value = dotEnvValues.get(envKey);
    if (value != null && !value.isBlank()) {
      try {
        double parsed = Double.parseDouble(value);
        if (Double.isFinite(parsed)) {
          target.put(configPath, parsed);
        } else {
          LOG.warn("Invalid non-finite double value for {}: {}", envKey, value);
        }
      } catch (NumberFormatException e) {
        LOG.warn("Invalid double value for {}: {}", envKey, value);
      }
    }
  }

  private void mapEnvToConfigLong(Map<String, Object> target, String envKey, String configPath) {
    String value = dotEnvValues.get(envKey);
    if (value != null && !value.isBlank()) {
      try {
        target.put(configPath, Long.parseLong(value));
      } catch (NumberFormatException e) {
        LOG.warn("Invalid long value for {}: {}", envKey, value);
      }
    }
  }

  private void mapSysEnvToConfig(Map<String, Object> target, String envKey, String configPath) {
    String value = System.getenv(envKey);
    if (value != null && !value.isBlank()) {
      target.put(configPath, value);
    }
  }

  private void mapSysEnvToConfigInt(Map<String, Object> target, String envKey, String configPath) {
    String value = System.getenv(envKey);
    if (value != null && !value.isBlank()) {
      try {
        target.put(configPath, Integer.parseInt(value));
      } catch (NumberFormatException e) {
        LOG.warn("Invalid integer value for {}: {}, using default", envKey, value);
      }
    }
  }

  private void mapSysEnvToConfigDouble(
      Map<String, Object> target, String envKey, String configPath) {
    String value = System.getenv(envKey);
    if (value != null && !value.isBlank()) {
      try {
        double parsed = Double.parseDouble(value);
        if (Double.isFinite(parsed)) {
          target.put(configPath, parsed);
        } else {
          LOG.warn("Invalid non-finite double value for {}: {}, using default", envKey, value);
        }
      } catch (NumberFormatException e) {
        LOG.warn("Invalid double value for {}: {}, using default", envKey, value);
      }
    }
  }

  private void mapSysEnvToConfigLong(Map<String, Object> target, String envKey, String configPath) {
    String value = System.getenv(envKey);
    if (value != null && !value.isBlank()) {
      try {
        target.put(configPath, Long.parseLong(value));
      } catch (NumberFormatException e) {
        LOG.warn("Invalid long value for {}: {}, using default", envKey, value);
      }
    }
  }

  private void mapEnvToConfigBoolean(Map<String, Object> target, String envKey, String configPath) {
    String value = dotEnvValues.get(envKey);
    if (value != null && !value.isBlank()) {
      try {
        target.put(configPath, Boolean.parseBoolean(value));
      } catch (Exception e) {
        LOG.warn("Invalid boolean value for {}: {}, using default", envKey, value);
      }
    }
  }

  private void mapSysEnvToConfigBoolean(
      Map<String, Object> target, String envKey, String configPath) {
    String value = System.getenv(envKey);
    if (value != null && !value.isBlank()) {
      try {
        target.put(configPath, Boolean.parseBoolean(value));
      } catch (Exception e) {
        LOG.warn("Invalid boolean value for {}: {}, using default", envKey, value);
      }
    }
  }

  private String getEnvOrDotEnv(String envKey) {
    String value = System.getenv(envKey);
    if (value != null && !value.isBlank()) {
      return value;
    }
    value = dotEnvValues.get(envKey);
    if (value != null && !value.isBlank()) {
      return value;
    }
    return null;
  }

  private String getConfigOrDefault(String path, String defaultValue) {
    if (config.hasPath(path)) {
      String value = config.getString(path);
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return defaultValue;
  }

  private int getConfigIntOrDefault(String path, int defaultValue) {
    if (config.hasPath(path)) {
      try {
        return config.getInt(path);
      } catch (Exception e) {
        LOG.warn("Invalid integer value for config {}: {}, using default", path, e.getMessage());
      }
    }
    return defaultValue;
  }

  /**
   * Gets the Discord bot token. This value must be set via environment variable or .env.
   *
   * @return the Discord bot token
   * @throws IllegalStateException if the token is not set
   */
  public String getDiscordBotToken() {
    if (!config.hasPath(CFG_DISCORD_BOT_TOKEN)) {
      throw new IllegalStateException(
          "Discord bot token not configured. Set "
              + ENV_DISCORD_BOT_TOKEN
              + " environment variable.");
    }
    String token = config.getString(CFG_DISCORD_BOT_TOKEN);
    if (token == null || token.isBlank()) {
      throw new IllegalStateException(
          "Discord bot token not configured. Set "
              + ENV_DISCORD_BOT_TOKEN
              + " environment variable.");
    }
    return token;
  }

  /**
   * Gets the database JDBC URL.
   *
   * @return the database URL
   */
  public String getDatabaseUrl() {
    String explicitUrl = getEnvOrDotEnv(ENV_DB_URL);
    if (explicitUrl != null) {
      return explicitUrl;
    }

    String host = getEnvOrDotEnv(ENV_DATABASE_HOST);
    String port = getEnvOrDotEnv(ENV_DATABASE_PORT);
    String name = getEnvOrDotEnv(ENV_DATABASE_NAME);

    if (host != null || port != null || name != null) {
      String resolvedHost =
          host != null ? host : getConfigOrDefault(CFG_DATABASE_HOST, "localhost");
      int resolvedPort =
          port != null
              ? parsePortOrDefault(port, getConfigIntOrDefault(CFG_DATABASE_PORT, 5432))
              : getConfigIntOrDefault(CFG_DATABASE_PORT, 5432);
      String resolvedName =
          name != null ? name : getConfigOrDefault(CFG_DATABASE_NAME, "currency_bot");
      return "jdbc:postgresql://" + resolvedHost + ":" + resolvedPort + "/" + resolvedName;
    }

    return config.getString(CFG_DB_URL);
  }

  /**
   * Gets the database username.
   *
   * @return the database username
   */
  public String getDatabaseUsername() {
    String username = getEnvOrDotEnv(ENV_DB_USERNAME);
    if (username != null) {
      return username;
    }
    username = getEnvOrDotEnv(ENV_DATABASE_USER);
    if (username != null) {
      return username;
    }
    if (config.hasPath(CFG_DATABASE_USERNAME)) {
      return config.getString(CFG_DATABASE_USERNAME);
    }
    return config.getString(CFG_DB_USERNAME);
  }

  /**
   * Gets the database password.
   *
   * @return the database password
   */
  public String getDatabasePassword() {
    String password = getEnvOrDotEnv(ENV_DB_PASSWORD);
    if (password != null) {
      return password;
    }
    password = getEnvOrDotEnv(ENV_DATABASE_PASSWORD);
    if (password != null) {
      return password;
    }
    if (config.hasPath(CFG_DATABASE_PASSWORD)) {
      return config.getString(CFG_DATABASE_PASSWORD);
    }
    return config.getString(CFG_DB_PASSWORD);
  }

  private int parsePortOrDefault(String value, int defaultValue) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      LOG.warn("Invalid port value for {}: {}, using default", ENV_DATABASE_PORT, value);
      return defaultValue;
    }
  }

  /**
   * Gets the maximum connection pool size.
   *
   * @return the maximum pool size
   */
  public int getPoolMaxSize() {
    return config.getInt(CFG_DB_POOL_MAX_SIZE);
  }

  /**
   * Gets the minimum idle connections in the pool.
   *
   * @return the minimum idle connections
   */
  public int getPoolMinIdle() {
    return config.getInt(CFG_DB_POOL_MIN_IDLE);
  }

  /**
   * Gets the connection timeout in milliseconds.
   *
   * @return the connection timeout
   */
  public long getPoolConnectionTimeout() {
    return config.getLong(CFG_DB_POOL_CONNECTION_TIMEOUT);
  }

  /**
   * Gets the idle connection timeout in milliseconds.
   *
   * @return the idle timeout
   */
  public long getPoolIdleTimeout() {
    return config.getLong(CFG_DB_POOL_IDLE_TIMEOUT);
  }

  /**
   * Gets the maximum connection lifetime in milliseconds.
   *
   * @return the max lifetime
   */
  public long getPoolMaxLifetime() {
    return config.getLong(CFG_DB_POOL_MAX_LIFETIME);
  }

  /**
   * Gets the Redis connection URI.
   *
   * @return the Redis URI (e.g., redis://localhost:6379)
   */
  public String getRedisUri() {
    return config.getString(CFG_REDIS_URI);
  }

  /**
   * Gets the AI service base URL.
   *
   * @return the AI service base URL
   */
  public String getAIServiceBaseUrl() {
    return config.getString(CFG_AI_SERVICE_BASE_URL);
  }

  /**
   * Gets the AI service API key.
   *
   * @return the AI service API key
   * @throws IllegalStateException if the API key is not set
   */
  public String getAIServiceApiKey() {
    if (!config.hasPath(CFG_AI_SERVICE_API_KEY)) {
      throw new IllegalStateException(
          "AI service API key not configured. Set "
              + ENV_AI_SERVICE_API_KEY
              + " environment variable.");
    }
    String apiKey = config.getString(CFG_AI_SERVICE_API_KEY);
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException(
          "AI service API key not configured. Set "
              + ENV_AI_SERVICE_API_KEY
              + " environment variable.");
    }
    return apiKey;
  }

  /**
   * Gets the AI service model name.
   *
   * @return the AI service model name
   */
  public String getAIServiceModel() {
    return config.getString(CFG_AI_SERVICE_MODEL);
  }

  /**
   * Gets the AI service temperature.
   *
   * @return the AI service temperature
   */
  public double getAIServiceTemperature() {
    return config.getDouble(CFG_AI_SERVICE_TEMPERATURE);
  }

  /**
   * Gets the AI service timeout in seconds.
   *
   * @return the AI service timeout in seconds
   */
  public int getAIServiceTimeoutSeconds() {
    return config.getInt(CFG_AI_SERVICE_TIMEOUT_SECONDS);
  }

  /**
   * Gets a configuration value with a default fallback.
   *
   * @param key the configuration key
   * @param defaultValue the default value if not configured
   * @return the configuration value or default
   */
  public String getOrDefault(String key, String defaultValue) {
    if (config.hasPath(key)) {
      return config.getString(key);
    }
    return defaultValue;
  }

  /**
   * Gets a required configuration value.
   *
   * @param key the configuration key
   * @return the configuration value
   * @throws IllegalStateException if the value is not set
   */
  public String getRequired(String key) {
    if (!config.hasPath(key)) {
      throw new IllegalStateException("Required configuration " + key + " is not set.");
    }
    String value = config.getString(key);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Required configuration " + key + " is not set.");
    }
    return value;
  }

  /**
   * Gets a double configuration value with a default fallback.
   *
   * @param key the configuration key
   * @param defaultValue the default value if not configured
   * @return the configuration value or default
   */
  public double getDoubleOrDefault(String key, double defaultValue) {
    if (config.hasPath(key)) {
      return config.getDouble(key);
    }
    return defaultValue;
  }

  /**
   * Gets an integer configuration value with a default fallback.
   *
   * @param key the configuration key
   * @param defaultValue the default value if not configured
   * @return the configuration value or default
   */
  public int getIntOrDefault(String key, int defaultValue) {
    if (config.hasPath(key)) {
      return config.getInt(key);
    }
    return defaultValue;
  }

  /**
   * Gets the prompts directory path.
   *
   * <p>This is the directory containing external prompt markdown files that are loaded on every AI
   * request.
   *
   * @return the prompts directory path (relative or absolute)
   */
  public String getPromptsDirPath() {
    return config.getString(CFG_PROMPTS_DIR_PATH);
  }

  /**
   * Gets the maximum size of a single prompt file in bytes.
   *
   * <p>Files exceeding this size will be skipped with a warning log.
   *
   * @return the maximum file size in bytes (default: 1MB)
   */
  public long getPromptMaxSizeBytes() {
    return config.getBytes(CFG_PROMPT_MAX_SIZE);
  }

  /**
   * 取得是否顯示 AI 推理內容。
   *
   * @return true 顯示，false 隱藏
   */
  public boolean getAIShowReasoning() {
    return config.getBoolean(CFG_AI_SHOW_REASONING);
  }

  /**
   * 取得是否啟用 Markdown 格式驗證。
   *
   * @return true 啟用，false 停用
   */
  public boolean getAIMarkdownValidationEnabled() {
    return config.getBoolean(CFG_AI_MARKDOWN_VALIDATION_ENABLED);
  }

  /**
   * 取得串流模式是否繞過 Markdown 驗證。
   *
   * @return true 繞過驗證，false 正常驗證
   */
  public boolean getAIMarkdownValidationStreamingBypass() {
    return config.getBoolean(CFG_AI_MARKDOWN_VALIDATION_STREAMING_BYPASS);
  }

  /**
   * Gets ECPay MerchantID used for payment API calls.
   *
   * @return MerchantID, empty string when not configured
   */
  public String getEcpayMerchantId() {
    return config.getString(CFG_ECPAY_MERCHANT_ID);
  }

  /**
   * Gets ECPay HashKey used for request encryption.
   *
   * @return HashKey, empty string when not configured
   */
  public String getEcpayHashKey() {
    return config.getString(CFG_ECPAY_HASH_KEY);
  }

  /**
   * Gets ECPay HashIV used for request encryption.
   *
   * @return HashIV, empty string when not configured
   */
  public String getEcpayHashIv() {
    return config.getString(CFG_ECPAY_HASH_IV);
  }

  /**
   * Gets ECPay payment result callback URL.
   *
   * @return callback URL, empty string when not configured
   */
  public String getEcpayReturnUrl() {
    return config.getString(CFG_ECPAY_RETURN_URL);
  }

  /**
   * Gets whether ECPay stage endpoint should be used.
   *
   * @return true for stage, false for production
   */
  public boolean getEcpayStageMode() {
    return config.getBoolean(CFG_ECPAY_STAGE_MODE);
  }

  /**
   * Gets CVS code expiration time in minutes.
   *
   * @return expiration minutes
   */
  public int getEcpayCvsExpireMinutes() {
    return config.getInt(CFG_ECPAY_CVS_EXPIRE_MINUTES);
  }
}
