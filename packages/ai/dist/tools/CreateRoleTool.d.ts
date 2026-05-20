import { type Guild } from 'discord.js';
import { z } from 'zod';
import { ToolCallerAuthorizationGuard } from './ToolCallerAuthorizationGuard.js';
export declare const CreateRoleParamsSchema: z.ZodObject<{
    name: z.ZodString;
    color: z.ZodOptional<z.ZodString>;
    permissions: z.ZodOptional<z.ZodArray<z.ZodObject<{
        id: z.ZodString;
        type: z.ZodEnum<["role", "member"]>;
        allow: z.ZodOptional<z.ZodString>;
        deny: z.ZodOptional<z.ZodString>;
        allowSet: z.ZodOptional<z.ZodArray<z.ZodString, "many">>;
        denySet: z.ZodOptional<z.ZodArray<z.ZodString, "many">>;
    }, "strip", z.ZodTypeAny, {
        type: "member" | "role";
        id: string;
        allow?: string | undefined;
        deny?: string | undefined;
        allowSet?: string[] | undefined;
        denySet?: string[] | undefined;
    }, {
        type: "member" | "role";
        id: string;
        allow?: string | undefined;
        deny?: string | undefined;
        allowSet?: string[] | undefined;
        denySet?: string[] | undefined;
    }>, "many">>;
}, "strip", z.ZodTypeAny, {
    name: string;
    permissions?: {
        type: "member" | "role";
        id: string;
        allow?: string | undefined;
        deny?: string | undefined;
        allowSet?: string[] | undefined;
        denySet?: string[] | undefined;
    }[] | undefined;
    color?: string | undefined;
}, {
    name: string;
    permissions?: {
        type: "member" | "role";
        id: string;
        allow?: string | undefined;
        deny?: string | undefined;
        allowSet?: string[] | undefined;
        denySet?: string[] | undefined;
    }[] | undefined;
    color?: string | undefined;
}>;
export type CreateRoleParams = z.infer<typeof CreateRoleParamsSchema>;
/**
 * Creates a role in the guild.
 * Tool name: create_role
 */
export declare class CreateRoleTool {
    private readonly authGuard;
    readonly name = "create_role";
    readonly description = "\u5728\u4F3A\u670D\u5668\u4E2D\u5275\u5EFA\u4E00\u500B\u65B0\u7684\u8EAB\u5206\u7D44";
    readonly schema: z.ZodObject<{
        name: z.ZodString;
        color: z.ZodOptional<z.ZodString>;
        permissions: z.ZodOptional<z.ZodArray<z.ZodObject<{
            id: z.ZodString;
            type: z.ZodEnum<["role", "member"]>;
            allow: z.ZodOptional<z.ZodString>;
            deny: z.ZodOptional<z.ZodString>;
            allowSet: z.ZodOptional<z.ZodArray<z.ZodString, "many">>;
            denySet: z.ZodOptional<z.ZodArray<z.ZodString, "many">>;
        }, "strip", z.ZodTypeAny, {
            type: "member" | "role";
            id: string;
            allow?: string | undefined;
            deny?: string | undefined;
            allowSet?: string[] | undefined;
            denySet?: string[] | undefined;
        }, {
            type: "member" | "role";
            id: string;
            allow?: string | undefined;
            deny?: string | undefined;
            allowSet?: string[] | undefined;
            denySet?: string[] | undefined;
        }>, "many">>;
    }, "strip", z.ZodTypeAny, {
        name: string;
        permissions?: {
            type: "member" | "role";
            id: string;
            allow?: string | undefined;
            deny?: string | undefined;
            allowSet?: string[] | undefined;
            denySet?: string[] | undefined;
        }[] | undefined;
        color?: string | undefined;
    }, {
        name: string;
        permissions?: {
            type: "member" | "role";
            id: string;
            allow?: string | undefined;
            deny?: string | undefined;
            allowSet?: string[] | undefined;
            denySet?: string[] | undefined;
        }[] | undefined;
        color?: string | undefined;
    }>;
    constructor(authGuard: ToolCallerAuthorizationGuard);
    execute(params: CreateRoleParams, guild: Guild): Promise<string>;
}
