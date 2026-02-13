package ltdjms.discord.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Integration tests for {@link DatabaseMigrationRunner} using real PostgreSQL via Testcontainers.
 */
@Testcontainers(disabledWithoutDocker = true)
class DatabaseMigrationRunnerIntegrationTest {

  @Container
  @SuppressWarnings("resource")
  private static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("flyway_test")
          .withUsername("test")
          .withPassword("test");

  private HikariDataSource dataSource;

  @BeforeEach
  void setUp() {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(postgres.getJdbcUrl());
    config.setUsername(postgres.getUsername());
    config.setPassword(postgres.getPassword());
    config.setMaximumPoolSize(5);
    config.setMinimumIdle(1);
    config.setPoolName("FlywayTestPool");
    dataSource = new HikariDataSource(config);

    // Clean up any existing tables and flyway history from previous tests
    cleanupDatabase();
  }

  @AfterEach
  void tearDown() {
    if (dataSource != null && !dataSource.isClosed()) {
      dataSource.close();
    }
  }

  private void cleanupDatabase() {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      // Drop flyway history table if exists
      stmt.execute("DROP TABLE IF EXISTS flyway_schema_history CASCADE");
      // Drop all application tables
      stmt.execute("DROP TABLE IF EXISTS currency_transaction CASCADE");
      stmt.execute("DROP TABLE IF EXISTS game_token_transaction CASCADE");
      stmt.execute("DROP TABLE IF EXISTS dice_game2_config CASCADE");
      stmt.execute("DROP TABLE IF EXISTS dice_game1_config CASCADE");
      stmt.execute("DROP TABLE IF EXISTS game_token_account CASCADE");
      stmt.execute("DROP TABLE IF EXISTS member_currency_account CASCADE");
      stmt.execute("DROP TABLE IF EXISTS guild_currency_config CASCADE");
      stmt.execute("DROP TABLE IF EXISTS escort_dispatch_order CASCADE");
      // Drop functions
      stmt.execute("DROP FUNCTION IF EXISTS update_updated_at_column() CASCADE");
    } catch (SQLException e) {
      // Ignore cleanup errors
    }
  }

  @Test
  @DisplayName("空資料庫第一次啟動時，應透過 Flyway migrations 建立所有表格與索引")
  void shouldCreateAllTablesOnEmptyDatabase() throws Exception {
    DatabaseMigrationRunner runner = DatabaseMigrationRunner.forDefaultMigrations();

    runner.migrate(dataSource);

    try (Connection conn = dataSource.getConnection()) {
      // Verify core tables exist
      assertThat(tableExists(conn, "guild_currency_config"))
          .as("guild_currency_config table should exist")
          .isTrue();
      assertThat(tableExists(conn, "member_currency_account"))
          .as("member_currency_account table should exist")
          .isTrue();
      assertThat(tableExists(conn, "game_token_account"))
          .as("game_token_account table should exist")
          .isTrue();
      assertThat(tableExists(conn, "dice_game1_config"))
          .as("dice_game1_config table should exist")
          .isTrue();
      assertThat(tableExists(conn, "dice_game2_config"))
          .as("dice_game2_config table should exist")
          .isTrue();
      assertThat(tableExists(conn, "game_token_transaction"))
          .as("game_token_transaction table should exist")
          .isTrue();
      assertThat(tableExists(conn, "currency_transaction"))
          .as("currency_transaction table should exist")
          .isTrue();
      assertThat(tableExists(conn, "escort_dispatch_order"))
          .as("escort_dispatch_order table should exist")
          .isTrue();

      // Verify indexes exist
      assertThat(indexExists(conn, "idx_member_currency_account_guild"))
          .as("idx_member_currency_account_guild index should exist")
          .isTrue();
      assertThat(indexExists(conn, "idx_game_token_account_guild"))
          .as("idx_game_token_account_guild index should exist")
          .isTrue();

      // Verify Flyway history table was created
      assertThat(tableExists(conn, "flyway_schema_history"))
          .as("flyway_schema_history table should exist")
          .isTrue();
    }
  }

  @Test
  @DisplayName("舊版只有 tokens_per_play 欄位的 dice_game1_config 應被 migration 升級為 range 欄位並保留設定")
  void shouldMigrateLegacyDiceGame1ConfigToRangeColumns() throws Exception {
    // Arrange: simulate legacy schema with single tokens_per_play column
    long legacyGuildId = 123456789L;
    long legacyTokensPerPlay = 5L;

    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(
          """
          CREATE TABLE dice_game1_config (
              guild_id BIGINT PRIMARY KEY,
              tokens_per_play BIGINT NOT NULL DEFAULT 1,
              created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
              updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
              CONSTRAINT tokens_per_play_non_negative CHECK (tokens_per_play >= 0)
          )
          """);

      stmt.execute(
          """
          INSERT INTO dice_game1_config (guild_id, tokens_per_play)
          VALUES (%d, %d)
          """
              .formatted(legacyGuildId, legacyTokensPerPlay));
    }

    // Act: run Flyway migrations (should upgrade legacy schema)
    // V002 adds range columns, V003 removes default_tokens_per_play
    DatabaseMigrationRunner runner = DatabaseMigrationRunner.forDefaultMigrations();
    runner.migrate(dataSource);

    // Assert: new range columns exist and values are derived from legacy tokens_per_play
    // After V003, default_tokens_per_play no longer exists
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps =
            conn.prepareStatement(
                "SELECT min_tokens_per_play, max_tokens_per_play, reward_per_dice_value "
                    + "FROM dice_game1_config WHERE guild_id = ?")) {
      ps.setLong(1, legacyGuildId);

      try (ResultSet rs = ps.executeQuery()) {
        assertThat(rs.next()).as("Upgraded config row should exist").isTrue();
        long minTokens = rs.getLong("min_tokens_per_play");
        long maxTokens = rs.getLong("max_tokens_per_play");
        long rewardPerDiceValue = rs.getLong("reward_per_dice_value");

        // For legacy data, V002 migration sets:
        //   min_tokens_per_play = 1
        //   max_tokens_per_play = tokens_per_play
        //   reward_per_dice_value = 250000 (default)
        // V003 removes default_tokens_per_play column
        assertThat(minTokens).isEqualTo(1L);
        assertThat(maxTokens).isEqualTo(legacyTokensPerPlay);
        assertThat(rewardPerDiceValue).isEqualTo(250_000L);
      }
    }
  }

  @Test
  @DisplayName("已有資料的情況下，新增非破壞性欄位的 migration 應能自動套用且不修改既有資料")
  void shouldApplyNonDestructiveMigrationWithoutDataLoss() throws Exception {
    DatabaseMigrationRunner runner = DatabaseMigrationRunner.forDefaultMigrations();

    // First run: create all tables
    runner.migrate(dataSource);

    // Insert test data
    long guildId = 123456789L;
    long userId = 987654321L;
    long initialBalance = 1000L;
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps =
            conn.prepareStatement(
                "INSERT INTO member_currency_account (guild_id, user_id, balance) VALUES (?, ?,"
                    + " ?)")) {
      ps.setLong(1, guildId);
      ps.setLong(2, userId);
      ps.setLong(3, initialBalance);
      ps.executeUpdate();
    }

    // Second run: should be idempotent and not modify existing data
    runner.migrate(dataSource);

    // Verify data is preserved
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps =
            conn.prepareStatement(
                "SELECT balance FROM member_currency_account WHERE guild_id = ? AND user_id = ?")) {
      ps.setLong(1, guildId);
      ps.setLong(2, userId);
      try (ResultSet rs = ps.executeQuery()) {
        assertThat(rs.next()).isTrue();
        assertThat(rs.getLong("balance")).isEqualTo(initialBalance);
      }
    }
  }

  @Test
  @DisplayName("已存在的資料庫使用 baselineOnMigrate 時應正確設定 baseline 版本")
  void shouldBaselineExistingDatabase() throws Exception {
    // Manually create schema without Flyway (simulating existing production DB)
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(
          """
          CREATE TABLE IF NOT EXISTS guild_currency_config (
              guild_id BIGINT PRIMARY KEY,
              currency_name VARCHAR(50) NOT NULL DEFAULT 'Coins',
              currency_icon VARCHAR(64) NOT NULL DEFAULT '🪙',
              created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
              updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
          )
          """);
    }

    // Run migration with baselineOnMigrate enabled
    DatabaseMigrationRunner runner = DatabaseMigrationRunner.forDefaultMigrations();
    runner.migrate(dataSource);

    // Verify Flyway tracked the baseline
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps =
            conn.prepareStatement(
                "SELECT version, description FROM flyway_schema_history WHERE success = true ORDER"
                    + " BY installed_rank")) {
      try (ResultSet rs = ps.executeQuery()) {
        assertThat(rs.next()).as("Should have at least one migration record").isTrue();
      }
    }
  }

  @Test
  @DisplayName("Migration 失敗時應丟出 SchemaMigrationException")
  void shouldThrowExceptionOnMigrationFailure() {
    // Create a runner with a broken migration file that has SQL syntax errors
    DatabaseMigrationRunner runner = new DatabaseMigrationRunner("classpath:db/broken_migration");

    assertThatThrownBy(() -> runner.migrate(dataSource))
        .isInstanceOf(SchemaMigrationException.class);
  }

  @Test
  @DisplayName("第二次執行 migrate 應為冪等操作且不重複套用 migration")
  void shouldBeIdempotent() throws Exception {
    DatabaseMigrationRunner runner = DatabaseMigrationRunner.forDefaultMigrations();

    // First run
    runner.migrate(dataSource);

    // Count migrations applied
    int migrationsAfterFirstRun = countMigrations();

    // Second run
    runner.migrate(dataSource);

    // Count migrations again
    int migrationsAfterSecondRun = countMigrations();

    assertThat(migrationsAfterSecondRun)
        .as("Migration count should be the same after second run")
        .isEqualTo(migrationsAfterFirstRun);
  }

  private int countMigrations() throws SQLException {
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps =
            conn.prepareStatement(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true")) {
      try (ResultSet rs = ps.executeQuery()) {
        rs.next();
        return rs.getInt(1);
      }
    }
  }

  private boolean tableExists(Connection conn, String tableName) throws SQLException {
    try (PreparedStatement ps =
        conn.prepareStatement(
            "SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name ="
                + " ?")) {
      ps.setString(1, tableName);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next();
      }
    }
  }

  private boolean indexExists(Connection conn, String indexName) throws SQLException {
    try (PreparedStatement ps =
        conn.prepareStatement(
            "SELECT 1 FROM pg_indexes WHERE schemaname = 'public' AND indexname = ?")) {
      ps.setString(1, indexName);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next();
      }
    }
  }
}
