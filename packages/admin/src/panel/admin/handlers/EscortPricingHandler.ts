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
  ButtonBuilder,
  ButtonStyle,
} from 'discord.js';
import { AdminPanelSessionManager } from '../../../session/AdminPanelSessionManager.js';
import { AdminPanelViewState } from '../../../session/types.js';
import { BotErrorHandler } from '../../../commands/infra/BotErrorHandler.js';
import { ZhTwStrings } from '../../../i18n/zh-TW.js';
import { BaseAdminHandler } from '../BaseAdminHandler.js';
import { DispatchManagementFacade } from '../../../facades/DispatchManagementFacade.js';
import { AdminPanelModalFactory } from '../views/AdminPanelModalFactory.js';
import { Colors } from '../../../constants/colors.js';

/**
 * Handler for escort pricing interactions (admin_escortprice_*).
 * Supports view pricing list, edit guild override, reset to default.
 */
export class EscortPricingHandler extends BaseAdminHandler {
  readonly customIdPrefix = 'admin_escortprice';

  constructor(
    sessionManager: AdminPanelSessionManager,
    private readonly facade: DispatchManagementFacade,
    private readonly modalFactory: AdminPanelModalFactory,
    errorHandler: BotErrorHandler,
  ) {
    super(sessionManager, errorHandler);
  }

  async execute(
    interaction: DiscordInteraction,
    context: DiscordContext,
  ): Promise<void> {
    const guildId = interaction.getGuildId();
    const userId = interaction.getUserId();

    // Permission check
    if (!this.checkAdminPermission(interaction)) {
      await interaction.reply(ZhTwStrings.permissionAdminRequired);
      return;
    }

    const session = this.getSession(interaction);
    if (!session) {
      await interaction.reply(ZhTwStrings.sessionExpired);
      return;
    }

    await this.ensureDeferred(interaction);

    this.sessionManager.setViewState(guildId, userId, AdminPanelViewState.ESCORT_PRICING);

    const fullCustomId = interaction.getCustomId();

    // Handle modal submit for editing price
    if (fullCustomId.startsWith('admin_escortprice_save_')) {
      const optionCode = fullCustomId.replace('admin_escortprice_save_', '');
      await this.handleEditPriceSave(interaction, guildId, userId, optionCode);
      return;
    }

    // Handle reset confirmation dialog
    if (fullCustomId.startsWith('admin_escortprice_reset_')) {
      const optionCode = fullCustomId.replace('admin_escortprice_reset_', '');
      if (optionCode) {
        await this.showResetConfirmation(interaction, guildId, optionCode);
      }
      return;
    }

    // Handle confirmed reset execution
    if (fullCustomId.startsWith('admin_escortprice_confirm_reset_')) {
      const optionCode = fullCustomId.replace('admin_escortprice_confirm_reset_', '');
      if (optionCode) {
        await this.handleResetPrice(interaction, guildId, userId, optionCode);
      }
      return;
    }

    // Show edit modal
    if (fullCustomId.startsWith('admin_escortprice_edit_')) {
      const optionCode = fullCustomId.replace('admin_escortprice_edit_', '');
      if (optionCode) {
        await this.showEditModal(interaction, guildId, optionCode);
      }
      return;
    }

    // Handle back to pricing list
    if (fullCustomId === 'admin_escortprice_back') {
      this.sessionManager.setViewState(guildId, userId, AdminPanelViewState.ESCORT_PRICING);
      await this.showPricingList(interaction, guildId);
      return;
    }

    // Default: show pricing list
    await this.showPricingList(interaction, guildId);
  }

  private async showEditModal(
    interaction: DiscordInteraction,
    guildId: string,
    optionCode: string,
  ): Promise<void> {
    // Get current pricing to pre-fill the modal
    const pricesResult = await this.facade.listPricing(guildId);
    let currentOverride: number | null = null;
    let optionName = optionCode;

    if (pricesResult.isOk()) {
      const prices = pricesResult.getValue();
      const found = prices.find((p) => p.optionCode === optionCode);
      if (found) {
        optionName = `${found.option.type} - ${found.option.target}`;
        currentOverride = found.overridden ? found.effectivePriceTwd : null;
      }
    }

    const modalData = this.modalFactory.buildEscortPricingEditModal(
      optionName,
      0,
      currentOverride,
    );

    const modal = new ModalBuilder()
      .setCustomId('admin_escortprice_save_' + optionCode)
      .setTitle(modalData.title);

    for (const field of modalData.fields) {
      const input = new TextInputBuilder()
        .setCustomId(field.label)
        .setLabel(field.label)
        .setStyle(TextInputStyle.Short)
        .setValue(field.value)
        .setMinLength(field.minLength)
        .setMaxLength(field.maxLength)
        .setRequired(field.required);
      if ('placeholder' in field && field.placeholder) {
        input.setPlaceholder(field.placeholder);
      }
      modal.addComponents(
        new ActionRowBuilder<TextInputBuilder>().addComponents(input),
      );
    }

    await interaction.showModal(modal);
  }

  private async handleEditPriceSave(
    interaction: DiscordInteraction,
    guildId: string,
    actorId: string,
    optionCode: string,
  ): Promise<void> {
    const priceStr = interaction.getTextInputValue(ZhTwStrings.escortPricingEditLabel);
    const price = parseInt(priceStr, 10);

    if (isNaN(price) || price <= 0) {
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.escortPricingTitle)
        .setDescription('請輸入有效的正整數價格')
        .setColor(Colors.WARNING);
      await interaction.editEmbed(embed);
      return;
    }

    const result = await this.facade.updatePricing(guildId, actorId, optionCode, price);

    if (result.isOk()) {
      const updated = result.getValue();

      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.escortPricingTitle)
        .setDescription(
          ZhTwStrings.escortPricingUpdated
            .replace('{name}', `${updated.option.type} - ${updated.option.target}`)
            .replace('{price}', String(price)),
        )
        .setColor(Colors.SUCCESS);
      await interaction.editEmbed(embed);
    } else {
      await this.errorHandler.handle(result.getError(), interaction);
    }
  }

  private async showResetConfirmation(
    interaction: DiscordInteraction,
    guildId: string,
    optionCode: string,
  ): Promise<void> {
    const pricesResult = await this.facade.listPricing(guildId);
    let optionName = optionCode;

    if (pricesResult.isOk()) {
      const prices = pricesResult.getValue();
      const found = prices.find((p) => p.optionCode === optionCode);
      if (found) {
        optionName = `${found.option.type} - ${found.option.target}`;
      }
    }

    const embed = new EmbedBuilder()
      .setTitle(ZhTwStrings.escortPricingTitle)
      .setDescription(
        ZhTwStrings.escortPricingResetConfirm.replace('{name}', optionName),
      )
      .setColor(Colors.WARNING);

    const confirmBtn = new ButtonBuilder()
      .setCustomId('admin_escortprice_confirm_reset_' + optionCode)
      .setLabel('確認重設')
      .setStyle(ButtonStyle.Danger);

    const cancelBtn = new ButtonBuilder()
      .setCustomId('admin_escortprice_back')
      .setLabel('取消')
      .setStyle(ButtonStyle.Secondary);

    const row = new ActionRowBuilder<ButtonBuilder>().addComponents(confirmBtn, cancelBtn);

    await interaction.editWithComponents(embed, [row]);
  }

  private async handleResetPrice(
    interaction: DiscordInteraction,
    guildId: string,
    actorId: string,
    optionCode: string,
  ): Promise<void> {
    const result = await this.facade.resetPricing(guildId, optionCode);

    if (result.isOk()) {
      // Query global default price after reset
      const catalogEntry = await this.facade.findCatalogEntry(optionCode);
      const defaultPrice = catalogEntry.isOk() && catalogEntry.getValue()
        ? String(catalogEntry.getValue().priceTwd)
        : '0';

      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.escortPricingTitle)
        .setDescription(
          ZhTwStrings.escortPricingResetDone.replace('{name}', optionCode).replace('{price}', defaultPrice),
        )
        .setColor(Colors.SUCCESS);
      await interaction.editEmbed(embed);
    } else {
      await this.errorHandler.handle(result.getError(), interaction);
    }
  }

  private async showPricingList(
    interaction: DiscordInteraction,
    guildId: string,
  ): Promise<void> {
    const result = await this.facade.listPricing(guildId);

    let description: string;
    if (result.isOk()) {
      const prices = result.getValue();
      if (prices.length === 0) {
        description = '目前沒有任何護航定價資料';
      } else {
        const lines = prices.map((p) => {
          const overrideLine = p.overridden
            ? ZhTwStrings.escortPricingGuildOverride.replace('{price}', String(p.effectivePriceTwd))
            : ZhTwStrings.escortPricingNoOverride;
          return ZhTwStrings.escortPricingItem
            .replace('{name}', `${p.option.type} - ${p.option.target}`)
            .replace('{default}', String(p.defaultPriceTwd))
            .replace('{guildOverride}', overrideLine);
        });
        description = lines.join('\n\n');
      }
    } else {
      description = '護航定價資料暫時無法取得';
    }

    const embed = new EmbedBuilder()
      .setTitle(ZhTwStrings.escortPricingTitle)
      .setDescription(description)
      .setColor(Colors.WARNING);
    await interaction.editEmbed(embed);
  }
}
