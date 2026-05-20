import { type Guild } from 'discord.js';
import { z } from 'zod';
import { ToolCallerAuthorizationGuard } from './ToolCallerAuthorizationGuard.js';
export declare const GetChannelPermissionsParamsSchema: z.ZodObject<{
    channelId: z.ZodString;
}, "strip", z.ZodTypeAny, {
    channelId: string;
}, {
    channelId: string;
}>;
export type GetChannelPermissionsParams = z.infer<typeof GetChannelPermissionsParamsSchema>;
/**
 * Gets permission overwrites for a specific channel.
 * Tool name: get_channel_permissions
 */
export declare class GetChannelPermissionsTool {
    private readonly authGuard;
    readonly name = "get_channel_permissions";
    readonly description = "\u7372\u53D6\u6307\u5B9A\u983B\u9053\u7684\u6B0A\u9650\u8A2D\u5B9A";
    readonly schema: z.ZodObject<{
        channelId: z.ZodString;
    }, "strip", z.ZodTypeAny, {
        channelId: string;
    }, {
        channelId: string;
    }>;
    constructor(authGuard: ToolCallerAuthorizationGuard);
    execute(params: GetChannelPermissionsParams, guild: Guild): Promise<string>;
}
