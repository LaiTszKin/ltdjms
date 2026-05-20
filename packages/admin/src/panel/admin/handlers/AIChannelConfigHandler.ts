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
 * Handler for AI channel config interactions (admin_aichannel_*).
 * Supports add/remove channels and categories from the AI allowlist.
 */
export class AIChannelConfigHandler implements InteractionHandler {
  readonly customIdPrefix = 'admin_aichannel';

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

    // Get current AI channel config
    const [channelsResult, categoriesResult] = await Promise.all([
      this.facade.getAllowedChannels(guildId),
      this.facade.getAllowedCategories(guildId),
    ]);

    const channelList = channelsResult.isOk() && channelsResult.getValue().length > 0
      ? channelsResult.getValue().map((c) => `<#${c.channelId}>`).join('\n')
      : '無';
    const categoryList = categoriesResult.isOk() && categoriesResult.getValue().length > 0
      ? categoriesResult.getValue().map((c) => c.categoryName).join('\n')
      : '無';

    const description = (channelsResult.isOk() && channelsResult.getValue().length === 0 &&
      categoriesResult.isOk() && categoriesResult.getValue().length === 0)
      ? ZhTwStrings.aiChannelEmpty
      : ZhTwStrings.aiChannelList
          .replace('{channels}', channelList)
          .replace('{categories}', categoryList);

    const embed = new EmbedBuilder()
      .setTitle(ZhTwStrings.aiChannelTitle)
      .setDescription(description)
      .setColor(0x5865F2);
    await interaction.editEmbed(embed);
  }
}
