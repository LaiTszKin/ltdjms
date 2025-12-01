package ltdjms.discord.currency.integration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import ltdjms.discord.shared.DatabaseSchemaMigrator;
import ltdjms.discord.shared.SchemaMigrationException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link DatabaseSchemaMigrator} using real PostgreSQL via Testcontainers.
 */
@Testcontainers
class DatabaseSchemaMigratorIntegrationTest {

    @Container
    @SuppressWarnings("resource") // managed by Testcontainers lifecycle, closed automatically
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("migration_test")
            .withUsername("test")
            .withPassword("test");

    private static DataSource dataSource;

    @BeforeAll
    static void setUpDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setPoolName("MigrationTestPool");

        dataSource = new HikariDataSource(config);
    }

    @AfterAll
    static void tearDown() {
        if (dataSource instanceof HikariDataSource ds && !ds.isClosed()) {
            ds.close();
        }
    }

    @Test
    @DisplayName("空資料庫第一次啟動時，應套用預設 schema.sql 並建立必要表與索引")
    void shouldApplyDefaultSchemaOnEmptyDatabase() throws Exception {
        DatabaseSchemaMigrator migrator = DatabaseSchemaMigrator.forDefaultSchema();

        migrator.migrate(dataSource);

        try (Connection conn = dataSource.getConnection()) {
            assertThat(tableExists(conn, "guild_currency_config"))
                    .as("guild_currency_config table should exist")
                    .isTrue();
            assertThat(tableExists(conn, "member_currency_account"))
                    .as("member_currency_account table should exist")
                    .isTrue();
            assertThat(indexExists(conn, "idx_member_currency_account_guild"))
                    .as("idx_member_currency_account_guild index should exist")
                    .isTrue();
        }
    }

    @Test
    @DisplayName("schema 新增具預設值的新欄位時，第二次啟動應自動以非破壞性方式新增欄位")
    void shouldAddNonDestructiveColumnsOnSecondRun() throws Exception {
        DatabaseSchemaMigrator v1 = new DatabaseSchemaMigrator("db/migration_v1.sql");
        DatabaseSchemaMigrator v2 = new DatabaseSchemaMigrator("db/migration_v2_add_column.sql");

        // First run: create base table
        v1.migrate(dataSource);

        // Insert existing row before adding the new column
        long accountId = 42L;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO test_accounts_v1 (id) VALUES (?)")) {
            ps.setLong(1, accountId);
            ps.executeUpdate();
        }

        // Second run: should add balance column with default value
        v2.migrate(dataSource);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT balance FROM test_accounts_v1 WHERE id = ?")) {
            ps.setLong(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong("balance")).isEqualTo(0L);
            }
        }
    }

    @Test
    @DisplayName("偵測到破壞性變更（例如移除欄位）時應丟出 SchemaMigrationException 並中止啟動")
    void shouldFailOnDestructiveDropColumnChanges() throws Exception {
        DatabaseSchemaMigrator v1 = new DatabaseSchemaMigrator("db/migration_v3_destructive_drop_column_v1.sql");
        DatabaseSchemaMigrator v2 = new DatabaseSchemaMigrator("db/migration_v3_destructive_drop_column_v2.sql");

        // First run: create table with two columns
        v1.migrate(dataSource);

        // Second run: canonical schema drops a column -> should be treated as destructive
        assertThatThrownBy(() -> v2.migrate(dataSource))
                .isInstanceOf(SchemaMigrationException.class)
                .hasMessageContaining("destructive");
    }

    private boolean tableExists(Connection conn, String tableName) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?")) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean indexExists(Connection conn, String indexName) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM pg_indexes WHERE schemaname = 'public' AND indexname = ?")) {
            ps.setString(1, indexName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
