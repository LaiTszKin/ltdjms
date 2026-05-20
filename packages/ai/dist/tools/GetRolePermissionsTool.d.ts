import { type Guild } from 'discord.js';
import { z } from 'zod';
import { ToolCallerAuthorizationGuard } from './ToolCallerAuthorizationGuard.js';
export declare const GetRolePermissionsParamsSchema: z.ZodObject<{
    roleId: z.ZodString;
}, "strip", z.ZodTypeAny, {
    roleId: string;
}, {
    roleId: string;
}>;
export type GetRolePermissionsParams = z.infer<typeof GetRolePermissionsParamsSchema>;
/**
 * Gets permissions for a specific role.
 * Tool name: get_role_permissions
 */
export declare class GetRolePermissionsTool {
    private readonly authGuard;
    readonly name = "get_role_permissions";
    readonly description = "\u7372\u53D6\u6307\u5B9A\u8EAB\u5206\u7D44\u7684\u6B0A\u9650\u8A2D\u5B9A";
    readonly schema: z.ZodObject<{
        roleId: z.ZodString;
    }, "strip", z.ZodTypeAny, {
        roleId: string;
    }, {
        roleId: string;
    }>;
    constructor(authGuard: ToolCallerAuthorizationGuard);
    execute(params: GetRolePermissionsParams, guild: Guild): Promise<string>;
}
