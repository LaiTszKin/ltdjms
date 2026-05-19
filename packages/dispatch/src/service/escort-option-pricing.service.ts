import type { Result, Unit } from '@ltdjms/shared';
import { Ok, Err, DomainError, okVoid } from '@ltdjms/shared';

import type { EscortOptionPriceRepo } from '../repo/escort-option-price.repo.js';
import type { EscortOrderOption, OptionPriceView } from '../domain/option-price-view.js';

/**
 * Minimal catalog entry for an escort option.
 * This will be replaced by the shared EscortOptionCatalogRepository once ported.
 */
export interface EscortOptionCatalogEntry {
  readonly code: string;
  readonly type: string;
  readonly level: string;
  readonly mapScope: string;
  readonly target: string;
  readonly priceTwd: number;
}

/**
 * Minimal catalog repository interface.
 * This will be replaced by the shared EscortOptionCatalogRepository once ported.
 */
export interface EscortOptionCatalogRepository {
  findAll(): Promise<EscortOptionCatalogEntry[]>;
  findByCode(code: string): Promise<EscortOptionCatalogEntry | null>;
  existsByCode(code: string): Promise<boolean>;
}

/**
 * Service for guild-level escort option pricing overrides.
 * Matches Java EscortOptionPricingService exactly.
 */
export class EscortOptionPricingService {
  constructor(
    private readonly repository: EscortOptionPriceRepo,
    private readonly catalogRepository: EscortOptionCatalogRepository,
  ) {}

  async listOptionPrices(guildId: number): Promise<Result<OptionPriceView[], DomainError>> {
    try {
      const overrides = await this.repository.findAllByGuildId(guildId);
      const catalogs = await this.catalogRepository.findAll();
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
      const allCodes = (await this.catalogRepository.findAll())
        .map((c) => c.code)
        .join(', ');
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

  async resetOptionPrice(
    guildId: number,
    optionCode: string,
  ): Promise<Result<Unit, DomainError>> {
    if (!optionCode || optionCode.trim().length === 0) {
      return new Err(DomainError.invalidInput('護航選項代碼不能為空'));
    }
    const normalizedCode = optionCode.trim().toUpperCase();

    const exists = await this.catalogRepository.existsByCode(normalizedCode);
    if (!exists) {
      const allCodes = (await this.catalogRepository.findAll())
        .map((c) => c.code)
        .join(', ');
      return new Err(DomainError.invalidInput(`護航選項代碼無效，可用代碼：${allCodes}`));
    }

    try {
      await this.repository.delete(guildId, normalizedCode);
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
