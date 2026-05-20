import { type Guild } from 'discord.js';
import { z } from 'zod';
import { ToolCallerAuthorizationGuard } from './ToolCallerAuthorizationGuard.js';
import { PermissionParser } from './PermissionParser.js';
export declare const ModifyChannelPermissionsParamsSchema: z.ZodObject<{
    channelId: z.ZodString;
    permissions: z.ZodArray<z.ZodObject<{
        id: z.ZodString;
        type: z.ZodEnum<["role", "member"]>;
        allow: z.ZodOptional<z.ZodString>;
        deny: z.ZodOptional<z.ZodString>;
        allowSet: z.ZodOptional<z.ZodArray<z.ZodString, "many">>;
        denySet: z.ZodOptional<z.ZodArray<z.ZodString, "many">>;
    }, "strip", z.ZodTypeAny, {
        type: "role" | "member";
        id: string;
        allow?: string | undefined;
        deny?: string | undefined;
        allowSet?: string[] | undefined;
        denySet?: string[] | undefined;
    }, {
        type: "role" | "member";
        id: string;
        allow?: string | undefined;
        deny?: string | undefined;
        allowSet?: string[] | undefined;
        denySet?: string[] | undefined;
    }>, "many">;
}, "strip", z.ZodTypeAny, {
    channelId: string;
    permissions: {
        type: "role" | "member";
        id: string;
        allow?: string | undefined;
        deny?: string | undefined;
        allowSet?: string[] | undefined;
        denySet?: string[] | undefined;
    }[];
}, {
    channelId: string;
    permissions: {
        type: "role" | "member";
        id: string;
        allow?: string | undefined;
        deny?: string | undefined;
        allowSet?: string[] | undefined;
        denySet?: string[] | undefined;
    }[];
}>;
export type ModifyChannelPermissionsParams = z.infer<typeof ModifyChannelPermissionsParamsSchema>;
/**
 * Modifies permission overwrites for a specific channel.
 * Tool name: modify_channel_permissions
 */
export declare class ModifyChannelPermissionsTool {
    private readonly authGuard;
    private readonly permissionParser;
    readonly name = "modify_channel_permissions";
    readonly description = "\u4FEE\u6539\u6307\u5B9A\u983B\u9053\u7684\u6B0A\u9650\u8A2D\u5B9A";
    readonly schema: z.ZodObject<{
        channelId: z.ZodString;
        permissions: z.ZodArray<z.ZodObject<{
            id: z.ZodString;
            type: z.ZodEnum<["role", "member"]>;
            allow: z.ZodOptional<z.ZodString>;
            deny: z.ZodOptional<z.ZodString>;
            allowSet: z.ZodOptional<z.ZodArray<z.ZodString, "many">>;
            denySet: z.ZodOptional<z.ZodArray<z.ZodString, "many">>;
        }, "strip", z.ZodTypeAny, {
            type: "role" | "member";
            id: string;
            allow?: string | undefined;
            deny?: string | undefined;
            allowSet?: string[] | undefined;
            denySet?: string[] | undefined;
        }, {
            type: "role" | "member";
            id: string;
            allow?: string | undefined;
            deny?: string | undefined;
            allowSet?: string[] | undefined;
            denySet?: string[] | undefined;
        }>, "many">;
    }, "strip", z.ZodTypeAny, {
        channelId: string;
        permissions: {
            type: "role" | "member";
            id: string;
            allow?: string | undefined;
            deny?: string | undefined;
            allowSet?: string[] | undefined;
            denySet?: string[] | undefined;
        }[];
    }, {
        channelId: string;
        permissions: {
            type: "role" | "member";
            id: string;
            allow?: string | undefined;
            deny?: string | undefined;
            allowSet?: string[] | undefined;
            denySet?: string[] | undefined;
        }[];
    }>;
    constructor(authGuard: ToolCallerAuthorizationGuard, permissionParser: PermissionParser);
    execute(params: ModifyChannelPermissionsParams, guild: Guild): Promise<string>;
}
