package ltdjms.discord.currency.integration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Base class for integration tests that require a PostgreSQL database.
 * Uses Testcontainers to start a PostgreSQL container and applies schema.sql.
 */
@Testcontainers
public abstract class PostgresIntegrationTestBase {

    @Container
    @SuppressWarnings("resource") // managed by Testcontainers; closed via JUnit/Testcontainers lifecycle
    protected static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("currency_bot_test")
            .withUsername("test")
            .withPassword("test");

    protected static DataSource dataSource;

    @BeforeAll
    static void setUpDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setPoolName("TestPool");

        dataSource = new HikariDataSource(config);

        // Apply schema
        applySchema();
    }

    @BeforeEach
    void cleanDatabase() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            // Truncate tables in correct order (respecting foreign keys if any)
            stmt.execute("TRUNCATE TABLE member_currency_account CASCADE");
            stmt.execute("TRUNCATE TABLE guild_currency_config CASCADE");
            stmt.execute("TRUNCATE TABLE game_token_account CASCADE");
            stmt.execute("TRUNCATE TABLE dice_game1_config CASCADE");
            stmt.execute("TRUNCATE TABLE dice_game2_config CASCADE");
        }
    }

    private static void applySchema() {
        try (InputStream is = PostgresIntegrationTestBase.class.getClassLoader()
                .getResourceAsStream("db/schema.sql")) {
            if (is == null) {
                throw new RuntimeException("schema.sql not found on classpath");
            }
            String schema = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute(schema);
            }
        } catch (IOException | SQLException e) {
            throw new RuntimeException("Failed to apply schema", e);
        }
    }

    /**
     * Returns the test data source.
     *
     * @return the data source connected to the test PostgreSQL container
     */
    protected DataSource getDataSource() {
        return dataSource;
    }
}
