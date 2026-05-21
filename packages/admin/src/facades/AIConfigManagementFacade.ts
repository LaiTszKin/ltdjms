import { type Result, DomainError, Unit, type DomainEventPublisher } from '@ltdjms/shared';
import type {
  AIChannelRestrictionService,
  AIAgentChannelConfigService,
  AllowedChannel,
  AllowedCategory,
  AIChannelConfigChangedEvent,
  AIAgentChannelConfigChangedEvent,
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
    private readonly eventPublisher: DomainEventPublisher,
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
    const result = await this.channelRestrictionService.addAllowedChannel(guildId, {
      channelId,
      channelName,
    });
    if (result.isOk()) {
      this.eventPublisher.publish({
        eventType: 'ai_channel_config_changed',
        guildId,
        changeType: 'channel_added',
        targetId: channelId,
      } as AIChannelConfigChangedEvent);
    }
    return result;
  }

  /**
   * Removes a channel from the AI allowlist.
   */
  async removeAllowedChannel(
    guildId: string,
    channelId: string,
  ): Promise<Result<Unit, DomainError>> {
    const result = await this.channelRestrictionService.removeAllowedChannel(guildId, channelId);
    if (result.isOk()) {
      this.eventPublisher.publish({
        eventType: 'ai_channel_config_changed',
        guildId,
        changeType: 'channel_removed',
        targetId: channelId,
      } as AIChannelConfigChangedEvent);
    }
    return result;
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
    const result = await this.channelRestrictionService.addAllowedCategory(guildId, {
      categoryId,
      categoryName,
    });
    if (result.isOk()) {
      this.eventPublisher.publish({
        eventType: 'ai_channel_config_changed',
        guildId,
        changeType: 'category_added',
        targetId: categoryId,
      } as AIChannelConfigChangedEvent);
    }
    return result;
  }

  /**
   * Removes a category from the AI allowlist.
   */
  async removeAllowedCategory(
    guildId: string,
    categoryId: string,
  ): Promise<Result<Unit, DomainError>> {
    const result = await this.channelRestrictionService.removeAllowedCategory(guildId, categoryId);
    if (result.isOk()) {
      this.eventPublisher.publish({
        eventType: 'ai_channel_config_changed',
        guildId,
        changeType: 'category_removed',
        targetId: categoryId,
      } as AIChannelConfigChangedEvent);
    }
    return result;
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
   *   Currently only AGENT mode is supported; CHAT and HYBRID fall back
   *   to AGENT until the service layer supports mode discrimination.
   *   Tracking: spec administration R7.2 + DB schema migration needed.
   */
  async enableAgent(
    guildId: string,
    channelId: string,
    mode: AgentMode,
  ): Promise<Result<Unit, DomainError>> {
    if (mode !== AgentMode.AGENT) {
      console.warn(
        `[AIConfigManagementFacade] Non-AGENT mode (${mode}) requested for guild=${guildId}, ` +
        `channel=${channelId}. Falling back to AGENT — service layer only supports enabled/disabled.`,
      );
    }
    const result = await this.agentConfigService.setAgentEnabled(guildId, channelId, true);
    if (result.isOk()) {
      this.eventPublisher.publish({
        eventType: 'ai_agent_channel_config_changed',
        guildId,
        channelId: Number(channelId),
        agentEnabled: true,
        changedAt: new Date(),
      } as AIAgentChannelConfigChangedEvent);
    }
    return result;
  }

  /**
   * Disables agent mode for a channel.
   */
  async disableAgent(
    guildId: string,
    channelId: string,
  ): Promise<Result<Unit, DomainError>> {
    const result = await this.agentConfigService.setAgentEnabled(guildId, channelId, false);
    if (result.isOk()) {
      this.eventPublisher.publish({
        eventType: 'ai_agent_channel_config_changed',
        guildId,
        channelId: Number(channelId),
        agentEnabled: false,
        changedAt: new Date(),
      } as AIAgentChannelConfigChangedEvent);
    }
    return result;
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
