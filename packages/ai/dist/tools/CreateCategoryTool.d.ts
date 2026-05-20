import { type Guild } from 'discord.js';
import { z } from 'zod';
import { ToolCallerAuthorizationGuard } from './ToolCallerAuthorizationGuard.js';
import { PermissionParser } from './PermissionParser.js';
export declare const CreateCategoryParamsSchema: z.ZodObject<{
    name: z.ZodString;
    permissions: z.ZodOptional<z.ZodArray<z.ZodObject<{
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
    }>, "many">>;
}, "strip", z.ZodTypeAny, {
    name: string;
    permissions?: {
        type: "role" | "member";
        id: string;
        allow?: string | undefined;
        deny?: string | undefined;
        allowSet?: string[] | undefined;
        denySet?: string[] | undefined;
    }[] | undefined;
}, {
    name: string;
    permissions?: {
        type: "role" | "member";
        id: string;
        allow?: string | undefined;
        deny?: string | undefined;
        allowSet?: string[] | undefined;
        denySet?: string[] | undefined;
    }[] | undefined;
}>;
export type CreateCategoryParams = z.infer<typeof CreateCategoryParamsSchema>;
/**
 * Creates a category in the guild.
 * Tool name: create_category
 */
export declare class CreateCategoryTool {
    private readonly authGuard;
    private readonly permissionParser;
    readonly name = "create_category";
    readonly description = "\u5728\u4F3A\u670D\u5668\u4E2D\u5275\u5EFA\u4E00\u500B\u65B0\u7684\u5206\u985E";
    readonly schema: z.ZodObject<{
        name: z.ZodString;
        permissions: z.ZodOptional<z.ZodArray<z.ZodObject<{
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
        }>, "many">>;
    }, "strip", z.ZodTypeAny, {
        name: string;
        permissions?: {
            type: "role" | "member";
            id: string;
            allow?: string | undefined;
            deny?: string | undefined;
            allowSet?: string[] | undefined;
            denySet?: string[] | undefined;
        }[] | undefined;
    }, {
        name: string;
        permissions?: {
            type: "role" | "member";
            id: string;
            allow?: string | undefined;
            deny?: string | undefined;
            allowSet?: string[] | undefined;
            denySet?: string[] | undefined;
        }[] | undefined;
    }>;
    constructor(authGuard: ToolCallerAuthorizationGuard, permissionParser: PermissionParser);
    execute(params: CreateCategoryParams, guild: Guild): Promise<string>;
}
