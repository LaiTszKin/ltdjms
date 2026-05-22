/**
 * Process items with a bounded concurrency limit.
 * Uses the worker-pool pattern: N concurrent workers pull items from a shared index.
 * This avoids the overhead of Promise.race and Set cleanup from alternative approaches.
 */
export async function processWithConcurrencyLimit<T, R>(
  items: T[],
  fn: (item: T) => Promise<R>,
  limit: number,
): Promise<R[]> {
  if (items.length === 0) return [];
  const results: R[] = new Array(items.length);
  let index = 0;

  const worker = async (): Promise<void> => {
    while (index < items.length) {
      const i = index++;
      results[i] = await fn(items[i]);
    }
  };

  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, () => worker()));
  return results;
}
