/**
 * Facade for AI channel and agent configuration management.
 * Wraps AIChannelRestrictionService and AIAgentChannelConfigService.
 * Matches Java AIConfigManagementFacade.
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
     * Lists all allowed AI channels for a guild.
     */
    async listAllowedChannels(guildId) {
        return this.channelRestrictionService.getAllowedChannels(guildId);
    }
    /**
     * Adds a channel to the AI allowlist.
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
     * Lists all allowed AI categories for a guild.
     */
    async listAllowedCategories(guildId) {
        return this.channelRestrictionService.getAllowedCategories(guildId);
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
     * Lists all channels with agent configuration for a guild.
     */
    async listAgentChannels(guildId) {
        return this.agentConfigService.getEnabledChannels(guildId);
    }
    /**
     * Enables or disables agent mode for a channel.
     */
    async setAgentEnabled(guildId, channelId, enabled) {
        return this.agentConfigService.setAgentEnabled(guildId, channelId, enabled);
    }
    /**
     * Removes the agent configuration for a channel.
     */
    async removeAgentConfig(guildId, channelId) {
        return this.agentConfigService.removeChannel(guildId, channelId);
    }
}
//# sourceMappingURL=AIConfigManagementFacade.js.map