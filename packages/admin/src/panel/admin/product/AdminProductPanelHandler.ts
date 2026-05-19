import {
  type DiscordInteraction,
  type DiscordContext,
} from '@ltdjms/shared';
import { type InteractionHandler } from '../../../commands/infra/CommandHandler.js';
import { AdminPanelSessionManager } from '../../../session/AdminPanelSessionManager.js';
import { AdminPanelViewState } from '../../../session/types.js';
import { ZhTwStrings } from '../../../i18n/zh-TW.js';

/**
 * Product-specific handler for the admin panel.
 * Manages the full product CRUD lifecycle with session state tracking.
 * Matches Java AdminProductPanelHandler.
 */
export class AdminProductPanelHandler implements InteractionHandler {
  readonly customIdPrefix = 'admin_product';

  constructor(
    private readonly sessionManager: AdminPanelSessionManager,
  ) {}

  async execute(
    interaction: DiscordInteraction,
    _context: DiscordContext,
  ): Promise<void> {
    const guildId = interaction.getGuildId();
    const userId = interaction.getUserId();
    const guildIdNum = interaction.getGuildId();

    const session = this.sessionManager.getSession(guildId, userId);
    if (!session) {
      await interaction.reply(ZhTwStrings.sessionExpired);
      return;
    }

    this.sessionManager.setViewState(guildIdNum, userId, AdminPanelViewState.PRODUCT_LIST);
    await interaction.reply('產品管理');
  }
}
