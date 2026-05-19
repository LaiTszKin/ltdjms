import type { Result, Unit } from '@ltdjms/shared';
import { DomainError } from '@ltdjms/shared';
import type { EscortOptionPriceRepo } from '../repo/escort-option-price.repo.js';
import type { OptionPriceView } from '../domain/option-price-view.js';
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
export declare class EscortOptionPricingService {
    private readonly repository;
    private readonly catalogRepository;
    constructor(repository: EscortOptionPriceRepo, catalogRepository: EscortOptionCatalogRepository);
    listOptionPrices(guildId: number): Promise<Result<OptionPriceView[], DomainError>>;
    updateOptionPrice(guildId: number, updatedByUserId: number, optionCode: string, priceTwd: number): Promise<Result<OptionPriceView, DomainError>>;
    resetOptionPrice(guildId: number, optionCode: string): Promise<Result<Unit, DomainError>>;
    getEffectivePrice(guildId: number, optionCode: string): Promise<Result<number, DomainError>>;
}
