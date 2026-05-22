import { describe, it, expect, vi, beforeEach } from 'vitest';
import * as fc from 'fast-check';
import { Ok, Err, DomainError, DomainErrorCategory, okVoid, type DomainEventPublisher } from '@ltdjms/shared';
import { DispatchManagementFacade } from '../facades/DispatchManagementFacade.js';
import type { DispatchAfterSalesStaffService, EscortOptionPricingService, EscortCatalogService, EscortDispatchOrderService, OptionPriceView, EscortOptionCatalogEntry } from '@ltdjms/dispatch';

const guildId = (): fc.Arbitrary<number> => fc.integer({ min: 1, max: 2147483647 });
const userId = (): fc.Arbitrary<number> => fc.integer({ min: 1, max: 2147483647 });

describe('DispatchManagementFacade PBT', () => {
  let facade: DispatchManagementFacade;
  let mockStaff: Partial<DispatchAfterSalesStaffService>;
  let mockPrice: Partial<EscortOptionPricingService>;
  let mockCat: Partial<EscortCatalogService>;
  let mockOrder: Partial<EscortDispatchOrderService>;
  let mockEvt: Partial<DomainEventPublisher>;

  beforeEach(() => {
    mockStaff = { getStaffUserIds: vi.fn(), addStaff: vi.fn(), removeStaff: vi.fn() };
    mockPrice = { listOptionPrices: vi.fn(), updateOptionPrice: vi.fn(), resetOptionPrice: vi.fn() };
    mockCat = { findAll: vi.fn(), findByCode: vi.fn(), create: vi.fn(), update: vi.fn(), delete: vi.fn(), countByOptionCode: vi.fn(), findGuildIdsByOptionCode: vi.fn() };
    mockOrder = { countActiveOrders: vi.fn() };
    mockEvt = { publish: vi.fn() };
    facade = new DispatchManagementFacade(mockStaff as DispatchAfterSalesStaffService, mockPrice as EscortOptionPricingService, mockCat as EscortCatalogService, mockEvt as DomainEventPublisher, mockOrder as EscortDispatchOrderService);
  });

  describe('售後人員', () => {
    it('listStaff 委派並回傳集合', async () => {
      await fc.assert(fc.asyncProperty(guildId(), fc.array(userId()), async (gId, ids) => {
        const set = new Set(ids);
        mockStaff.getStaffUserIds = vi.fn().mockResolvedValue(new Ok(set));
        const r = await facade.listStaff(String(gId));
        expect(r.isOk()).toBe(true);
        expect(r.getValue()).toEqual(set);
        expect(mockStaff.getStaffUserIds).toHaveBeenLastCalledWith(gId);
        return true;
      }));
    });
    it('addStaff 成功時發布事件', async () => {
      await fc.assert(fc.asyncProperty(guildId(), userId(), async (gId, uId) => {
        mockStaff.addStaff = vi.fn().mockResolvedValue(okVoid());
        const r = await facade.addStaff(String(gId), String(uId));
        expect(r.isOk()).toBe(true);
        expect(r.getValue()).toBe(true);
        expect(mockEvt.publish).toHaveBeenLastCalledWith(expect.objectContaining({ eventType: 'dispatch_after_sales_config_changed' }));
        return true;
      }));
    });
    it('addStaff 失敗時不發布事件', async () => {
      await fc.assert(fc.asyncProperty(guildId(), userId(), async (gId, uId) => {
        mockStaff.addStaff = vi.fn().mockResolvedValue(new Err(DomainError.invalidInput('已在名單中')));
        const r = await facade.addStaff(String(gId), String(uId));
        expect(r.isErr()).toBe(true);
        expect(mockEvt.publish).not.toHaveBeenCalled();
        return true;
      }));
    });
    it('removeStaff 成功時發布事件', async () => {
      await fc.assert(fc.asyncProperty(guildId(), userId(), async (gId, uId) => {
        mockStaff.removeStaff = vi.fn().mockResolvedValue(okVoid());
        const r = await facade.removeStaff(String(gId), String(uId));
        expect(r.isOk()).toBe(true);
        expect(mockEvt.publish).toHaveBeenLastCalledWith(expect.objectContaining({ eventType: 'dispatch_after_sales_config_changed' }));
        return true;
      }));
    });
  });

  describe('訂單統計', () => {
    it('countActiveOrders 委派到 orderService', async () => {
      await fc.assert(fc.asyncProperty(guildId(), fc.integer({ min: 0, max: 1000 }), async (gId, cnt) => {
        mockOrder.countActiveOrders = vi.fn().mockResolvedValue(new Ok(cnt));
        const r = await facade.countActiveOrders(String(gId));
        expect(r.isOk()).toBe(true);
        expect(r.getValue()).toBe(cnt);
        expect(mockOrder.countActiveOrders).toHaveBeenLastCalledWith(gId);
        return true;
      }));
    });
  });

  describe('定價管理', () => {
    it('listPricing 委派到 pricingService', async () => {
      await fc.assert(fc.asyncProperty(guildId(), async (gId) => {
        mockPrice.listOptionPrices = vi.fn().mockResolvedValue(new Ok([]));
        const r = await facade.listPricing(String(gId));
        expect(r.isOk()).toBe(true);
        expect(mockPrice.listOptionPrices).toHaveBeenLastCalledWith(gId);
        return true;
      }));
    });
  });

  describe('目錄管理', () => {
    it('listCatalog 回傳清單', async () => {
      await fc.assert(fc.asyncProperty(fc.constant(null), async () => {
        mockCat.findAll = vi.fn().mockResolvedValue([]);
        const r = await facade.listCatalog();
        expect(r.isOk()).toBe(true);
        expect(r.getValue()).toEqual([]);
        return true;
      }));
    });
    it('findCatalogEntry 對任何 code 委派並回傳找到的條目', async () => {
      await fc.assert(fc.asyncProperty(fc.stringMatching(/^[a-z0-9]{1,10}$/), async (code) => {
        const entry: EscortOptionCatalogEntry = { code, type: 'CARRY', level: 'STANDARD', mapScope: 'ANY', target: 'test', priceTwd: 1000 };
        mockCat.findByCode = vi.fn().mockResolvedValue(entry);
        const r = await facade.findCatalogEntry(code);
        expect(r.isOk()).toBe(true);
        expect(r.getValue()).toEqual(entry);
        expect(mockCat.findByCode).toHaveBeenLastCalledWith(code);
        return true;
      }));
    });
    it('checkCatalogRefCount 委派', async () => {
      await fc.assert(fc.asyncProperty(fc.stringMatching(/^[a-z0-9]{1,10}$/), fc.integer({ min: 0, max: 100 }), async (code, cnt) => {
        mockCat.countByOptionCode = vi.fn().mockResolvedValue(cnt);
        const r = await facade.checkCatalogRefCount(code);
        expect(r.isOk()).toBe(true);
        expect(r.getValue()).toBe(cnt);
        expect(mockCat.countByOptionCode).toHaveBeenLastCalledWith(code);
        return true;
      }));
    });
  });
});
