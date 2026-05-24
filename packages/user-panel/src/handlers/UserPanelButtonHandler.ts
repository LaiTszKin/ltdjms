import {
  type DiscordInteraction,
  type DiscordContext,
  ensureDeferred,
  type InteractionHandler,
} from '@ltdjms/shared';
import {
  EmbedBuilder,
  ModalBuilder,
  TextInputBuilder,
  TextInputStyle,
  ActionRowBuilder,
} from 'discord.js';
import { formatRedemptionSuccessMessage } from '@ltdjms/shop';
import { UserPanelService } from '../services/UserPanelService.js';
import { PanelSessionManager } from '../session/PanelSessionManager.js';
import { ZhTwStrings } from '../i18n/zh-TW.js';
import {
  UserPanelEmbedBuilder,
  getCurrencyHistoryButtonLabel,
} from '../services/UserPanelEmbedBuilder.js';
import { UserPanelHistoryViewFactory } from '../services/UserPanelHistoryViewFactory.js';
import { UserPanelConstants } from '../constants/UserPanelConstants.js';

/**
 * Handles user panel button and modal interactions (user_panel_*).
 * Mirrors Java UserPanelButtonHandler.
 */
export class UserPanelButtonHandler implements InteractionHandler {
  readonly customIdPrefix = UserPanelConstants.ROUTING_PREFIX;

  constructor(
    private readonly userPanelService: UserPanelService,
    private readonly sessionManager: PanelSessionManager,
  ) {}

  async execute(interaction: DiscordInteraction, _context: DiscordContext): Promise<void> {
    const guildId = interaction.getGuildId();
    if (!guildId) {
      await interaction.reply(ZhTwStrings.guildOnly);
      return;
    }

    const userId = interaction.getUserId();
    const customId = interaction.getCustomId();

    if (customId !== UserPanelConstants.MODAL_REDEEM) {
      const session = this.sessionManager.getSession(guildId, userId);
      if (!session) {
        interaction.makeEphemeral();
        await interaction.reply(ZhTwStrings.sessionExpired);
        return;
      }
    }

    try {
      if (customId === UserPanelConstants.MODAL_REDEEM) {
        await this.handleRedemptionModal(interaction, guildId, userId);
        return;
      }

      if (customId === UserPanelConstants.BUTTON_PREFIX_TOKEN_HISTORY) {
        await this.showTokenHistoryPage(interaction, guildId, userId, 1);
      } else if (customId.startsWith(UserPanelConstants.BUTTON_PREFIX_TOKEN_PAGE)) {
        const page = this.parsePageNumber(customId, UserPanelConstants.BUTTON_PREFIX_TOKEN_PAGE);
        await this.showTokenHistoryPage(interaction, guildId, userId, page);
      } else if (customId === UserPanelConstants.BUTTON_PREFIX_CURRENCY_HISTORY) {
        await this.showCurrencyHistoryPage(interaction, guildId, userId, 1);
      } else if (customId.startsWith(UserPanelConstants.BUTTON_PREFIX_CURRENCY_PAGE)) {
        const page = this.parsePageNumber(customId, UserPanelConstants.BUTTON_PREFIX_CURRENCY_PAGE);
        await this.showCurrencyHistoryPage(interaction, guildId, userId, page);
      } else if (customId === UserPanelConstants.BUTTON_REDEEM) {
        await this.openRedemptionModal(interaction);
      } else if (customId === UserPanelConstants.BUTTON_PREFIX_PRODUCT_REDEMPTION_HISTORY) {
        await this.showProductRedemptionHistoryPage(interaction, guildId, userId, 1);
      } else if (customId.startsWith(UserPanelConstants.BUTTON_PREFIX_PRODUCT_REDEMPTION_PAGE)) {
        const page = this.parsePageNumber(
          customId,
          UserPanelConstants.BUTTON_PREFIX_PRODUCT_REDEMPTION_PAGE,
        );
        await this.showProductRedemptionHistoryPage(interaction, guildId, userId, page);
      } else if (customId === UserPanelConstants.BUTTON_BACK_TO_PANEL) {
        await this.showMainPanel(interaction, guildId, userId);
      }
    } catch {
      await interaction.reply(ZhTwStrings.genericError);
    }
  }

  static buildRedeemModal(): ModalBuilder {
    const codeInput = new TextInputBuilder()
      .setCustomId(UserPanelConstants.MODAL_REDEEM_CODE_FIELD)
      .setLabel(ZhTwStrings.redeemCodeLabel)
      .setPlaceholder(ZhTwStrings.redeemCodePlaceholder)
      .setStyle(TextInputStyle.Short)
      .setMinLength(16)
      .setMaxLength(20)
      .setRequired(true);

    return new ModalBuilder()
      .setCustomId(UserPanelConstants.MODAL_REDEEM)
      .setTitle(ZhTwStrings.redeemCodeModalTitle)
      .addComponents(new ActionRowBuilder<TextInputBuilder>().addComponents(codeInput));
  }

  private parsePageNumber(customId: string, prefix: string): number {
    const pageStr = customId.slice(prefix.length);
    const page = parseInt(pageStr, 10);
    if (Number.isNaN(page)) {
      throw new Error(`Invalid page number in customId: ${customId}`);
    }
    return page;
  }

  private async openRedemptionModal(interaction: DiscordInteraction): Promise<void> {
    await interaction.showModal(UserPanelButtonHandler.buildRedeemModal());
  }

  private async handleRedemptionModal(
    interaction: DiscordInteraction,
    guildId: string,
    userId: string,
  ): Promise<void> {
    const code = interaction.getTextInputValue(UserPanelConstants.MODAL_REDEEM_CODE_FIELD)?.trim();
    if (!code) {
      interaction.makeEphemeral();
      await interaction.reply(ZhTwStrings.redeemEmptyCode);
      return;
    }

    const result = await this.userPanelService.redeemCode(code, guildId, userId);

    interaction.makeEphemeral();
    if (result.isErr()) {
      await interaction.reply(`${ZhTwStrings.redeemFailurePrefix}${result.getError().message}`);
      return;
    }

    await interaction.reply(
      `${ZhTwStrings.redeemSuccessPrefix}${formatRedemptionSuccessMessage(result.getValue())}`,
    );
  }

  private async showMainPanel(
    interaction: DiscordInteraction,
    guildId: string,
    userId: string,
  ): Promise<void> {
    await ensureDeferred(interaction);

    const result = await this.userPanelService.getUserPanelView(guildId, userId);
    if (result.isErr()) {
      await interaction.reply(ZhTwStrings.loadPanelFailed);
      return;
    }

    const view = result.getValue();
    const userMention = `<@${userId}>`;
    const embedBuilder = new UserPanelEmbedBuilder();
    const embedData = embedBuilder.buildPanelEmbed(view, userMention);
    const embed = this.toEmbed(embedData);
    const rows = UserPanelEmbedBuilder.buildPanelComponents(getCurrencyHistoryButtonLabel(view));

    await interaction.editWithComponents(embed, rows);
  }

  private async showTokenHistoryPage(
    interaction: DiscordInteraction,
    guildId: string,
    userId: string,
    page: number,
  ): Promise<void> {
    await ensureDeferred(interaction);

    const txPage = await this.userPanelService.getTokenTransactionPage(guildId, userId, page);
    const embedData = UserPanelHistoryViewFactory.buildTokenHistoryEmbed(txPage);
    const row = UserPanelHistoryViewFactory.buildTokenPaginationButtons(txPage);

    await interaction.editWithComponents(this.toEmbed(embedData), [row]);
  }

  private async showCurrencyHistoryPage(
    interaction: DiscordInteraction,
    guildId: string,
    userId: string,
    page: number,
  ): Promise<void> {
    await ensureDeferred(interaction);

    const txPage = await this.userPanelService.getCurrencyTransactionPage(guildId, userId, page);
    const embedData = UserPanelHistoryViewFactory.buildCurrencyHistoryEmbed(txPage);
    const row = UserPanelHistoryViewFactory.buildCurrencyPaginationButtons(txPage);

    await interaction.editWithComponents(this.toEmbed(embedData), [row]);
  }

  private async showProductRedemptionHistoryPage(
    interaction: DiscordInteraction,
    guildId: string,
    userId: string,
    page: number,
  ): Promise<void> {
    await ensureDeferred(interaction);

    const txPage = await this.userPanelService.getProductRedemptionTransactionPage(
      guildId,
      userId,
      page,
    );
    const embedData = UserPanelHistoryViewFactory.buildProductRedemptionHistoryEmbed(txPage);
    const row = UserPanelHistoryViewFactory.buildProductRedemptionPaginationButtons(txPage);

    await interaction.editWithComponents(this.toEmbed(embedData), [row]);
  }

  private toEmbed(data: {
    title: string;
    description: string;
    color: number;
    footer?: string;
    fields?: { name: string; value: string; inline: boolean }[];
  }): EmbedBuilder {
    const embed = new EmbedBuilder()
      .setTitle(data.title)
      .setDescription(data.description)
      .setColor(data.color);

    if (data.footer) {
      embed.setFooter({ text: data.footer });
    }

    if (data.fields) {
      embed.addFields(data.fields);
    }

    return embed;
  }
}
