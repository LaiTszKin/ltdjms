import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ShopService } from '../shop.service.js';
import { createProduct } from '../../domain/product-types.js';
import oracle from '../../../../../docs/plans/2026-05-24/java-parity-shop-ai/shop-java-parity/fixtures/java-shop-service-oracle.json';

describe('UT-301 ShopService pagination parity', () => {
  const guildId = 123456789012345678;
  const pageSize = 5;

  let repository: {
    countByGuildId: ReturnType<typeof vi.fn>;
    findByGuildIdPaginated: ReturnType<typeof vi.fn>;
    countByGuildIdAndNameContaining: ReturnType<typeof vi.fn>;
    findByGuildIdAndNameContaining: ReturnType<typeof vi.fn>;
  };
  let shopService: ShopService;

  beforeEach(() => {
    repository = {
      countByGuildId: vi.fn(),
      findByGuildIdPaginated: vi.fn(),
      countByGuildIdAndNameContaining: vi.fn(),
      findByGuildIdAndNameContaining: vi.fn(),
    };
    shopService = new ShopService(repository, undefined);
  });

  function makeProducts(count: number) {
    return Array.from({ length: count }, (_, i) =>
      createProduct(guildId, `Product ${i + 1}`, null, null, null, 100, null),
    ).map((product, index) => ({ ...product, id: index + 1 }));
  }

  for (const testCase of oracle.cases) {
    it(`matches oracle case: ${testCase.name}`, async () => {
      repository.countByGuildId.mockResolvedValue(testCase.totalProducts);
      const expectedPageIndex = Math.max(
        0,
        Math.min(
          testCase.inputPageIndex,
          testCase.expected.totalPages === 0 ? 0 : testCase.expected.totalPages - 1,
        ),
      );
      const sliceSize = Math.min(
        pageSize,
        Math.max(0, testCase.totalProducts - expectedPageIndex * pageSize),
      );
      repository.findByGuildIdPaginated.mockResolvedValue(makeProducts(sliceSize));

      const result = await shopService.getShopPage(guildId, testCase.inputPageIndex);

      expect(result.isEmpty()).toBe(testCase.expected.isEmpty ?? result.products.length === 0);
      if (testCase.expected.productCount !== undefined) {
        expect(result.products.length).toBe(testCase.expected.productCount);
      }
      expect(result.currentPage).toBe(testCase.expected.currentPage);
      expect(result.totalPages).toBe(testCase.expected.totalPages);
      if (testCase.expected.hasNextPage !== undefined) {
        expect(result.hasNextPage()).toBe(testCase.expected.hasNextPage);
      }
      if (testCase.expected.hasPreviousPage !== undefined) {
        expect(result.hasPreviousPage()).toBe(testCase.expected.hasPreviousPage);
      }
    });
  }

  it('hasProducts returns true when products exist', async () => {
    repository.countByGuildId.mockResolvedValue(5);
    await expect(shopService.hasProducts(guildId)).resolves.toBe(true);
  });

  it('hasProducts returns false when empty', async () => {
    repository.countByGuildId.mockResolvedValue(0);
    await expect(shopService.hasProducts(guildId)).resolves.toBe(false);
  });

  it('getProductCount delegates to repository', async () => {
    repository.countByGuildId.mockResolvedValue(10);
    await expect(shopService.getProductCount(guildId)).resolves.toBe(10);
  });

  it('searchProducts returns empty page for blank keyword', async () => {
    const result = await shopService.searchProducts(guildId, '  ', 0);
    expect(result.isEmpty()).toBe(true);
    expect(result.totalPages).toBe(0);
  });

  it('returns products in repository order (name ascending)', async () => {
    repository.countByGuildId.mockResolvedValue(2);
    repository.findByGuildIdPaginated.mockResolvedValue([
      { ...createProduct(guildId, 'Alpha', null, null, null, 100, null), id: 1 },
      { ...createProduct(guildId, 'Beta', null, null, null, 200, null), id: 2 },
    ]);

    const result = await shopService.getShopPage(guildId, 0);
    expect(result.products.map((p) => p.name)).toEqual(['Alpha', 'Beta']);
  });
});
