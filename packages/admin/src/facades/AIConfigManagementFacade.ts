import { type Result, Ok, Err, DomainError, Unit } from '@ltdjms/shared';
import type {
  AIChannelRestrictionService,
  AIAgentChannelConfigService,
  AllowedChannel,
  AllowedCategory,
} from '@ltdjms/ai';
import { AgentMode } from './agent-mode.js';

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
  ): Promise<Result<Unit, DomainError>> {
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
  ): Promise<Result<Unit, DomainError>> {
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
   * Enables agent mode for a channel.
   *
   * @param mode - The agent mode to enable (CHAT, AGENT, or HYBRID).
   *   Currently accepted but the underlying service layer only supports
   *   enabled/disabled boolean. TODO: pass mode through to service when
   *   AIAgentChannelConfigService supports it.
   */
  async enableAgent(
    guildId: string,
    channelId: string,
    _mode: AgentMode,
  ): Promise<Result<Unit, DomainError>> {
    return this.agentConfigService.setAgentEnabled(guildId, channelId, true);
  }

  /**
   * Disables agent mode for a channel.
   */
  async disableAgent(
    guildId: string,
    channelId: string,
  ): Promise<Result<Unit, DomainError>> {
    return this.agentConfigService.setAgentEnabled(guildId, channelId, false);
  }

  /**
   * Enables or disables agent mode for a channel.
   * Convenience method that delegates to enableAgent/disableAgent.
   * Defaults to AGENT mode when enabling.
   */
  async setAgentEnabled(
    guildId: string,
    channelId: string,
    enabled: boolean,
  ): Promise<Result<Unit, DomainError>> {
    if (enabled) {
      return this.enableAgent(guildId, channelId, AgentMode.AGENT);
    }
    return this.disableAgent(guildId, channelId);
  }

  /**
   * Removes the agent configuration for a channel.
   */
  async removeAgentConfig(
    guildId: string,
    channelId: string,
  ): Promise<Result<Unit, DomainError>> {
    return this.agentConfigService.removeChannel(guildId, channelId);
  }
}
