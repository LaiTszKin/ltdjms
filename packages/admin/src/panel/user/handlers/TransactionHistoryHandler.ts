import {
  type DiscordInteraction,
  type DiscordContext,
} from '@ltdjms/shared';
import {
  EmbedBuilder,
  ActionRowBuilder,
  ButtonBuilder,
  ButtonStyle,
} from 'discord.js';
import { type InteractionHandler } from '../../../commands/infra/CommandHandler.js';
import { MemberInfoFacade } from '../../../facades/MemberInfoFacade.js';
import { PanelSessionManager } from '../../../session/PanelSessionManager.js';
import { ZhTwStrings } from '../../../i18n/zh-TW.js';
import { Colors } from '../../../constants/colors.js';

const PAGE_SIZE = 10;

/**
 * In-memory page tracker for transaction history per user and type.
 * Key format: `${guildId}:${userId}:${type}`
 */
const pageTracker = new Map<string, number>();

function pageKey(guildId: string, userId: string, type: string): string {
  return `${guildId}:${userId}:${type}`;
}

function getPage(guildId: string, userId: string, type: string): number {
  return pageTracker.get(pageKey(guildId, userId, type)) ?? 1;
}

function setPage(guildId: string, userId: string, type: string, page: number): void {
  pageTracker.set(pageKey(guildId, userId, type), page);
}

/**
 * Handler for transaction history interactions (user_history_*).
 * Supports paginated view of currency, token, and redemption transactions.
 * Branches on full customId to distinguish types and prev/next navigation.
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

    // Parse: user_{type}_history, user_{type}_history_prev_{page}, user_{type}_history_next_{page}
    if (fullCustomId.startsWith('user_token_history')) {
      await this.showTokenHistory(interaction, guildId, userId, fullCustomId);
    } else if (fullCustomId.startsWith('user_redemption_history')) {
      await this.showRedemptionHistory(interaction, guildId, userId, fullCustomId);
    } else {
      // Default and currency navigation: user_currency_history*
      await this.showCurrencyHistory(interaction, guildId, userId, fullCustomId);
    }
  }

  private parseNavCustomId(
    fullCustomId: string,
    prefix: string,
  ): { page: number } {
    if (fullCustomId === prefix || fullCustomId === `${prefix}_history`) {
      return { page: 1 };
    }
    if (fullCustomId.startsWith(`${prefix}_history_prev_`)) {
      const p = parseInt(fullCustomId.slice(`${prefix}_history_prev_`.length), 10);
      return { page: isNaN(p) ? 1 : p };
    }
    if (fullCustomId.startsWith(`${prefix}_history_next_`)) {
      const p = parseInt(fullCustomId.slice(`${prefix}_history_next_`.length), 10);
      return { page: isNaN(p) ? 1 : p };
    }
    return { page: 1 };
  }

  private buildNavRow(
    currentPage: number,
    totalPages: number,
    customIdPrefix: string,
  ): ActionRowBuilder<ButtonBuilder> {
    const row = new ActionRowBuilder<ButtonBuilder>();
    const prevPage = currentPage > 1 ? currentPage - 1 : 1;
    const nextPage = currentPage < totalPages ? currentPage + 1 : totalPages;

    row.addComponents(
      new ButtonBuilder()
        .setCustomId(`${customIdPrefix}_history_prev_${prevPage}`)
        .setLabel(ZhTwStrings.historyPrevBtn)
        .setStyle(ButtonStyle.Secondary)
        .setDisabled(currentPage <= 1),
      new ButtonBuilder()
        .setCustomId(`${customIdPrefix}_history_next_${nextPage}`)
        .setLabel(ZhTwStrings.historyNextBtn)
        .setStyle(ButtonStyle.Secondary)
        .setDisabled(currentPage >= totalPages),
    );
    return row;
  }

  private async showCurrencyHistory(
    interaction: DiscordInteraction,
    guildId: string,
    userId: string,
    fullCustomId: string,
  ): Promise<void> {
    const { page } = this.parseNavCustomId(fullCustomId, 'user_currency');
    setPage(guildId, userId, 'currency', page);

    const result = await this.memberInfoFacade.getCurrencyTransactionPage(
      guildId,
      userId,
      page,
      PAGE_SIZE,
    );

    let description: string;
    let totalPages = 1;

    if (result.isOk()) {
      const txPage = result.getValue();
      totalPages = txPage.totalPages;
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
      .setColor(Colors.HISTORY_CURRENCY);

    const row = this.buildNavRow(page, totalPages, 'user_currency');
    const raw = interaction.getHook() as {
      editReply: (opts: { embeds: EmbedBuilder[]; components: ActionRowBuilder<ButtonBuilder>[] }) => Promise<void>;
    };
    await raw.editReply({ embeds: [embed], components: [row] });
  }

  private async showTokenHistory(
    interaction: DiscordInteraction,
    guildId: string,
    userId: string,
    fullCustomId: string,
  ): Promise<void> {
    const { page } = this.parseNavCustomId(fullCustomId, 'user_token');
    setPage(guildId, userId, 'token', page);

    const result = await this.memberInfoFacade.getTokenTransactionPage(
      guildId,
      userId,
      page,
      PAGE_SIZE,
    );

    let description: string;
    let totalPages = 1;

    if (result.isOk()) {
      const txPage = result.getValue();
      totalPages = txPage.totalPages;
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
      .setColor(Colors.HISTORY_TOKEN);

    const row = this.buildNavRow(page, totalPages, 'user_token');
    const raw = interaction.getHook() as {
      editReply: (opts: { embeds: EmbedBuilder[]; components: ActionRowBuilder<ButtonBuilder>[] }) => Promise<void>;
    };
    await raw.editReply({ embeds: [embed], components: [row] });
  }

  private async showRedemptionHistory(
    interaction: DiscordInteraction,
    guildId: string,
    userId: string,
    fullCustomId: string,
  ): Promise<void> {
    const { page } = this.parseNavCustomId(fullCustomId, 'user_redemption');
    setPage(guildId, userId, 'redemption', page);

    const result = await this.memberInfoFacade.getProductRedemptionTransactionPage(
      guildId,
      userId,
      page,
      PAGE_SIZE,
    );

    let description: string;
    let totalPages = 1;

    if (result.isOk()) {
      const history = result.getValue();
      totalPages = history.totalPages;
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
      .setColor(Colors.HISTORY_REDEMPTION);

    const row = this.buildNavRow(page, totalPages, 'user_redemption');
    const raw = interaction.getHook() as {
      editReply: (opts: { embeds: EmbedBuilder[]; components: ActionRowBuilder<ButtonBuilder>[] }) => Promise<void>;
    };
    await raw.editReply({ embeds: [embed], components: [row] });
  }
}
