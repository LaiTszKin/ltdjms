import { z } from 'zod';
export const SearchMessagesParamsSchema = z.object({
    keywords: z.string().min(1),
    channelIds: z.array(z.string()).optional(),
    maxResultsPerChannel: z.number().int().positive().optional().default(5),
    maxMessagesToScan: z.number().int().positive().optional().default(100),
});
/**
 * Searches for messages containing keywords in the guild.
 * Results are REDACTED for cross-turn memory isolation.
 * Tool name: search_messages
 */
export class SearchMessagesTool {
    authGuard;
    name = 'search_messages';
    description = '搜尋歷史訊息，關鍵字搜尋';
    schema = SearchMessagesParamsSchema;
    constructor(authGuard) {
        this.authGuard = authGuard;
    }
    async execute(params, guild) {
        const authError = await this.authGuard.validateAdministrator(guild, this.name);
        if (authError)
            return authError;
        try {
            const keywords = params.keywords.toLowerCase();
            const maxResults = params.maxResultsPerChannel ?? 5;
            const maxScan = params.maxMessagesToScan ?? 100;
            let channels = guild.channels.cache;
            if (params.channelIds && params.channelIds.length > 0) {
                channels = channels.filter((c) => params.channelIds.includes(c.id));
            }
            const results = [];
            for (const [, channel] of channels) {
                if (!channel.isTextBased())
                    continue;
                if (!channel.isSendable())
                    continue;
                try {
                    const fetched = await channel.messages.fetch({ limit: Math.min(maxScan, 100) });
                    const matching = fetched
                        .filter((msg) => msg.content.toLowerCase().includes(keywords))
                        .first(maxResults);
                    if (matching.length > 0) {
                        results.push({
                            channelId: channel.id,
                            channelName: channel.name,
                            messages: matching.map((msg) => ({
                                id: msg.id,
                                author: msg.author.tag,
                                content: msg.content.slice(0, 200), // Truncate for memory safety
                                timestamp: msg.createdAt.toISOString(),
                            })),
                        });
                    }
                }
                catch {
                    // Skip channels that can't be read
                    continue;
                }
            }
            if (results.length === 0) {
                return `未找到包含「${params.keywords}」的訊息。`;
            }
            // Mark results as REDACTED for memory isolation
            const summary = `找到 ${results.length} 個頻道包含關鍵字「${params.keywords}」的訊息。`;
            return (summary +
                '\n\n' +
                JSON.stringify(results, null, 2) +
                '\n\n(REDACTED: 搜尋結果已標記為跨回合隔離)');
        }
        catch (error) {
            return `搜尋訊息失敗：${error instanceof Error ? error.message : String(error)}`;
        }
    }
}
//# sourceMappingURL=SearchMessagesTool.js.map