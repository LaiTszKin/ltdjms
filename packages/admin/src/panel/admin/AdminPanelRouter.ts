import {
  type DiscordInteraction,
  type DiscordContext,
} from '@ltdjms/shared';
import { type InteractionHandler } from '../../commands/infra/CommandHandler.js';
import { AdminPanelSessionManager } from '../../session/AdminPanelSessionManager.js';
import { AdminPanelViewState } from '../../session/types.js';
import { ZhTwStrings } from '../../i18n/zh-TW.js';

/**
 * Routes admin panel button/select/modal interactions to the appropriate sub-handler
 * based on the customId prefix.
 * Matches Java AdminPanelRouter.
 */
export class AdminPanelRouter implements InteractionHandler {
  readonly customIdPrefix = 'admin_';

  constructor(
    private readonly sessionManager: AdminPanelSessionManager,
  ) {}

  async execute(
    interaction: DiscordInteraction,
    _context: DiscordContext,
  ): Promise<void> {
    const guildId = interaction.getGuildId();
    const userId = interaction.getUserId();

    // Check session validity
    const session = this.sessionManager.getSession(guildId, userId);
    if (!session) {
      await interaction.reply(ZhTwStrings.sessionExpired);
      return;
    }

    // The actual routing to sub-handlers is done by SlashCommandListener via prefix matching.
    // This router serves as the base handler for all admin_* interactions.
    // Specific sub-handlers (admin_balance_*, admin_token_*, etc.) will match
    // with longer prefixes and override this base handler.
    await interaction.reply('處理中...');
  }
}
