import { describe, it, expect } from 'vitest';
import { processWithConcurrencyLimit } from '../utils/concurrency.js';

describe('processWithConcurrencyLimit', () => {
  it('processes all items and returns results in order', async () => {
    const input = [1, 2, 3, 4, 5];
    const result = await processWithConcurrencyLimit(input, async (n) => n * 2, 2);
    expect(result).toEqual([2, 4, 6, 8, 10]);
  });

  it('handles empty array', async () => {
    const result = await processWithConcurrencyLimit([], async (n: number) => n, 3);
    expect(result).toEqual([]);
  });

  it('respects concurrency limit of 1 (sequential)', async () => {
    const order: number[] = [];
    const input = [1, 2, 3];
    await processWithConcurrencyLimit(
      input,
      async (n) => {
        order.push(n);
        return n;
      },
      1,
    );
    expect(order).toEqual([1, 2, 3]);
  });

  it('respects concurrency limit greater than item count', async () => {
    const input = [10, 20, 30];
    const result = await processWithConcurrencyLimit(input, async (n) => n + 1, 10);
    expect(result).toEqual([11, 21, 31]);
  });

  it('preserves item order with concurrent execution', async () => {
    const input = ['a', 'b', 'c', 'd', 'e'];
    // Each item takes a random delay to simulate real concurrency
    const result = await processWithConcurrencyLimit(input, async (s) => s.toUpperCase(), 3);
    expect(result).toEqual(['A', 'B', 'C', 'D', 'E']);
  });

  it('handles single item', async () => {
    const result = await processWithConcurrencyLimit([42], async (n) => n * 2, 2);
    expect(result).toEqual([84]);
  });

  it('does not exceed the specified concurrency limit', async () => {
    let concurrent = 0;
    let maxConcurrent = 0;
    const input = [1, 2, 3, 4, 5, 6];

    await processWithConcurrencyLimit(
      input,
      async (n) => {
        concurrent++;
        maxConcurrent = Math.max(maxConcurrent, concurrent);
        // Simulate async work
        await new Promise((resolve) => setImmediate(resolve));
        concurrent--;
        return n;
      },
      2,
    );

    expect(maxConcurrent).toBeLessThanOrEqual(2);
  });
});
