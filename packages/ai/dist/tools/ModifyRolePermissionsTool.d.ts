import { type Guild } from 'discord.js';
import { z } from 'zod';
import { ToolCallerAuthorizationGuard } from './ToolCallerAuthorizationGuard.js';
export declare const ModifyRolePermissionsParamsSchema: z.ZodObject<{
    roleId: z.ZodString;
    permissions: z.ZodArray<z.ZodObject<{
        id: z.ZodString;
        type: z.ZodEnum<["role", "member"]>;
        allow: z.ZodOptional<z.ZodString>;
        deny: z.ZodOptional<z.ZodString>;
    }, "strip", z.ZodTypeAny, {
        type: "role" | "member";
        id: string;
        allow?: string | undefined;
        deny?: string | undefined;
    }, {
        type: "role" | "member";
        id: string;
        allow?: string | undefined;
        deny?: string | undefined;
    }>, "many">;
}, "strip", z.ZodTypeAny, {
    permissions: {
        type: "role" | "member";
        id: string;
        allow?: string | undefined;
        deny?: string | undefined;
    }[];
    roleId: string;
}, {
    permissions: {
        type: "role" | "member";
        id: string;
        allow?: string | undefined;
        deny?: string | undefined;
    }[];
    roleId: string;
}>;
export type ModifyRolePermissionsParams = z.infer<typeof ModifyRolePermissionsParamsSchema>;
/**
 * Modifies permissions for a specific role.
 * Tool name: modify_role_permissions
 */
export declare class ModifyRolePermissionsTool {
    private readonly authGuard;
    readonly name = "modify_role_permissions";
    readonly description = "\u4FEE\u6539\u6307\u5B9A\u8EAB\u5206\u7D44\u7684\u6B0A\u9650\u8A2D\u5B9A";
    readonly schema: z.ZodObject<{
        roleId: z.ZodString;
        permissions: z.ZodArray<z.ZodObject<{
            id: z.ZodString;
            type: z.ZodEnum<["role", "member"]>;
            allow: z.ZodOptional<z.ZodString>;
            deny: z.ZodOptional<z.ZodString>;
        }, "strip", z.ZodTypeAny, {
            type: "role" | "member";
            id: string;
            allow?: string | undefined;
            deny?: string | undefined;
        }, {
            type: "role" | "member";
            id: string;
            allow?: string | undefined;
            deny?: string | undefined;
        }>, "many">;
    }, "strip", z.ZodTypeAny, {
        permissions: {
            type: "role" | "member";
            id: string;
            allow?: string | undefined;
            deny?: string | undefined;
        }[];
        roleId: string;
    }, {
        permissions: {
            type: "role" | "member";
            id: string;
            allow?: string | undefined;
            deny?: string | undefined;
        }[];
        roleId: string;
    }>;
    constructor(authGuard: ToolCallerAuthorizationGuard);
    execute(params: ModifyRolePermissionsParams, guild: Guild): Promise<string>;
}
