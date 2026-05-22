import type { EscortOrderOption } from '../domain/option-price-view.js';

/**
 * Minimal catalog entry for an escort option.
 */
export interface EscortOptionCatalogEntry extends Omit<EscortOrderOption, 'defaultPriceTwd'> {
  readonly priceTwd: number;
}

/**
 * Catalog repository interface for escort option CRUD.
 */
export interface EscortOptionCatalogRepository {
  findAll(): Promise<EscortOptionCatalogEntry[]>;
  findByCode(code: string): Promise<EscortOptionCatalogEntry | null>;
  existsByCode(code: string): Promise<boolean>;
  create(
    entry: Omit<EscortOptionCatalogEntry, 'code'> & { code: string },
  ): Promise<EscortOptionCatalogEntry>;
  update(
    code: string,
    data: Partial<Omit<EscortOptionCatalogEntry, 'code'>>,
  ): Promise<EscortOptionCatalogEntry | null>;
  delete(code: string): Promise<boolean>;
}
