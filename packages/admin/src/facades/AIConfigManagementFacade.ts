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
   * Lists all allowed AI channels for a guild.
   */
  async listAllowedChannels(guildId: string): Promise<Result<AllowedChannel[], DomainError>> {
    return this.channelRestrictionService.getAllowedChannels(guildId);
  }

  /**
   * Adds a channel to the AI allowlist.
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
   * Lists all allowed AI categories for a guild.
   */
  async listAllowedCategories(guildId: string): Promise<Result<AllowedCategory[], DomainError>> {
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
   * Lists all channels with agent configuration for a guild.
   */
  async listAgentChannels(guildId: string): Promise<Result<string[], DomainError>> {
    return this.agentConfigService.getEnabledChannels(guildId);
  }

  /**
   * Enables or disables agent mode for a channel.
   */
  async setAgentEnabled(
    guildId: string,
    channelId: string,
    enabled: boolean,
  ): Promise<Result<void, DomainError>> {
    return this.agentConfigService.setAgentEnabled(guildId, channelId, enabled);
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
