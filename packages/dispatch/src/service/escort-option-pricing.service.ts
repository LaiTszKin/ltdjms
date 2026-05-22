import type { Result, Unit } from '@ltdjms/shared';
import { Ok, Err, DomainError, okVoid } from '@ltdjms/shared';

import type { EscortOptionPriceRepo } from '../repo/escort-option-price.repo.js';
import type {
  EscortOptionCatalogRepository,
  EscortOptionCatalogEntry,
} from '../repo/escort-option-catalog.repo.js';
import type { EscortOrderOption, OptionPriceView } from '../domain/option-price-view.js';

/**
 * Service for guild-level escort option pricing overrides.
 * Matches Java EscortOptionPricingService exactly.
 */
export class EscortOptionPricingService {
  constructor(
    private readonly repository: EscortOptionPriceRepo,
    private readonly catalogRepository: EscortOptionCatalogRepository,
  ) {}

  /**
   * In-memory TTL cache for the full catalog used by listOptionPrices.
   * Invalidated after CATALOG_CACHE_TTL_MS (5 minutes) to reduce DB round trips.
   */
  private catalogCache: { data: EscortOptionCatalogEntry[]; expiry: number } | null = null;
  private static readonly CATALOG_CACHE_TTL_MS = 5 * 60 * 1000;
  private pendingCatalogFetch: Promise<EscortOptionCatalogEntry[]> | null = null;

  /** Clears the in-memory catalog cache so the next listOptionPrices call re-fetches from DB. */
  clearCatalogCache(): void {
    this.catalogCache = null;
  }

  private async getCachedCatalogs(): Promise<EscortOptionCatalogEntry[]> {
    const now = Date.now();
    if (this.catalogCache != null && now < this.catalogCache.expiry) {
      return this.catalogCache.data;
    }

    // Coalesce concurrent cache misses
    if (this.pendingCatalogFetch) {
      return this.pendingCatalogFetch;
    }

    this.pendingCatalogFetch = this.catalogRepository.findAll();
    try {
      const data = await this.pendingCatalogFetch;
      this.catalogCache = { data, expiry: now + EscortOptionPricingService.CATALOG_CACHE_TTL_MS };
      return data;
    } finally {
      this.pendingCatalogFetch = null;
    }
  }

  async listOptionPrices(guildId: number): Promise<Result<OptionPriceView[], DomainError>> {
    try {
      const overrides = await this.repository.findAllByGuildId(guildId);
      const catalogs = await this.getCachedCatalogs();
      const prices: OptionPriceView[] = [];

      for (const cat of catalogs) {
        const override = overrides.get(cat.code);
        const effective = override ?? cat.priceTwd;
        const option: EscortOrderOption = {
          code: cat.code,
          type: cat.type,
          level: cat.level,
          mapScope: cat.mapScope,
          target: cat.target,
          defaultPriceTwd: cat.priceTwd,
        };
        prices.push({
          optionCode: cat.code,
          option,
          defaultPriceTwd: cat.priceTwd,
          effectivePriceTwd: effective,
          overridden: override != null,
        });
      }
      return new Ok(prices);
    } catch (e) {
      const err = e instanceof Error ? e : new Error(String(e));
      return new Err(DomainError.persistenceFailure('查詢護航定價失敗', err));
    }
  }

  async updateOptionPrice(
    guildId: number,
    updatedByUserId: number,
    optionCode: string,
    priceTwd: number,
  ): Promise<Result<OptionPriceView, DomainError>> {
    if (priceTwd <= 0) {
      return new Err(DomainError.invalidInput('護航價格必須大於 0'));
    }
    if (!optionCode || optionCode.trim().length === 0) {
      return new Err(DomainError.invalidInput('護航選項代碼不能為空'));
    }
    const normalizedCode = optionCode.trim().toUpperCase();

    const cat = await this.catalogRepository.findByCode(normalizedCode);
    if (cat == null) {
      const allCodes = (await this.catalogRepository.findAll()).map((c) => c.code).join(', ');
      return new Err(DomainError.invalidInput(`護航選項代碼無效，可用代碼：${allCodes}`));
    }

    const option: EscortOrderOption = {
      code: cat.code,
      type: cat.type,
      level: cat.level,
      mapScope: cat.mapScope,
      target: cat.target,
      defaultPriceTwd: cat.priceTwd,
    };

    try {
      await this.repository.upsert(guildId, normalizedCode, priceTwd, updatedByUserId);
      // Invalidate catalog cache so listOptionPrices reflects the updated pricing
      this.catalogCache = null;
      return new Ok({
        optionCode: normalizedCode,
        option,
        defaultPriceTwd: cat.priceTwd,
        effectivePriceTwd: priceTwd,
        overridden: true,
      });
    } catch (e) {
      const err = e instanceof Error ? e : new Error(String(e));
      return new Err(DomainError.persistenceFailure('更新護航定價失敗', err));
    }
  }

  async resetOptionPrice(guildId: number, optionCode: string): Promise<Result<Unit, DomainError>> {
    if (!optionCode || optionCode.trim().length === 0) {
      return new Err(DomainError.invalidInput('護航選項代碼不能為空'));
    }
    const normalizedCode = optionCode.trim().toUpperCase();

    const exists = await this.catalogRepository.existsByCode(normalizedCode);
    if (!exists) {
      const allCodes = (await this.catalogRepository.findAll()).map((c) => c.code).join(', ');
      return new Err(DomainError.invalidInput(`護航選項代碼無效，可用代碼：${allCodes}`));
    }

    try {
      await this.repository.delete(guildId, normalizedCode);
      // Invalidate catalog cache so listOptionPrices reflects the reset pricing
      this.catalogCache = null;
      return okVoid();
    } catch (e) {
      const err = e instanceof Error ? e : new Error(String(e));
      return new Err(DomainError.persistenceFailure('重置護航定價失敗', err));
    }
  }

  async getEffectivePrice(
    guildId: number,
    optionCode: string,
  ): Promise<Result<number, DomainError>> {
    if (!optionCode || optionCode.trim().length === 0) {
      return new Err(DomainError.invalidInput('護航選項代碼不能為空'));
    }
    const normalizedCode = optionCode.trim().toUpperCase();

    const cat = await this.catalogRepository.findByCode(normalizedCode);
    if (cat == null) {
      return new Err(DomainError.invalidInput('護航選項代碼無效'));
    }

    try {
      const override = await this.repository.findByGuildIdAndOptionCode(guildId, normalizedCode);
      return new Ok(override ?? cat.priceTwd);
    } catch (e) {
      const err = e instanceof Error ? e : new Error(String(e));
      return new Err(DomainError.persistenceFailure('查詢護航定價失敗', err));
    }
  }
}
