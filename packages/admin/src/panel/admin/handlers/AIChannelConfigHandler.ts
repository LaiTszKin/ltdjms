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
 * Handler for AI channel config interactions (admin_aichannel_*).
 * Supports add/remove channels and categories from the AI allowlist.
 */
export class AIChannelConfigHandler extends BaseAdminHandler {
  readonly customIdPrefix = 'admin_aichannel';

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

    this.sessionManager.setViewState(guildId, userId, AdminPanelViewState.AI_CHANNEL);

    const fullCustomId = interaction.getCustomId();

    // Branch on sub-action
    if (fullCustomId === 'admin_aichannel_add_channel') {
      // TODO: show channel select menu
      await this.showChannelConfig(interaction, guildId);
      return;
    }
    if (fullCustomId === 'admin_aichannel_remove_channel') {
      // TODO: show channel remove select menu
      await this.showChannelConfig(interaction, guildId);
      return;
    }
    if (fullCustomId === 'admin_aichannel_add_category') {
      // TODO: show category select menu
      await this.showChannelConfig(interaction, guildId);
      return;
    }
    if (fullCustomId === 'admin_aichannel_remove_category') {
      // TODO: show category remove select menu
      await this.showChannelConfig(interaction, guildId);
      return;
    }

    // Default: show channel config overview
    await this.showChannelConfig(interaction, guildId);
  }

  private async showChannelConfig(
    interaction: DiscordInteraction,
    guildId: string,
  ): Promise<void> {
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
