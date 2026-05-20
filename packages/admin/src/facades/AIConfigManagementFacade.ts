import { type Result, Ok, Err, DomainError } from '@ltdjms/shared';
import type {
  AIChannelRestrictionService,
  AIAgentChannelConfigService,
  AllowedChannel,
  AllowedCategory,
} from '@ltdjms/ai';

/**
 * Facade for AI channel and agent configuration management.
 * Wraps AIChannelRestrictionService and AIAgentChannelConfigService.
 * Matches Java AIConfigManagementFacade.
 */
export class AIConfigManagementFacade {
  constructor(
    private readonly channelRestrictionService: AIChannelRestrictionService,
    private readonly agentConfigService: AIAgentChannelConfigService,
  ) {}

  // ============================================================
  // Allowed Channels
  // ============================================================

  /**
   * Gets all allowed AI channels for a guild.
   */
  async getAllowedChannels(guildId: string): Promise<Result<AllowedChannel[], DomainError>> {
    return this.channelRestrictionService.getAllowedChannels(guildId);
  }

  /**
   * Adds a channel to the AI allowlist.
   *
   * @param channelName - Display name of the channel. Passed through to
   *   AIChannelRestrictionService.addAllowedChannel for logging/reference.
   *   This parameter is accepted for API completeness (R13.4) but may not
   *   be persisted by all implementations.
   */
  async addAllowedChannel(
    guildId: string,
    channelId: string,
    channelName: string,
  ): Promise<Result<AllowedChannel, DomainError>> {
    return this.channelRestrictionService.addAllowedChannel(guildId, {
      channelId,
      channelName,
    });
  }

  /**
   * Removes a channel from the AI allowlist.
   */
  async removeAllowedChannel(
    guildId: string,
    channelId: string,
  ): Promise<Result<void, DomainError>> {
    return this.channelRestrictionService.removeAllowedChannel(guildId, channelId);
  }

  // ============================================================
  // Allowed Categories
  // ============================================================

  /**
   * Gets all allowed AI categories for a guild.
   */
  async getAllowedCategories(guildId: string): Promise<Result<AllowedCategory[], DomainError>> {
    return this.channelRestrictionService.getAllowedCategories(guildId);
  }

  /**
   * Adds a category to the AI allowlist.
   */
  async addAllowedCategory(
    guildId: string,
    categoryId: string,
    categoryName: string,
  ): Promise<Result<AllowedCategory, DomainError>> {
    return this.channelRestrictionService.addAllowedCategory(guildId, {
      categoryId,
      categoryName,
    });
  }

  /**
   * Removes a category from the AI allowlist.
   */
  async removeAllowedCategory(
    guildId: string,
    categoryId: string,
  ): Promise<Result<void, DomainError>> {
    return this.channelRestrictionService.removeAllowedCategory(guildId, categoryId);
  }

  // ============================================================
  // Agent Config
  // ============================================================

  /**
   * Gets all channels with agent configuration for a guild.
   */
  async getAgentConfigs(guildId: string): Promise<Result<string[], DomainError>> {
    return this.agentConfigService.getEnabledChannels(guildId);
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
   * TODO(P2-8): Once AIAgentChannelConfigService.setAgentEnabled supports a mode
   * parameter (chat/agent/hybrid), update the handler to collect the mode choice
   * from the admin and pass it here instead of the hardcoded 'default'.
   * TODO(P2-7): 目前 _mode 被忽略；當 setAgentEnabled 支援 mode 參數時，
   * 將 `_mode` 傳入 service call：
   * return this.agentConfigService.setAgentEnabled(guildId, channelId, true, _mode);
   */
  async enableAgent(
    guildId: string,
    channelId: string,
    _mode: string,
  ): Promise<Result<void, DomainError>> {
    return this.agentConfigService.setAgentEnabled(guildId, channelId, true);
  }

  /**
   * Disables agent mode for a channel.
   */
  async disableAgent(
    guildId: string,
    channelId: string,
  ): Promise<Result<void, DomainError>> {
    return this.agentConfigService.setAgentEnabled(guildId, channelId, false);
  }

  /**
   * Enables or disables agent mode for a channel.
   * Convenience method that delegates to enableAgent/disableAgent.
   */
  async setAgentEnabled(
    guildId: string,
    channelId: string,
    enabled: boolean,
  ): Promise<Result<void, DomainError>> {
    if (enabled) {
      return this.enableAgent(guildId, channelId, 'default');
    }
    return this.disableAgent(guildId, channelId);
  }

  /**
   * Removes the agent configuration for a channel.
   */
  async removeAgentConfig(
    guildId: string,
    channelId: string,
  ): Promise<Result<void, DomainError>> {
    return this.agentConfigService.removeChannel(guildId, channelId);
  }
}
