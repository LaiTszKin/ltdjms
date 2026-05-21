import { type DiscordInteraction, type DiscordContext } from '@ltdjms/shared';
import { ShopService, type ShopPage } from '../services/shop.service.js';
import { FiatOrderService, formatFiatOrderDMMessage } from '../services/fiat-order.service.js';
import { CurrencyPurchaseService, formatPurchaseSuccessMessage } from '../services/currency-purchase.service.js';
import type { ProductRepository } from '../domain/product-types.js';
import {
  ModalBuilder,
  TextInputBuilder,
  ActionRowBuilder,
  TextInputStyle,
  StringSelectMenuBuilder,
  StringSelectMenuOptionBuilder,
} from 'discord.js';
import {
  buildShopEmbed,
  buildEmptyShopEmbed,
  buildShopComponents,
  buildSearchComponents,
  buildSearchResultEmbed,
  buildPaymentMethodChoiceEmbed,
  buildPurchaseConfirmEmbed,
  decodeKeyword,
  BUTTON_PREV_PAGE,
  BUTTON_NEXT_PAGE,
  BUTTON_BUY,
  SELECT_BUY_PRODUCT,
  BUTTON_SEARCH,
  BUTTON_PAY_WITH_CURRENCY,
  BUTTON_PAY_WITH_FIAT,
  BUTTON_BACK_TO_SHOP,
  BUTTON_SEARCH_PREV,
  BUTTON_SEARCH_NEXT,
  MODAL_SEARCH,
  SELECT_SEARCH_BUY,
} from '../view/shop-view.js';

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

  constructor(
    private readonly shopService: ShopService,
    private readonly fiatOrderService: FiatOrderService,
    private readonly currencyPurchaseService: CurrencyPurchaseService,
    private readonly productRepository: ProductRepository,
  ) {}

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
      await interaction.editEmbed(buildEmptyShopEmbed());
      return;
    }

    const embed = buildShopEmbed(page.products, page.currentPage, page.totalPages);
    const components = buildShopComponents(page.currentPage, page.totalPages);
    await this.editWithComponents(interaction, embed, components);
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

    // Buy: show product selection via select menu
    if (customId === BUTTON_BUY) {
      await this.showBuySelection(interaction, guildId);
      return;
    }

    // Buy select menu: user picked a product (from main shop view)
    if (customId === SELECT_BUY_PRODUCT) {
      const raw = this.getRaw(interaction);
      const productIdStr = raw.values?.[0];
      if (!productIdStr) {
        await interaction.reply('無法取得商品資訊');
        return;
      }
      const productId = parseInt(productIdStr, 10);
      if (isNaN(productId)) {
        await interaction.reply('商品編號無效');
        return;
      }
      await this.showPaymentChoice(interaction, guildId, productId);
      return;
    }

    // Search buy select menu: user picked a product from search results
    if (customId === SELECT_SEARCH_BUY) {
      const raw = this.getRaw(interaction);
      const productIdStr = raw.values?.[0];
      if (!productIdStr) {
        await interaction.reply('無法取得商品資訊');
        return;
      }
      const productId = parseInt(productIdStr, 10);
      if (isNaN(productId)) {
        await interaction.reply('商品編號無效');
        return;
      }
      await this.showPaymentChoice(interaction, guildId, productId);
      return;
    }

    // Payment method: currency
    if (customId.startsWith(BUTTON_PAY_WITH_CURRENCY)) {
      const productId = parseInt(customId.replace(BUTTON_PAY_WITH_CURRENCY, ''), 10);
      if (isNaN(productId)) return;
      await this.showCurrencyPurchaseConfirm(interaction, guildId, productId);
      return;
    }

    // Payment method: fiat (P2-14)
    if (customId.startsWith(BUTTON_PAY_WITH_FIAT)) {
      const productId = parseInt(customId.replace(BUTTON_PAY_WITH_FIAT, ''), 10);
      if (isNaN(productId)) return;
      if (!this.fiatOrderService) {
        await interaction.reply('法幣支付功能尚未啟用（缺少訂單服務）');
        return;
      }
      const userId = interaction.getUserId();
      const result = await this.fiatOrderService.createFiatOnlyOrder(guildId, userId, productId);
      if (result.isErr()) {
        await interaction.reply(`建立訂單失敗：${result.getError().message}`);
        return;
      }
      const orderResult = result.getValue();
      await interaction.reply(formatFiatOrderDMMessage(orderResult));
      return;
    }

    // Back to main shop
    if (customId === BUTTON_BACK_TO_SHOP) {
      const page = await this.shopService.getShopPage(guildId, 1);
      if (ShopCommandHandler.pageIsEmpty(page)) {
        await interaction.editEmbed(buildEmptyShopEmbed());
      } else {
        const embed = buildShopEmbed(page.products, page.currentPage, page.totalPages);
        const components = buildShopComponents(page.currentPage, page.totalPages);
        await this.editWithComponents(interaction, embed, components);
      }
      return;
    }

    // Search: opening the search modal (P2-13)
    if (customId === BUTTON_SEARCH) {
      const raw = this.getRaw(interaction);
      const modal = new ModalBuilder()
        .setCustomId(MODAL_SEARCH)
        .setTitle('搜尋商品')
        .addComponents(
          new ActionRowBuilder<TextInputBuilder>().addComponents(
            new TextInputBuilder()
              .setCustomId('shop_search_input')
              .setLabel('關鍵字')
              .setStyle(TextInputStyle.Short)
              .setMinLength(1)
              .setMaxLength(100)
              .setRequired(true),
          ),
        );
      await raw.showModal(modal);
      return;
    }

    // Search modal submit (P1-13)
    if (customId === MODAL_SEARCH) {
      const raw = this.getRaw(interaction);
      const keyword: string = raw.fields?.getTextInputValue('shop_search_input') ?? '';
      if (!keyword || keyword.trim().length === 0) {
        await interaction.reply('請輸入搜尋關鍵字');
        return;
      }
      const page = await this.shopService.searchProducts(guildId, keyword.trim(), 1);
      if (ShopCommandHandler.pageIsEmpty(page)) {
        await interaction.reply('找不到符合條件的商品');
        return;
      }
      const trimmedKeyword = keyword.trim();
      const embed = buildSearchResultEmbed(page.products, page.currentPage, page.totalPages, trimmedKeyword);
      const components = buildSearchComponents(page.currentPage, page.totalPages, trimmedKeyword, page.products);
      await this.editWithComponents(interaction, embed, components);
      return;
    }

    // Search pagination: previous page (P1-13)
    if (customId.startsWith(BUTTON_SEARCH_PREV)) {
      const parsed = this.parseSearchCustomId(customId, BUTTON_SEARCH_PREV);
      if (!parsed) return;
      const page = await this.shopService.searchProducts(guildId, parsed.keyword, parsed.pageNum);
      if (ShopCommandHandler.pageIsEmpty(page)) {
        await interaction.reply('此頁面沒有搜尋結果');
        return;
      }
      const embed = buildSearchResultEmbed(page.products, page.currentPage, page.totalPages, parsed.keyword);
      const components = buildSearchComponents(page.currentPage, page.totalPages, parsed.keyword, page.products);
      await this.editWithComponents(interaction, embed, components);
      return;
    }

    // Search pagination: next page (P1-13)
    if (customId.startsWith(BUTTON_SEARCH_NEXT)) {
      const parsed = this.parseSearchCustomId(customId, BUTTON_SEARCH_NEXT);
      if (!parsed) return;
      const page = await this.shopService.searchProducts(guildId, parsed.keyword, parsed.pageNum);
      if (ShopCommandHandler.pageIsEmpty(page)) {
        await interaction.reply('此頁面沒有搜尋結果');
        return;
      }
      const embed = buildSearchResultEmbed(page.products, page.currentPage, page.totalPages, parsed.keyword);
      const components = buildSearchComponents(page.currentPage, page.totalPages, parsed.keyword, page.products);
      await this.editWithComponents(interaction, embed, components);
      return;
    }

    // Unknown interaction
    await interaction.reply('未知的操作');
  }

  /**
   * Shows a specific page of products in the shop embed with pagination buttons.
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
    const components = buildShopComponents(page.currentPage, page.totalPages);
    await this.editWithComponents(interaction, embed, components);
  }

  /**
   * Shows a select menu for choosing a product to buy.
   * Loads all products across all pages and renders them as select menu options.
   */
  private async showBuySelection(
    interaction: DiscordInteraction,
    guildId: number,
  ): Promise<void> {
    // Load all products at once (large page size) so the select menu is not
    // restricted to the first page only.
    const page = await this.shopService.getShopPageWithSize(guildId, 1, 25);
    if (ShopCommandHandler.pageIsEmpty(page)) {
      await interaction.reply('目前沒有可購買的商品');
      return;
    }

    // Build select menu options from all products on the current page
    // Discord enforces a maximum of 25 options per select menu (P0-5)
    const options = page.products.slice(0, 25).map((product) => {
      return new StringSelectMenuOptionBuilder()
        .setLabel(product.name.length > 100 ? product.name.substring(0, 97) + '...' : product.name)
        .setValue(String(product.id))
        .setDescription(
          product.fiatPriceTwd
            ? `NT$${product.fiatPriceTwd}`
            : product.currencyPrice
              ? `${product.currencyPrice} 貨幣`
              : '可購買',
        );
    });

    const selectMenu = new StringSelectMenuBuilder()
      .setCustomId(SELECT_BUY_PRODUCT)
      .setPlaceholder('選擇要購買的商品')
      .addOptions(options);

    const row = new ActionRowBuilder<StringSelectMenuBuilder>().addComponents(selectMenu);

    const hook = this.getRaw(interaction);
    if (hook) {
      await hook.editReply({ content: '請選擇一個商品：', components: [row] });
    } else {
      await interaction.reply('請選擇一個商品：');
    }
  }

  /**
   * Shows the payment method choice for a specific product.
   * Called after the user selects a product from the buy select menu.
   */
  private async showPaymentChoice(
    interaction: DiscordInteraction,
    guildId: number,
    productId: number,
  ): Promise<void> {
    const product = await this.productRepository.findById(productId);
    if (!product || product.guildId !== guildId) {
      await interaction.reply('找不到此商品');
      return;
    }
    const embed = buildPaymentMethodChoiceEmbed(product);
    await interaction.editEmbed(embed);
  }

  /**
   * Shows the currency purchase confirmation.
   * Wires up CurrencyPurchaseService.purchaseProduct when available (P2-15).
   */
  private async showCurrencyPurchaseConfirm(
    interaction: DiscordInteraction,
    guildId: number,
    productId: number,
  ): Promise<void> {
    if (!this.currencyPurchaseService) {
      await interaction.reply('貨幣購買功能尚未啟用（缺少購買服務）');
      return;
    }
    const userId = interaction.getUserId();
        const result = await this.currencyPurchaseService.purchaseProduct(guildId, userId, productId);
    if (result.isErr()) {
      await interaction.reply(`購買失敗：${result.getError().message}`);
      return;
    }
    const purchaseResult = result.getValue();
    await interaction.reply(formatPurchaseSuccessMessage(purchaseResult));
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

  /**
   * Temporary helper to bypass DiscordInteraction interface limitations.
   * The shared DiscordInteraction interface does not expose all Discord.js raw API methods.
   * This provides access to the underlying interaction hook for features like
   * modals, select menu values, and editReply with components.
   * Once the shared layer extends DiscordInteraction, this can be removed.
   */
  private getRaw(interaction: DiscordInteraction): any {
    return interaction.getHook();
  }

  /**
   * Edits the interaction reply with an embed and action row components.
   * Uses the raw hook because the abstract DiscordInteraction.editEmbed only accepts an embed.
   */
  private async editWithComponents(
    interaction: DiscordInteraction,
    embed: unknown,
    components: unknown[],
  ): Promise<void> {
    const hook = this.getRaw(interaction);
    if (!hook) {
      await interaction.editEmbed(embed);
      return;
    }
    await hook.editReply({ embeds: [embed], components });
  }

  /**
   * Parses a search pagination customId to extract the keyword and page number.
   * Format: <prefix><base64keyword>_<pageNum>
   */
  private parseSearchCustomId(
    customId: string,
    prefix: string,
  ): { keyword: string; pageNum: number } | null {
    const rest = customId.substring(prefix.length);
    const lastUnderscore = rest.lastIndexOf('_');
    if (lastUnderscore < 0) return null;
    const encodedKeyword = rest.substring(0, lastUnderscore);
    const pageNum = parseInt(rest.substring(lastUnderscore + 1), 10);
    if (isNaN(pageNum)) return null;
    try {
      return { keyword: decodeKeyword(encodedKeyword), pageNum };
    } catch {
      return null;
    }
  }
}
