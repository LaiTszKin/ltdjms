import {
  type DiscordInteraction,
  type DiscordContext,
} from '@ltdjms/shared';
import { EmbedBuilder } from 'discord.js';
import { type InteractionHandler } from '../../../commands/infra/CommandHandler.js';
import { AdminPanelSessionManager } from '../../../session/AdminPanelSessionManager.js';
import { AdminPanelViewState } from '../../../session/types.js';
import { ZhTwStrings } from '../../../i18n/zh-TW.js';
import { type ShopService } from '@ltdjms/shop';

/**
 * Product-specific handler for the admin panel.
 * Manages the full product CRUD lifecycle with session state tracking.
 * Matches Java AdminProductPanelHandler.
 */
export class AdminProductPanelHandler implements InteractionHandler {
  readonly customIdPrefix = 'admin_product';

  constructor(
    private readonly sessionManager: AdminPanelSessionManager,
    private readonly shopService: ShopService,
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

    this.sessionManager.setViewState(guildId, userId, AdminPanelViewState.PRODUCT_LIST);

    // Try to get product list
    try {
      const shopPage = await this.shopService.getShopPage(Number(guildId), 1);

      let description: string;
      if (shopPage.products.length === 0) {
        description = ZhTwStrings.productListEmpty;
      } else {
        description = ZhTwStrings.productPageIndicator
          .replace('{current}', String(shopPage.currentPage))
          .replace('{total}', String(shopPage.totalPages))
          .replace('{count}', String(shopPage.products.length));
      }

      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.productListTitle)
        .setDescription(description)
        .setColor(0x5865F2);

      if (shopPage.products.length > 0) {
        for (const product of shopPage.products) {
          embed.addFields({
            name: product.name,
            value: `價格：${product.currencyPrice ?? 'N/A'} | 庫存：${product.description ?? '無描述'}`,
            inline: false,
          });
        }
      }

      await interaction.editEmbed(embed);
    } catch (err) {
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.productListTitle)
        .setDescription('產品資料暫時無法取得')
        .setColor(0x5865F2);
      await interaction.editEmbed(embed);
    }
  }
}
