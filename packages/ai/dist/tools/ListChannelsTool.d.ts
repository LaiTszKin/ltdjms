import { type Guild } from 'discord.js';
import { z } from 'zod';
import { ToolCallerAuthorizationGuard } from './ToolCallerAuthorizationGuard.js';
export declare const ListChannelsParamsSchema: z.ZodObject<{
    type: z.ZodOptional<z.ZodEnum<["text", "voice", "category", "forum", "media", "stage"]>>;
}, "strip", z.ZodTypeAny, {
    type?: "text" | "voice" | "category" | "forum" | "media" | "stage" | undefined;
}, {
    type?: "text" | "voice" | "category" | "forum" | "media" | "stage" | undefined;
}>;
export type ListChannelsParams = z.infer<typeof ListChannelsParamsSchema>;
/**
 * Lists all channels in the guild, optionally filtered by type.
 * Tool name: list_channels
 */
export declare class ListChannelsTool {
    private readonly authGuard;
    readonly name = "list_channels";
    readonly description = "\u5217\u51FA\u4F3A\u670D\u5668\u4E2D\u7684\u6240\u6709\u983B\u9053\uFF0C\u53EF\u6309\u985E\u578B\u7BE9\u9078";
    readonly schema: z.ZodObject<{
        type: z.ZodOptional<z.ZodEnum<["text", "voice", "category", "forum", "media", "stage"]>>;
    }, "strip", z.ZodTypeAny, {
        type?: "text" | "voice" | "category" | "forum" | "media" | "stage" | undefined;
    }, {
        type?: "text" | "voice" | "category" | "forum" | "media" | "stage" | undefined;
    }>;
    constructor(authGuard: ToolCallerAuthorizationGuard);
    execute(params: ListChannelsParams, guild: Guild): Promise<string>;
}
