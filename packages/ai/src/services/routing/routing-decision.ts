import { ChannelType, type Channel, type Guild } from 'discord.js';
import { Decision, Route, Source } from '../ai-chat-service.js';
import { AIAgentChannelConfigService } from './agent-config-service.js';
import { AIChannelRestrictionService } from './channel-restriction-service.js';

/**
 * Resolves the effective restriction channel ID for Thread channels.
 * Threads inherit their parent channel's allowlist/agent config status.
 */
export function resolveRestrictionChannelId(channel: Channel): string {
  if (channel.isThread()) {
    return channel.parentId ?? channel.id;
  }
  return channel.id;
}

/**
 * Resolves the category ID for a channel.
 * Threads resolve through their parent channel.
 */
export function resolveCategoryId(
  channel: Channel,
  guild: Guild,
): string | null {
  let targetChannel = channel;

  // If thread, use parent channel
  if (channel.isThread()) {
    const parent = channel.parent;
    if (parent) {
      targetChannel = parent;
    } else {
      return null;
    }
  }

  // If the channel itself is a category, return its ID
  if (targetChannel.type === ChannelType.GuildCategory) {
    return targetChannel.id;
  }

  // Otherwise get the parent category
  if ('parentId' in targetChannel && targetChannel.parentId) {
    return targetChannel.parentId;
  }

  return null;
}

/**
 * Three-layer priority routing decision matrix:
 * 1. Agent config enabled → AGENT_ROUTE
 * 2. Channel/category allowlisted → AI_CHAT_ROUTE
 * 3. Otherwise → DENY
 *
 * Thread channels inherit their parent channel's settings.
 */
export class AIChatMentionRoutingDecision {
  constructor(
    private readonly agentConfigService: AIAgentChannelConfigService,
    private readonly channelRestrictionService: AIChannelRestrictionService,
  ) {}

  /**
   * Decides the route for a given channel.
   *
   * @param guildId - The guild ID
   * @param channelId - The original channel ID (may be a thread)
   * @param restrictionChannelId - The resolved channel ID for restrictions (thread → parent)
   * @param categoryId - The category ID (if any)
   */
  async decide(
    guildId: string,
    channelId: string,
    restrictionChannelId: string,
    categoryId: string | null,
  ): Promise<Decision> {
    // Track whether agent config was unavailable
    let agentConfigUnavailable = false;

    // Priority 1: Check Agent config (uses restrictionChannelId for thread inheritance)
    try {
      const agentEnabled = this.agentConfigService.isAgentEnabled
        ? this.agentConfigService.isAgentEnabled(guildId, restrictionChannelId)
        : await this.agentConfigService.isAgentEnabledAsync(guildId, restrictionChannelId);

      // Handle both sync and async return values
      const enabled = typeof agentEnabled === 'boolean'
        ? agentEnabled
        : await agentEnabled;

      if (enabled) {
        return {
          route: Route.AGENT_ROUTE,
          source: Source.AGENT_CONFIG,
          detail: `Agent enabled for channel ${restrictionChannelId}`,
        };
      }
    } catch {
      // Agent config unavailable (Redis/DB failure) — don't treat as AGENT_ROUTE
      agentConfigUnavailable = true;
    }

    // Priority 2: Check channel/category allowlist
    const channelAllowed = await this.channelRestrictionService.isChannelAllowed(
      guildId,
      restrictionChannelId,
      categoryId ?? undefined,
    );

    if (channelAllowed) {
      return {
        route: Route.AI_CHAT_ROUTE,
        source: categoryId
          ? Source.CATEGORY_ALLOWLIST
          : Source.CHANNEL_ALLOWLIST,
        detail: categoryId
          ? `Category ${categoryId} is allowlisted`
          : `Channel ${restrictionChannelId} is allowlisted`,
      };
    }

    // Priority 3: Deny
    if (agentConfigUnavailable) {
      return {
        route: Route.DENY,
        source: Source.AGENT_CONFIG_UNAVAILABLE,
        detail: `Agent config unavailable and channel ${restrictionChannelId} is not allowlisted`,
      };
    }
    return {
      route: Route.DENY,
      source: Source.NO_ALLOWLIST,
      detail: `Channel ${restrictionChannelId} is not allowlisted and agent is not enabled`,
    };
  }
}
