import { type Guild } from 'discord.js';
import { z } from 'zod';
import { ToolCallerAuthorizationGuard } from './ToolCallerAuthorizationGuard.js';
export declare const DeleteDiscordResourceParamsSchema: z.ZodObject<{
    resourceType: z.ZodEnum<["channel", "category", "role"]>;
    resourceId: z.ZodString;
}, "strip", z.ZodTypeAny, {
    resourceType: "role" | "channel" | "category";
    resourceId: string;
}, {
    resourceType: "role" | "channel" | "category";
    resourceId: string;
}>;
export type DeleteDiscordResourceParams = z.infer<typeof DeleteDiscordResourceParamsSchema>;
/**
 * Deletes a Discord resource (channel, category, or role).
 * Tool name: delete_discord_resource
 */
export declare class DeleteDiscordResourceTool {
    private readonly authGuard;
    readonly name = "delete_discord_resource";
    readonly description = "\u522A\u9664 Discord \u8CC7\u6E90\uFF08\u983B\u9053/\u5206\u985E/\u8EAB\u5206\u7D44\uFF09";
    readonly schema: z.ZodObject<{
        resourceType: z.ZodEnum<["channel", "category", "role"]>;
        resourceId: z.ZodString;
    }, "strip", z.ZodTypeAny, {
        resourceType: "role" | "channel" | "category";
        resourceId: string;
    }, {
        resourceType: "role" | "channel" | "category";
        resourceId: string;
    }>;
    constructor(authGuard: ToolCallerAuthorizationGuard);
    execute(params: DeleteDiscordResourceParams, guild: Guild): Promise<string>;
}
