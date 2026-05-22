import { describe, it, expect, vi, beforeEach } from 'vitest';
import * as fc from 'fast-check';
import type { DomainEventPublisher } from '@ltdjms/shared';
import { ProductManagementFacade } from '../facades/ProductManagementFacade.js';
import type { ShopService, ProductRepository, RedemptionCodeRepository, RedemptionCodeGenerator, RedemptionCode } from '@ltdjms/shop';

const CODE_CHARS = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
const CODE_LENGTH = 16;
function generateCode(): string { let c = ''; for (let i = 0; i < CODE_LENGTH; i++) c += CODE_CHARS[Math.floor(Math.random() * CODE_CHARS.length)]; return c; }
function isValidFormat(code: string): boolean { if (!code || code.length !== CODE_LENGTH) return false; for (const ch of code) { if (CODE_CHARS.indexOf(ch) === -1) return false; } return true; }
const guildId = (): fc.Arbitrary<number> => fc.integer({ min: 1, max: 2147483647 });

describe('ProductManagementFacade —— 兌換碼生成 PBT', () => {
  let facade: ProductManagementFacade;
  let mockShop: Partial<ShopService>;
  let mockRepo: Partial<ProductRepository>;
  let mockCodeRepo: Partial<RedemptionCodeRepository>;
  let mockGen: Partial<RedemptionCodeGenerator>;
  let mockEvt: Partial<DomainEventPublisher>;
  const generated: string[] = [];

  beforeEach(() => {
    generated.length = 0;
    mockShop = { getShopPage: vi.fn() };
    mockRepo = { create: vi.fn(), update: vi.fn(), delete: vi.fn(), findById: vi.fn() };
    mockCodeRepo = { saveAll: vi.fn(), getStatsByProductId: vi.fn() };
    mockGen = { generate: vi.fn(() => { const c = generateCode(); generated.push(c); return c; }) };
    mockEvt = { publish: vi.fn() };
    facade = new ProductManagementFacade(mockShop as ShopService, mockRepo as ProductRepository, mockCodeRepo as RedemptionCodeRepository, mockGen as RedemptionCodeGenerator, mockEvt as DomainEventPublisher);
  });

  describe('生成數量正確', () => {
    it('count >= 1 生成並儲存對應數量', async () => {
      await fc.assert(fc.asyncProperty(fc.integer({ min: 1, max: 100 }), async (count) => {
        generated.length = 0;
        mockGen.generate = vi.fn(() => { const c = generateCode(); generated.push(c); return c; });
        mockCodeRepo.saveAll = vi.fn((codes: RedemptionCode[]) => Promise.resolve(codes));
        const result = await facade.generateAndSaveCodes(1, count, 1, null);
        expect(result).toHaveLength(count);
        expect(mockGen.generate).toHaveBeenCalledTimes(count);
        const arg = mockCodeRepo.saveAll.mock.calls[0][0] as RedemptionCode[];
        expect(arg).toHaveLength(count);
        return true;
      }));
    });
    it('count = 0 不生成', async () => {
      await fc.assert(fc.asyncProperty(fc.constant(null), async () => {
        generated.length = 0;
        mockGen.generate = vi.fn();
        mockCodeRepo.saveAll = vi.fn().mockResolvedValue([]);
        expect(await facade.generateAndSaveCodes(1, 0, 1, null)).toHaveLength(0);
        expect(mockGen.generate).not.toHaveBeenCalled();
        return true;
      }));
    });
  });

  describe('兌換碼唯一性', () => {
    it('同一批次中所有兌換碼唯一', async () => {
      await fc.assert(fc.asyncProperty(fc.integer({ min: 2, max: 50 }), async (count) => {
        generated.length = 0;
        const set = new Set<string>();
        mockGen.generate = vi.fn(() => { let c: string; do { c = generateCode(); } while (set.has(c)); set.add(c); generated.push(c); return c; });
        mockCodeRepo.saveAll = vi.fn((codes: RedemptionCode[]) => Promise.resolve(codes));
        const result = await facade.generateAndSaveCodes(1, count, 1, null);
        const strs = result.map(c => c.code);
        expect(new Set(strs).size).toBe(count);
        return true;
      }));
    });
  });

  describe('兌換碼格式符合規範', () => {
    it('16 位英數字元不含 O/0/I/L', async () => {
      await fc.assert(fc.asyncProperty(fc.integer({ min: 1, max: 50 }), async (count) => {
        generated.length = 0;
        mockGen.generate = vi.fn(() => { const c = generateCode(); generated.push(c); return c; });
        mockCodeRepo.saveAll = vi.fn((codes: RedemptionCode[]) => Promise.resolve(codes));
        for (const rc of await facade.generateAndSaveCodes(1, count, 1, null)) {
          expect(isValidFormat(rc.code)).toBe(true);
        }
        return true;
      }));
    });
  });

  describe('發布事件', () => {
    it('生成成功後發布 RedemptionCodesGeneratedEvent', async () => {
      await fc.assert(fc.asyncProperty(fc.integer({ min: 1, max: 100 }), guildId(), fc.integer({ min: 1, max: 1000 }), async (count, gId, prodId) => {
        generated.length = 0;
        mockGen.generate = vi.fn(() => { const c = generateCode(); generated.push(c); return c; });
        mockCodeRepo.saveAll = vi.fn((codes: RedemptionCode[]) => Promise.resolve(codes));
        await facade.generateAndSaveCodes(prodId, count, gId, null);
        expect(mockEvt.publish).toHaveBeenLastCalledWith(expect.objectContaining({ eventType: 'redemption_codes_generated', guildId: String(gId), productId: prodId, count }));
        return true;
      }));
    });
  });
});
