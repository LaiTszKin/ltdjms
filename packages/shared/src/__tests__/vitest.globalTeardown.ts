/**
 * Global teardown — a deliberate no-op.
 *
 * The Testcontainers Ryuk sidecar automatically cleans up the PostgreSQL
 * container when the Node.js process exits, so we never need to stop it
 * explicitly here.  This is especially important in sequential mode where
 * multiple vitest runs reuse the same container.
 */
export async function teardown(): Promise<void> {
  // Everything is handled by Ryuk's automatic sidecar cleanup.
}
