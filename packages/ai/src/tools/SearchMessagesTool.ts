import { type Guild, type TextChannel } from 'discord.js';
import { z } from 'zod';
import { ToolCallerAuthorizationGuard } from './ToolCallerAuthorizationGuard.js';
import { ToolExecutionContext } from './ToolExecutionContext.js';
import { parseSnowflakeId } from './permission-modify-helper.js';
import { TOOL_DESCRIPTIONS } from './tool-descriptions.js';

const MAX_CHANNELS = 10;
const DEFAULT_RESULTS_PER_CHANNEL = 20;
const MAX_RESULTS_PER_CHANNEL = 50;
const DEFAULT_SCAN_PER_CHANNEL = 200;
const MAX_SCAN_PER_CHANNEL = 1000;
const FETCH_BATCH_SIZE = 100;
const CONTENT_SNIPPET_LENGTH = 180;

export const SearchMessagesParamsSchema = z.object({
  keywords: z.string().min(1),
  channelIds: z.array(z.string()).optional(),
  maxResultsPerChannel: z.number().int().positive().optional(),
  maxMessagesToScan: z.number().int().positive().optional(),
});

export type SearchMessagesParams = z.infer<typeof SearchMessagesParamsSchema>;

function clamp(value: number | undefined, defaultValue: number, min: number, max: number): number {
  if (value === undefined) {
    return defaultValue;
  }
  return Math.min(max, Math.max(min, value));
}

function splitKeywords(keywords: string): string[] {
  return keywords
    .toLowerCase()
    .trim()
    .split(/\s+/)
    .filter((part) => part.length > 0);
}

function normalizeChannelIds(
  channelIds: string[] | undefined,
  currentChannelId: string | null,
): string[] {
  const deduplicated: string[] = [];
  const seen = new Set<string>();

  if (channelIds) {
    for (const channelId of channelIds) {
      const trimmed = channelId?.trim();
      if (trimmed && !seen.has(trimmed)) {
        seen.add(trimmed);
        deduplicated.push(trimmed);
      }
    }
  }

  if (deduplicated.length === 0 && currentChannelId) {
    deduplicated.push(currentChannelId);
  }

  return deduplicated;
}

function containsAllKeywords(content: string, keywords: string[]): boolean {
  const normalized = content.toLowerCase();
  return keywords.every((keyword) => normalized.includes(keyword));
}

function buildSnippet(content: string): string {
  const normalized = content.replace(/\n/g, ' ').replace(/\r/g, ' ').trim();
  if (normalized.length <= CONTENT_SNIPPET_LENGTH) {
    return normalized;
  }
  return `${normalized.slice(0, CONTENT_SNIPPET_LENGTH)}...`;
}

async function fetchMessages(channel: TextChannel, scanLimit: number) {
  const messages = [];
  let remaining = scanLimit;
  let before: string | undefined;

  while (remaining > 0) {
    const batchSize = Math.min(FETCH_BATCH_SIZE, remaining);
    const batch = await channel.messages.fetch({ limit: batchSize, before });
    if (batch.size === 0) {
      break;
    }

    const batchArray = [...batch.values()];
    messages.push(...batchArray);
    remaining -= batchArray.length;

    if (batchArray.length < batchSize) {
      break;
    }

    before = batchArray[batchArray.length - 1]?.id;
  }

  return messages;
}

/**
 * Searches for messages containing keywords in the guild.
 * Results are REDACTED for cross-turn memory isolation.
 * Tool name: search_messages
 */
export class SearchMessagesTool {
  readonly name = 'search_messages';
  readonly description = TOOL_DESCRIPTIONS.search_messages;
  readonly schema = SearchMessagesParamsSchema;

  constructor(private readonly authGuard: ToolCallerAuthorizationGuard) {}

  async execute(params: SearchMessagesParams, guild: Guild): Promise<string> {
    const authError = await this.authGuard.validateAdministrator(guild, this.name);
    if (authError) return authError;

    try {
      const keywordTokens = splitKeywords(params.keywords);
      if (keywordTokens.length === 0) {
        return 'keywords 不能為空';
      }

      const normalizedChannelIds = normalizeChannelIds(
        params.channelIds,
        ToolExecutionContext.getChannelId(),
      );
      if (normalizedChannelIds.length === 0) {
        return '未提供有效的 channelIds，且當前頻道不可用';
      }
      if (normalizedChannelIds.length > MAX_CHANNELS) {
        return `一次最多可搜尋 ${MAX_CHANNELS} 個頻道`;
      }

      const resultsLimit = clamp(
        params.maxResultsPerChannel,
        DEFAULT_RESULTS_PER_CHANNEL,
        1,
        MAX_RESULTS_PER_CHANNEL,
      );
      const scanLimit = clamp(
        params.maxMessagesToScan,
        DEFAULT_SCAN_PER_CHANNEL,
        1,
        MAX_SCAN_PER_CHANNEL,
      );

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

      for (const rawChannelId of normalizedChannelIds) {
        const targetChannelId = parseSnowflakeId(rawChannelId);
        if (!targetChannelId) {
          continue;
        }

        const channel = guild.channels.cache.get(targetChannelId);
        if (!channel || !channel.isTextBased() || !channel.isSendable()) {
          continue;
        }

        try {
          const recentMessages = await fetchMessages(channel as TextChannel, scanLimit);
          const matching = [];
          for (const msg of recentMessages) {
            if (matching.length >= resultsLimit) {
              break;
            }
            if (!msg.content || !containsAllKeywords(msg.content, keywordTokens)) {
              continue;
            }
            matching.push({
              id: msg.id,
              author: msg.author.tag,
              content: buildSnippet(msg.content),
              timestamp: msg.createdAt.toISOString(),
            });
          }

          if (matching.length > 0) {
            results.push({
              channelId: channel.id,
              channelName: channel.name,
              messages: matching,
            });
          }
        } catch {
          // Skip channels that can't be read
        }
      }

      if (results.length === 0) {
        return `未找到包含「${params.keywords}」的訊息。`;
      }

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
