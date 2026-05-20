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
import { type ShopService } from '@ltdjms/shop';

/**
 * Product-specific handler for the admin panel.
 * Manages the full product CRUD lifecycle with session state tracking.
 * Matches Java AdminProductPanelHandler.
 */
export class AdminProductPanelHandler extends BaseAdminHandler {
  readonly customIdPrefix = 'admin_product';

  constructor(
    sessionManager: AdminPanelSessionManager,
    private readonly shopService: ShopService,
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

    this.sessionManager.setViewState(guildId, userId, AdminPanelViewState.PRODUCT_LIST);

    const fullCustomId = interaction.getCustomId();

    // Branch on sub-action
    if (fullCustomId === 'admin_product_create') {
      // TODO: show create modal
      await this.showProductList(interaction, guildId);
      return;
    }
    if (fullCustomId === 'admin_product_prev' || fullCustomId === 'admin_product_next') {
      // TODO: implement pagination
      await this.showProductList(interaction, guildId);
      return;
    }
    if (fullCustomId === 'admin_product_back') {
      // Back to main state
      this.sessionManager.setViewState(guildId, userId, AdminPanelViewState.MAIN);
      return;
    }

    // Default: show product list
    await this.showProductList(interaction, guildId);
  }

  private async showProductList(
    interaction: DiscordInteraction,
    guildId: string,
  ): Promise<void> {
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
            value: `價格：${product.currencyPrice ?? 'N/A'} | 描述：${product.description ?? '無描述'}`,
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
