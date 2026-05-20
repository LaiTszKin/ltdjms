import { type Pool } from 'pg';
/**
 * Runs database migrations from the specified directory.
 * Uses drizzle-orm/node-postgres migrator to apply pending migrations.
 */
export declare function runMigrations(pool: Pool, migrationsDir: string): Promise<void>;
