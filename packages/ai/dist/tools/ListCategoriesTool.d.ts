import { type Guild } from 'discord.js';
import { z } from 'zod';
import { ToolCallerAuthorizationGuard } from './ToolCallerAuthorizationGuard.js';
/**
 * Lists all categories in the guild.
 * Tool name: list_categories
 */
export declare class ListCategoriesTool {
    private readonly authGuard;
    readonly name = "list_categories";
    readonly description = "\u5217\u51FA\u4F3A\u670D\u5668\u4E2D\u7684\u6240\u6709\u5206\u985E";
    readonly schema: z.ZodObject<{}, "strip", z.ZodTypeAny, {}, {}>;
    constructor(authGuard: ToolCallerAuthorizationGuard);
    execute(_params: unknown, guild: Guild): Promise<string>;
}
