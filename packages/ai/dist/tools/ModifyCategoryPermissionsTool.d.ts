import { type Guild } from 'discord.js';
import { z } from 'zod';
import { ToolCallerAuthorizationGuard } from './ToolCallerAuthorizationGuard.js';
import { PermissionParser } from './PermissionParser.js';
export declare const ModifyCategoryPermissionsParamsSchema: z.ZodObject<{
    categoryId: z.ZodString;
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
    categoryId: string;
    permissions: {
        type: "role" | "member";
        id: string;
        allow?: string | undefined;
        deny?: string | undefined;
        allowSet?: string[] | undefined;
        denySet?: string[] | undefined;
    }[];
}, {
    categoryId: string;
    permissions: {
        type: "role" | "member";
        id: string;
        allow?: string | undefined;
        deny?: string | undefined;
        allowSet?: string[] | undefined;
        denySet?: string[] | undefined;
    }[];
}>;
export type ModifyCategoryPermissionsParams = z.infer<typeof ModifyCategoryPermissionsParamsSchema>;
/**
 * Modifies permission overwrites for a specific category.
 * Tool name: modify_category_permissions
 */
export declare class ModifyCategoryPermissionsTool {
    private readonly authGuard;
    private readonly permissionParser;
    readonly name = "modify_category_permissions";
    readonly description = "\u4FEE\u6539\u6307\u5B9A\u5206\u985E\u7684\u6B0A\u9650\u8A2D\u5B9A";
    readonly schema: z.ZodObject<{
        categoryId: z.ZodString;
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
        categoryId: string;
        permissions: {
            type: "role" | "member";
            id: string;
            allow?: string | undefined;
            deny?: string | undefined;
            allowSet?: string[] | undefined;
            denySet?: string[] | undefined;
        }[];
    }, {
        categoryId: string;
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
    execute(params: ModifyCategoryPermissionsParams, guild: Guild): Promise<string>;
}
