import { Pool } from 'pg';
export interface DatabaseConfig {
    readonly url: string;
    readonly max: number;
    readonly connectionTimeoutMillis: number;
    readonly idleTimeoutMillis: number;
}
/**
 * Creates a PostgreSQL connection pool with retry logic.
 * Retries up to 3 times on connection failure with 2s delay.
 */
export declare function createDatabasePool(config: DatabaseConfig): Promise<Pool>;
