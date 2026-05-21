import { describe, it, expect, vi, beforeEach } from 'vitest';
import { isOk, isErr } from '@ltdjms/shared';
import { EscortOptionPricingService } from '../../src/service/escort-option-pricing.service.js';
import type { EscortOptionCatalogRepository } from '../../src/repo/escort-option-catalog.repo.js';
import type { EscortOptionPriceRepo } from '../../src/repo/escort-option-price.repo.js';

describe('EscortOptionPricingService', () => {
  let mockPriceRepo: EscortOptionPriceRepo;
  let mockCatalogRepo: EscortOptionCatalogRepository;
  let service: EscortOptionPricingService;

  beforeEach(() => {
    mockPriceRepo = {
      findAllByGuildId: vi.fn(),
      findByGuildIdAndOptionCode: vi.fn(),
      upsert: vi.fn(),
      delete: vi.fn(),
    };

    mockCatalogRepo = {
      findAll: vi.fn(),
      findByCode: vi.fn(),
      existsByCode: vi.fn(),
    };

    service = new EscortOptionPricingService(mockPriceRepo, mockCatalogRepo);
  });

  describe('listOptionPrices', () => {
    it('should merge catalog defaults with overrides', async () => {
      vi.mocked(mockCatalogRepo.findAll).mockResolvedValue([
        { code: 'CONF_DAM_300W', type: '包本單', level: '機密護', mapScope: '機密大壩', target: '300 萬目標', priceTwd: 500 },
        { code: 'CONF_DAM_600W', type: '包本單', level: '機密護', mapScope: '機密大壩', target: '600 萬目標', priceTwd: 1100 },
      ]);
      vi.mocked(mockPriceRepo.findAllByGuildId).mockResolvedValue(new Map([['CONF_DAM_300W', 600]]));

      const result = await service.listOptionPrices(100);

      expect(isOk(result)).toBe(true);
      if (isOk(result)) {
        const prices = result.getValue();
        expect(prices).toHaveLength(2);

        const overridden = prices.find((p) => p.optionCode === 'CONF_DAM_300W');
        expect(overridden?.effectivePriceTwd).toBe(600);
        expect(overridden?.overridden).toBe(true);

        const defaultPrice = prices.find((p) => p.optionCode === 'CONF_DAM_600W');
        expect(defaultPrice?.effectivePriceTwd).toBe(1100);
        expect(defaultPrice?.overridden).toBe(false);
      }
    });
  });

  describe('updateOptionPrice', () => {
    it('should upsert a price override', async () => {
      vi.mocked(mockCatalogRepo.findByCode).mockResolvedValue({
        code: 'CONF_DAM_300W', type: '包本單', level: '機密護', mapScope: '機密大壩', target: '300 萬目標', priceTwd: 500,
      });
      vi.mocked(mockPriceRepo.upsert).mockResolvedValue(undefined);

      const result = await service.updateOptionPrice(100, 200, 'CONF_DAM_300W', 600);

      expect(isOk(result)).toBe(true);
      if (isOk(result)) {
        const view = result.getValue();
        expect(view.optionCode).toBe('CONF_DAM_300W');
        expect(view.effectivePriceTwd).toBe(600);
        expect(view.overridden).toBe(true);
        expect(view.defaultPriceTwd).toBe(500);
      }
    });

    it('should reject negative or zero price', async () => {
      const result = await service.updateOptionPrice(100, 200, 'CONF_DAM_300W', 0);

      expect(isErr(result)).toBe(true);
    });

    it('should reject invalid option code', async () => {
      vi.mocked(mockCatalogRepo.findByCode).mockResolvedValue(null);
      vi.mocked(mockCatalogRepo.findAll).mockResolvedValue([
        { code: 'CONF_DAM_300W', type: '包本單', level: '機密護', mapScope: '機密大壩', target: '300 萬目標', priceTwd: 500 },
      ]);

      const result = await service.updateOptionPrice(100, 200, 'INVALID_CODE', 600);

      expect(isErr(result)).toBe(true);
    });
  });

  describe('resetOptionPrice', () => {
    it('should delete a price override', async () => {
      vi.mocked(mockCatalogRepo.existsByCode).mockResolvedValue(true);
      vi.mocked(mockPriceRepo.delete).mockResolvedValue(true);

      const result = await service.resetOptionPrice(100, 'CONF_DAM_300W');

      expect(isOk(result)).toBe(true);
    });

    it('should reject invalid option code', async () => {
      vi.mocked(mockCatalogRepo.existsByCode).mockResolvedValue(false);
      vi.mocked(mockCatalogRepo.findAll).mockResolvedValue([
        { code: 'CONF_DAM_300W', type: '包本單', level: '機密護', mapScope: '機密大壩', target: '300 萬目標', priceTwd: 500 },
      ]);

      const result = await service.resetOptionPrice(100, 'INVALID_CODE');

      expect(isErr(result)).toBe(true);
    });
  });

  describe('getEffectivePrice', () => {
    it('should return override when present', async () => {
      vi.mocked(mockCatalogRepo.findByCode).mockResolvedValue({
        code: 'CONF_DAM_300W', type: '包本單', level: '機密護', mapScope: '機密大壩', target: '300 萬目標', priceTwd: 500,
      });
      vi.mocked(mockPriceRepo.findByGuildIdAndOptionCode).mockResolvedValue(600);

      const result = await service.getEffectivePrice(100, 'CONF_DAM_300W');

      expect(isOk(result)).toBe(true);
      if (isOk(result)) {
        expect(result.getValue()).toBe(600);
      }
    });

    it('should return catalog default when no override', async () => {
      vi.mocked(mockCatalogRepo.findByCode).mockResolvedValue({
        code: 'CONF_DAM_300W', type: '包本單', level: '機密護', mapScope: '機密大壩', target: '300 萬目標', priceTwd: 500,
      });
      vi.mocked(mockPriceRepo.findByGuildIdAndOptionCode).mockResolvedValue(null);

      const result = await service.getEffectivePrice(100, 'CONF_DAM_300W');

      expect(isOk(result)).toBe(true);
      if (isOk(result)) {
        expect(result.getValue()).toBe(500);
      }
    });
  });
});
