import {
  type DiscordInteraction,
  type DiscordContext,
} from '@ltdjms/shared';
import { EmbedBuilder } from 'discord.js';
import { type InteractionHandler } from '../../../commands/infra/CommandHandler.js';
import { AdminPanelSessionManager } from '../../../session/AdminPanelSessionManager.js';
import { ZhTwStrings } from '../../../i18n/zh-TW.js';
import { type EscortOptionPricingService } from '@ltdjms/dispatch';

/**
 * Handler for escort pricing interactions (admin_escortprice_*).
 * Supports view pricing list, edit guild override, reset to default.
 */
export class EscortPricingHandler implements InteractionHandler {
  readonly customIdPrefix = 'admin_escortprice';

  constructor(
    private readonly sessionManager: AdminPanelSessionManager,
    private readonly pricingService: EscortOptionPricingService,
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

    // Try to get pricing list
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
