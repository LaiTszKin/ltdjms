import { type Guild } from 'discord.js';
/**
 * Validates that the tool caller has ADMINISTRATOR permission or is the guild owner.
 * Matches Java ToolCallerAuthorizationGuard.
 */
export declare class ToolCallerAuthorizationGuard {
    /**
     * Validates that the caller has administrator permissions.
     *
     * @param guild - The Discord guild
     * @param toolName - The tool name for logging
     * @returns null if authorized, or an error message string if unauthorized
     */
    validateAdministrator(guild: Guild, toolName: string): Promise<string | null>;
}
