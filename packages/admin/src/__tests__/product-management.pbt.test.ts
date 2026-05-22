import { describe, it, expect, vi, beforeEach } from 'vitest';
import * as fc from 'fast-check';
import { Ok, OperationType, type DomainEventPublisher } from '@ltdjms/shared';
import { ProductManagementFacade } from '../facades/ProductManagementFacade.js';
import type {
  ShopService,
  ProductRepository,
  RedemptionCodeRepository,
  RedemptionCodeGenerator,
  Product,
  RedemptionCode,
  CodeStats,
  ShopPage,
} from '@ltdjms/shop';

const guildId = (): fc.Arbitrary<number> => fc.integer({ min: 1, max: 2147483647 });

const makeProduct = (id: number, gId: number, name: string, price: number): Product => ({
  id,
  guildId: gId,
  name,
  description: null,
  rewardType: 'CURRENCY',
  rewardAmount: 1000,
  currencyPrice: price,
  fiatPriceTwd: null,
  autoCreateEscortOrder: false,
  escortOptionCode: null,
  createdAt: new Date(),
  updatedAt: new Date(),
});

describe('ProductManagementFacade PBT', () => {
  let facade: ProductManagementFacade;
  let mockShop: Partial<ShopService>;
  let mockRepo: Partial<ProductRepository>;
  let mockCodeRepo: Partial<RedemptionCodeRepository>;
  let mockGen: Partial<RedemptionCodeGenerator>;
  let mockEvt: Partial<DomainEventPublisher>;
  let counter: number;

  beforeEach(() => {
    counter = 0;
    mockShop = { getShopPage: vi.fn() };
    mockRepo = { create: vi.fn(), update: vi.fn(), delete: vi.fn(), findById: vi.fn() };
    mockCodeRepo = { saveAll: vi.fn(), getStatsByProductId: vi.fn() };
    mockGen = {
      generate: vi.fn(() => {
        counter++;
        return `C-${String(counter).padStart(6, '0')}`;
      }),
    };
    mockEvt = { publish: vi.fn() };
    facade = new ProductManagementFacade(
      mockShop as ShopService,
      mockRepo as ProductRepository,
      mockCodeRepo as RedemptionCodeRepository,
      mockGen as RedemptionCodeGenerator,
      mockEvt as DomainEventPublisher,
    );
  });

  describe('getShopPage', () => {
    it('delegate 到 shopService', async () => {
      await fc.assert(
        fc.asyncProperty(
          fc.integer({ min: 1, max: 1e5 }),
          fc.integer({ min: 1, max: 100 }),
          async (gId, page) => {
            const sp: ShopPage = { items: [], total: 0, page, pageSize: 10 };
            mockShop.getShopPage = vi.fn().mockResolvedValue(sp);
            expect(await facade.getShopPage(gId, page)).toEqual(sp);
            expect(mockShop.getShopPage).toHaveBeenLastCalledWith(gId, page);
            return true;
          },
        ),
      );
    });
  });

  describe('findProductById', () => {
    it('delegate 到 repository', async () => {
      await fc.assert(
        fc.asyncProperty(fc.integer({ min: 1, max: 1e5 }), async (id) => {
          const p = makeProduct(id, 1, 'Test', 500);
          mockRepo.findById = vi.fn().mockResolvedValue(p);
          expect(await facade.findProductById(id)).toEqual(p);
          expect(mockRepo.findById).toHaveBeenLastCalledWith(id);
          return true;
        }),
      );
    });
  });

  describe('createProduct', () => {
    it('建立後發布 ProductChangedEvent', async () => {
      await fc.assert(
        fc.asyncProperty(
          guildId(),
          fc.string({ minLength: 1, maxLength: 20 }),
          fc.integer({ min: 1, max: 1e5 }),
          async (gId, name, price) => {
            const p = makeProduct(42, gId, name, price);
            mockRepo.create = vi.fn().mockResolvedValue(p);
            const data = {
              guildId: gId,
              name,
              description: null,
              rewardType: 'CURRENCY' as const,
              rewardAmount: 1000,
              currencyPrice: price,
              fiatPriceTwd: null,
              autoCreateEscortOrder: false,
              escortOptionCode: null,
            };
            expect(await facade.createProduct(data, String(gId))).toEqual(p);
            expect(mockEvt.publish).toHaveBeenLastCalledWith(
              expect.objectContaining({
                eventType: 'product_changed',
                productId: 42,
                operationType: OperationType.CREATED,
              }),
            );
            return true;
          },
        ),
      );
    });
  });

  describe('updateProduct', () => {
    it('更新後發布 ProductChangedEvent', async () => {
      await fc.assert(
        fc.asyncProperty(
          fc.integer({ min: 1, max: 1e5 }),
          guildId(),
          fc.string({ minLength: 1, maxLength: 50 }),
          async (id, gId, name) => {
            const p = makeProduct(id, gId, name, 500);
            mockRepo.update = vi.fn().mockResolvedValue(p);
            expect(await facade.updateProduct(id, { name }, String(gId))).toEqual(p);
            expect(mockEvt.publish).toHaveBeenLastCalledWith(
              expect.objectContaining({
                eventType: 'product_changed',
                productId: id,
                operationType: OperationType.UPDATED,
              }),
            );
            return true;
          },
        ),
      );
    });
  });

  describe('deleteProduct', () => {
    it('成功刪除時發布事件', async () => {
      await fc.assert(
        fc.asyncProperty(fc.integer({ min: 1, max: 1e5 }), guildId(), async (id, gId) => {
          mockRepo.delete = vi.fn().mockResolvedValue(true);
          expect(await facade.deleteProduct(id, String(gId))).toBe(true);
          expect(mockEvt.publish).toHaveBeenLastCalledWith(
            expect.objectContaining({
              eventType: 'product_changed',
              productId: id,
              operationType: OperationType.DELETED,
            }),
          );
          return true;
        }),
      );
    });
    it('不存在的商品回傳 false', async () => {
      await fc.assert(
        fc.asyncProperty(fc.integer({ min: 1, max: 1e5 }), guildId(), async (id, gId) => {
          mockRepo.delete = vi.fn().mockResolvedValue(false);
          expect(await facade.deleteProduct(id, String(gId))).toBe(false);
          expect(mockEvt.publish).not.toHaveBeenCalled();
          return true;
        }),
      );
    });
  });

  describe('generateAndSaveCodes', () => {
    it('生成的兌換碼數量等於請求的 count', async () => {
      await fc.assert(
        fc.asyncProperty(
          fc.integer({ min: 1, max: 100 }),
          guildId(),
          fc.integer({ min: 1, max: 1000 }),
          async (count, gId, prodId) => {
            counter = 0;
            mockGen.generate = vi.fn(() => {
              counter++;
              return `G-${String(counter).padStart(6, '0')}`;
            });
            const saved: RedemptionCode[] = Array.from({ length: count }, (_, i) => ({
              id: i + 1,
              code: `G-${String(i + 1).padStart(6, '0')}`,
              guildId: gId,
              productId: prodId,
              quantity: 1,
              expiresAt: null,
              createdAt: new Date(),
            }));
            mockCodeRepo.saveAll = vi.fn().mockResolvedValue(saved);
            expect(await facade.generateAndSaveCodes(prodId, count, gId, null)).toHaveLength(count);
            expect(mockCodeRepo.saveAll).toHaveBeenCalled();
            expect(mockEvt.publish).toHaveBeenLastCalledWith(
              expect.objectContaining({ eventType: 'redemption_codes_generated', count }),
            );
            return true;
          },
        ),
      );
    });
    it('count 為 0 生成空陣列', async () => {
      await fc.assert(
        fc.asyncProperty(fc.constant(null), async () => {
          counter = 0;
          mockGen.generate = vi.fn();
          mockCodeRepo.saveAll = vi.fn().mockResolvedValue([]);
          expect(await facade.generateAndSaveCodes(1, 0, 1, null)).toHaveLength(0);
          expect(mockGen.generate).not.toHaveBeenCalled();
          return true;
        }),
      );
    });
  });

  describe('getCodeStatsByProductId', () => {
    it('delegate 到 redemptionCodeRepo', async () => {
      await fc.assert(
        fc.asyncProperty(fc.integer({ min: 1, max: 1e5 }), async (id) => {
          const stats: CodeStats = { total: 10, used: 3, expired: 0 };
          mockCodeRepo.getStatsByProductId = vi.fn().mockResolvedValue(stats);
          expect(await facade.getCodeStatsByProductId(id)).toEqual(stats);
          expect(mockCodeRepo.getStatsByProductId).toHaveBeenLastCalledWith(id);
          return true;
        }),
      );
    });
  });
});
