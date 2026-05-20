import {
  type DiscordInteraction,
  type DiscordContext,
} from '@ltdjms/shared';
import { EmbedBuilder } from 'discord.js';
import { AdminPanelSessionManager } from '../../../session/AdminPanelSessionManager.js';
import { AdminPanelViewState } from '../../../session/types.js';
import { BotErrorHandler } from '../../../commands/infra/BotErrorHandler.js';
import { AIConfigManagementFacade } from '../../../facades/AIConfigManagementFacade.js';
import { ZhTwStrings } from '../../../i18n/zh-TW.js';
import { BaseAdminHandler } from '../BaseAdminHandler.js';

/**
 * Handler for AI agent config interactions (admin_aiagent_*).
 * Supports enable/disable/remove agent mode on channels.
 */
export class AIAgentConfigHandler extends BaseAdminHandler {
  readonly customIdPrefix = 'admin_aiagent';

  constructor(
    private readonly facade: AIConfigManagementFacade,
    sessionManager: AdminPanelSessionManager,
    errorHandler: BotErrorHandler,
  ) {
    super(sessionManager, errorHandler);
  }

  async execute(
    interaction: DiscordInteraction,
    context: DiscordContext,
  ): Promise<void> {
    const guildId = interaction.getGuildId();
    const userId = interaction.getUserId();

    // Permission check
    if (!this.checkAdminPermission(interaction)) {
      await interaction.reply(ZhTwStrings.permissionAdminRequired);
      return;
    }

    const session = this.getSession(interaction);
    if (!session) {
      await interaction.reply(ZhTwStrings.sessionExpired);
      return;
    }

    await this.ensureDeferred(interaction);

    this.sessionManager.setViewState(guildId, userId, AdminPanelViewState.AI_AGENT);

    const fullCustomId = interaction.getCustomId();

    // Branch on sub-action
    if (fullCustomId === 'admin_aiagent_enable') {
      // TODO: show channel select for enabling agent
      await this.showAgentConfig(interaction, guildId);
      return;
    }
    if (fullCustomId === 'admin_aiagent_disable') {
      // TODO: show channel select for disabling agent
      await this.showAgentConfig(interaction, guildId);
      return;
    }
    if (fullCustomId === 'admin_aiagent_remove') {
      // TODO: show channel select for removing agent config
      await this.showAgentConfig(interaction, guildId);
      return;
    }

    // Default: show agent config overview
    await this.showAgentConfig(interaction, guildId);
  }

  private async showAgentConfig(
    interaction: DiscordInteraction,
    guildId: string,
  ): Promise<void> {
    const result = await this.facade.getAgentConfigs(guildId);

    let description: string;
    if (result.isOk() && result.getValue().length > 0) {
      const channelList = result.getValue().map((ch) => `<#${ch}>`).join('\n');
      description = ZhTwStrings.aiAgentList.replace('{channels}', channelList);
    } else {
      description = ZhTwStrings.aiAgentEmpty;
    }

    const embed = new EmbedBuilder()
      .setTitle(ZhTwStrings.aiAgentTitle)
      .setDescription(description)
      .setColor(0x5865F2);
    await interaction.editEmbed(embed);
  }
}
