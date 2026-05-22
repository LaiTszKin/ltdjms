/**
 * Asserts that the total sum of balances is conserved across a set of accounts
 * before and after an operation. Throws on mismatch.
 */
export function assertBalanceConserved(
  before: Array<{ userId: number; balance: number }>,
  after: Array<{ userId: number; balance: number }>,
): void {
  const beforeSum = before.reduce((sum, a) => sum + a.balance, 0);
  const afterSum = after.reduce((sum, a) => sum + a.balance, 0);

  if (beforeSum !== afterSum) {
    throw new Error(`Balance not conserved: before=${beforeSum}, after=${afterSum}`);
  }
}

/**
 * Default allowed state transitions for fiat orders.
 */
const ALLOWED_TRANSITIONS: Record<string, string[]> = {
  PENDING_PAYMENT: ['PAID', 'EXPIRED', 'CANCELLED'],
  PAID: ['FULFILLING', 'REFUNDED'],
  FULFILLING: ['FULFILLED', 'FAILED'],
  EXPIRED: [],
  CANCELLED: [],
  REFUNDED: [],
  FULFILLED: [],
  FAILED: [],
};

/**
 * Asserts that a state transition is valid per the allowed transitions map.
 */
export function assertStateTransition(
  from: string,
  to: string,
  allowed?: Record<string, string[]>,
): void {
  const transitions = allowed ?? ALLOWED_TRANSITIONS;
  if (!transitions[from]?.includes(to)) {
    throw new Error(`Invalid state transition: ${from} -> ${to}`);
  }
}

export interface TimedResult<T> {
  result: T;
  durationMs: number;
}

/**
 * Measures the execution time of an async function.
 * Optionally asserts that the duration does not exceed maxMs.
 */
export async function measureResponseTime<T>(
  fn: () => Promise<T>,
  maxMs?: number,
): Promise<TimedResult<T>> {
  const start = Date.now();
  const result = await fn();
  const durationMs = Date.now() - start;

  if (maxMs !== undefined && durationMs > maxMs) {
    throw new Error(`Response time ${durationMs}ms exceeded max ${maxMs}ms`);
  }

  return { result, durationMs };
}
