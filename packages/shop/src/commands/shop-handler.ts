import { type DiscordInteraction, type DiscordContext } from '@ltdjms/shared';
import { ShopService, type ShopPage } from '../services/shop.service.js';
import {
  buildShopEmbed,
  buildEmptyShopEmbed,
  buildShopComponents,
  buildPaymentMethodChoiceEmbed,
  buildPurchaseConfirmEmbed,
  BUTTON_PREV_PAGE,
  BUTTON_NEXT_PAGE,
  BUTTON_BUY,
  BUTTON_SEARCH,
  BUTTON_PAY_WITH_CURRENCY,
  BUTTON_PAY_WITH_FIAT,
  BUTTON_BACK_TO_SHOP,
  BUTTON_SEARCH_PREV,
  BUTTON_SEARCH_NEXT,
  MODAL_SEARCH,
} from '../services/shop-view.js';

/**
 * Handler for the /shop slash command and its associated component interactions.
 *
 * Flow:
 *   /shop -> fetches first page -> shows embed + action row (prev/buy/search/next)
 *   Button clicks -> paginate, trigger buy flow, or open search modal
 *   Search modal submit -> shows search results with separate pagination
 *   Buy flow -> product selection -> payment method choice -> confirmation
 */
export class ShopCommandHandler {
  readonly commandName = 'shop';

  constructor(private readonly shopService: ShopService) {}

  /**
   * Handles the initial /shop slash command.
   * Fetches the first page of products and replies with an embed.
   */
  async execute(
    interaction: DiscordInteraction,
    _context: DiscordContext,
  ): Promise<void> {
    await interaction.deferReply();

    const guildId = this.parseGuildId(interaction.getGuildId());
    if (guildId == null) return;

    const page = await this.shopService.getShopPage(guildId, 1);

    if (ShopCommandHandler.pageIsEmpty(page)) {
      await interaction.replyEmbed(buildEmptyShopEmbed());
      return;
    }

    const embed = buildShopEmbed(page.products, page.currentPage, page.totalPages);
    await interaction.replyEmbed(embed);
  }

  /**
   * Handles component interactions (button clicks, select menus, modal submits)
   * for the shop panel. The customId determines which action to take.
   */
  async handleInteraction(
    interaction: DiscordInteraction,
    _context: DiscordContext,
    customId: string,
  ): Promise<void> {
    const guildId = this.parseGuildId(interaction.getGuildId());
    if (guildId == null) return;

    // Pagination: previous page
    if (customId.startsWith(BUTTON_PREV_PAGE)) {
      const pageNum = parseInt(customId.replace(BUTTON_PREV_PAGE, ''), 10);
      if (isNaN(pageNum)) return;
      await this.showPage(interaction, guildId, pageNum);
      return;
    }

    // Pagination: next page
    if (customId.startsWith(BUTTON_NEXT_PAGE)) {
      const pageNum = parseInt(customId.replace(BUTTON_NEXT_PAGE, ''), 10);
      if (isNaN(pageNum)) return;
      await this.showPage(interaction, guildId, pageNum);
      return;
    }

    // Buy: show product selection
    if (customId === BUTTON_BUY) {
      await this.showBuySelection(interaction, guildId);
      return;
    }

    // Payment method: currency
    if (customId.startsWith(BUTTON_PAY_WITH_CURRENCY)) {
      const productId = parseInt(customId.replace(BUTTON_PAY_WITH_CURRENCY, ''), 10);
      if (isNaN(productId)) return;
      await this.showCurrencyPurchaseConfirm(interaction, guildId, productId);
      return;
    }

    // Payment method: fiat
    if (customId.startsWith(BUTTON_PAY_WITH_FIAT)) {
      const productId = parseInt(customId.replace(BUTTON_PAY_WITH_FIAT, ''), 10);
      if (isNaN(productId)) return;
      await interaction.reply('法幣支付功能');
      return;
    }

    // Back to main shop
    if (customId === BUTTON_BACK_TO_SHOP) {
      const page = await this.shopService.getShopPage(guildId, 1);
      if (ShopCommandHandler.pageIsEmpty(page)) {
        await interaction.editEmbed(buildEmptyShopEmbed());
      } else {
        await interaction.editEmbed(buildShopEmbed(page.products, page.currentPage, page.totalPages));
      }
      return;
    }

    // Search: opening the search modal
    if (customId === BUTTON_SEARCH) {
      // Search requires a modal interaction, which needs to be handled via Discord.js directly.
      // At the abstraction level, we acknowledge and instruct the user.
      await interaction.reply('請使用搜尋功能。');
      return;
    }

    // Unknown interaction
    await interaction.reply('未知的操作');
  }

  /**
   * Shows a specific page of products in the shop embed.
   */
  private async showPage(
    interaction: DiscordInteraction,
    guildId: number,
    pageNum: number,
  ): Promise<void> {
    const page = await this.shopService.getShopPage(guildId, pageNum);
    if (ShopCommandHandler.pageIsEmpty(page)) {
      await interaction.reply('此頁面沒有商品');
      return;
    }
    const embed = buildShopEmbed(page.products, page.currentPage, page.totalPages);
    await interaction.editEmbed(embed);
  }

  /**
   * Shows the buy product selection interface.
   */
  private async showBuySelection(
    interaction: DiscordInteraction,
    guildId: number,
  ): Promise<void> {
    const page = await this.shopService.getShopPage(guildId, 1);
    if (ShopCommandHandler.pageIsEmpty(page)) {
      await interaction.reply('目前沒有可購買的商品');
      return;
    }

    // For now, show the first product's payment choice as a demonstration.
    // Full product selection via select menu requires extending the abstraction.
    const product = page.products[0];
    const embed = buildPaymentMethodChoiceEmbed(product);
    await interaction.replyEmbed(embed);
  }

  /**
   * Shows the currency purchase confirmation.
   */
  private async showCurrencyPurchaseConfirm(
    interaction: DiscordInteraction,
    guildId: number,
    productId: number,
  ): Promise<void> {
    // This requires a BalanceService to look up the user's balance.
    // For now, acknowledge the action.
    await interaction.reply(`正在處理商品 ${productId} 的貨幣購買...`);
  }

  /**
   * Parses the guild ID string to a number. Returns null on invalid input.
   */
  private parseGuildId(guildId: string): number | null {
    const id = parseInt(guildId, 10);
    if (isNaN(id)) {
      return null;
    }
    return id;
  }

  /**
   * Checks whether a shop page is empty.
   */
  private static pageIsEmpty(page: ShopPage): boolean {
    return page.products.length === 0;
  }
}
