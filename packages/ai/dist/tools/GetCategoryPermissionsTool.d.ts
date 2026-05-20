import { type Guild } from 'discord.js';
import { z } from 'zod';
import { ToolCallerAuthorizationGuard } from './ToolCallerAuthorizationGuard.js';
export declare const GetCategoryPermissionsParamsSchema: z.ZodObject<{
    categoryId: z.ZodString;
}, "strip", z.ZodTypeAny, {
    categoryId: string;
}, {
    categoryId: string;
}>;
export type GetCategoryPermissionsParams = z.infer<typeof GetCategoryPermissionsParamsSchema>;
/**
 * Gets permission overwrites for a specific category.
 * Tool name: get_category_permissions
 */
export declare class GetCategoryPermissionsTool {
    private readonly authGuard;
    readonly name = "get_category_permissions";
    readonly description = "\u7372\u53D6\u6307\u5B9A\u5206\u985E\u7684\u6B0A\u9650\u8A2D\u5B9A";
    readonly schema: z.ZodObject<{
        categoryId: z.ZodString;
    }, "strip", z.ZodTypeAny, {
        categoryId: string;
    }, {
        categoryId: string;
    }>;
    constructor(authGuard: ToolCallerAuthorizationGuard);
    execute(params: GetCategoryPermissionsParams, guild: Guild): Promise<string>;
}
