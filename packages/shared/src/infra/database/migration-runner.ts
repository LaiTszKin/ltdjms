import { sql } from 'drizzle-orm';
import { type NodePgDatabase, drizzle } from 'drizzle-orm/node-postgres';
import { migrate } from 'drizzle-orm/node-postgres/migrator';
import { type Pool } from 'pg';
import { SchemaMigrationException } from './schema-migration-exception.js';

/**
 * Runs database migrations from the specified directory.
 * Uses drizzle-orm/node-postgres migrator to apply pending migrations.
 */
export async function runMigrations(
  pool: Pool,
  migrationsDir: string,
): Promise<void> {
  try {
    const db: NodePgDatabase = drizzle(pool) as unknown as NodePgDatabase;

    // Check if the __drizzle_migrations tracking table exists
    const result = await db.execute<{ exists: boolean }>(
      sql`SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = '__drizzle_migrations') as "exists"`,
    );
    const tableExists = result.rows?.[0]?.exists ?? false;

    if (!tableExists) {
      // Check if any other tables exist (meaning schema was created externally)
      const tableResult = await db.execute<{ count: number }>(
        sql`SELECT COUNT(*)::int as "count" FROM information_schema.tables WHERE table_schema = 'public'`,
      );
      const tableCount = tableResult.rows?.[0]?.count ?? 0;

      if (tableCount > 0) {
        console.log(
          `[migration-runner] __drizzle_migrations table not found but ${tableCount} tables exist — skipping migrate()`,
        );
        return;
      }
    }

    await migrate(db, { migrationsFolder: migrationsDir });
  } catch (err) {
    const message =
      err instanceof Error ? err.message : 'Unknown migration error';
    throw new SchemaMigrationException(
      `Database migration failed: ${message}`,
      err instanceof Error ? err : undefined,
    );
  }
}
