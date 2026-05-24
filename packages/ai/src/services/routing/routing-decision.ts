import { ChannelType, type Channel } from 'discord.js';
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
export function resolveCategoryId(channel: Channel): string | null {
  let targetChannel = channel;

  if (channel.isThread()) {
    const parent = channel.parent;
    if (parent) {
      targetChannel = parent;
    } else {
      return null;
    }
  }

  if (targetChannel.type === ChannelType.GuildCategory) {
    return targetChannel.id;
  }

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
 * Agent config uses the message channel ID; allowlist uses restrictionChannelId
 * (thread parent). Agent config unavailable → fail closed (DENY).
 */
export class AIChatMentionRoutingDecision {
  constructor(
    private readonly agentConfigService: AIAgentChannelConfigService,
    private readonly channelRestrictionService: AIChannelRestrictionService,
  ) {}

  /**
   * Decides the route for a mention in a channel.
   *
   * @param guildId - The guild ID
   * @param channelId - The message channel ID (for agent config lookup)
   * @param restrictionChannelId - Resolved channel ID for allowlist (thread → parent)
   * @param categoryId - The category ID (if any)
   */
  async decide(
    guildId: string,
    channelId: string,
    restrictionChannelId: string,
    categoryId: string | null,
  ): Promise<Decision> {
    let agentEnabled: boolean | null = null;
    try {
      agentEnabled = await this.agentConfigService.isAgentEnabledAsync(guildId, channelId);
    } catch {
      agentEnabled = null;
    }

    if (agentEnabled === null) {
      return {
        route: Route.DENY,
        source: Source.AGENT_CONFIG_UNAVAILABLE,
        detail: 'agent config service unavailable',
      };
    }

    if (agentEnabled) {
      return {
        route: Route.AGENT_ROUTE,
        source: Source.AGENT_ENABLED,
      };
    }

    const allowed = await this.channelRestrictionService.isChannelAllowed(
      guildId,
      restrictionChannelId,
      categoryId ?? undefined,
    );

    if (allowed) {
      return {
        route: Route.AI_CHAT_ROUTE,
        source: Source.AI_ALLOWLIST,
      };
    }

    return {
      route: Route.DENY,
      source: Source.AI_ALLOWLIST_DENIED,
      detail: 'allowlist denied',
    };
  }
}
