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
export class AIConfigManagementFacade {
    channelRestrictionService;
    agentConfigService;
    constructor(channelRestrictionService, agentConfigService) {
        this.channelRestrictionService = channelRestrictionService;
        this.agentConfigService = agentConfigService;
    }
    // ============================================================
    // Allowed Channels
    // ============================================================
    /**
     * Gets all allowed AI channels for a guild.
     */
    async getAllowedChannels(guildId) {
        return this.channelRestrictionService.getAllowedChannels(guildId);
    }
    /**
     * Lists all allowed AI channels for a guild.
     * @deprecated Use {@link getAllowedChannels} instead.
     */
    async listAllowedChannels(guildId) {
        return this.getAllowedChannels(guildId);
    }
    /**
     * Adds a channel to the AI allowlist.
     *
     * @param channelName - Display name of the channel. Passed through to
     *   AIChannelRestrictionService.addAllowedChannel for logging/reference.
     *   This parameter is accepted for API completeness (R13.4) but may not
     *   be persisted by all implementations.
     */
    async addAllowedChannel(guildId, channelId, channelName) {
        return this.channelRestrictionService.addAllowedChannel(guildId, {
            channelId,
            channelName,
        });
    }
    /**
     * Removes a channel from the AI allowlist.
     */
    async removeAllowedChannel(guildId, channelId) {
        return this.channelRestrictionService.removeAllowedChannel(guildId, channelId);
    }
    // ============================================================
    // Allowed Categories
    // ============================================================
    /**
     * Gets all allowed AI categories for a guild.
     */
    async getAllowedCategories(guildId) {
        return this.channelRestrictionService.getAllowedCategories(guildId);
    }
    /**
     * Lists all allowed AI categories for a guild.
     * @deprecated Use {@link getAllowedCategories} instead.
     */
    async listAllowedCategories(guildId) {
        return this.getAllowedCategories(guildId);
    }
    /**
     * Adds a category to the AI allowlist.
     */
    async addAllowedCategory(guildId, categoryId, categoryName) {
        return this.channelRestrictionService.addAllowedCategory(guildId, {
            categoryId,
            categoryName,
        });
    }
    /**
     * Removes a category from the AI allowlist.
     */
    async removeAllowedCategory(guildId, categoryId) {
        return this.channelRestrictionService.removeAllowedCategory(guildId, categoryId);
    }
    // ============================================================
    // Agent Config
    // ============================================================
    /**
     * Gets all channels with agent configuration for a guild.
     */
    async getAgentConfigs(guildId) {
        return this.agentConfigService.getEnabledChannels(guildId);
    }
    /**
     * Lists all channels with agent configuration for a guild.
     * @deprecated Use {@link getAgentConfigs} instead.
     */
    async listAgentChannels(guildId) {
        return this.getAgentConfigs(guildId);
    }
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
    async enableAgent(guildId, channelId, _mode) {
        return this.agentConfigService.setAgentEnabled(guildId, channelId, true);
    }
    /**
     * Disables agent mode for a channel.
     */
    async disableAgent(guildId, channelId) {
        return this.agentConfigService.setAgentEnabled(guildId, channelId, false);
    }
    /**
     * Enables or disables agent mode for a channel.
     * Convenience method that delegates to enableAgent/disableAgent.
     */
    async setAgentEnabled(guildId, channelId, enabled) {
        if (enabled) {
            return this.enableAgent(guildId, channelId, 'default');
        }
        return this.disableAgent(guildId, channelId);
    }
    /**
     * Removes the agent configuration for a channel.
     */
    async removeAgentConfig(guildId, channelId) {
        return this.agentConfigService.removeChannel(guildId, channelId);
    }
}
//# sourceMappingURL=AIConfigManagementFacade.js.map