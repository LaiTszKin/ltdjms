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
 * Handler for transaction history interactions (user_history_*).
 * Supports paginated view of currency, token, and redemption transactions.
 * Branches on full customId to distinguish:
 * - user_currency_history → currency transactions
 * - user_token_history → token transactions
 * - user_redemption_history → redemption history
 */
export class TransactionHistoryHandler implements InteractionHandler {
  readonly customIdPrefix = 'user_history';

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

    const fullCustomId = interaction.getCustomId();

    if (fullCustomId === 'user_token_history') {
      await this.showTokenHistory(interaction, guildId, userId);
    } else if (fullCustomId === 'user_redemption_history') {
      await this.showRedemptionHistory(interaction, guildId, userId);
    } else {
      // Default: show currency transaction history
      await this.showCurrencyHistory(interaction, guildId, userId);
    }
  }

  private async showCurrencyHistory(
    interaction: DiscordInteraction,
    guildId: string,
    userId: string,
  ): Promise<void> {
    const result = await this.memberInfoFacade.getCurrencyTransactionPage(
      guildId,
      userId,
      1,
      5,
    );

    let description: string;
    if (result.isOk()) {
      const txPage = result.getValue();
      const txs = txPage.transactions as unknown as Array<{ createdAt: Date; amount: number; description?: string }>;
      if (txs.length === 0) {
        description = ZhTwStrings.historyEmpty;
      } else {
        const lines = txs.map((tx) => {
          const time = new Date(tx.createdAt).toLocaleString('zh-TW');
          return `${time}\n${tx.amount > 0 ? '+' : ''}${tx.amount} | ${tx.description ?? ''}`;
        });
        description = [
          ZhTwStrings.historyPageIndicator
            .replace('{current}', String(txPage.currentPage))
            .replace('{total}', String(txPage.totalPages)),
          '',
          ...lines,
        ].join('\n');
      }
    } else {
      description = '交易記錄暫時無法取得';
    }

    const embed = new EmbedBuilder()
      .setTitle(ZhTwStrings.historyTitleCurrency)
      .setDescription(description)
      .setColor(0x2ECC71);
    await interaction.editEmbed(embed);
  }

  private async showTokenHistory(
    interaction: DiscordInteraction,
    guildId: string,
    userId: string,
  ): Promise<void> {
    const result = await this.memberInfoFacade.getTokenTransactionPage(
      guildId,
      userId,
      1,
      5,
    );

    let description: string;
    if (result.isOk()) {
      const txPage = result.getValue();
      if (txPage.transactions.length === 0) {
        description = ZhTwStrings.historyEmpty;
      } else {
        const lines = txPage.transactions.map((tx) => {
          const time = new Date(tx.createdAt).toLocaleString('zh-TW');
          return `${time}\n${tx.amount > 0 ? '+' : ''}${tx.amount} 個代幣 | ${tx.description ?? ''}`;
        });
        description = [
          ZhTwStrings.historyPageIndicator
            .replace('{current}', String(txPage.currentPage))
            .replace('{total}', String(txPage.totalPages)),
          '',
          ...lines,
        ].join('\n');
      }
    } else {
      description = '代幣記錄暫時無法取得';
    }

    const embed = new EmbedBuilder()
      .setTitle(ZhTwStrings.historyTitleToken)
      .setDescription(description)
      .setColor(0x9B59B6);
    await interaction.editEmbed(embed);
  }

  private async showRedemptionHistory(
    interaction: DiscordInteraction,
    guildId: string,
    userId: string,
  ): Promise<void> {
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
        description = ZhTwStrings.historyEmpty;
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
      description = '兌換記錄暫時無法取得';
    }

    const embed = new EmbedBuilder()
      .setTitle(ZhTwStrings.historyTitleRedemption)
      .setDescription(description)
      .setColor(0xE67E22);
    await interaction.editEmbed(embed);
  }
}
