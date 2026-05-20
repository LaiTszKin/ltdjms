import { type OverwriteResolvable } from 'discord.js';
import type { PermissionSetting } from '../services/ai-chat-service.js';
/**
 * Parses PermissionSetting[] into discord.js OverwriteResolvable[].
 * Matches Java PermissionParser.
 */
export declare class PermissionParser {
    /**
     * Converts PermissionSetting[] to OverwriteResolvable[].
     */
    parse(permissions: PermissionSetting[]): OverwriteResolvable[];
    /**
     * Resolves a permission name string to a PermissionFlagsBits BigInt.
     */
    private resolvePermission;
}
