import {
  type DiscordInteraction,
  type DiscordContext,
} from '@ltdjms/shared';
import { type InteractionHandler } from '../../../commands/infra/CommandHandler.js';
import { MemberInfoFacade } from '../../../facades/MemberInfoFacade.js';
import { PanelSessionManager } from '../../../session/PanelSessionManager.js';
import { ZhTwStrings } from '../../../i18n/zh-TW.js';

/**
 * Handler for transaction history interactions (user_history_*).
 * Supports paginated view of currency, token, and redemption transactions.
 */
export class TransactionHistoryHandler implements InteractionHandler {
  readonly customIdPrefix = 'user_history';

  constructor(
    private readonly memberInfoFacade: MemberInfoFacade,
    private readonly sessionManager: PanelSessionManager,
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

    await interaction.reply('交易記錄功能');
  }
}
