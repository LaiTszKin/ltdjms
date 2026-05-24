import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ok, MockDiscordContext } from '@ltdjms/shared';
import {
  normalizeEmbedForSnapshot,
  assertEmbedParity,
} from '../../../../shared/src/__tests__/parity/json-snapshot.js';
import { ShopCommandHandler } from '../shop-handler.js';
import { ShopService } from '../../services/shop.service.js';
import { ProductService } from '../../services/product-service.js';
import oracle from '../../../../../docs/plans/2026-05-24/java-parity-shop-ai/shop-java-parity/fixtures/java-shop-view-oracle.json';
import {
  BUTTON_PREV_PAGE,
  BUTTON_BUY,
  BUTTON_SEARCH,
  BUTTON_BACK_TO_SHOP,
  BUTTON_SEARCH_PREV,
  BUTTON_SEARCH_NEXT,
  encodeKeyword,
} from '../../view/shop-view.js';
import {
  ShopTestInteraction,
  createTestProduct,
} from '../../__tests__/helpers/shop-test-interaction.js';

describe('UT-306 ShopCommandHandler parity', () => {
  const guildId = '123456789';
  const userId = '987654321';
  const guildIdNum = 123456789;

  let shopService: ShopService;
  let productService: ProductService;
  let handler: ShopCommandHandler;

  beforeEach(() => {
    shopService = {
      getShopPage: vi.fn(),
    } as unknown as ShopService;
    productService = {} as ProductService;
    handler = new ShopCommandHandler(
      shopService,
      productService,
      { tryGetBalance: vi.fn() } as never,
      { purchaseProduct: vi.fn() } as never,
      { createFiatOnlyOrder: vi.fn() } as never,
      { handoffFromFiatPayment: vi.fn(), handoffFromCurrencyPurchase: vi.fn() } as never,
      { notifyAdminsOrderCreated: vi.fn() } as never,
      { notifyEscortOrderCreated: vi.fn() } as never,
      { requireReadyClient: vi.fn() } as never,
    );
  });

  it('rejects non-guild interactions', async () => {
    const interaction = new ShopTestInteraction('0', userId, { interactionType: 'chatInput' });
    await handler.execute(interaction, new MockDiscordContext('0', userId, '1', `<@${userId}>`));
    expect(interaction.getReplyMessages()[0]).toBe('此功能只能在伺服器中使用');
  });

  it('/shop loads page index 0 and replies with embed + components', async () => {
    const product = createTestProduct({ guildId: guildIdNum });
    vi.mocked(shopService.getShopPage).mockResolvedValue({
      products: [product],
      currentPage: 1,
      totalPages: 1,
      isEmpty: () => false,
      hasPreviousPage: () => false,
      hasNextPage: () => false,
      formatPageIndicator: () => '共 1 個商品',
    });

    const interaction = new ShopTestInteraction(guildId, userId, { interactionType: 'chatInput' });
    await handler.execute(
      interaction,
      new MockDiscordContext(guildId, userId, '1', `<@${userId}>`),
    );

    expect(shopService.getShopPage).toHaveBeenCalledWith(guildIdNum, 0);
    expect(interaction.getReplyEmbedCount()).toBe(1);
    expect(interaction.getReplyComponents().length).toBeGreaterThan(0);
  });

  it('empty shop embed matches java-shop-view oracle', async () => {
    vi.mocked(shopService.getShopPage).mockResolvedValue({
      products: [],
      currentPage: 1,
      totalPages: 0,
      isEmpty: () => true,
      hasPreviousPage: () => false,
      hasNextPage: () => false,
      formatPageIndicator: () => '共 0 個商品',
    });

    const interaction = new ShopTestInteraction(guildId, userId, { interactionType: 'chatInput' });
    await handler.execute(
      interaction,
      new MockDiscordContext(guildId, userId, '1', `<@${userId}>`),
    );

    const embed = interaction.getReplyEmbeds()[0];
    assertEmbedParity(normalizeEmbedForSnapshot(embed), {
      title: oracle.scenarios.emptyShop.title,
      description: oracle.scenarios.emptyShop.description,
      color: embed.color,
    });
  });

  it('shows empty embed without components when catalog is empty', async () => {
    vi.mocked(shopService.getShopPage).mockResolvedValue({
      products: [],
      currentPage: 1,
      totalPages: 0,
      isEmpty: () => true,
      hasPreviousPage: () => false,
      hasNextPage: () => false,
      formatPageIndicator: () => '共 0 個商品',
    });

    const interaction = new ShopTestInteraction(guildId, userId, { interactionType: 'chatInput' });
    await handler.execute(
      interaction,
      new MockDiscordContext(guildId, userId, '1', `<@${userId}>`),
    );

    expect(interaction.getReplyEmbedCount()).toBe(1);
    expect(interaction.getReplyComponents()).toHaveLength(0);
  });
});

describe('UT-307 ShopButtonHandler browse/search parity', () => {
  const guildId = '123456789';
  const userId = '987654321';
  const guildIdNum = 123456789;

  let shopService: ShopService;
  let productService: ProductService;
  let handler: ShopCommandHandler;

  beforeEach(() => {
    shopService = {
      getShopPage: vi.fn(),
      searchProducts: vi.fn(),
    } as unknown as ShopService;
    productService = {
      getAllPurchasableProducts: vi.fn(),
    } as unknown as ProductService;
    handler = new ShopCommandHandler(
      shopService,
      productService,
      { tryGetBalance: vi.fn() } as never,
      { purchaseProduct: vi.fn() } as never,
      { createFiatOnlyOrder: vi.fn() } as never,
      { handoffFromFiatPayment: vi.fn(), handoffFromCurrencyPurchase: vi.fn() } as never,
      { notifyAdminsOrderCreated: vi.fn() } as never,
      { notifyEscortOrderCreated: vi.fn() } as never,
      { requireReadyClient: vi.fn() } as never,
    );
  });

  it('pagination converts 1-based button page to 0-based service input', async () => {
    vi.mocked(shopService.getShopPage).mockResolvedValue({
      products: [createTestProduct({ guildId: guildIdNum })],
      currentPage: 2,
      totalPages: 3,
      isEmpty: () => false,
      hasPreviousPage: () => true,
      hasNextPage: () => true,
      formatPageIndicator: () => '第 2 / 3 頁',
    });

    const interaction = new ShopTestInteraction(guildId, userId, {
      customId: `${BUTTON_PREV_PAGE}2`,
    });
    await handler.handleInteraction(
      interaction,
      new MockDiscordContext(guildId, userId, '1', `<@${userId}>`),
      `${BUTTON_PREV_PAGE}2`,
    );

    expect(shopService.getShopPage).toHaveBeenCalledWith(guildIdNum, 1);
    expect(interaction.getEditedComponents().length).toBeGreaterThan(0);
  });

  it('buy button uses getAllPurchasableProducts', async () => {
    vi.mocked(productService.getAllPurchasableProducts).mockResolvedValue([
      createTestProduct({ guildId: guildIdNum, id: 1 }),
    ]);

    const interaction = new ShopTestInteraction(guildId, userId, { customId: BUTTON_BUY });
    await handler.handleInteraction(
      interaction,
      new MockDiscordContext(guildId, userId, '1', `<@${userId}>`),
      BUTTON_BUY,
    );

    expect(productService.getAllPurchasableProducts).toHaveBeenCalledWith(guildIdNum);
    expect(interaction.getReplyMessages()[0]).toBe('請選擇要購買的商品');
  });

  it('search button opens modal', async () => {
    const interaction = new ShopTestInteraction(guildId, userId, { customId: BUTTON_SEARCH });
    await handler.handleInteraction(
      interaction,
      new MockDiscordContext(guildId, userId, '1', `<@${userId}>`),
      BUTTON_SEARCH,
    );
    expect(interaction.wasShowModalCalled()).toBe(true);
  });

  it('search empty uses keyword-specific message', async () => {
    vi.mocked(shopService.searchProducts).mockResolvedValue({
      products: [],
      currentPage: 1,
      totalPages: 0,
      isEmpty: () => true,
      hasPreviousPage: () => false,
      hasNextPage: () => false,
      formatPageIndicator: () => '共 0 個商品',
    });

    const interaction = new ShopTestInteraction(guildId, userId, {
      customId: 'shop_search_modal',
      interactionType: 'modalSubmit',
      textInputValues: { keyword: 'missing' },
    });
    await handler.handleInteraction(
      interaction,
      new MockDiscordContext(guildId, userId, '1', `<@${userId}>`),
      'shop_search_modal',
    );

    expect(interaction.getReplyMessages()[0]).toBe('找不到符合「missing」的商品');
  });

  it('back to shop loads first page', async () => {
    vi.mocked(shopService.getShopPage).mockResolvedValue({
      products: [],
      currentPage: 1,
      totalPages: 0,
      isEmpty: () => true,
      hasPreviousPage: () => false,
      hasNextPage: () => false,
      formatPageIndicator: () => '共 0 個商品',
    });

    const interaction = new ShopTestInteraction(guildId, userId, { customId: BUTTON_BACK_TO_SHOP });
    await handler.handleInteraction(
      interaction,
      new MockDiscordContext(guildId, userId, '1', `<@${userId}>`),
      BUTTON_BACK_TO_SHOP,
    );

    expect(shopService.getShopPage).toHaveBeenCalledWith(guildIdNum, 0);
  });

  it('search pagination converts shop_snext customId to 0-based searchProducts page', async () => {
    const product = createTestProduct({ guildId: guildIdNum, name: 'PagedProduct' });
    vi.mocked(shopService.searchProducts).mockResolvedValue({
      products: [product],
      currentPage: 2,
      totalPages: 3,
      isEmpty: () => false,
      hasPreviousPage: () => true,
      hasNextPage: () => true,
      formatPageIndicator: () => '第 2 / 3 頁',
    });

    const keyword = 'keyword';
    const customId = `${BUTTON_SEARCH_NEXT}${encodeKeyword(keyword)}_2`;
    const interaction = new ShopTestInteraction(guildId, userId, { customId });
    await handler.handleInteraction(
      interaction,
      new MockDiscordContext(guildId, userId, '1', `<@${userId}>`),
      customId,
    );

    expect(shopService.searchProducts).toHaveBeenCalledWith(guildIdNum, keyword, 1);
    expect(interaction.getEditedComponents().length).toBeGreaterThan(0);
  });

  it('search pagination shop_sprev uses encoded keyword and page index', async () => {
    const product = createTestProduct({ guildId: guildIdNum, name: 'PagedProduct' });
    vi.mocked(shopService.searchProducts).mockResolvedValue({
      products: [product],
      currentPage: 1,
      totalPages: 3,
      isEmpty: () => false,
      hasPreviousPage: () => false,
      hasNextPage: () => true,
      formatPageIndicator: () => '第 1 / 3 頁',
    });

    const keyword = 'find me';
    const customId = `${BUTTON_SEARCH_PREV}${encodeKeyword(keyword)}_1`;
    const interaction = new ShopTestInteraction(guildId, userId, { customId });
    await handler.handleInteraction(
      interaction,
      new MockDiscordContext(guildId, userId, '1', `<@${userId}>`),
      customId,
    );

    expect(shopService.searchProducts).toHaveBeenCalledWith(guildIdNum, keyword, 0);
  });
});
