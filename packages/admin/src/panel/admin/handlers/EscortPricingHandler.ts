import {
  type DiscordInteraction,
  type DiscordContext,
} from '@ltdjms/shared';
import { EmbedBuilder } from 'discord.js';
import { AdminPanelSessionManager } from '../../../session/AdminPanelSessionManager.js';
import { AdminPanelViewState } from '../../../session/types.js';
import { BotErrorHandler } from '../../../commands/infra/BotErrorHandler.js';
import { ZhTwStrings } from '../../../i18n/zh-TW.js';
import { BaseAdminHandler } from '../BaseAdminHandler.js';
import { type EscortOptionPricingService } from '@ltdjms/dispatch';

/**
 * Handler for escort pricing interactions (admin_escortprice_*).
 * Supports view pricing list, edit guild override, reset to default.
 */
export class EscortPricingHandler extends BaseAdminHandler {
  readonly customIdPrefix = 'admin_escortprice';

  constructor(
    sessionManager: AdminPanelSessionManager,
    private readonly pricingService: EscortOptionPricingService,
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

    // Branch on sub-action
    if (fullCustomId.startsWith('admin_escortprice_edit')) {
      // TODO: show modal for editing price
      await this.showPricingList(interaction, guildId);
      return;
    }
    if (fullCustomId.startsWith('admin_escortprice_reset')) {
      // TODO: reset price to default
      await this.showPricingList(interaction, guildId);
      return;
    }

    // Default: show pricing list
    await this.showPricingList(interaction, guildId);
  }

  private async showPricingList(
    interaction: DiscordInteraction,
    guildId: string,
  ): Promise<void> {
    const result = await this.pricingService.listOptionPrices(Number(guildId));

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
      .setColor(0xFEE75C);
    await interaction.editEmbed(embed);
  }
}
