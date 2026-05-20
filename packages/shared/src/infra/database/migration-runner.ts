import { sql } from 'drizzle-orm';
import { type NodePgDatabase, drizzle } from 'drizzle-orm/node-postgres';
import { migrate } from 'drizzle-orm/node-postgres/migrator';
import { type Pool } from 'pg';
import pino from 'pino';
import { type Logger } from 'pino';
import { SchemaMigrationException } from './schema-migration-exception.js';

/**
 * Runs database migrations from the specified directory.
 * Uses drizzle-orm/node-postgres migrator to apply pending migrations.
 * Retries up to 3 times with 1s backoff on failure.
 */
export async function runMigrations(
  pool: Pool,
  migrationsDir: string,
  logger?: Logger,
): Promise<void> {
  const log = logger ?? (pino({ level: 'silent' }) as Logger);
  let lastError: Error | undefined;

  for (let attempt = 1; attempt <= 3; attempt++) {
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
          log.info(
            `__drizzle_migrations table not found but ${tableCount} tables exist — skipping migrate()`,
          );
          return;
        }
      }

      await migrate(db, { migrationsFolder: migrationsDir });
      return;
    } catch (err) {
      lastError = err instanceof Error ? err : new Error(String(err));

      if (attempt < 3) {
        log.warn({ err: lastError }, `Migration attempt ${attempt} failed, retrying in 1s...`);
        await new Promise((resolve) => setTimeout(resolve, 1000));
      }
    }
  }

  throw new SchemaMigrationException(
    `Database migration failed after 3 attempts: ${lastError?.message}`,
    lastError,
  );
}
