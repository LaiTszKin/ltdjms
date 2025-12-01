package ltdjms.discord.shared;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import javax.sql.DataSource;

/**
 * Factory for creating JOOQ DSLContext instances.
 * Provides a centralized way to configure and build DSLContext with PostgreSQL dialect.
 */
public final class JooqDSLContextFactory {

    private JooqDSLContextFactory() {
        // Utility class
    }

    /**
     * Creates a new DSLContext using the provided DataSource with PostgreSQL dialect.
     *
     * @param dataSource the DataSource to use for database connections
     * @return a configured DSLContext
     */
    public static DSLContext create(DataSource dataSource) {
        return DSL.using(dataSource, SQLDialect.POSTGRES);
    }
}
