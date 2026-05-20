import { type Guild } from 'discord.js';
import { z } from 'zod';
import { ToolCallerAuthorizationGuard } from './ToolCallerAuthorizationGuard.js';
export declare const SearchMessagesParamsSchema: z.ZodObject<{
    keywords: z.ZodString;
    channelIds: z.ZodOptional<z.ZodArray<z.ZodString, "many">>;
    maxResultsPerChannel: z.ZodDefault<z.ZodOptional<z.ZodNumber>>;
    maxMessagesToScan: z.ZodDefault<z.ZodOptional<z.ZodNumber>>;
}, "strip", z.ZodTypeAny, {
    keywords: string;
    maxResultsPerChannel: number;
    maxMessagesToScan: number;
    channelIds?: string[] | undefined;
}, {
    keywords: string;
    channelIds?: string[] | undefined;
    maxResultsPerChannel?: number | undefined;
    maxMessagesToScan?: number | undefined;
}>;
export type SearchMessagesParams = z.infer<typeof SearchMessagesParamsSchema>;
/**
 * Searches for messages containing keywords in the guild.
 * Results are REDACTED for cross-turn memory isolation.
 * Tool name: search_messages
 */
export declare class SearchMessagesTool {
    private readonly authGuard;
    readonly name = "search_messages";
    readonly description = "\u641C\u5C0B\u6B77\u53F2\u8A0A\u606F\uFF0C\u95DC\u9375\u5B57\u641C\u5C0B";
    readonly schema: z.ZodObject<{
        keywords: z.ZodString;
        channelIds: z.ZodOptional<z.ZodArray<z.ZodString, "many">>;
        maxResultsPerChannel: z.ZodDefault<z.ZodOptional<z.ZodNumber>>;
        maxMessagesToScan: z.ZodDefault<z.ZodOptional<z.ZodNumber>>;
    }, "strip", z.ZodTypeAny, {
        keywords: string;
        maxResultsPerChannel: number;
        maxMessagesToScan: number;
        channelIds?: string[] | undefined;
    }, {
        keywords: string;
        channelIds?: string[] | undefined;
        maxResultsPerChannel?: number | undefined;
        maxMessagesToScan?: number | undefined;
    }>;
    constructor(authGuard: ToolCallerAuthorizationGuard);
    execute(params: SearchMessagesParams, guild: Guild): Promise<string>;
}
