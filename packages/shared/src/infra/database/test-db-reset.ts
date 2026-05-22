import { Pool } from 'pg';

const DEFAULT_DB_NAME = 'ltdjms_test';
const TEMPLATE_DB_NAME = 'template_clean';

/**
 * Rewrites a connection URL to connect to the `postgres` admin database
 * instead of the target test database, so DDL operations (DROP/CREATE DATABASE)
 * can be executed without being connected to the target database.
 */
function adminUrl(connectionUrl: string): string {
  return connectionUrl.replace(/\/[^/]+$/, '/postgres');
}

/**
 * Creates the `template_clean` database from the initial test database.
 * Must be called once after migrations have been applied.
 * Connects via the admin (postgres) database to avoid self-drop conflicts.
 */
export async function createTemplateDatabase(connectionUrl: string): Promise<void> {
  const adminPool = new Pool({ connectionString: adminUrl(connectionUrl), max: 1 });
  try {
    const result = await adminPool.query(
      `SELECT 1 FROM pg_database WHERE datname = '${TEMPLATE_DB_NAME}'`,
    );
    if (result.rows.length === 0) {
      await adminPool.query(
        `CREATE DATABASE ${TEMPLATE_DB_NAME} TEMPLATE ${DEFAULT_DB_NAME}`,
      );
    }
  } finally {
    await adminPool.end();
  }
}

/**
 * Resets the test database by dropping and recreating it from the clean template.
 * Terminates all existing connections to the database before dropping.
 *
 * @param connectionUrl - The connection URL of the test database.
 *   Internally connects via the `postgres` admin database for DDL operations.
 * @param dbName - The database name to reset (default: ltdjms_test).
 */
export async function resetDatabase(
  connectionUrl: string,
  dbName: string = DEFAULT_DB_NAME,
): Promise<void> {
  const adminPool = new Pool({ connectionString: adminUrl(connectionUrl), max: 1 });
  try {
    // Terminate other connections to the target database
    await adminPool.query(`
      SELECT pg_terminate_backend(pg_stat_activity.pid)
      FROM pg_stat_activity
      WHERE pg_stat_activity.datname = $1
        AND pid <> pg_backend_pid()
    `, [dbName]);

    // Drop and recreate from template
    await adminPool.query(`DROP DATABASE IF EXISTS "${dbName}"`);
    await adminPool.query(`CREATE DATABASE "${dbName}" TEMPLATE ${TEMPLATE_DB_NAME}`);
  } finally {
    await adminPool.end();
  }
}

/**
 * Creates a minimal pg Pool connected to the given connection URL.
 * The pool is configured with a single connection (max: 1) for test isolation.
 */
export function getTestPool(connectionUrl: string): Pool {
  return new Pool({ connectionString: connectionUrl, max: 1 });
}
