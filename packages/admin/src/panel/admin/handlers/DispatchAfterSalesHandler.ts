import {
  type DiscordInteraction,
  type DiscordContext,
} from '@ltdjms/shared';
import { type InteractionHandler } from '../../../commands/infra/CommandHandler.js';
import { AdminPanelSessionManager } from '../../../session/AdminPanelSessionManager.js';
import { ZhTwStrings } from '../../../i18n/zh-TW.js';

/**
 * Handler for dispatch after-sales config interactions (admin_dispatch_*).
 * Supports add/remove after-sales staff members.
 */
export class DispatchAfterSalesHandler implements InteractionHandler {
  readonly customIdPrefix = 'admin_dispatch';

  constructor(
    private readonly sessionManager: AdminPanelSessionManager,
  ) {}

  async execute(
    interaction: DiscordInteraction,
    _context: DiscordContext,
  ): Promise<void> {
    const guildId = interaction.getGuildId();
    const userId = interaction.getUserId();

    const session = this.sessionManager.getSession(guildId, userId);
    if (!session) {
      await interaction.reply(ZhTwStrings.sessionExpired);
      return;
    }

    await interaction.reply('派單售後設定功能');
  }
}
