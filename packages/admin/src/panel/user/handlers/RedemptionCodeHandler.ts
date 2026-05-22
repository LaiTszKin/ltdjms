import { type DiscordInteraction, type DiscordContext, DomainErrorCategory } from '@ltdjms/shared';
import {
  EmbedBuilder,
  ModalBuilder,
  TextInputBuilder,
  TextInputStyle,
  ActionRowBuilder,
} from 'discord.js';
import { type InteractionHandler } from '../../../commands/infra/CommandHandler.js';
import { MemberInfoFacade } from '../../../facades/MemberInfoFacade.js';
import { PanelSessionManager } from '../../../session/PanelSessionManager.js';
import { ZhTwStrings } from '../../../i18n/zh-TW.js';
import { ensureDeferred } from '../../admin/BaseAdminHandler.js';

/**
 * Handler for redemption code interactions (user_redeem_*).
 * Supports inputting a code via modal and executing the redemption.
 * - user_redeem_code → shows a modal for code input
 * - user_redeem_submit → processes the redemption
 */
export class RedemptionCodeHandler implements InteractionHandler {
  readonly customIdPrefix = 'user_redeem';

  constructor(
    private readonly memberInfoFacade: MemberInfoFacade,
    private readonly sessionManager: PanelSessionManager,
  ) {}

  async execute(interaction: DiscordInteraction, context: DiscordContext): Promise<void> {
    const guildId = interaction.getGuildId();
    const userId = interaction.getUserId();

    const session = this.sessionManager.getSession(guildId, userId);
    if (!session) {
      await interaction.reply(ZhTwStrings.sessionExpired);
      return;
    }

    const fullCustomId = interaction.getCustomId();

    if (fullCustomId === 'user_redeem_code') {
      // Show the redeem modal (not deferred — modal must be shown before first reply)
      await this.showRedeemModal(interaction);
      return;
    }

    if (fullCustomId === 'user_redeem_submit') {
      // Process the redemption
      await ensureDeferred(interaction);
      await this.processRedemption(interaction, guildId, userId);
      return;
    }

    // Default: show redemption history as a preview
    await ensureDeferred(interaction);

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
          const maskedCode =
            item.code.length > 8 ? `${item.code.slice(0, 4)}****${item.code.slice(-4)}` : item.code;
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
      .setColor(0xe67e22);
    await interaction.editEmbed(embed);
  }

  /**
   * Builds a redeem code modal.
   * Shared with RedeemCodeCommandHandler to avoid duplicate modal construction.
   */
  static buildRedeemModal(): ModalBuilder {
    const modal = new ModalBuilder()
      .setCustomId('user_redeem_submit')
      .setTitle(ZhTwStrings.redeemCodeModalTitle);

    const codeInput = new TextInputBuilder()
      .setCustomId('redeem_code')
      .setLabel(ZhTwStrings.redeemCodeLabel)
      .setPlaceholder(ZhTwStrings.redeemCodePlaceholder)
      .setStyle(TextInputStyle.Short)
      .setMinLength(16)
      .setMaxLength(32)
      .setRequired(true);

    modal.addComponents(new ActionRowBuilder<TextInputBuilder>().addComponents(codeInput));

    return modal;
  }

  private async showRedeemModal(interaction: DiscordInteraction): Promise<void> {
    await interaction.showModal(RedemptionCodeHandler.buildRedeemModal());
  }

  private async processRedemption(
    interaction: DiscordInteraction,
    guildId: string,
    userId: string,
  ): Promise<void> {
    const codeStr = interaction.getTextInputValue('redeem_code');
    if (!codeStr || codeStr.trim().length === 0) {
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.redeemCodeModalTitle)
        .setDescription('請輸入有效的兌換碼')
        .setColor(0xe67e22);
      await interaction.editEmbed(embed);
      return;
    }

    const result = await this.memberInfoFacade.redeemCode(guildId, userId, codeStr.trim());

    if (result.isOk()) {
      const redemption = result.getValue();

      // Fetch updated balance to reflect post-redemption state
      const memberSummary = await this.memberInfoFacade.getMemberSummary(guildId, userId);
      let description = ZhTwStrings.redeemSuccess.replace('{product}', redemption.product.name);
      if (memberSummary.isOk()) {
        const summary = memberSummary.getValue();
        description += `\n\n當前餘額：${summary.balance} ${summary.currencyIcon}`;
        description += `\n遊戲代幣：${summary.tokens} 個`;
      }

      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.redeemCodeModalTitle)
        .setDescription(description)
        .setColor(0x57f287);
      await interaction.editEmbed(embed);
    } else {
      const error = result.getError();
      let friendlyMsg: string;
      if (error.category === DomainErrorCategory.REDEEM_CODE_USED) {
        friendlyMsg = ZhTwStrings.redeemAlreadyUsed;
      } else if (error.category === DomainErrorCategory.REDEEM_CODE_EXPIRED) {
        friendlyMsg = ZhTwStrings.redeemExpired;
      } else if (error.category === DomainErrorCategory.REDEEM_CODE_INVALID) {
        friendlyMsg = ZhTwStrings.redeemInvalid;
      } else {
        friendlyMsg = '兌換失敗：' + error.message;
      }

      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.redeemCodeModalTitle)
        .setDescription(friendlyMsg)
        .setColor(0xed4245);
      await interaction.editEmbed(embed);
    }
  }
}
