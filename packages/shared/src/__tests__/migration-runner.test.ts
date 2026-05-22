import { describe, it, expect, vi, beforeEach } from 'vitest';
import { SchemaMigrationException } from '../infra/database/schema-migration-exception.js';

// ---- Mock setup ----
// Use vi.hoisted() so variables are available in vi.mock() factory callbacks,
// since vi.mock calls are hoisted to the top of the file.

const mockPoolQuery = vi.fn();
const mockPool = {
  query: mockPoolQuery,
} as any;

const { mockDbExecute } = vi.hoisted(() => ({
  mockDbExecute: vi.fn(),
}));

const { mockReaddir } = vi.hoisted(() => ({
  mockReaddir: vi.fn(),
}));

const { mockReadFile } = vi.hoisted(() => ({
  mockReadFile: vi.fn(),
}));

vi.mock('drizzle-orm/node-postgres', () => ({
  drizzle: vi.fn(() => ({
    execute: mockDbExecute,
  })),
}));

vi.mock('node:fs/promises', () => ({
  readdir: mockReaddir,
  readFile: mockReadFile,
}));

const MIGRATIONS_DIR = '/mock/migrations';

import { runMigrations } from '../infra/database/migration-runner.js';

describe('runMigrations', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockPoolQuery.mockReset();
    mockDbExecute.mockReset();
    mockReaddir.mockReset();
    mockReadFile.mockReset();
  });

  describe('fresh database (no tracking table, no existing tables)', () => {
    beforeEach(() => {
      // CREATE TABLE IF NOT EXISTS tracking table — succeeds
      mockDbExecute.mockResolvedValueOnce({ rows: [] });
      // SELECT COUNT(*) FROM tracking table — 0 entries
      mockDbExecute.mockResolvedValueOnce({ rows: [{ count: 0 }] });
      // SELECT COUNT(*) FROM information_schema — 0 other tables
      mockDbExecute.mockResolvedValueOnce({ rows: [{ count: 0 }] });
    });

    it('applies all migration files in order on a fresh database', async () => {
      mockReaddir.mockResolvedValue([
        'V001__init.sql',
        'V002__add_users.sql',
        'V003__add_products.sql',
      ]);
      mockReadFile
        .mockResolvedValueOnce('CREATE TABLE test (id INT);')
        .mockResolvedValueOnce('ALTER TABLE test ADD COLUMN name TEXT;')
        .mockResolvedValueOnce('CREATE TABLE products (id INT);');
      mockPoolQuery.mockResolvedValue({ rows: [] });
      // INSERT INTO tracking table (3 calls)
      mockDbExecute
        .mockResolvedValueOnce({ rows: [] })
        .mockResolvedValueOnce({ rows: [] })
        .mockResolvedValueOnce({ rows: [] });

      await runMigrations(mockPool, MIGRATIONS_DIR);

      expect(mockReaddir).toHaveBeenCalledWith(MIGRATIONS_DIR);
      expect(mockPoolQuery).toHaveBeenCalledTimes(3);
      expect(mockPoolQuery.mock.calls[0][0]).toBe('CREATE TABLE test (id INT);');
      expect(mockPoolQuery.mock.calls[1][0]).toBe('ALTER TABLE test ADD COLUMN name TEXT;');
      expect(mockPoolQuery.mock.calls[2][0]).toBe('CREATE TABLE products (id INT);');
      // Verify tracking table inserts
      expect(mockDbExecute).toHaveBeenCalledTimes(6); // 3 setup + 3 inserts
    });
  });

  describe('incremental (tracking table has existing entries)', () => {
    beforeEach(() => {
      // CREATE TABLE IF NOT EXISTS tracking table — succeeds
      mockDbExecute.mockResolvedValueOnce({ rows: [] });
      // SELECT COUNT(*) FROM tracking table — 2 entries already
      mockDbExecute.mockResolvedValueOnce({ rows: [{ count: 2 }] });
      // SELECT filename FROM tracking table — returns already applied files
      mockDbExecute.mockResolvedValueOnce({
        rows: [{ filename: 'V001__init.sql' }, { filename: 'V002__add_users.sql' }],
      });
    });

    it('applies only unmarked migration files', async () => {
      mockReaddir.mockResolvedValue([
        'V001__init.sql',
        'V002__add_users.sql',
        'V003__add_products.sql',
      ]);
      mockReadFile.mockResolvedValueOnce('CREATE TABLE products (id INT);');
      mockPoolQuery.mockResolvedValue({ rows: [] });
      // INSERT for the new migration
      mockDbExecute.mockResolvedValueOnce({ rows: [] });

      await runMigrations(mockPool, MIGRATIONS_DIR);

      expect(mockPoolQuery).toHaveBeenCalledTimes(1);
      expect(mockPoolQuery.mock.calls[0][0]).toBe('CREATE TABLE products (id INT);');
      expect(mockReadFile).toHaveBeenCalledTimes(1);
    });

    it('skips all files when all are already applied', async () => {
      mockReaddir.mockResolvedValue(['V001__init.sql', 'V002__add_users.sql']);

      await runMigrations(mockPool, MIGRATIONS_DIR);

      expect(mockPoolQuery).not.toHaveBeenCalled();
      expect(mockReadFile).not.toHaveBeenCalled();
    });
  });

  describe('baseline (existing tables, empty tracking table)', () => {
    beforeEach(() => {
      // CREATE TABLE IF NOT EXISTS tracking table — succeeds
      mockDbExecute.mockResolvedValueOnce({ rows: [] });
      // SELECT COUNT(*) FROM tracking table — 0 entries
      mockDbExecute.mockResolvedValueOnce({ rows: [{ count: 0 }] });
      // SELECT COUNT(*) FROM information_schema — 5 other tables exist
      mockDbExecute.mockResolvedValueOnce({ rows: [{ count: 5 }] });
    });

    it('baselines by marking all migration files as applied without executing them', async () => {
      mockReaddir.mockResolvedValue([
        'V001__init.sql',
        'V002__add_users.sql',
        'V003__add_products.sql',
      ]);
      // INSERT INTO tracking table for each file (3 calls)
      mockDbExecute
        .mockResolvedValueOnce({ rows: [] })
        .mockResolvedValueOnce({ rows: [] })
        .mockResolvedValueOnce({ rows: [] });

      await runMigrations(mockPool, MIGRATIONS_DIR);

      // No migrations should have been executed
      expect(mockPoolQuery).not.toHaveBeenCalled();
      expect(mockReadFile).not.toHaveBeenCalled();
      // All 3 files should have been baselined
      expect(mockDbExecute).toHaveBeenCalledTimes(6); // 3 setup + 3 baselines
    });
  });

  describe('error handling', () => {
    it('retries on failure and throws SchemaMigrationException after exhausting retries', async () => {
      // CREATE TABLE IF NOT EXISTS tracking table — fails each time
      mockDbExecute.mockRejectedValue(new Error('connection error'));

      vi.useFakeTimers();
      // Use a catch handler upfront to prevent unhandled rejection warning
      // when using fake timers (the rejection happens during timer advancement).
      const migrationPromise = runMigrations(mockPool, MIGRATIONS_DIR).catch((err) => err);

      // Advance past retry delays (1s + 1s)
      await vi.advanceTimersByTimeAsync(2000);

      const err = await migrationPromise;
      expect(err).toBeInstanceOf(SchemaMigrationException);
      expect((err as SchemaMigrationException).message).toContain(
        'Database migration failed after 3 attempts',
      );

      // Should have tried 3 times
      expect(mockDbExecute).toHaveBeenCalledTimes(3);
      vi.useRealTimers();
    });

    it('throws SchemaMigrationException when migration SQL has syntax error', async () => {
      vi.useFakeTimers();
      // First call: CREATE TABLE IF NOT EXISTS tracking table — succeeds
      mockDbExecute.mockResolvedValueOnce({ rows: [] });
      // Second call: SELECT COUNT(*) FROM tracking — 0
      mockDbExecute.mockResolvedValueOnce({ rows: [{ count: 0 }] });
      // Third call: SELECT COUNT(*) FROM information_schema — 0
      mockDbExecute.mockResolvedValueOnce({ rows: [{ count: 0 }] });

      mockReaddir.mockResolvedValue(['V001__init.sql']);
      mockReadFile.mockResolvedValueOnce('INVALID SQL !!!');
      // pool.query throws on invalid SQL
      mockPoolQuery.mockRejectedValue(new Error('syntax error at or near "INVALID"'));

      // Retry attempts: the retry loop will re-attempt the whole flow 3 times
      // Each retry: CREATE TABLE IF NOT EXISTS (succeeds), SELECT COUNT (0),
      //   SELECT COUNT info_schema (0), readdir, readFile, pool.query (fails)
      // Setup for retry 2:
      mockDbExecute
        .mockResolvedValueOnce({ rows: [] }) // tracking table
        .mockResolvedValueOnce({ rows: [{ count: 0 }] }) // tracking count
        .mockResolvedValueOnce({ rows: [{ count: 0 }] }); // info_schema count
      mockReaddir.mockResolvedValue(['V001__init.sql']);
      mockReadFile.mockResolvedValueOnce('INVALID SQL !!!');
      mockPoolQuery.mockRejectedValue(new Error('syntax error'));
      // Setup for retry 3:
      mockDbExecute
        .mockResolvedValueOnce({ rows: [] }) // tracking table
        .mockResolvedValueOnce({ rows: [{ count: 0 }] }) // tracking count
        .mockResolvedValueOnce({ rows: [{ count: 0 }] }); // info_schema count
      mockReaddir.mockResolvedValue(['V001__init.sql']);
      mockReadFile.mockResolvedValueOnce('INVALID SQL !!!');
      mockPoolQuery.mockRejectedValue(new Error('syntax error'));

      const migrationPromise = runMigrations(mockPool, MIGRATIONS_DIR).catch((err) => err);

      // Advance past retry delays (1s + 1s)
      await vi.advanceTimersByTimeAsync(2000);

      const err = await migrationPromise;
      expect(err).toBeInstanceOf(SchemaMigrationException);
      expect((err as SchemaMigrationException).message).toContain(
        'Database migration failed after 3 attempts',
      );

      expect(mockPoolQuery).toHaveBeenCalledTimes(3);
      expect(mockDbExecute).toHaveBeenCalledTimes(9); // 3 per attempt × 3 attempts
      vi.useRealTimers();
    });
  });
});
