import {
  type DiscordInteraction,
  type DiscordContext,
} from '@ltdjms/shared';
import { type CommandHandler } from '../../commands/infra/CommandHandler.js';
import { MemberInfoFacade } from '../../facades/MemberInfoFacade.js';
import { PanelSessionManager } from '../../session/PanelSessionManager.js';
import { ZhTwStrings } from '../../i18n/zh-TW.js';

/**
 * /user-panel slash command handler.
 * Opens the user panel showing balance, tokens, and action buttons.
 */
export class UserPanelCommand implements CommandHandler {
  readonly commandName = 'user-panel';

  constructor(
    private readonly memberInfoFacade: MemberInfoFacade,
    private readonly sessionManager: PanelSessionManager,
  ) {}

  async execute(
    interaction: DiscordInteraction,
    context: DiscordContext,
  ): Promise<void> {
    const guildId = interaction.getGuildId();
    const userId = interaction.getUserId();

    // Create session
    this.sessionManager.createSession(guildId, userId);

    // Query member info
    const result = await this.memberInfoFacade.getUserPanelView(guildId, userId);

    if (result.isErr()) {
      await interaction.reply(ZhTwStrings.unexpectedError);
      return;
    }

    const view = result.getValue();

    const panelText = [
      `**${ZhTwStrings.userPanelTitle}**`,
      '',
      ZhTwStrings.userPanelBalance
        .replace('{balance}', String(view.balance))
        .replace('{currencyIcon}', view.currencyIcon),
      ZhTwStrings.userPanelTokens.replace('{tokens}', String(view.tokens)),
      '',
      '---',
      `\`/currency-history\` ${ZhTwStrings.userPanelBtnCurrencyHistory}`,
      `\`/token-history\` ${ZhTwStrings.userPanelBtnTokenHistory}`,
      `\`/redemption-history\` ${ZhTwStrings.userPanelBtnRedemptionHistory}`,
      `\`/redeem-code\` ${ZhTwStrings.userPanelBtnRedeemCode}`,
    ].join('\n');

    await interaction.reply(panelText);
  }
}
