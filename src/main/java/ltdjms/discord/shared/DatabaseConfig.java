package ltdjms.discord.shared;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/** Configures and provides a HikariCP connection pool for PostgreSQL. */
public final class DatabaseConfig {

  private static final Logger LOG = LoggerFactory.getLogger(DatabaseConfig.class);

  private final HikariDataSource dataSource;

  public DatabaseConfig(EnvironmentConfig envConfig) {
    LOG.info("Initializing database connection pool");

    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(envConfig.getDatabaseUrl());
    config.setUsername(envConfig.getDatabaseUsername());
    config.setPassword(envConfig.getDatabasePassword());

    // Pool configuration
    config.setMaximumPoolSize(envConfig.getPoolMaxSize());
    config.setMinimumIdle(envConfig.getPoolMinIdle());
    config.setConnectionTimeout(envConfig.getPoolConnectionTimeout());
    config.setIdleTimeout(envConfig.getPoolIdleTimeout());
    config.setMaxLifetime(envConfig.getPoolMaxLifetime());

    // PostgreSQL specific settings
    config.addDataSourceProperty("cachePrepStmts", "true");
    config.addDataSourceProperty("prepStmtCacheSize", "250");
    config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

    // Pool name for logging
    config.setPoolName("CurrencyBotPool");

    this.dataSource = new HikariDataSource(config);

    LOG.info("Database connection pool initialized: url={}", envConfig.getDatabaseUrl());
  }

  /**
   * Returns the configured data source for database operations.
   *
   * @return the HikariCP data source
   */
  public DataSource getDataSource() {
    return dataSource;
  }

  /** Closes the connection pool and releases resources. */
  public void close() {
    if (dataSource != null && !dataSource.isClosed()) {
      LOG.info("Closing database connection pool");
      dataSource.close();
    }
  }
}
