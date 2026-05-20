import { ChannelType } from 'discord.js';
import { z } from 'zod';
/**
 * Lists all categories in the guild.
 * Tool name: list_categories
 */
export class ListCategoriesTool {
    authGuard;
    name = 'list_categories';
    description = '列出伺服器中的所有分類';
    schema = z.object({});
    constructor(authGuard) {
        this.authGuard = authGuard;
    }
    async execute(_params, guild) {
        const authError = await this.authGuard.validateAdministrator(guild, this.name);
        if (authError)
            return authError;
        try {
            const categories = guild.channels.cache
                .filter((c) => c.type === ChannelType.GuildCategory)
                .map((c) => ({
                id: c.id,
                name: c.name,
                position: 'position' in c ? c.position : 0,
            }));
            return JSON.stringify(categories, null, 2);
        }
        catch (error) {
            return `列出分類失敗：${error instanceof Error ? error.message : String(error)}`;
        }
    }
}
//# sourceMappingURL=ListCategoriesTool.js.map