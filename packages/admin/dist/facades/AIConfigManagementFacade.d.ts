import { type Result, DomainError } from '@ltdjms/shared';
import type { AIChannelRestrictionService, AIAgentChannelConfigService, AllowedChannel, AllowedCategory } from '@ltdjms/ai';
/**
 * Facade for AI channel and agent configuration management.
 * Wraps AIChannelRestrictionService and AIAgentChannelConfigService.
 * Matches Java AIConfigManagementFacade.
 *
 * DEPRECATED METHODS: The following methods are deprecated and retained only
 * for backward compatibility. New code should use their replacements:
 *   - listAllowedChannels  → getAllowedChannels
 *   - listAllowedCategories → getAllowedCategories
 *   - listAgentChannels    → getAgentConfigs
 */
export declare class AIConfigManagementFacade {
    private readonly channelRestrictionService;
    private readonly agentConfigService;
    constructor(channelRestrictionService: AIChannelRestrictionService, agentConfigService: AIAgentChannelConfigService);
    /**
     * Gets all allowed AI channels for a guild.
     */
    getAllowedChannels(guildId: string): Promise<Result<AllowedChannel[], DomainError>>;
    /**
     * Lists all allowed AI channels for a guild.
     * @deprecated Use {@link getAllowedChannels} instead.
     */
    listAllowedChannels(guildId: string): Promise<Result<AllowedChannel[], DomainError>>;
    /**
     * Adds a channel to the AI allowlist.
     *
     * @param channelName - Display name of the channel. Passed through to
     *   AIChannelRestrictionService.addAllowedChannel for logging/reference.
     *   This parameter is accepted for API completeness (R13.4) but may not
     *   be persisted by all implementations.
     */
    addAllowedChannel(guildId: string, channelId: string, channelName: string): Promise<Result<AllowedChannel, DomainError>>;
    /**
     * Removes a channel from the AI allowlist.
     */
    removeAllowedChannel(guildId: string, channelId: string): Promise<Result<void, DomainError>>;
    /**
     * Gets all allowed AI categories for a guild.
     */
    getAllowedCategories(guildId: string): Promise<Result<AllowedCategory[], DomainError>>;
    /**
     * Lists all allowed AI categories for a guild.
     * @deprecated Use {@link getAllowedCategories} instead.
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
     * Gets all channels with agent configuration for a guild.
     */
    getAgentConfigs(guildId: string): Promise<Result<string[], DomainError>>;
    /**
     * Lists all channels with agent configuration for a guild.
     * @deprecated Use {@link getAgentConfigs} instead.
     */
    listAgentChannels(guildId: string): Promise<Result<string[], DomainError>>;
    /**
     * Enables agent mode for a channel with the specified mode.
     *
     * NOTE: The `_mode` parameter is accepted for API consistency but is currently
     * ignored because AIAgentChannelConfigService.setAgentEnabled(guildId, channelId, enabled)
     * only supports a boolean on/off toggle. Once the service supports mode selection
     * (e.g., 'chat', 'agent', 'hybrid'), pass `_mode` through to the service.
     *
     * TODO(P1-36): Pass `mode` through to the service layer when setAgentEnabled
     * signature is extended to accept a mode parameter.
     */
    enableAgent(guildId: string, channelId: string, _mode: string): Promise<Result<void, DomainError>>;
    /**
     * Disables agent mode for a channel.
     */
    disableAgent(guildId: string, channelId: string): Promise<Result<void, DomainError>>;
    /**
     * Enables or disables agent mode for a channel.
     * Convenience method that delegates to enableAgent/disableAgent.
     */
    setAgentEnabled(guildId: string, channelId: string, enabled: boolean): Promise<Result<void, DomainError>>;
    /**
     * Removes the agent configuration for a channel.
     */
    removeAgentConfig(guildId: string, channelId: string): Promise<Result<void, DomainError>>;
}
