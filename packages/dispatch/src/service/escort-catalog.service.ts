import type {
  EscortOptionCatalogRepository,
  EscortOptionCatalogEntry,
} from '../repo/escort-option-catalog.repo.js';
import type { EscortOptionPriceRepo } from '../repo/escort-option-price.repo.js';

/**
 * Data required to create a new catalog entry.
 */
export interface CreateCatalogData {
  code: string;
  type: string;
  level: string;
  mapScope: string;
  target: string;
  priceTwd: number;
}

/**
 * Partial update data for a catalog entry (code is immutable).
 */
export type UpdateCatalogData = Partial<Omit<EscortOptionCatalogEntry, 'code'>>;

/**
 * Service layer for escort option catalog management.
 * Wraps EscortOptionCatalogRepository and EscortOptionPriceRepo,
 * providing a unified API for catalog CRUD and reference counting.
 *
 * NOTE: Event publishing is handled by the caller (facade) which
 * has access to guild context.
 */
export class EscortCatalogService {
  constructor(
    private readonly catalogRepository: EscortOptionCatalogRepository,
    private readonly priceRepo: EscortOptionPriceRepo,
  ) {}

  async findAll(): Promise<EscortOptionCatalogEntry[]> {
    return this.catalogRepository.findAll();
  }

  async findByCode(code: string): Promise<EscortOptionCatalogEntry | null> {
    return this.catalogRepository.findByCode(code);
  }

  async create(data: CreateCatalogData): Promise<EscortOptionCatalogEntry> {
    return this.catalogRepository.create(data);
  }

  async update(
    code: string,
    data: UpdateCatalogData,
  ): Promise<EscortOptionCatalogEntry | null> {
    return this.catalogRepository.update(code, data);
  }

  async delete(code: string): Promise<boolean> {
    return this.catalogRepository.delete(code);
  }

  async countByOptionCode(code: string): Promise<number> {
    return this.priceRepo.countByOptionCode(code);
  }

  async findGuildIdsByOptionCode(code: string): Promise<number[]> {
    return this.priceRepo.findGuildIdsByOptionCode(code);
  }
}
