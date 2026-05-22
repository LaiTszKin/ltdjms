/**
 * Global teardown for vitest.
 *
 * The Testcontainers Ryuk sidecar container automatically cleans up
 * the PostgreSQL container when the Node.js process exits, so explicit
 * container teardown is not strictly necessary here.
 *
 * If the container reference was stored on globalThis during setup,
 * we stop it cleanly here.
 */
export async function teardown(): Promise<void> {
  // eslint-disable-next-line @typescript-eslint/no-unnecessary-condition
  if (globalThis.__TEST_PG_CONTAINER) {
    try {
      await globalThis.__TEST_PG_CONTAINER.stop();
    } catch {
      // Ignore errors during teardown — Ryuk will handle cleanup
    }
  }
}
