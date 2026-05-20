import { type Guild } from 'discord.js';
import { z } from 'zod';
import { ToolCallerAuthorizationGuard } from './ToolCallerAuthorizationGuard.js';
export declare const SendMessagesParamsSchema: z.ZodObject<{
    channelIds: z.ZodOptional<z.ZodArray<z.ZodString, "many">>;
    message: z.ZodOptional<z.ZodString>;
    messages: z.ZodOptional<z.ZodArray<z.ZodString, "many">>;
}, "strip", z.ZodTypeAny, {
    message?: string | undefined;
    messages?: string[] | undefined;
    channelIds?: string[] | undefined;
}, {
    message?: string | undefined;
    messages?: string[] | undefined;
    channelIds?: string[] | undefined;
}>;
export type SendMessagesParams = z.infer<typeof SendMessagesParamsSchema>;
/**
 * Sends messages to specified channels.
 * Tool name: send_messages
 */
export declare class SendMessagesTool {
    private readonly authGuard;
    readonly name = "send_messages";
    readonly description = "\u767C\u9001\u8A0A\u606F\u81F3\u6307\u5B9A\u7684\u983B\u9053";
    readonly schema: z.ZodObject<{
        channelIds: z.ZodOptional<z.ZodArray<z.ZodString, "many">>;
        message: z.ZodOptional<z.ZodString>;
        messages: z.ZodOptional<z.ZodArray<z.ZodString, "many">>;
    }, "strip", z.ZodTypeAny, {
        message?: string | undefined;
        messages?: string[] | undefined;
        channelIds?: string[] | undefined;
    }, {
        message?: string | undefined;
        messages?: string[] | undefined;
        channelIds?: string[] | undefined;
    }>;
    constructor(authGuard: ToolCallerAuthorizationGuard);
    execute(params: SendMessagesParams, guild: Guild): Promise<string>;
}
