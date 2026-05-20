import { sql } from 'drizzle-orm';
import { type NodePgDatabase, drizzle } from 'drizzle-orm/node-postgres';
import { migrate } from 'drizzle-orm/node-postgres/migrator';
import { type Pool } from 'pg';
import pino, { type Logger } from 'pino';
import { readdir, readFile } from 'node:fs/promises';
import { join } from 'node:path';
import { SchemaMigrationException } from './schema-migration-exception.js';

const TRACKING_TABLE = '_ltdjms_migrations';

/**
 * Runs database migrations from the specified directory.
 * Uses a `_ltdjms_migrations` tracking table for incremental migration support:
 * - If the tracking table does not exist and tables already exist in the schema,
 *   it creates the tracking table and baselines all existing migration files
 *   (marking them as applied without re-executing).
 * - If the tracking table already exists, only unmarked migration files are applied.
 * - On a fresh database (no tables), drizzle's built-in migrate() applies all files.
 * Retries up to 3 times with 1s backoff on failure.
 */
export async function runMigrations(
  pool: Pool,
  migrationsDir: string,
  logger?: Logger,
): Promise<void> {
  const log = logger ?? pino({ level: 'silent' });
  let lastError: Error | undefined;

  for (let attempt = 1; attempt <= 3; attempt++) {
    try {
      const db: NodePgDatabase = drizzle(pool) as unknown as NodePgDatabase;

      // Create our tracking table if it does not exist
      await db.execute(sql`
        CREATE TABLE IF NOT EXISTS ${sql.identifier(TRACKING_TABLE)} (
          id SERIAL PRIMARY KEY,
          filename VARCHAR(255) NOT NULL UNIQUE,
          applied_at TIMESTAMP DEFAULT NOW()
        )
      `);

      // Check if the tracking table has any entries
      const trackingResult = await db.execute<{ count: number }>(
        sql`SELECT COUNT(*)::int as "count" FROM ${sql.identifier(TRACKING_TABLE)}`,
      );
      const trackedCount = trackingResult.rows?.[0]?.count ?? 0;

      // Check if any other tables exist in the public schema
      const tableResult = await db.execute<{ count: number }>(
        sql`SELECT COUNT(*)::int as "count" FROM information_schema.tables WHERE table_schema = 'public' AND table_name != ${TRACKING_TABLE}`,
      );
      const tableCount = tableResult.rows?.[0]?.count ?? 0;

      if (trackedCount === 0 && tableCount > 0) {
        // Baseline scenario: tracking table is empty but schema has tables
        // created externally (e.g., by direct SQL or another system).
        // Mark all existing migration files as applied without re-executing.
        const files = await readdir(migrationsDir);
        const sqlFiles = files
          .filter((f) => f.endsWith('.sql'))
          .sort();

        for (const file of sqlFiles) {
          await db.execute(
            sql`INSERT INTO ${sql.identifier(TRACKING_TABLE)} (filename) VALUES (${file}) ON CONFLICT (filename) DO NOTHING`,
          );
        }

        log.info(
          { trackedCount: sqlFiles.length, tableCount },
          `_ltdjms_migrations table baselined with ${sqlFiles.length} migration files`,
        );
        return;
      }

      if (trackedCount > 0) {
        // Incremental: apply only unmarked migration files
        const files = await readdir(migrationsDir);
        const sqlFiles = files
          .filter((f) => f.endsWith('.sql'))
          .sort();

        for (const file of sqlFiles) {
          const applied = await db.execute<{ count: number }>(
            sql`SELECT COUNT(*)::int as "count" FROM ${sql.identifier(TRACKING_TABLE)} WHERE filename = ${file}`,
          );
          const alreadyApplied = applied.rows?.[0]?.count ?? 0;

          if (alreadyApplied === 0) {
            const filePath = join(migrationsDir, file);
            const fileContent = await readFile(filePath, 'utf-8');

            log.info({ migration: file }, 'Applying migration');

            // Execute the migration SQL directly
            await pool.query(fileContent);

            // Record as applied
            await db.execute(
              sql`INSERT INTO ${sql.identifier(TRACKING_TABLE)} (filename) VALUES (${file})`,
            );

            log.info({ migration: file }, 'Migration applied successfully');
          }
        }

        // Also run drizzle's migrate() to keep __drizzle_migrations in sync
        // if the project ever switches to drizzle-generated migrations.
        await migrate(db, { migrationsFolder: migrationsDir });
        return;
      }

      // Fresh database: no tables at all
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
