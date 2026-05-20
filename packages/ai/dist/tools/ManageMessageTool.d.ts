import { type Guild } from 'discord.js';
import { z } from 'zod';
import { ToolCallerAuthorizationGuard } from './ToolCallerAuthorizationGuard.js';
export declare const ManageMessageParamsSchema: z.ZodObject<{
    messageId: z.ZodString;
    action: z.ZodEnum<["pin", "delete", "edit"]>;
    channelId: z.ZodOptional<z.ZodString>;
    newContent: z.ZodOptional<z.ZodString>;
    editMode: z.ZodOptional<z.ZodEnum<["replace", "append", "prepend"]>>;
}, "strip", z.ZodTypeAny, {
    messageId: string;
    action: "delete" | "pin" | "edit";
    channelId?: string | undefined;
    newContent?: string | undefined;
    editMode?: "replace" | "append" | "prepend" | undefined;
}, {
    messageId: string;
    action: "delete" | "pin" | "edit";
    channelId?: string | undefined;
    newContent?: string | undefined;
    editMode?: "replace" | "append" | "prepend" | undefined;
}>;
export type ManageMessageParams = z.infer<typeof ManageMessageParamsSchema>;
/**
 * Manages messages: pin, delete, or edit.
 * Tool name: manage_message
 */
export declare class ManageMessageTool {
    private readonly authGuard;
    readonly name = "manage_message";
    readonly description = "\u7BA1\u7406\u8A0A\u606F\uFF08\u91D8\u9078/\u522A\u9664/\u7DE8\u8F2F\uFF09";
    readonly schema: z.ZodObject<{
        messageId: z.ZodString;
        action: z.ZodEnum<["pin", "delete", "edit"]>;
        channelId: z.ZodOptional<z.ZodString>;
        newContent: z.ZodOptional<z.ZodString>;
        editMode: z.ZodOptional<z.ZodEnum<["replace", "append", "prepend"]>>;
    }, "strip", z.ZodTypeAny, {
        messageId: string;
        action: "delete" | "pin" | "edit";
        channelId?: string | undefined;
        newContent?: string | undefined;
        editMode?: "replace" | "append" | "prepend" | undefined;
    }, {
        messageId: string;
        action: "delete" | "pin" | "edit";
        channelId?: string | undefined;
        newContent?: string | undefined;
        editMode?: "replace" | "append" | "prepend" | undefined;
    }>;
    constructor(authGuard: ToolCallerAuthorizationGuard);
    execute(params: ManageMessageParams, guild: Guild): Promise<string>;
}
