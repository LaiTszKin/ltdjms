import { Pool } from 'pg';
import fs from 'node:fs';

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
      await adminPool.query(`CREATE DATABASE ${TEMPLATE_DB_NAME} TEMPLATE ${DEFAULT_DB_NAME}`);
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
    await adminPool.query(
      `
      SELECT pg_terminate_backend(pg_stat_activity.pid)
      FROM pg_stat_activity
      WHERE pg_stat_activity.datname = $1
        AND pid <> pg_backend_pid()
    `,
      [dbName],
    );

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

/**
 * Discovers the Testcontainer PostgreSQL server and creates a dedicated
 * database for a workspace project (e.g., `ltdjms_test_economy`).
 *
 * In vitest workspace mode, each project runs in its own fork and needs
 * an independent database to avoid cross-project data races.
 *
 * Call this once per workspace project (e.g., in a vitest setupFile).
 */
export async function initProjectDatabase(
  projectName: string,
  adminUrlOverride?: string,
): Promise<string> {
  // Determine admin connection string
  let adminConnStr: string;

  if (adminUrlOverride) {
    adminConnStr = adminUrlOverride;
  } else {
    // Try reading from shared info file (written by globalSetup)
    const infoFile = '/tmp/ltdjms-testcontainers.json';
    if (fs.existsSync(infoFile)) {
      const info = JSON.parse(fs.readFileSync(infoFile, 'utf-8'));
      adminConnStr = info.adminUrl;
    } else if (process.env.__TEST_CONTAINER_URL) {
      adminConnStr = adminUrl(process.env.__TEST_CONTAINER_URL);
    } else {
      throw new Error(
        'Testcontainer not initialized. The shared package globalSetup must run first. ' +
          `Cannot find container info for project "${projectName}".`,
      );
    }
  }

  const dbName = `ltdjms_test_${projectName}`;
  const adminPool = new Pool({ connectionString: adminConnStr, max: 1 });

  try {
    // Drop existing database if present (from a previous run)
    await adminPool.query(
      `
      SELECT pg_terminate_backend(pg_stat_activity.pid)
      FROM pg_stat_activity
      WHERE pg_stat_activity.datname = $1 AND pid <> pg_backend_pid()
    `,
      [dbName],
    );
    await adminPool.query(`DROP DATABASE IF EXISTS "${dbName}"`);

    // Create fresh database from the clean template
    await adminPool.query(`CREATE DATABASE "${dbName}" TEMPLATE template_clean`);
  } finally {
    await adminPool.end();
  }

  // Build connection URL for the new database
  const baseUrl = adminConnStr.replace('/postgres', `/${dbName}`);
  return baseUrl;
}

/**
 * Safely cleans all data from all tables in the public schema without
 * dropping or recreating the database. Uses DELETE (not TRUNCATE) to
 * avoid FK constraint issues — deletes in dependency-safe order.
 *
 * Unlike resetDatabase(), this does NOT terminate connections, making it
 * safe for parallel workspace-mode test execution.
 */
export async function cleanAllTestTables(connectionUrl: string): Promise<void> {
  const pool = new Pool({ connectionString: connectionUrl, max: 1 });
  try {
    // Disable triggers temporarily to allow unordered deletion
    await pool.query('SET session_replication_role = replica');

    // Query all user tables in public schema (exclude migration tracking)
    const result = await pool.query<{ tablename: string }>(`
      SELECT tablename FROM pg_tables
      WHERE schemaname = 'public'
        AND tablename != '_ltdjms_migrations'
      ORDER BY tablename
    `);

    for (const row of result.rows) {
      await pool.query(`DELETE FROM "${row.tablename}"`);
    }

    // Re-enable triggers
    await pool.query('SET session_replication_role = origin');
  } finally {
    await pool.end();
  }
}
