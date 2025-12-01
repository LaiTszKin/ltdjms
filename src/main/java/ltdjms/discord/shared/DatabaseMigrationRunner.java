package ltdjms.discord.shared;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

/**
 * Executes Flyway database migrations at application startup.
 *
 * <p>This class wraps Flyway configuration and execution, providing:</p>
 * <ul>
 *   <li>Automatic migration on empty databases</li>
 *   <li>Baseline support for existing databases (via baselineOnMigrate)</li>
 *   <li>Clear error reporting when migrations fail</li>
 * </ul>
 *
 * <p>Non-destructive migrations are stored in {@code classpath:db/migration}.</p>
 */
public final class DatabaseMigrationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseMigrationRunner.class);
    private static final String DEFAULT_MIGRATION_LOCATION = "classpath:db/migration";

    private final String migrationLocation;

    /**
     * Creates a migration runner with a custom migration location.
     *
     * @param migrationLocation Flyway migration location (e.g., "classpath:db/migration")
     */
    public DatabaseMigrationRunner(String migrationLocation) {
        this.migrationLocation = migrationLocation;
    }

    /**
     * Creates a migration runner using the default migration location.
     *
     * @return a new DatabaseMigrationRunner instance
     */
    public static DatabaseMigrationRunner forDefaultMigrations() {
        return new DatabaseMigrationRunner(DEFAULT_MIGRATION_LOCATION);
    }

    /**
     * Executes all pending migrations against the given data source.
     *
     * <p>This method will:</p>
     * <ul>
     *   <li>Create all tables on an empty database</li>
     *   <li>Apply any pending migrations on an existing database</li>
     *   <li>Baseline existing databases that have not been previously tracked by Flyway</li>
     * </ul>
     *
     * @param dataSource the target data source
     * @throws SchemaMigrationException if migration fails
     */
    public void migrate(DataSource dataSource) {
        LOG.info("Starting database migration from location: {}", migrationLocation);

        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations(migrationLocation)
                    .baselineOnMigrate(true)
                    .baselineVersion("0")
                    .baselineDescription("Baseline for existing database")
                    .validateMigrationNaming(true)
                    .load();

            MigrateResult result = flyway.migrate();

            if (result.success) {
                LOG.info("Database migration completed successfully. Applied {} migration(s). Current version: {}",
                        result.migrationsExecuted,
                        result.targetSchemaVersion);
            } else {
                String message = "Database migration reported failure";
                LOG.error(message);
                throw new SchemaMigrationException(message);
            }
        } catch (FlywayException e) {
            String message = "Failed to execute database migration from " + migrationLocation;
            LOG.error(message, e);
            throw new SchemaMigrationException(message, e);
        }
    }

    /**
     * Returns information about pending migrations without applying them.
     *
     * @param dataSource the target data source
     * @return migration info output as a string
     */
    public String info(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(migrationLocation)
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();

        var info = flyway.info();
        var pending = info.pending();

        StringBuilder sb = new StringBuilder();
        sb.append("Current schema version: ").append(info.current() != null ? info.current().getVersion() : "none").append("\n");
        sb.append("Pending migrations: ").append(pending.length).append("\n");

        for (var migration : pending) {
            sb.append("  - ").append(migration.getVersion())
                    .append(": ").append(migration.getDescription()).append("\n");
        }

        return sb.toString();
    }
}
