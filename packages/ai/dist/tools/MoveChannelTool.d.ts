import { type Guild } from 'discord.js';
import { z } from 'zod';
import { ToolCallerAuthorizationGuard } from './ToolCallerAuthorizationGuard.js';
export declare const MoveChannelParamsSchema: z.ZodObject<{
    channelId: z.ZodString;
    targetCategoryId: z.ZodString;
}, "strip", z.ZodTypeAny, {
    channelId: string;
    targetCategoryId: string;
}, {
    channelId: string;
    targetCategoryId: string;
}>;
export type MoveChannelParams = z.infer<typeof MoveChannelParamsSchema>;
/**
 * Moves a channel to a different category.
 * Tool name: move_channel
 */
export declare class MoveChannelTool {
    private readonly authGuard;
    readonly name = "move_channel";
    readonly description = "\u79FB\u52D5\u983B\u9053\u81F3\u6307\u5B9A\u5206\u985E";
    readonly schema: z.ZodObject<{
        channelId: z.ZodString;
        targetCategoryId: z.ZodString;
    }, "strip", z.ZodTypeAny, {
        channelId: string;
        targetCategoryId: string;
    }, {
        channelId: string;
        targetCategoryId: string;
    }>;
    constructor(authGuard: ToolCallerAuthorizationGuard);
    execute(params: MoveChannelParams, guild: Guild): Promise<string>;
}
