import { type DiscordInteraction, type DiscordContext } from '@ltdjms/shared';
import { type InteractionHandler } from '../../commands/infra/CommandHandler.js';
import { AdminPanelSessionManager } from '../../session/AdminPanelSessionManager.js';
import { ZhTwStrings } from '../../i18n/zh-TW.js';

/**
 * Catch-all fallback handler for admin panel button/select/modal interactions.
 * Routes unmatched admin_* prefix interactions (after longer-prefix sub-handlers
 * have been tried) and returns an error message to the user.
 * Matches Java AdminPanelRouter.
 */
export class AdminPanelFallbackHandler implements InteractionHandler {
  readonly customIdPrefix = 'admin_';

  constructor(private readonly sessionManager: AdminPanelSessionManager) {}

  async execute(interaction: DiscordInteraction, _context: DiscordContext): Promise<void> {
    const guildId = interaction.getGuildId();
    const userId = interaction.getUserId();

    // Check session validity
    const session = this.sessionManager.getSession(guildId, userId);
    if (!session) {
      interaction.makeEphemeral();
      await interaction.reply(ZhTwStrings.sessionExpired);
      return;
    }

    // The actual routing to sub-handlers is done by SlashCommandListener via prefix matching.
    // This router serves as the base handler for all admin_* interactions.
    // Specific sub-handlers (admin_balance_*, admin_token_*, etc.) will match
    // with longer prefixes and override this base handler.
    //
    // If this handler is reached, it means no sub-handler matched the customId,
    // indicating an invalid/unregistered interaction route.
    interaction.makeEphemeral();
    await interaction.reply('無效的操作，請重新開啟管理面板。若問題持續發生，請聯絡管理員。');
  }
}
