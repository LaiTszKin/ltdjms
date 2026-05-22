import { sql } from 'drizzle-orm';
import { type NodePgDatabase, drizzle } from 'drizzle-orm/node-postgres';
import { type Pool } from 'pg';
import pino, { type Logger } from 'pino';
import { readdir, readFile } from 'node:fs/promises';
import { join } from 'node:path';
import { SchemaMigrationException } from './schema-migration-exception.js';

const TRACKING_TABLE = '_ltdjms_migrations';

/**
 * Runs database migrations from the specified directory.
 * Uses a `_ltdjms_migrations` tracking table (NOT drizzle-kit's __drizzle_migrations)
 * for incremental migration support:
 * - If the tracking table does not exist and tables already exist in the schema,
 *   it creates the tracking table and baselines all existing migration files
 *   (marking them as applied without re-executing).
 * - If the tracking table already exists, only unmarked migration files are applied.
 * - On a fresh database (no tables), all migration files are applied sequentially.
 *
 * NOTE: This uses a custom tracking table instead of drizzle-kit's built-in
 * __drizzle_migrations because the schema is defined by Java Flyway SQL migrations,
 * not by Drizzle schema definitions. The custom table avoids dual-application risk.
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

      // Read all migration files sorted by name
      const files = await readdir(migrationsDir);
      const sqlFiles = files.filter((f) => f.endsWith('.sql')).sort();

      // Check if tracking table has entries
      const trackingResult = await db.execute<{ count: number }>(
        sql`SELECT COUNT(*)::int as "count" FROM ${sql.identifier(TRACKING_TABLE)}`,
      );
      const trackedCount = trackingResult.rows?.[0]?.count ?? 0;

      if (trackedCount === 0) {
        // Check if any other tables exist (baseline detection)
        const tableResult = await db.execute<{ count: number }>(
          sql`SELECT COUNT(*)::int as "count" FROM information_schema.tables WHERE table_schema = 'public' AND table_name != ${TRACKING_TABLE}`,
        );
        const tableCount = tableResult.rows?.[0]?.count ?? 0;

        if (tableCount > 0) {
          // Baseline scenario: tracking table is empty but schema has tables
          // created externally (e.g., by direct SQL or another system).
          // Mark all existing migration files as applied without re-executing.
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

        // Fresh database: apply all migration files
        for (const file of sqlFiles) {
          const filePath = join(migrationsDir, file);
          const fileContent = await readFile(filePath, 'utf-8');

          log.info({ migration: file }, 'Applying migration');
          await pool.query(fileContent);

          await db.execute(
            sql`INSERT INTO ${sql.identifier(TRACKING_TABLE)} (filename) VALUES (${file})`,
          );

          log.info({ migration: file }, 'Migration applied successfully');
        }
        return;
      }

      // Incremental: apply only unmarked migration files
      // Batch all existence checks into a single query to avoid N round-trips
      const appliedResult = await db.execute<{ filename: string }>(
        sql`SELECT filename FROM ${sql.identifier(TRACKING_TABLE)}`,
      );
      const appliedFiles = new Set(appliedResult.rows?.map((r) => r.filename) ?? []);

      for (const file of sqlFiles) {
        if (!appliedFiles.has(file)) {
          const filePath = join(migrationsDir, file);
          const fileContent = await readFile(filePath, 'utf-8');

          log.info({ migration: file }, 'Applying migration');
          await pool.query(fileContent);

          await db.execute(
            sql`INSERT INTO ${sql.identifier(TRACKING_TABLE)} (filename) VALUES (${file})`,
          );

          log.info({ migration: file }, 'Migration applied successfully');
        }
      }
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
