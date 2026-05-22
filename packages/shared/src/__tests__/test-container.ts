import { container as rootContainer } from 'tsyringe';
import { TOKENS, initializeContainer } from '../infra/di/container.js';
import { Pool } from 'pg';
import pino from 'pino';
import type { NodePgDatabase } from 'drizzle-orm/node-postgres';
import { drizzle } from 'drizzle-orm/node-postgres';

export interface TestContainerOptions {
  pool: Pool;
  overrides?: Partial<Record<keyof typeof TOKENS, unknown>>;
}

/**
 * Initializes the root DI container for testing with a real database pool,
 * silent logger, and default NoOp services.
 *
 * After calling this, modules like economy and shop can call their configure
 * functions which will `container.resolve()` from the root.
 *
 * NOTE: Call `resetRootContainer()` in `beforeEach` to fully clear instances
 * between tests. This is important because tsyringe stores singleton instances.
 */
export function createTestContainer(options: TestContainerOptions): {
  container: typeof rootContainer;
  db: NodePgDatabase;
} {
  // Clear any previously registered instances to avoid cross-test pollution
  rootContainer.clearInstances();

  initializeContainer({
    databasePool: options.pool,
    logger: pino({ level: 'silent' }),
  });

  // Apply user overrides after default initialization
  if (options.overrides) {
    for (const [tokenKey, instance] of Object.entries(options.overrides)) {
      const token = TOKENS[tokenKey as keyof typeof TOKENS];
      if (token) {
        rootContainer.registerInstance(token, instance);
      }
    }
  }

  const db = drizzle(options.pool);

  return { container: rootContainer, db };
}

/**
 * Resets the root DI container for test isolation.
 * Call this in `beforeEach` when testing modules that use the root container
 * directly (e.g., economy or shop module configure functions).
 *
 * @example
 * ```typescript
 * import { resetRootContainer } from '@ltdjms/shared/__tests__/test-container';
 *
 * beforeEach(() => {
 *   resetRootContainer(testPool);
 *   configureEconomyContainer();
 * });
 * ```
 */
export function resetRootContainer(pool?: Pool): void {
  rootContainer.clearInstances();

  if (pool) {
    initializeContainer({
      databasePool: pool,
      logger: pino({ level: 'silent' }),
    });
  }
}
