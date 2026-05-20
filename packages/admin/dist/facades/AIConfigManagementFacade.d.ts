import { type Result, DomainError } from '@ltdjms/shared';
import type { AIChannelRestrictionService, AIAgentChannelConfigService, AllowedChannel, AllowedCategory } from '@ltdjms/ai';
/**
 * Facade for AI channel and agent configuration management.
 * Wraps AIChannelRestrictionService and AIAgentChannelConfigService.
 * Matches Java AIConfigManagementFacade.
 */
export declare class AIConfigManagementFacade {
    private readonly channelRestrictionService;
    private readonly agentConfigService;
    constructor(channelRestrictionService: AIChannelRestrictionService, agentConfigService: AIAgentChannelConfigService);
    /**
     * Lists all allowed AI channels for a guild.
     */
    listAllowedChannels(guildId: string): Promise<Result<AllowedChannel[], DomainError>>;
    /**
     * Adds a channel to the AI allowlist.
     */
    addAllowedChannel(guildId: string, channelId: string, channelName: string): Promise<Result<AllowedChannel, DomainError>>;
    /**
     * Removes a channel from the AI allowlist.
     */
    removeAllowedChannel(guildId: string, channelId: string): Promise<Result<void, DomainError>>;
    /**
     * Lists all allowed AI categories for a guild.
     */
    listAllowedCategories(guildId: string): Promise<Result<AllowedCategory[], DomainError>>;
    /**
     * Adds a category to the AI allowlist.
     */
    addAllowedCategory(guildId: string, categoryId: string, categoryName: string): Promise<Result<AllowedCategory, DomainError>>;
    /**
     * Removes a category from the AI allowlist.
     */
    removeAllowedCategory(guildId: string, categoryId: string): Promise<Result<void, DomainError>>;
    /**
     * Lists all channels with agent configuration for a guild.
     */
    listAgentChannels(guildId: string): Promise<Result<string[], DomainError>>;
    /**
     * Enables or disables agent mode for a channel.
     */
    setAgentEnabled(guildId: string, channelId: string, enabled: boolean): Promise<Result<void, DomainError>>;
    /**
     * Removes the agent configuration for a channel.
     */
    removeAgentConfig(guildId: string, channelId: string): Promise<Result<void, DomainError>>;
}
