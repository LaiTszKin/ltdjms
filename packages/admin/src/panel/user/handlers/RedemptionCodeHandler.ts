import {
  type DiscordInteraction,
  type DiscordContext,
} from '@ltdjms/shared';
import { EmbedBuilder } from 'discord.js';
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

    // Show redemption history as a preview
    const result = await this.memberInfoFacade.getProductRedemptionTransactionPage(
      guildId,
      userId,
      1,
      5,
    );

    let description: string;
    if (result.isOk()) {
      const history = result.getValue();
      if (history.items.length === 0) {
        description = '輸入兌換碼來兌換產品\n\n尚未有任何兌換記錄';
      } else {
        const lines = history.items.map((item) => {
          const maskedCode = item.code.length > 8
            ? `${item.code.slice(0, 4)}****${item.code.slice(-4)}`
            : item.code;
          const time = new Date(item.createdAt).toLocaleString('zh-TW');
          return `${time}\n${item.productName} - ${maskedCode}`;
        });
        description = lines.join('\n\n');
      }
    } else {
      description = '輸入兌換碼來兌換產品';
    }

    const embed = new EmbedBuilder()
      .setTitle(ZhTwStrings.redeemCodeModalTitle)
      .setDescription(description)
      .setColor(0xE67E22);
    await interaction.editEmbed(embed);
  }
}
