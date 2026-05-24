import {
  type DiscordInteraction,
  type DiscordContext,
  type DiscordRuntimeGateway,
} from '@ltdjms/shared';
import { ShopService } from '../services/shop.service.js';
import {
  FiatOrderService,
  formatFiatOrderDMMessage,
  type FiatOrderResult,
} from '../services/fiat-order.service.js';
import {
  CurrencyPurchaseService,
  formatPurchaseSuccessMessage,
} from '../services/currency-purchase.service.js';
import { ProductService } from '../services/product-service.js';
import {
  type Product,
  hasCurrencyPrice,
  hasFiatPriceTwd,
  shouldAutoCreateEscortOrder,
} from '../domain/product-types.js';
import type { EscortDispatchHandoffService } from '../domain/escort-dispatch-handoff-service.js';
import { EscortOrderBuyerNotificationService } from '../services/escort-order-buyer-notification.service.js';
import { ShopAdminNotificationService } from '../services/shop-admin-notification.service.js';
import type { BalanceService } from '../di/shop-module.js';
import {
  buildShopEmbed,
  buildEmptyShopEmbed,
  buildShopComponents,
  buildSearchResultComponents,
  buildBuyMenu,
  buildPaymentMethodChoiceEmbed,
  buildPaymentMethodChoiceComponents,
  buildPurchaseConfirmEmbed,
  buildPurchaseConfirmComponents,
  buildSearchModal,
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
  BUTTON_CONFIRM_PURCHASE,
  BUTTON_CANCEL_PURCHASE,
} from '../view/shop-view.js';

interface DiscordUserLike {
  id: string;
  send(message: string): Promise<unknown>;
}

interface DiscordClientLike {
  users: {
    fetch(userId: string): Promise<DiscordUserLike>;
  };
}

/**
 * Handler for the /shop slash command and shop_* component interactions.
 * Mirrors Java ShopCommandHandler, ShopButtonHandler, and ShopSelectMenuHandler.
 */
export class ShopCommandHandler {
  readonly commandName = 'shop';
  readonly inflightFiatOrders = new Set<string>();

  constructor(
    private readonly shopService: ShopService,
    private readonly productService: ProductService,
    private readonly balanceService: BalanceService,
    private readonly currencyPurchaseService: CurrencyPurchaseService,
    private readonly fiatOrderService: FiatOrderService,
    private readonly escortDispatchHandoffService: EscortDispatchHandoffService,
    private readonly adminNotificationService: ShopAdminNotificationService,
    private readonly escortOrderBuyerNotificationService: EscortOrderBuyerNotificationService,
    private readonly discordRuntimeGateway: DiscordRuntimeGateway,
  ) {}

  async execute(interaction: DiscordInteraction, _context: DiscordContext): Promise<void> {
    interaction.makeEphemeral();

    const guildId = this.parseGuildId(interaction.getGuildId());
    if (guildId == null) {
      await interaction.reply('此功能只能在伺服器中使用');
      return;
    }

    try {
      const shopPage = await this.shopService.getShopPage(guildId, 0);

      if (shopPage.isEmpty()) {
        await interaction.replyEmbed(buildEmptyShopEmbed());
        return;
      }

      const embed = buildShopEmbed(shopPage.products, shopPage.currentPage, shopPage.totalPages);
      const components = buildShopComponents(
        shopPage.currentPage,
        shopPage.totalPages,
        true,
      );
      await interaction.replyWithComponents(embed, components);
    } catch {
      await interaction.reply('發生錯誤，請稍後再試');
    }
  }

  async handleInteraction(
    interaction: DiscordInteraction,
    _context: DiscordContext,
    customId: string,
  ): Promise<void> {
    interaction.makeEphemeral();

    if (customId === BUTTON_SEARCH) {
      await this.handleSearchButton(interaction);
      return;
    }

    if (customId === MODAL_SEARCH) {
      await this.handleSearchModalSubmit(interaction);
      return;
    }

    if (this.isPurchaseSelect(customId)) {
      await this.handlePurchaseSelect(interaction, customId);
      return;
    }

    if (customId.startsWith(BUTTON_PAY_WITH_CURRENCY)) {
      await this.handlePayWithCurrency(interaction, customId);
      return;
    }

    if (customId.startsWith(BUTTON_PAY_WITH_FIAT)) {
      await this.handlePayWithFiat(interaction, customId);
      return;
    }

    if (
      customId.startsWith(BUTTON_CONFIRM_PURCHASE) ||
      customId === BUTTON_CANCEL_PURCHASE
    ) {
      await this.handlePurchaseConfirmButton(interaction, customId);
      return;
    }

    if (!this.isShopBrowseButton(customId)) {
      return;
    }

    const guildId = this.parseGuildId(interaction.getGuildId());
    if (guildId == null) {
      await interaction.reply('此功能只能在伺服器中使用');
      return;
    }

    try {
      if (customId.startsWith(BUTTON_PREV_PAGE) || customId.startsWith(BUTTON_NEXT_PAGE)) {
        const page = this.parsePageFromButtonId(
          customId,
          customId.startsWith(BUTTON_PREV_PAGE) ? BUTTON_PREV_PAGE : BUTTON_NEXT_PAGE,
        );
        await this.showShopPage(interaction, guildId, page);
        return;
      }

      if (customId === BUTTON_BUY) {
        await this.showBuyMenu(interaction, guildId);
        return;
      }

      if (customId === BUTTON_BACK_TO_SHOP) {
        await this.showShopPage(interaction, guildId, 1);
        return;
      }

      if (customId.startsWith(BUTTON_SEARCH_PREV) || customId.startsWith(BUTTON_SEARCH_NEXT)) {
        await this.handleSearchPagination(
          interaction,
          guildId,
          customId,
          customId.startsWith(BUTTON_SEARCH_PREV) ? BUTTON_SEARCH_PREV : BUTTON_SEARCH_NEXT,
        );
      }
    } catch {
      await interaction.reply('發生錯誤，請稍後再試');
    }
  }

  private async handleSearchButton(interaction: DiscordInteraction): Promise<void> {
    const guildId = this.parseGuildId(interaction.getGuildId());
    if (guildId == null) {
      await interaction.reply('此功能只能在伺服器中使用');
      return;
    }

    await interaction.showModal(buildSearchModal());
  }

  private async handleSearchModalSubmit(interaction: DiscordInteraction): Promise<void> {
    const guildId = this.parseGuildId(interaction.getGuildId());
    if (guildId == null) {
      await interaction.reply('此功能只能在伺服器中使用');
      return;
    }

    const keyword = interaction.getTextInputValue('keyword');
    if (!keyword || keyword.trim().length === 0) {
      await interaction.reply('請輸入有效的關鍵字');
      return;
    }

    try {
      const searchResults = await this.shopService.searchProducts(guildId, keyword.trim(), 0);
      if (searchResults.isEmpty()) {
        await interaction.reply(`找不到符合「${keyword}」的商品`);
        return;
      }

      const embed = buildShopEmbed(
        searchResults.products,
        searchResults.currentPage,
        searchResults.totalPages,
      );
      const components = buildSearchResultComponents(
        searchResults.currentPage,
        searchResults.totalPages,
        keyword.trim(),
        searchResults.products,
      );
      await interaction.replyWithComponents(embed, components);
    } catch {
      await interaction.reply('搜尋發生錯誤，請稍後再試');
    }
  }

  private async showShopPage(
    interaction: DiscordInteraction,
    guildId: number,
    pageOneBased: number,
  ): Promise<void> {
    const shopPage = await this.shopService.getShopPage(guildId, pageOneBased - 1);

    if (shopPage.isEmpty()) {
      await interaction.editWithComponents(buildEmptyShopEmbed(), []);
      return;
    }

    const embed = buildShopEmbed(shopPage.products, shopPage.currentPage, shopPage.totalPages);
    const components = buildShopComponents(shopPage.currentPage, shopPage.totalPages, true);
    await interaction.editWithComponents(embed, components);
  }

  private async showBuyMenu(interaction: DiscordInteraction, guildId: number): Promise<void> {
    const allProducts = await this.productService.getAllPurchasableProducts(guildId);
    if (allProducts.length === 0) {
      await interaction.reply('目前沒有可購買的商品');
      return;
    }

    const buyRows = buildBuyMenu(allProducts);
    await this.replyWithMessageAndComponents(interaction, '請選擇要購買的商品', buyRows);
  }

  private async replyWithMessageAndComponents(
    interaction: DiscordInteraction,
    content: string,
    components: unknown[],
  ): Promise<void> {
    const raw = interaction.getHook() as
      | { reply?: (options: Record<string, unknown>) => Promise<unknown> }
      | null;
    if (raw?.reply) {
      const opts: Record<string, unknown> = { content, components };
      if (interaction.isEphemeral()) {
        opts.ephemeral = true;
      }
      await raw.reply(opts);
      return;
    }
    await interaction.reply(content);
  }

  private async handleSearchPagination(
    interaction: DiscordInteraction,
    guildId: number,
    customId: string,
    prefix: string,
  ): Promise<void> {
    const parsed = this.parseSearchCustomId(customId, prefix);
    if (!parsed) {
      await interaction.reply('發生錯誤，請重新搜尋');
      return;
    }

    const searchResults = await this.shopService.searchProducts(
      guildId,
      parsed.keyword,
      parsed.pageOneBased - 1,
    );

    if (searchResults.isEmpty()) {
      await interaction.editWithComponents(buildEmptyShopEmbed(), []);
      return;
    }

    const embed = buildShopEmbed(
      searchResults.products,
      searchResults.currentPage,
      searchResults.totalPages,
    );
    const components = buildSearchResultComponents(
      searchResults.currentPage,
      searchResults.totalPages,
      parsed.keyword,
      searchResults.products,
    );
    await interaction.editWithComponents(embed, components);
  }

  private async handlePurchaseSelect(
    interaction: DiscordInteraction,
    customId: string,
  ): Promise<void> {
    const guildId = this.parseGuildId(interaction.getGuildId());
    if (guildId == null) {
      await interaction.reply('此功能只能在伺服器中使用');
      return;
    }

    const userId = interaction.getUserId();

    try {
      const values = interaction.getSelectedValues();
      if (values.length === 0) {
        await interaction.reply('請先選擇商品');
        return;
      }

      const productId = parseInt(values[0], 10);
      if (Number.isNaN(productId)) {
        await interaction.reply('商品編號無效');
        return;
      }

      const product = await this.productService.getProduct(productId);
      if (!product) {
        await interaction.reply('找不到該商品');
        return;
      }

      const hasCurrency = hasCurrencyPrice(product);
      const hasFiat = hasFiatPriceTwd(product);

      if (hasCurrency && hasFiat) {
        const embed = buildPaymentMethodChoiceEmbed(product);
        const components = buildPaymentMethodChoiceComponents(product);
        await interaction.editWithComponents(embed, components);
        return;
      }

      if (hasCurrency) {
        await this.showPurchaseConfirm(interaction, product, guildId, userId, productId);
        return;
      }

      if (hasFiat) {
        await this.startDeferredFiatOrder(interaction, guildId, userId, productId);
        return;
      }

      await interaction.reply('此商品暫無可用的購買方式');
    } catch {
      await interaction.reply('發生錯誤，請稍後再試');
    }
  }

  private async handlePayWithCurrency(
    interaction: DiscordInteraction,
    customId: string,
  ): Promise<void> {
    const guildId = this.parseGuildId(interaction.getGuildId());
    if (guildId == null) {
      await interaction.reply('此功能只能在伺服器中使用');
      return;
    }

    try {
      const productId = parseInt(customId.replace(BUTTON_PAY_WITH_CURRENCY, ''), 10);
      const product = await this.productService.getProduct(productId);
      if (!product) {
        await interaction.reply('找不到該商品');
        return;
      }

      await this.showPurchaseConfirm(
        interaction,
        product,
        guildId,
        interaction.getUserId(),
        productId,
      );
    } catch {
      await interaction.reply('發生錯誤，請稍後再試');
    }
  }

  private async handlePayWithFiat(
    interaction: DiscordInteraction,
    customId: string,
  ): Promise<void> {
    const guildId = this.parseGuildId(interaction.getGuildId());
    if (guildId == null) {
      await interaction.reply('此功能只能在伺服器中使用');
      return;
    }

    try {
      const productId = parseInt(customId.replace(BUTTON_PAY_WITH_FIAT, ''), 10);
      await this.startDeferredFiatOrder(interaction, guildId, interaction.getUserId(), productId);
    } catch {
      await interaction.reply('發生錯誤，請稍後再試');
    }
  }

  private async handlePurchaseConfirmButton(
    interaction: DiscordInteraction,
    customId: string,
  ): Promise<void> {
    const guildId = this.parseGuildId(interaction.getGuildId());
    if (guildId == null) {
      await interaction.reply('此功能只能在伺服器中使用');
      return;
    }

    try {
      if (customId === BUTTON_CANCEL_PURCHASE) {
        await interaction.reply('已取消購買');
        return;
      }

      const productId = parseInt(customId.replace(BUTTON_CONFIRM_PURCHASE, ''), 10);
      const userId = interaction.getUserId();
      const purchaseResult = await this.currencyPurchaseService.purchaseProduct(
        guildId,
        userId,
        productId,
      );

      if (purchaseResult.isErr()) {
        await interaction.reply(`購買失敗：${purchaseResult.getError().message}`);
        return;
      }

      let successMessage = formatPurchaseSuccessMessage(purchaseResult.getValue());
      const purchasedProduct = purchaseResult.getValue().product;

      if (shouldAutoCreateEscortOrder(purchasedProduct)) {
        const handoffResult = await this.escortDispatchHandoffService.handoffFromCurrencyPurchase(
          guildId,
          this.parseUserIdNumber(userId),
          purchasedProduct,
          this.resolveInteractionId(interaction),
        );

        if (handoffResult.isOk()) {
          const dispatchOrder = handoffResult.getValue();
          this.escortOrderBuyerNotificationService.notifyEscortOrderCreated(dispatchOrder);
          this.adminNotificationService.notifyAdminsOrderCreated(
            guildId,
            this.parseUserIdNumber(userId),
            dispatchOrder,
          );
        } else {
          successMessage += '\n\n⚠️ 自動護航單建立失敗，請稍後通知管理員。';
        }
      }

      await interaction.reply(successMessage);
    } catch {
      await interaction.reply('發生錯誤，請稍後再試');
    }
  }

  private async showPurchaseConfirm(
    interaction: DiscordInteraction,
    product: Product,
    guildId: number,
    userId: string,
    productId: number,
  ): Promise<void> {
    const balanceResult = await this.balanceService.tryGetBalance(guildId, userId);
    const userBalance = balanceResult.isOk() ? balanceResult.getValue().balance : 0;
    const embed = buildPurchaseConfirmEmbed(product, userBalance);
    const components = buildPurchaseConfirmComponents(productId);
    await interaction.editWithComponents(embed, components);
  }

  private async startDeferredFiatOrder(
    interaction: DiscordInteraction,
    guildId: number,
    userId: string,
    productId: number,
  ): Promise<void> {
    const inflightKey = this.buildFiatOrderInflightKey(guildId, userId, productId);
    if (this.inflightFiatOrders.has(inflightKey)) {
      await interaction.reply('⚠️ 這筆法幣訂單正在處理中，請稍候檢查互動結果。');
      return;
    }
    this.inflightFiatOrders.add(inflightKey);

    await interaction.deferReply();

    try {
      await this.processDeferredFiatOrder(interaction, guildId, userId, productId, inflightKey);
    } catch {
      this.inflightFiatOrders.delete(inflightKey);
      await interaction.reply('發生錯誤，請稍後再試');
    }
  }

  private async processDeferredFiatOrder(
    interaction: DiscordInteraction,
    guildId: number,
    userId: string,
    productId: number,
    inflightKey: string,
  ): Promise<void> {
    const orderResult = await this.fiatOrderService.createFiatOnlyOrder(guildId, userId, productId);
    if (orderResult.isErr()) {
      this.inflightFiatOrders.delete(inflightKey);
      await interaction.reply(`下單失敗：${orderResult.getError().message}`);
      return;
    }

    const order = orderResult.getValue();
    const dmDelivered = await this.trySendFiatOrderDm(userId, order);
    const message = this.buildFiatOrderInteractionMessage(
      order,
      dmDelivered,
      dmDelivered ? null : '⚠️ 無法私訊你，請直接使用以下資訊付款。',
    );

    this.inflightFiatOrders.delete(inflightKey);
    await interaction.reply(message);
  }

  private async trySendFiatOrderDm(userId: string, order: FiatOrderResult): Promise<boolean> {
    try {
      const client = this.discordRuntimeGateway.requireReadyClient() as DiscordClientLike;
      const user = await client.users.fetch(userId);
      await user.send(formatFiatOrderDMMessage(order));
      return true;
    } catch {
      return false;
    }
  }

  private buildFiatOrderInteractionMessage(
    order: FiatOrderResult,
    dmDelivered: boolean,
    dmWarning: string | null,
  ): string {
    const sb: string[] = [];
    if (dmDelivered) {
      sb.push('✅ 法幣訂單已建立，完整付款資訊也已私訊給你。\n\n');
    } else {
      sb.push('✅ 法幣訂單已建立。\n');
      if (dmWarning && dmWarning.trim().length > 0) {
        sb.push(`${dmWarning}\n\n`);
      } else {
        sb.push('\n');
      }
    }
    sb.push(`**商品：** ${order.product.name}\n`);
    sb.push(`**訂單編號：** \`${order.orderNumber}\`\n`);
    sb.push(`**超商代碼：** \`${order.paymentNo}\`\n`);
    sb.push(`**金額：** ${order.product.fiatPriceTwd ? `NT$${order.product.fiatPriceTwd.toLocaleString()}` : ''}\n`);
    if (order.expireDate) {
      sb.push(`**繳費期限：** ${order.expireDate}\n`);
    }
    if (order.paymentUrl) {
      sb.push(`**繳費說明：** ${order.paymentUrl}\n`);
    }
    if (order.fulfillmentWarning) {
      sb.push(`${order.fulfillmentWarning}\n`);
    }
    sb.push('請在付款期限內完成付款，否則訂單將自動轉為逾期取消狀態。\n');
    sb.push('\n若需查詢訂單或回報付款，請提供訂單編號給管理員。');
    return sb.join('');
  }

  private isShopBrowseButton(customId: string): boolean {
    return (
      customId.startsWith(BUTTON_PREV_PAGE) ||
      customId.startsWith(BUTTON_NEXT_PAGE) ||
      customId === BUTTON_BUY ||
      customId === BUTTON_BACK_TO_SHOP ||
      customId.startsWith(BUTTON_SEARCH_PREV) ||
      customId.startsWith(BUTTON_SEARCH_NEXT)
    );
  }

  private isPurchaseSelect(customId: string): boolean {
    return customId === SELECT_BUY_PRODUCT || customId === SELECT_SEARCH_BUY;
  }

  private parsePageFromButtonId(customId: string, prefix: string): number {
    return parseInt(customId.substring(prefix.length), 10);
  }

  private parseGuildId(guildId: string): number | null {
    if (!guildId || guildId === '0') {
      return null;
    }
    const id = parseInt(guildId, 10);
    return Number.isNaN(id) ? null : id;
  }

  private parseUserIdNumber(userId: string): number {
    return parseInt(userId, 10);
  }

  private resolveInteractionId(interaction: DiscordInteraction): string {
    const hook = interaction.getHook() as { id?: string } | null;
    return hook?.id ?? 'interaction-unknown';
  }

  private buildFiatOrderInflightKey(
    guildId: number,
    userId: string,
    productId: number,
  ): string {
    return `${guildId}:${userId}:${productId}`;
  }

  private parseSearchCustomId(
    customId: string,
    prefix: string,
  ): { keyword: string; pageOneBased: number } | null {
    const rest = customId.substring(prefix.length);
    const lastUnderscore = rest.lastIndexOf('_');
    if (lastUnderscore < 0) return null;
    const encodedKeyword = rest.substring(0, lastUnderscore);
    const pageOneBased = parseInt(rest.substring(lastUnderscore + 1), 10);
    if (Number.isNaN(pageOneBased)) return null;
    try {
      return { keyword: decodeKeyword(encodedKeyword), pageOneBased };
    } catch {
      return null;
    }
  }
}
