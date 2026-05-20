import { drizzle } from 'drizzle-orm/node-postgres';
import { migrate } from 'drizzle-orm/node-postgres/migrator';
import { SchemaMigrationException } from './schema-migration-exception.js';
/**
 * Runs database migrations from the specified directory.
 * Uses drizzle-orm/node-postgres migrator to apply pending migrations.
 */
export async function runMigrations(pool, migrationsDir) {
    try {
        const db = drizzle(pool);
        await migrate(db, { migrationsFolder: migrationsDir });
    }
    catch (err) {
        const message = err instanceof Error ? err.message : 'Unknown migration error';
        throw new SchemaMigrationException(`Database migration failed: ${message}`, err instanceof Error ? err : undefined);
    }
}
//# sourceMappingURL=migration-runner.js.map