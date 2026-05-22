import { type Guild } from 'discord.js';
import { z } from 'zod';
import { ToolCallerAuthorizationGuard } from './ToolCallerAuthorizationGuard.js';

export const SearchMessagesParamsSchema = z.object({
  keywords: z.string().min(1),
  channelIds: z.array(z.string()).optional(),
  maxResultsPerChannel: z.number().int().positive().optional().default(5),
  maxMessagesToScan: z.number().int().positive().optional().default(100),
});

export type SearchMessagesParams = z.infer<typeof SearchMessagesParamsSchema>;

/**
 * Searches for messages containing keywords in the guild.
 * Results are REDACTED for cross-turn memory isolation.
 * Tool name: search_messages
 */
export class SearchMessagesTool {
  readonly name = 'search_messages';
  readonly description = '搜尋歷史訊息，關鍵字搜尋';
  readonly schema = SearchMessagesParamsSchema;

  constructor(private readonly authGuard: ToolCallerAuthorizationGuard) {}

  async execute(params: SearchMessagesParams, guild: Guild): Promise<string> {
    const authError = await this.authGuard.validateAdministrator(guild, this.name);
    if (authError) return authError;

    try {
      const keywords = params.keywords.toLowerCase();
      const maxResults = params.maxResultsPerChannel ?? 5;
      const maxScan = params.maxMessagesToScan ?? 100;
      // P1-14: Limit channels to search to prevent excessive API calls
      const maxChannelsToSearch = 10;
      const hasChannelFilter = params.channelIds && params.channelIds.length > 0;

      // Collect eligible text-based channels
      let channels = guild.channels.cache;
      if (hasChannelFilter) {
        channels = channels.filter((c) => params.channelIds!.includes(c.id));
      }
      const eligibleChannels = Array.from(channels.values())
        .filter((c) => c.isTextBased() && c.isSendable())
        .slice(0, hasChannelFilter ? undefined : maxChannelsToSearch);

      const results: Array<{
        channelId: string;
        channelName: string;
        messages: Array<{
          id: string;
          author: string;
          content: string;
          timestamp: string;
        }>;
      }> = [];

      // P3-12: Parallel fetch with concurrency limit
      const CONCURRENCY = 5;
      const worker = async (channelList: typeof eligibleChannels): Promise<void> => {
        while (channelList.length > 0) {
          const channel = channelList.pop()!;
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
          } catch {
            // Skip channels that can't be read
          }
        }
      };

      const effectiveConcurrency = Math.min(CONCURRENCY, eligibleChannels.length);
      const workers = Array.from({ length: effectiveConcurrency }, () => worker(eligibleChannels));
      await Promise.all(workers);

      if (results.length === 0) {
        return `未找到包含「${params.keywords}」的訊息。`;
      }

      // Mark results as REDACTED for memory isolation
      const summary = `找到 ${results.length} 個頻道包含關鍵字「${params.keywords}」的訊息。`;
      return (
        summary +
        '\n\n' +
        JSON.stringify(results, null, 2) +
        '\n\n(REDACTED: 搜尋結果已標記為跨回合隔離)'
      );
    } catch (error) {
      return `搜尋訊息失敗：${error instanceof Error ? error.message : String(error)}`;
    }
  }
}
