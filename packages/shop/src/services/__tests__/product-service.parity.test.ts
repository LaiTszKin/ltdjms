import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ProductService } from '../product-service.js';
import { createProduct, hasCurrencyPrice, isFiatOnly } from '../../domain/product-types.js';

describe('UT-305 getAllPurchasableProducts parity', () => {
  const guildId = 123456789;

  let repository: {
    findByGuildIdWithCurrencyPrice: ReturnType<typeof vi.fn>;
    findFiatOnlyByGuildId: ReturnType<typeof vi.fn>;
  };
  let productService: ProductService;

  beforeEach(() => {
    repository = {
      findByGuildIdWithCurrencyPrice: vi.fn(),
      findFiatOnlyByGuildId: vi.fn(),
    };
    productService = new ProductService(repository as never, { publish: vi.fn() } as never);
  });

  it('merges currency-priced and fiat-only products without duplicates', async () => {
    const currencyProduct = createProduct(guildId, 'Currency Item', null, null, null, 100, null);
    const fiatOnlyProduct = createProduct(guildId, 'Fiat Item', null, null, null, null, 500);
    const dualProduct = createProduct(guildId, 'Dual Item', null, null, null, 200, 800);

    repository.findByGuildIdWithCurrencyPrice.mockResolvedValue([
      { ...currencyProduct, id: 1 },
      { ...dualProduct, id: 3 },
    ]);
    repository.findFiatOnlyByGuildId.mockResolvedValue([{ ...fiatOnlyProduct, id: 2 }]);

    const result = await productService.getAllPurchasableProducts(guildId);

    expect(result).toHaveLength(3);
    expect(result.filter(hasCurrencyPrice)).toHaveLength(2);
    expect(result.filter(isFiatOnly)).toHaveLength(1);
    expect(result.some((p) => p.id === 2)).toBe(true);
  });

  it('calls filtered repository queries once each', async () => {
    repository.findByGuildIdWithCurrencyPrice.mockResolvedValue([]);
    repository.findFiatOnlyByGuildId.mockResolvedValue([]);

    await productService.getAllPurchasableProducts(guildId);

    expect(repository.findByGuildIdWithCurrencyPrice).toHaveBeenCalledTimes(1);
    expect(repository.findByGuildIdWithCurrencyPrice).toHaveBeenCalledWith(guildId);
    expect(repository.findFiatOnlyByGuildId).toHaveBeenCalledTimes(1);
    expect(repository.findFiatOnlyByGuildId).toHaveBeenCalledWith(guildId);
  });

  it('returns empty list when guild has no products', async () => {
    repository.findByGuildIdWithCurrencyPrice.mockResolvedValue([]);
    repository.findFiatOnlyByGuildId.mockResolvedValue([]);
    await expect(productService.getAllPurchasableProducts(guildId)).resolves.toEqual([]);
  });
});
