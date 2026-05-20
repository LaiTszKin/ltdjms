import {
  type DiscordInteraction,
  type DiscordContext,
} from '@ltdjms/shared';
import { EmbedBuilder } from 'discord.js';
import { type InteractionHandler } from '../../../commands/infra/CommandHandler.js';
import { AdminPanelSessionManager } from '../../../session/AdminPanelSessionManager.js';
import { ZhTwStrings } from '../../../i18n/zh-TW.js';
import { type EscortOptionCatalogRepository } from '@ltdjms/dispatch';

/**
 * Handler for escort catalog interactions (admin_escortcatalog_*).
 * Supports CRUD operations on the global escort option catalog.
 */
export class EscortCatalogHandler implements InteractionHandler {
  readonly customIdPrefix = 'admin_escortcatalog';

  constructor(
    private readonly sessionManager: AdminPanelSessionManager,
    private readonly catalogRepository: EscortOptionCatalogRepository,
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

    // Try to get catalog entries
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
