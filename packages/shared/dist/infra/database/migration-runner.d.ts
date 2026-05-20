import { type Pool } from 'pg';
import { type Logger } from 'pino';
/**
 * Runs database migrations from the specified directory.
 * Uses drizzle-orm/node-postgres migrator to apply pending migrations.
 * Retries up to 3 times with 1s backoff on failure.
 */
export declare function runMigrations(pool: Pool, migrationsDir: string, logger?: Logger): Promise<void>;
