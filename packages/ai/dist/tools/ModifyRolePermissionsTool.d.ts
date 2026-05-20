import { type Guild } from 'discord.js';
import { z } from 'zod';
import { ToolCallerAuthorizationGuard } from './ToolCallerAuthorizationGuard.js';
export declare const ModifyRolePermissionsParamsSchema: z.ZodObject<{
    roleId: z.ZodString;
    permissions: z.ZodArray<z.ZodObject<{
        allow: z.ZodOptional<z.ZodString>;
        deny: z.ZodOptional<z.ZodString>;
        allowSet: z.ZodOptional<z.ZodArray<z.ZodString, "many">>;
        denySet: z.ZodOptional<z.ZodArray<z.ZodString, "many">>;
    }, "strip", z.ZodTypeAny, {
        allow?: string | undefined;
        deny?: string | undefined;
        allowSet?: string[] | undefined;
        denySet?: string[] | undefined;
    }, {
        allow?: string | undefined;
        deny?: string | undefined;
        allowSet?: string[] | undefined;
        denySet?: string[] | undefined;
    }>, "many">;
}, "strip", z.ZodTypeAny, {
    permissions: {
        allow?: string | undefined;
        deny?: string | undefined;
        allowSet?: string[] | undefined;
        denySet?: string[] | undefined;
    }[];
    roleId: string;
}, {
    permissions: {
        allow?: string | undefined;
        deny?: string | undefined;
        allowSet?: string[] | undefined;
        denySet?: string[] | undefined;
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
            allow: z.ZodOptional<z.ZodString>;
            deny: z.ZodOptional<z.ZodString>;
            allowSet: z.ZodOptional<z.ZodArray<z.ZodString, "many">>;
            denySet: z.ZodOptional<z.ZodArray<z.ZodString, "many">>;
        }, "strip", z.ZodTypeAny, {
            allow?: string | undefined;
            deny?: string | undefined;
            allowSet?: string[] | undefined;
            denySet?: string[] | undefined;
        }, {
            allow?: string | undefined;
            deny?: string | undefined;
            allowSet?: string[] | undefined;
            denySet?: string[] | undefined;
        }>, "many">;
    }, "strip", z.ZodTypeAny, {
        permissions: {
            allow?: string | undefined;
            deny?: string | undefined;
            allowSet?: string[] | undefined;
            denySet?: string[] | undefined;
        }[];
        roleId: string;
    }, {
        permissions: {
            allow?: string | undefined;
            deny?: string | undefined;
            allowSet?: string[] | undefined;
            denySet?: string[] | undefined;
        }[];
        roleId: string;
    }>;
    constructor(authGuard: ToolCallerAuthorizationGuard);
    execute(params: ModifyRolePermissionsParams, guild: Guild): Promise<string>;
}
