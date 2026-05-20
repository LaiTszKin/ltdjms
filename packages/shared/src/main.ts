/**
 * Main entry point has been moved to apps/bot.
 *
 * The startup logic was migrated from shared/src/main.ts to apps/bot/src/main.ts
 * to resolve the circular dependency: shared should not depend on workspace packages
 * (economy, dispatch, shop, ai, admin) via dynamic imports.
 *
 * See apps/bot/src/main.ts for the current application entry point.
 */

export function main(): never {
  throw new Error(
    '[DEPRECATED] shared/src/main.ts has been moved to apps/bot/src/main.ts. ' +
    'Import from @ltdjms/bot instead of @ltdjms/shared.',
  );
}
