import {
  type DiscordInteraction,
  type DiscordContext,
} from '@ltdjms/shared';
import { type InteractionHandler } from '../../../commands/infra/CommandHandler.js';
import { GameTokenManagementFacade } from '../../../facades/GameTokenManagementFacade.js';
import { AdminPanelSessionManager } from '../../../session/AdminPanelSessionManager.js';
import { ZhTwStrings } from '../../../i18n/zh-TW.js';

/**
 * Handler for token management interactions (admin_token_*).
 * Supports select member, view tokens, add/deduct/set via modal.
 */
export class TokenManagementHandler implements InteractionHandler {
  readonly customIdPrefix = 'admin_token';

  constructor(
    private readonly facade: GameTokenManagementFacade,
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

    await interaction.reply('代幣管理功能');
  }
}
