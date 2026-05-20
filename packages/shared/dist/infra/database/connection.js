import { Pool } from 'pg';
import { DatabaseConnectionException } from './database-connection-exception.js';
/**
 * Creates a PostgreSQL connection pool with retry logic.
 * Retries up to 3 times on connection failure with 2s delay.
 */
export async function createDatabasePool(config) {
    const pool = new Pool({
        connectionString: config.url,
        max: config.max ?? 5,
        connectionTimeoutMillis: config.connectionTimeoutMillis ?? 5000,
        idleTimeoutMillis: config.idleTimeoutMillis ?? 30000,
    });
    let lastError;
    for (let attempt = 1; attempt <= 3; attempt++) {
        try {
            const client = await pool.connect();
            await client.query('SELECT 1');
            client.release();
            return pool;
        }
        catch (err) {
            lastError = err instanceof Error ? err : new Error(String(err));
            if (attempt < 3) {
                await new Promise((resolve) => setTimeout(resolve, 2000));
            }
        }
    }
    // Close pool on failure
    await pool.end().catch(() => { });
    throw new DatabaseConnectionException(`Failed to connect to database after 3 attempts: ${lastError?.message}`, lastError);
}
//# sourceMappingURL=connection.js.map