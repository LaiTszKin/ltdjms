import { fetch, type RequestInit, type Response } from 'undici';

const RETRYABLE_CODES = new Set(['ECONNRESET', 'ETIMEDOUT', 'ENOTFOUND', 'UND_ERR_CONNECT_TIMEOUT']);

/**
 * Wraps undici.fetch with retry logic for transient network errors.
 * Retries up to `maxRetries` times with exponential backoff (1s / 2s / 4s).
 * Non-retryable HTTP responses (4xx, 5xx) are returned as-is without retry.
 */
export async function fetchWithRetry(
  url: string,
  init: RequestInit,
  maxRetries = 3,
): Promise<Response> {
  for (let attempt = 1; ; attempt++) {
    try {
      return await fetch(url, init);
    } catch (err: any) {
      const isRetryable =
        err?.cause?.code && RETRYABLE_CODES.has(err.cause.code) ||
        err?.name === 'TimeoutError' ||
        err?.name === 'AbortError';

      if (!isRetryable || attempt >= maxRetries) {
        throw err;
      }

      const delay = Math.min(1000 * Math.pow(2, attempt - 1), 4000);
      await new Promise((resolve) => setTimeout(resolve, delay));
    }
  }
}
