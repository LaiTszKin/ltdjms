import { PostgreSqlContainer } from '@testcontainers/postgresql';
import type { StartedPostgreSqlContainer } from '@testcontainers/postgresql';
import { Pool } from 'pg';
import { runMigrations } from '../infra/database/migration-runner.js';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const MIGRATIONS_DIR = path.resolve(__dirname, '../../db/migrations');

declare global {
  // eslint-disable-next-line no-var
  var __TEST_PG_CONTAINER: StartedPostgreSqlContainer | undefined;
}

export async function setup(): Promise<void> {
  const container = await new PostgreSqlContainer('postgres:16-alpine')
    .withDatabase('ltdjms_test')
    .withUsername('test')
    .withPassword('test')
    .start();

  const host = container.getHost();
  const port = container.getPort();
  const connectionUrl = `postgresql://test:test@${host}:${port}/ltdjms_test`;
  const adminUrl = `postgresql://test:test@${host}:${port}/postgres`;

  // Run migrations on the test database
  const migratePool = new Pool({ connectionString: connectionUrl });
  try {
    await runMigrations(migratePool, MIGRATIONS_DIR);
  } finally {
    await migratePool.end();
  }

  // Create template database — must use a connection to the `postgres` admin database
  // because the source database (ltdjms_test) must have zero connections.
  const adminPool = new Pool({ connectionString: adminUrl, max: 1 });
  try {
    await adminPool.query('CREATE DATABASE template_clean TEMPLATE ltdjms_test');
  } finally {
    await adminPool.end();
  }

  // Store connection info for tests via environment variables
  process.env.__TEST_CONTAINER_URL = connectionUrl;
  process.env.__TEST_CONTAINER_HOST = host;
  process.env.__TEST_CONTAINER_PORT = String(port);

  // Store container reference for teardown
  globalThis.__TEST_PG_CONTAINER = container;
}
