import { PostgreSqlContainer } from '@testcontainers/postgresql';
import type { StartedPostgreSqlContainer } from '@testcontainers/postgresql';
import { Pool } from 'pg';
import { runMigrations } from '../infra/database/migration-runner.js';
import path from 'node:path';
import fs from 'node:fs';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const MIGRATIONS_DIR = path.resolve(__dirname, '../../db/migrations');

const INFO_FILE = '/tmp/ltdjms-testcontainers.json';

declare global {
  // eslint-disable-next-line no-var
  var __TEST_PG_CONTAINER: StartedPostgreSqlContainer | undefined;
}

export async function setup(): Promise<void> {
  // Reuse existing container from a previous process run (if any).
  // Retry a few times to handle brief container unavailability.
  if (fs.existsSync(INFO_FILE)) {
    const info = JSON.parse(fs.readFileSync(INFO_FILE, 'utf-8'));
    for (let attempt = 1; attempt <= 5; attempt++) {
      try {
        const testPool = new Pool({ connectionString: `postgresql://test:test@${info.host}:${info.port}/postgres`, max: 1, connectionTimeoutMillis: 3000 });
        await testPool.query('SELECT 1');
        await testPool.end();
        const connectionUrl = `postgresql://test:test@${info.host}:${info.port}/ltdjms_test`;
        process.env.__TEST_CONTAINER_URL = connectionUrl;
        process.env.__TEST_CONTAINER_HOST = info.host;
        process.env.__TEST_CONTAINER_PORT = String(info.port);
        return;
      } catch (err) {
        if (attempt === 5) {
          // eslint-disable-next-line no-console
          console.warn(`[testcontainer] Container at ${info.host}:${info.port} unreachable after 5 attempts — starting fresh`);
          fs.rmSync(INFO_FILE, { force: true });
        } else {
          await new Promise((r) => setTimeout(r, 1000));
        }
      }
    }
  }

  // Disable Ryuk so the container survives process exit and can be
  // reused by subsequent sequential vitest runs in `make test`.
  process.env.TESTCONTAINERS_RYUK_DISABLED = 'true';

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

  // Create template database
  const adminPool = new Pool({ connectionString: adminUrl, max: 1 });
  try {
    await adminPool.query('CREATE DATABASE template_clean TEMPLATE ltdjms_test');
  } finally {
    await adminPool.end();
  }

  process.env.__TEST_CONTAINER_URL = connectionUrl;
  process.env.__TEST_CONTAINER_HOST = host;
  process.env.__TEST_CONTAINER_PORT = String(port);

  const info = { host, port, adminUrl };
  fs.writeFileSync(INFO_FILE, JSON.stringify(info, null, 2));
  globalThis.__TEST_PG_CONTAINER = container;
  // eslint-disable-next-line no-console
  console.log(`[testcontainer] Started PostgreSQL at ${host}:${port}`);
}
