import {
  type DiscordInteraction,
  type DiscordContext,
} from '@ltdjms/shared';
import { EmbedBuilder } from 'discord.js';
import { AdminPanelSessionManager } from '../../../session/AdminPanelSessionManager.js';
import { AdminPanelViewState } from '../../../session/types.js';
import { BotErrorHandler } from '../../../commands/infra/BotErrorHandler.js';
import { ZhTwStrings } from '../../../i18n/zh-TW.js';
import { BaseAdminHandler } from '../BaseAdminHandler.js';
import { type EscortOptionCatalogRepository } from '@ltdjms/dispatch';

/**
 * Handler for escort catalog interactions (admin_escortcatalog_*).
 * Supports CRUD operations on the global escort option catalog.
 */
export class EscortCatalogHandler extends BaseAdminHandler {
  readonly customIdPrefix = 'admin_escortcatalog';

  constructor(
    sessionManager: AdminPanelSessionManager,
    private readonly catalogRepository: EscortOptionCatalogRepository,
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

    this.sessionManager.setViewState(guildId, userId, AdminPanelViewState.ESCORT_CATALOG);

    const fullCustomId = interaction.getCustomId();

    // Branch on sub-action
    if (fullCustomId === 'admin_escortcatalog_create') {
      // TODO: show create modal
      await this.showCatalog(interaction);
      return;
    }

    // Default: show catalog list
    await this.showCatalog(interaction);
  }

  private async showCatalog(interaction: DiscordInteraction): Promise<void> {
    try {
      const entries = await this.catalogRepository.findAll();
      let description: string;

      if (entries.length === 0) {
        description = ZhTwStrings.escortCatalogEmpty;
      } else {
        const items = entries.map((entry) =>
          ZhTwStrings.escortCatalogItem
            .replace('{name}', `${entry.type} - ${entry.target}`)
            .replace('{category}', entry.level)
            .replace('{price}', String(entry.priceTwd))
            .replace('{description}', entry.mapScope),
        );
        description = items.join('\n\n');
      }

      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.escortCatalogTitle)
        .setDescription(description)
        .setColor(0x5865F2);
      await interaction.editEmbed(embed);
    } catch (err) {
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.escortCatalogTitle)
        .setDescription('護航目錄資料暫時無法取得')
        .setColor(0x5865F2);
      await interaction.editEmbed(embed);
    }
  }
}
