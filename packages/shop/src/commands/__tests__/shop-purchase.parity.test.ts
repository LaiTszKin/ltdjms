import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ok, err, DomainError, MockDiscordContext } from '@ltdjms/shared';
import { ShopCommandHandler } from '../shop-handler.js';
import { ProductService } from '../../services/product-service.js';
import {
  SELECT_BUY_PRODUCT,
  BUTTON_PAY_WITH_CURRENCY,
  BUTTON_PAY_WITH_FIAT,
  BUTTON_CONFIRM_PURCHASE,
  BUTTON_CANCEL_PURCHASE,
} from '../../view/shop-view.js';
import {
  ShopTestInteraction,
  createTestProduct,
} from '../../__tests__/helpers/shop-test-interaction.js';

describe('UT-308 ShopSelectMenuHandler purchase parity', () => {
  const guildId = '123456789';
  const userId = '987654321';
  const guildIdNum = 123456789;
  const productId = 100;

  let productService: ProductService;
  let balanceService: { tryGetBalance: ReturnType<typeof vi.fn> };
  let currencyPurchaseService: { purchaseProduct: ReturnType<typeof vi.fn> };
  let fiatOrderService: { createFiatOnlyOrder: ReturnType<typeof vi.fn> };
  let escortHandoff: {
    handoffFromCurrencyPurchase: ReturnType<typeof vi.fn>;
  };
  let adminNotification: { notifyAdminsOrderCreated: ReturnType<typeof vi.fn> };
  let buyerNotification: { notifyEscortOrderCreated: ReturnType<typeof vi.fn> };
  let discordGateway: { requireReadyClient: ReturnType<typeof vi.fn> };
  let userSend: ReturnType<typeof vi.fn>;
  let handler: ShopCommandHandler;

  beforeEach(() => {
    productService = {
      getProduct: vi.fn(),
    } as unknown as ProductService;
    balanceService = { tryGetBalance: vi.fn() };
    currencyPurchaseService = { purchaseProduct: vi.fn() };
    fiatOrderService = { createFiatOnlyOrder: vi.fn() };
    escortHandoff = { handoffFromCurrencyPurchase: vi.fn() };
    adminNotification = { notifyAdminsOrderCreated: vi.fn() };
    buyerNotification = { notifyEscortOrderCreated: vi.fn() };
    userSend = vi.fn().mockResolvedValue(undefined);
    discordGateway = {
      requireReadyClient: vi.fn().mockReturnValue({
        users: {
          fetch: vi.fn().mockResolvedValue({ send: userSend }),
        },
      }),
    };

    handler = new ShopCommandHandler(
      {} as never,
      productService,
      balanceService as never,
      currencyPurchaseService as never,
      fiatOrderService as never,
      {
        handoffFromFiatPayment: vi.fn(),
        handoffFromCurrencyPurchase: escortHandoff.handoffFromCurrencyPurchase,
      },
      adminNotification as never,
      buyerNotification as never,
      discordGateway as never,
    );
  });

  it('dual-price select shows payment choice', async () => {
    const product = createTestProduct({
      id: productId,
      guildId: guildIdNum,
      currencyPrice: 100,
      fiatPriceTwd: 500,
    });
    vi.mocked(productService.getProduct).mockResolvedValue(product);

    const interaction = new ShopTestInteraction(guildId, userId, {
      customId: SELECT_BUY_PRODUCT,
      selectedValues: [String(productId)],
    });
    await handler.handleInteraction(
      interaction,
      new MockDiscordContext(guildId, userId, '1', `<@${userId}>`),
      SELECT_BUY_PRODUCT,
    );

    expect(interaction.getEditedComponents().length).toBeGreaterThan(0);
  });

  it('currency-only select shows confirm embed', async () => {
    const product = createTestProduct({ id: productId, guildId: guildIdNum, currencyPrice: 100 });
    vi.mocked(productService.getProduct).mockResolvedValue(product);
    balanceService.tryGetBalance.mockResolvedValue(ok({ balance: 500 }));

    const interaction = new ShopTestInteraction(guildId, userId, {
      customId: SELECT_BUY_PRODUCT,
      selectedValues: [String(productId)],
    });
    await handler.handleInteraction(
      interaction,
      new MockDiscordContext(guildId, userId, '1', `<@${userId}>`),
      SELECT_BUY_PRODUCT,
    );

    expect(interaction.getEditEmbedCount()).toBe(1);
    expect(interaction.getEditedComponents()[0]).toBeDefined();
  });

  it('cancel purchase replies with cancel message', async () => {
    const interaction = new ShopTestInteraction(guildId, userId, {
      customId: BUTTON_CANCEL_PURCHASE,
    });
    await handler.handleInteraction(
      interaction,
      new MockDiscordContext(guildId, userId, '1', `<@${userId}>`),
      BUTTON_CANCEL_PURCHASE,
    );
    expect(interaction.getReplyMessages()[0]).toBe('已取消購買');
  });

  it('confirm purchase completes currency flow', async () => {
    const product = createTestProduct({ id: productId, guildId: guildIdNum, currencyPrice: 100 });
    currencyPurchaseService.purchaseProduct.mockResolvedValue(
      ok({
        product,
        previousBalance: 500,
        newBalance: 400,
        price: 100,
        rewardMessage: '',
      }),
    );

    const interaction = new ShopTestInteraction(guildId, userId, {
      customId: `${BUTTON_CONFIRM_PURCHASE}${productId}`,
    });
    await handler.handleInteraction(
      interaction,
      new MockDiscordContext(guildId, userId, '1', `<@${userId}>`),
      `${BUTTON_CONFIRM_PURCHASE}${productId}`,
    );

    expect(currencyPurchaseService.purchaseProduct).toHaveBeenCalledWith(
      guildIdNum,
      userId,
      productId,
    );
    expect(interaction.getReplyMessages()[0]).toContain('購買成功');
  });

  it('fiat-only select defers and summarizes order', async () => {
    const product = createTestProduct({
      id: productId,
      guildId: guildIdNum,
      currencyPrice: null,
      fiatPriceTwd: 1200,
    });
    vi.mocked(productService.getProduct).mockResolvedValue(product);
    fiatOrderService.createFiatOnlyOrder.mockResolvedValue(
      ok({
        product,
        orderNumber: 'FD260409000001',
        paymentNo: 'ABC123456789',
        expireDate: '2026/04/12 23:59:59',
        paymentUrl: 'https://example.com/pay',
        fulfillmentWarning: null,
      }),
    );

    const interaction = new ShopTestInteraction(guildId, userId, {
      customId: SELECT_BUY_PRODUCT,
      selectedValues: [String(productId)],
    });
    await handler.handleInteraction(
      interaction,
      new MockDiscordContext(guildId, userId, '1', `<@${userId}>`),
      SELECT_BUY_PRODUCT,
    );

    expect(interaction.hasDeferred()).toBe(true);
    expect(interaction.getReplyMessages()[0]).toContain('法幣訂單已建立');
  });

  it('fiat-only select sends DM with payment URL on success', async () => {
    const product = createTestProduct({
      id: productId,
      guildId: guildIdNum,
      currencyPrice: null,
      fiatPriceTwd: 1200,
    });
    const paymentUrl = 'https://example.com/pay';
    vi.mocked(productService.getProduct).mockResolvedValue(product);
    fiatOrderService.createFiatOnlyOrder.mockResolvedValue(
      ok({
        product,
        orderNumber: 'FD260409000001',
        paymentNo: 'ABC123456789',
        expireDate: '2026/04/12 23:59:59',
        paymentUrl,
        fulfillmentWarning: null,
      }),
    );

    const interaction = new ShopTestInteraction(guildId, userId, {
      customId: SELECT_BUY_PRODUCT,
      selectedValues: [String(productId)],
    });
    await handler.handleInteraction(
      interaction,
      new MockDiscordContext(guildId, userId, '1', `<@${userId}>`),
      SELECT_BUY_PRODUCT,
    );

    expect(userSend).toHaveBeenCalledWith(expect.stringContaining(paymentUrl));
    expect(interaction.getReplyMessages()[0]).toContain('完整付款資訊也已私訊給你');
  });

  it('fiat-only select shows fallback when DM fails', async () => {
    const product = createTestProduct({
      id: productId,
      guildId: guildIdNum,
      currencyPrice: null,
      fiatPriceTwd: 1200,
    });
    vi.mocked(productService.getProduct).mockResolvedValue(product);
    discordGateway.requireReadyClient.mockReturnValue({
      users: {
        fetch: vi.fn().mockRejectedValue(new Error('DM disabled')),
      },
    });
    fiatOrderService.createFiatOnlyOrder.mockResolvedValue(
      ok({
        product,
        orderNumber: 'FD260409000002',
        paymentNo: 'ABC999999999',
        expireDate: '2026/04/12 23:59:59',
        paymentUrl: 'https://example.com/pay',
        fulfillmentWarning: null,
      }),
    );

    const interaction = new ShopTestInteraction(guildId, userId, {
      customId: SELECT_BUY_PRODUCT,
      selectedValues: [String(productId)],
    });
    await handler.handleInteraction(
      interaction,
      new MockDiscordContext(guildId, userId, '1', `<@${userId}>`),
      SELECT_BUY_PRODUCT,
    );

    expect(interaction.getReplyMessages()[0]).toContain('無法私訊你');
    expect(interaction.getReplyMessages()[0]).toContain('FD260409000002');
    expect(interaction.getReplyMessages()[0]).toContain('ABC999999999');
  });

  it('inflight fiat guard blocks duplicate keys', () => {
    const key = `${guildIdNum}:${userId}:${productId}`;
    expect(handler.inflightFiatOrders.has(key)).toBe(false);
    handler.inflightFiatOrders.add(key);
    expect(handler.inflightFiatOrders.has(key)).toBe(true);
  });

  it('inflight fiat dedup replies with processing message', async () => {
    const product = createTestProduct({
      id: productId,
      guildId: guildIdNum,
      currencyPrice: null,
      fiatPriceTwd: 1200,
    });
    vi.mocked(productService.getProduct).mockResolvedValue(product);
    handler.inflightFiatOrders.add(`${guildIdNum}:${userId}:${productId}`);

    const interaction = new ShopTestInteraction(guildId, userId, {
      customId: SELECT_BUY_PRODUCT,
      selectedValues: [String(productId)],
    });
    await handler.handleInteraction(
      interaction,
      new MockDiscordContext(guildId, userId, '1', `<@${userId}>`),
      SELECT_BUY_PRODUCT,
    );

    expect(interaction.getReplyMessages()[0]).toContain('這筆法幣訂單正在處理中');
    expect(fiatOrderService.createFiatOnlyOrder).not.toHaveBeenCalled();
  });

  it('auto escort purchase notifies buyer and admin', async () => {
    const product = createTestProduct({
      id: productId,
      guildId: guildIdNum,
      currencyPrice: 100,
      autoCreateEscortOrder: true,
      escortOptionCode: 'escort-a',
    });
    currencyPurchaseService.purchaseProduct.mockResolvedValue(
      ok({
        product,
        previousBalance: 500,
        newBalance: 400,
        price: 100,
        rewardMessage: '',
      }),
    );
    escortHandoff.handoffFromCurrencyPurchase.mockResolvedValue({
      isOk: () => true,
      getError: () => ({ message: '' }),
      getValue: () => ({
        guildId: guildIdNum,
        customerUserId: Number(userId),
        orderNumber: 'ESC-1',
      }),
    });

    const interaction = new ShopTestInteraction(guildId, userId, {
      customId: `${BUTTON_CONFIRM_PURCHASE}${productId}`,
    });
    await handler.handleInteraction(
      interaction,
      new MockDiscordContext(guildId, userId, '1', `<@${userId}>`),
      `${BUTTON_CONFIRM_PURCHASE}${productId}`,
    );

    expect(buyerNotification.notifyEscortOrderCreated).toHaveBeenCalled();
    expect(adminNotification.notifyAdminsOrderCreated).toHaveBeenCalled();
    expect(escortHandoff.handoffFromCurrencyPurchase).toHaveBeenCalledWith(
      guildIdNum,
      Number(userId),
      product,
      expect.any(String),
    );
  });

  it('pay with currency button shows confirm embed', async () => {
    const product = createTestProduct({
      id: productId,
      guildId: guildIdNum,
      currencyPrice: 100,
      fiatPriceTwd: 500,
    });
    vi.mocked(productService.getProduct).mockResolvedValue(product);
    balanceService.tryGetBalance.mockResolvedValue(ok({ balance: 500 }));

    const interaction = new ShopTestInteraction(guildId, userId, {
      customId: `${BUTTON_PAY_WITH_CURRENCY}${productId}`,
    });
    await handler.handleInteraction(
      interaction,
      new MockDiscordContext(guildId, userId, '1', `<@${userId}>`),
      `${BUTTON_PAY_WITH_CURRENCY}${productId}`,
    );

    expect(interaction.getEditEmbedCount()).toBe(1);
  });

  it('pay with fiat button triggers deferred fiat flow', async () => {
    const product = createTestProduct({
      id: productId,
      guildId: guildIdNum,
      currencyPrice: 100,
      fiatPriceTwd: 500,
    });
    vi.mocked(productService.getProduct).mockResolvedValue(product);
    fiatOrderService.createFiatOnlyOrder.mockResolvedValue(
      ok({
        product,
        orderNumber: 'FD260409000001',
        paymentNo: 'ABC123456789',
        expireDate: null,
        paymentUrl: null,
        fulfillmentWarning: null,
      }),
    );

    const interaction = new ShopTestInteraction(guildId, userId, {
      customId: `${BUTTON_PAY_WITH_FIAT}${productId}`,
    });
    await handler.handleInteraction(
      interaction,
      new MockDiscordContext(guildId, userId, '1', `<@${userId}>`),
      `${BUTTON_PAY_WITH_FIAT}${productId}`,
    );

    expect(interaction.hasDeferred()).toBe(true);
    expect(fiatOrderService.createFiatOnlyOrder).toHaveBeenCalled();
  });

  it('confirm purchase failure surfaces domain error', async () => {
    currencyPurchaseService.purchaseProduct.mockResolvedValue(
      err(DomainError.insufficientBalance('餘額不足')),
    );

    const interaction = new ShopTestInteraction(guildId, userId, {
      customId: `${BUTTON_CONFIRM_PURCHASE}${productId}`,
    });
    await handler.handleInteraction(
      interaction,
      new MockDiscordContext(guildId, userId, '1', `<@${userId}>`),
      `${BUTTON_CONFIRM_PURCHASE}${productId}`,
    );

    expect(interaction.getReplyMessages()[0]).toBe('購買失敗：餘額不足');
  });
});
