/**
 * A slash command definition suitable for Discord API registration.
 */
export interface SlashCommandDefinition {
    name: string;
    description: string;
    options?: unknown[];
    defaultMemberPermissions?: string | null;
    nameLocalizations?: Record<string, string>;
    descriptionLocalizations?: Record<string, string>;
}
/**
 * Registers slash commands with the Discord API via a REST client.
 * Matches Java SlashCommandRegistrar.
 */
export declare class SlashCommandRegistrar {
    /**
     * Returns all command definitions that should be registered.
     */
    static getAllDefinitions(): SlashCommandDefinition[];
    /**
     * Registers all commands globally via the provided REST client.
     * Typically called with discord.js REST.put().
     * @param applicationId - Discord application ID
     * @param restPut - Function that calls REST.put() with the given route and body
     * @param guildId - Optional guild ID for development (guild-specific registration)
     */
    static registerAll(applicationId: string, restPut: (route: string, body: unknown) => Promise<unknown>, guildId?: string): Promise<{
        success: boolean;
        message: string;
    }>;
}
