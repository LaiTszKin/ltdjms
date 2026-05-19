import {
  type DiscordInteraction,
  type DiscordContext,
} from '@ltdjms/shared';
import { type InteractionHandler } from '../../../commands/infra/CommandHandler.js';
import { MemberInfoFacade } from '../../../facades/MemberInfoFacade.js';
import { PanelSessionManager } from '../../../session/PanelSessionManager.js';
import { ZhTwStrings } from '../../../i18n/zh-TW.js';

/**
 * Handler for redemption code interactions (user_redeem_*).
 * Supports inputting a code via modal and executing the redemption.
 */
export class RedemptionCodeHandler implements InteractionHandler {
  readonly customIdPrefix = 'user_redeem';

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

    await interaction.reply('兌換碼功能');
  }
}
