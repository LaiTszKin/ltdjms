import {
  type DiscordInteraction,
  type DiscordContext,
} from '@ltdjms/shared';
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

    const fullCustomId = interaction.getCustomId();

    if (fullCustomId === 'user_redeem_code') {
      // Show the redeem modal (not deferred — modal must be shown before first reply)
      await this.showRedeemModal(interaction);
      return;
    }

    if (fullCustomId === 'user_redeem_submit') {
      // Process the redemption
      await this.ensureDeferred(interaction);
      await this.processRedemption(interaction, guildId, userId);
      return;
    }

    // Default: show redemption history as a preview
    await this.ensureDeferred(interaction);

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

  /**
   * Ensures the interaction has been deferred before replying.
   */
  private async ensureDeferred(interaction: DiscordInteraction): Promise<void> {
    if (!interaction.isAcknowledged()) {
      await interaction.deferReply();
    }
  }

  private async showRedeemModal(
    interaction: DiscordInteraction,
  ): Promise<void> {
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

    modal.addComponents(
      new ActionRowBuilder<TextInputBuilder>().addComponents(codeInput),
    );

    const raw = interaction.getHook() as {
      showModal: (modal: ModalBuilder) => Promise<void>;
    };
    await raw.showModal(modal);
  }

  private async processRedemption(
    interaction: DiscordInteraction,
    guildId: string,
    userId: string,
  ): Promise<void> {
    const raw = interaction.getHook() as {
      fields: { getTextInputValue: (customId: string) => string };
    };

    const codeStr = raw.fields.getTextInputValue('redeem_code');
    if (!codeStr || codeStr.trim().length === 0) {
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.redeemCodeModalTitle)
        .setDescription('請輸入有效的兌換碼')
        .setColor(0xE67E22);
      await interaction.editEmbed(embed);
      return;
    }

    const result = await this.memberInfoFacade.redeemCode(
      guildId,
      userId,
      codeStr.trim(),
    );

    if (result.isOk()) {
      const redemption = result.getValue();
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.redeemCodeModalTitle)
        .setDescription(
          ZhTwStrings.redeemSuccess.replace('{product}', redemption.product.name),
        )
        .setColor(0x57F287);
      await interaction.editEmbed(embed);
    } else {
      const errorMsg = result.getError().message;
      let friendlyMsg: string;
      if (errorMsg.includes('used') || errorMsg.includes('已使用')) {
        friendlyMsg = ZhTwStrings.redeemAlreadyUsed;
      } else if (errorMsg.includes('expired') || errorMsg.includes('已過期')) {
        friendlyMsg = ZhTwStrings.redeemExpired;
      } else if (errorMsg.includes('invalid') || errorMsg.includes('無效')) {
        friendlyMsg = ZhTwStrings.redeemInvalid;
      } else {
        friendlyMsg = '兌換失敗：' + errorMsg;
      }

      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.redeemCodeModalTitle)
        .setDescription(friendlyMsg)
        .setColor(0xED4245);
      await interaction.editEmbed(embed);
    }
  }
}
