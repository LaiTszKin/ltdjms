/**
 * Ambient module declarations for dynamic imports in main.ts entry point.
 * These declare the shape of workspace packages that are loaded at runtime
 * via dynamic import() to avoid compile-time circular dependencies.
 *
 * NOTE: These are TEMPORARY stubs that assume the API shape of workspace
 * packages that may not yet exist or may change. Once all packages are
 * implemented, these should be replaced with proper package-level type
 * exports (e.g. via tsconfig paths or actual package builds).
 */

declare module '@ltdjms/economy' {
  export const ECONOMY_TOKENS: Record<string, symbol>;
  export function configureEconomyContainer(): void;
}

declare module '@ltdjms/dispatch' {
  export const DISPATCH_TOKENS: Record<string, symbol>;
  export function configureDispatchContainer(): void;
}

declare module '@ltdjms/shop' {
  export function configureContainer(options: Record<string, unknown>): void;
  export class DrizzleProductRepository {
    constructor(db: unknown);
  }
  export class DrizzleRedemptionTransactionService {
    constructor(db: unknown);
  }
}

declare module '@ltdjms/ai' {
  export function initializeAIModule(): void;
  export const AI_TOKENS: Record<string, symbol>;
}

declare module '@ltdjms/admin' {
  export function configureAdminContainer(): void;
  export const ADMIN_TOKENS: Record<string, symbol>;
  export const SlashCommandListener: unknown;
  export const SlashCommandRegistrar: {
    registerAll(
      applicationId: string,
      restPut: (route: string, body: unknown) => Promise<unknown>,
      guildId?: string,
    ): Promise<{ success: boolean; message: string }>;
  };
}
