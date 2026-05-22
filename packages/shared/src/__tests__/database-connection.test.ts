import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { SchemaMigrationException } from '../infra/database/schema-migration-exception.js';

const mockClient = {
  query: vi.fn(),
  release: vi.fn(),
};

const mockPoolInstance = {
  connect: vi.fn(),
  end: vi.fn().mockResolvedValue(undefined),
  query: vi.fn(),
  on: vi.fn(),
};

vi.mock('pg', () => ({
  Pool: vi.fn(() => mockPoolInstance),
}));

import { createDatabasePool } from '../infra/database/connection.js';

describe('createDatabasePool', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockClient.query.mockReset();
    mockClient.release.mockReset();
    mockPoolInstance.connect.mockReset();
    mockPoolInstance.end.mockReset();
    mockPoolInstance.end.mockResolvedValue(undefined);
    mockPoolInstance.connect.mockResolvedValue(mockClient);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('returns pool when connection succeeds on first attempt', async () => {
    mockClient.query.mockResolvedValue({ rows: [{ '?column?': 1 }] });

    const pool = await createDatabasePool({
      url: 'postgresql://localhost:5432/test',
      max: 1,
      connectionTimeoutMillis: 1000,
      idleTimeoutMillis: 5000,
    });

    expect(pool).toBe(mockPoolInstance);
    expect(mockPoolInstance.connect).toHaveBeenCalledTimes(1);
    expect(mockClient.query).toHaveBeenCalledWith('SELECT 1');
    expect(mockClient.release).toHaveBeenCalledTimes(1);
  });

  it('retries on first connect failure and succeeds on second attempt', async () => {
    mockClient.query
      .mockRejectedValueOnce(new Error('connection refused'))
      .mockResolvedValueOnce({ rows: [{ '?column?': 1 }] });

    vi.useFakeTimers();
    const poolPromise = createDatabasePool({
      url: 'postgresql://localhost:5432/test',
      max: 1,
      connectionTimeoutMillis: 1000,
      idleTimeoutMillis: 5000,
    });

    // Advance past the 2s retry delay
    await vi.advanceTimersByTimeAsync(2000);

    const pool = await poolPromise;
    expect(pool).toBe(mockPoolInstance);
    expect(mockPoolInstance.connect).toHaveBeenCalledTimes(2);
    expect(mockClient.release).toHaveBeenCalledTimes(1);
  });

  it('retries 3 times and throws SchemaMigrationException when all attempts fail', async () => {
    mockClient.query.mockRejectedValue(new Error('connection refused'));

    vi.useFakeTimers();
    const poolPromise = createDatabasePool({
      url: 'postgresql://localhost:5432/test',
      max: 1,
      connectionTimeoutMillis: 1000,
      idleTimeoutMillis: 5000,
    }).catch((err) => err);

    // Advance past all retry delays (2s + 2s)
    await vi.advanceTimersByTimeAsync(4000);

    const err = await poolPromise;
    expect(err).toBeInstanceOf(SchemaMigrationException);
    expect((err as SchemaMigrationException).message).toContain(
      'Failed to connect to database after 3 attempts',
    );

    expect(mockPoolInstance.connect).toHaveBeenCalledTimes(3);
    expect(mockPoolInstance.end).toHaveBeenCalledTimes(1);
  });
});
