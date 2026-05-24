/**
 * Ambient module declarations for dynamic imports in main.ts entry point.
 * These declare the shape of workspace packages that are loaded at runtime
 * via dynamic import() to avoid compile-time circular dependencies.
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
}

declare module '@ltdjms/ai' {
  export function initializeAIModule(): void;
  export const AI_TOKENS: Record<string, symbol>;
}

declare module '@ltdjms/admin' {
  export function configureAdminContainer(): void;
  export function disposeAdminContainer(): void;
  export const ADMIN_TOKENS: Record<string, symbol>;
  export interface SlashCommandDefinition {
    name: string;
    description: string;
    options?: unknown[];
    defaultMemberPermissions?: string | null;
    nameLocalizations?: Record<string, string>;
    descriptionLocalizations?: Record<string, string>;
  }
  export const SlashCommandRegistrar: {
    getCoreDefinitions(): SlashCommandDefinition[];
    registerAll(
      applicationId: string,
      restPut: (route: string, body: unknown) => Promise<unknown>,
      guildId?: string,
      definitions?: SlashCommandDefinition[],
    ): Promise<{ success: boolean; message: string }>;
    registerDefinitions(
      definitions: SlashCommandDefinition[],
      applicationId: string,
      restPut: (route: string, body: unknown) => Promise<unknown>,
      guildId?: string,
    ): Promise<{ success: boolean; message: string }>;
  };
}
