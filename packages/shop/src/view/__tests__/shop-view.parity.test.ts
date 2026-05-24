import { describe, it, expect } from 'vitest';
import {
  MODAL_SEARCH,
  BUTTON_PREV_PAGE,
  BUTTON_NEXT_PAGE,
  BUTTON_BUY,
  SELECT_BUY_PRODUCT,
  BUTTON_SEARCH,
  BUTTON_PAY_WITH_CURRENCY,
  BUTTON_PAY_WITH_FIAT,
  BUTTON_BACK_TO_SHOP,
  SELECT_SEARCH_BUY,
  BUTTON_SEARCH_PREV,
  BUTTON_SEARCH_NEXT,
  BUTTON_CONFIRM_PURCHASE,
  BUTTON_CANCEL_PURCHASE,
  buildEmptyShopEmbed,
  buildShopEmbed,
  buildSearchModal,
  buildShopComponents,
  buildBuyMenu,
  buildPaymentMethodChoiceEmbed,
  buildPaymentMethodChoiceComponents,
  buildSearchResultComponents,
  buildPurchaseConfirmEmbed,
  buildPurchaseConfirmComponents,
  PAGE_SIZE,
} from '../shop-view.js';
import customIds from '../../../../../docs/plans/2026-05-24/java-parity-shop-ai/shop-java-parity/fixtures/java-shop-custom-ids.json';
import oracle from '../../../../../docs/plans/2026-05-24/java-parity-shop-ai/shop-java-parity/fixtures/java-shop-view-oracle.json';
import { createTestProduct } from '../../__tests__/helpers/shop-test-interaction.js';

describe('UT-302 ShopView embed + modal parity', () => {
  it('R1.3 customId constants match Java oracle', () => {
    expect(MODAL_SEARCH).toBe(customIds.constants.MODAL_SEARCH);
    expect(BUTTON_PREV_PAGE).toBe(customIds.constants.BUTTON_PREV_PAGE);
    expect(BUTTON_NEXT_PAGE).toBe(customIds.constants.BUTTON_NEXT_PAGE);
    expect(BUTTON_BUY).toBe(customIds.constants.BUTTON_BUY);
    expect(SELECT_BUY_PRODUCT).toBe(customIds.constants.SELECT_BUY_PRODUCT);
    expect(BUTTON_SEARCH).toBe(customIds.constants.BUTTON_SEARCH);
    expect(BUTTON_PAY_WITH_CURRENCY).toBe(customIds.constants.BUTTON_PAY_WITH_CURRENCY);
    expect(BUTTON_PAY_WITH_FIAT).toBe(customIds.constants.BUTTON_PAY_WITH_FIAT);
    expect(BUTTON_BACK_TO_SHOP).toBe(customIds.constants.BUTTON_BACK_TO_SHOP);
    expect(SELECT_SEARCH_BUY).toBe(customIds.constants.SELECT_SEARCH_BUY);
    expect(BUTTON_SEARCH_PREV).toBe(customIds.constants.BUTTON_SEARCH_PREV);
    expect(BUTTON_SEARCH_NEXT).toBe(customIds.constants.BUTTON_SEARCH_NEXT);
    expect(BUTTON_CONFIRM_PURCHASE).toBe(customIds.constants.BUTTON_CONFIRM_PURCHASE);
    expect(BUTTON_CANCEL_PURCHASE).toBe(customIds.constants.BUTTON_CANCEL_PURCHASE);
    expect(PAGE_SIZE).toBe(customIds.pageSize);
  });

  it('search modal uses keyword field id', () => {
    const modal = buildSearchModal();
    expect(modal.customId).toBe(oracle.scenarios.searchModal.id);
    expect(modal.components[0].components[0].customId).toBe(customIds.modalFields.searchKeyword);
    expect(modal.title).toBe('🔍 搜尋商品');
  });

  it('empty shop embed matches oracle', () => {
    const embed = buildEmptyShopEmbed();
    expect(embed.title).toBe(oracle.scenarios.emptyShop.title);
    expect(embed.description).toBe(oracle.scenarios.emptyShop.description);
  });

  it('browse single product embed matches oracle', () => {
    const product = createTestProduct({ name: '測試商品', currencyPrice: 100 });
    const embed = buildShopEmbed([product], 1, 1);
    expect(embed.title).toBe(oracle.scenarios.browseSingleProduct.title);
    for (const fragment of oracle.scenarios.browseSingleProduct.descriptionContains) {
      expect(embed.description).toContain(fragment);
    }
    expect(embed.footer.text).toBe(oracle.scenarios.browseSingleProduct.footer);
  });

  it('multi-page footer matches oracle', () => {
    const product = createTestProduct();
    const embed = buildShopEmbed([product], 2, 5);
    expect(embed.footer.text).toBe(oracle.scenarios.browseMultiPage.footer);
  });
});

describe('UT-303 ShopView pagination components parity', () => {
  it('first page disables prev and enables layout rows', () => {
    const components = buildShopComponents(1, 3, true);
    expect(components).toHaveLength(2);
    const pagination = components[0].components;
    expect(pagination[0].label).toBe('⬅️ 上一頁');
    expect(pagination[0].disabled).toBe(true);
    expect(pagination[1].label).toBe('下一頁 ➡️');
    expect(pagination[1].disabled).toBe(false);
    expect(components[1].components[0].customId).toBe(BUTTON_BUY);
    expect(components[1].components[1].customId).toBe(BUTTON_SEARCH);
  });

  it('browse without products only shows pagination row', () => {
    const components = buildShopComponents(1, 1, false);
    expect(components).toHaveLength(1);
  });
});

describe('UT-304 ShopView buy/search/confirm parity', () => {
  it('buy menu uses shared select id and combined price description', () => {
    const product = createTestProduct({
      name: '雙價格商品',
      currencyPrice: 100,
      fiatPriceTwd: 500,
    });
    const rows = buildBuyMenu([product]);
    const select = rows[0].components[0] as {
      customId: string;
      options: Array<{ description: string }>;
    };
    expect(select.customId).toBe(oracle.scenarios.buyMenu.selectId);
    expect(select.options[0].description).toContain('100');
    expect(select.options[0].description).toContain('NT$500');
  });

  it('payment choice embed and buttons match oracle', () => {
    const product = createTestProduct({ currencyPrice: 100, fiatPriceTwd: 500 });
    const embed = buildPaymentMethodChoiceEmbed(product);
    expect(embed.title).toBe(oracle.scenarios.paymentChoice.title);
    for (const fragment of oracle.scenarios.paymentChoice.descriptionContains) {
      expect(embed.description).toContain(fragment);
    }
    const buttons = buildPaymentMethodChoiceComponents(product)[0].components;
    expect(buttons[0].customId).toContain(oracle.scenarios.paymentChoice.buttons[0]);
    expect(buttons[0].label).toBe('💰 貨幣購買');
    expect(buttons[1].customId).toContain(oracle.scenarios.paymentChoice.buttons[1]);
    expect(buttons[1].label).toBe('💳 法幣下單');
  });

  it('search result component order matches oracle', () => {
    const product = createTestProduct({ name: 'TestProduct' });
    const components = buildSearchResultComponents(1, 3, 'test', [product]);
    expect(components).toHaveLength(3);
    expect(components[0].components[0]).toMatchObject({ customId: SELECT_SEARCH_BUY });
    expect(components[1].components[0]).toMatchObject({
      customId: expect.stringContaining(BUTTON_SEARCH_PREV),
    });
    expect(components[2].components[0]).toMatchObject({
      customId: oracle.scenarios.searchResults.backButtonId,
      label: '返回商店',
    });
  });

  it('confirm purchase embed sufficient and insufficient balance', () => {
    const product = createTestProduct({ name: '測試商品', currencyPrice: 100 });
    const sufficient = buildPurchaseConfirmEmbed(product, 500);
    expect(sufficient.title).toBe(oracle.scenarios.confirmPurchase.title);
    for (const fragment of oracle.scenarios.confirmPurchase.sufficientBalance.descriptionContains) {
      expect(sufficient.description).toContain(fragment);
    }

    const insufficient = buildPurchaseConfirmEmbed(product, 50);
    for (const fragment of oracle.scenarios.confirmPurchase.insufficientBalance
      .descriptionContains) {
      expect(insufficient.description).toContain(fragment);
    }

    const confirmComponents = buildPurchaseConfirmComponents(product.id!);
    expect(confirmComponents[0].components[0].customId).toBe(
      `${oracle.scenarios.confirmPurchase.confirmButtonPrefix}${product.id}`,
    );
    expect(confirmComponents[0].components[1].customId).toBe(
      oracle.scenarios.confirmPurchase.cancelButtonId,
    );
  });
});
