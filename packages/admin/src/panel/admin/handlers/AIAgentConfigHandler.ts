import {
  type DiscordInteraction,
  type DiscordContext,
} from '@ltdjms/shared';
import { EmbedBuilder } from 'discord.js';
import { type InteractionHandler } from '../../../commands/infra/CommandHandler.js';
import { AIConfigManagementFacade } from '../../../facades/AIConfigManagementFacade.js';
import { AdminPanelSessionManager } from '../../../session/AdminPanelSessionManager.js';
import { ZhTwStrings } from '../../../i18n/zh-TW.js';

/**
 * Handler for AI agent config interactions (admin_aiagent_*).
 * Supports enable/disable/remove agent mode on channels.
 */
export class AIAgentConfigHandler implements InteractionHandler {
  readonly customIdPrefix = 'admin_aiagent';

  constructor(
    private readonly facade: AIConfigManagementFacade,
    private readonly sessionManager: AdminPanelSessionManager,
  ) {}

  async execute(
    interaction: DiscordInteraction,
    context: DiscordContext,
  ): Promise<void> {
    const guildId = interaction.getGuildId();
    const userId = interaction.getUserId();

    const session = this.sessionManager.getSession(guildId, userId);
    if (!session) {
      await interaction.reply(ZhTwStrings.sessionExpired);
      return;
    }

    await interaction.deferReply();

    // Get current agent config
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
