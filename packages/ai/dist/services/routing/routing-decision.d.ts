import { type Channel, type Guild } from 'discord.js';
import { Decision } from '../ai-chat-service.js';
import { AIAgentChannelConfigService } from './agent-config-service.js';
import { AIChannelRestrictionService } from './channel-restriction-service.js';
/**
 * Resolves the effective restriction channel ID for Thread channels.
 * Threads inherit their parent channel's allowlist/agent config status.
 */
export declare function resolveRestrictionChannelId(channel: Channel): string;
/**
 * Resolves the category ID for a channel.
 * Threads resolve through their parent channel.
 */
export declare function resolveCategoryId(channel: Channel, guild: Guild): string | null;
/**
 * Three-layer priority routing decision matrix:
 * 1. Agent config enabled → AGENT_ROUTE
 * 2. Channel/category allowlisted → AI_CHAT_ROUTE
 * 3. Otherwise → DENY
 *
 * Thread channels inherit their parent channel's settings.
 */
export declare class AIChatMentionRoutingDecision {
    private readonly agentConfigService;
    private readonly channelRestrictionService;
    constructor(agentConfigService: AIAgentChannelConfigService, channelRestrictionService: AIChannelRestrictionService);
    /**
     * Decides the route for a given channel.
     *
     * @param guildId - The guild ID
     * @param channelId - The original channel ID (may be a thread)
     * @param restrictionChannelId - The resolved channel ID for restrictions (thread → parent)
     * @param categoryId - The category ID (if any)
     */
    decide(guildId: string, channelId: string, restrictionChannelId: string, categoryId: string | null): Promise<Decision>;
}
