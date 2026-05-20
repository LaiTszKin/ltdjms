import {
  type Result,
  Ok,
  Err,
  DomainError,
  type DomainEventPublisher,
  type DispatchAfterSalesConfigChangedEvent,
  type EscortPricingChangedEvent,
  type EscortCatalogChangedEvent,
  OperationType,
} from '@ltdjms/shared';
import {
  type DispatchAfterSalesStaffService,
  type EscortOptionPricingService,
  type EscortOptionCatalogRepository,
  type EscortOptionCatalogEntry,
  type EscortOptionPriceRepo,
  type OptionPriceView,
  type EscortDispatchOrderService,
} from '@ltdjms/dispatch';

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
 * Facade that aggregates dispatch module operations from four domains:
 *
 * 1. After-sales Staff — listStaff, addStaff, removeStaff
 * 2. Dispatch Orders — countActiveOrders
 * 3. Escort Pricing — listPricing, updatePricing, resetPricing
 * 4. Escort Catalog — listCatalog, findCatalogEntry, createCatalogEntry,
 *    updateCatalogEntry, deleteCatalogEntry, checkCatalogRefCount
 *
 * Wraps DispatchAfterSalesStaffService, EscortOptionPricingService,
 * EscortOptionCatalogRepository, EscortOptionPriceRepo, and
 * EscortDispatchOrderService.
 * Publishes domain events on successful mutations.
 */
export class DispatchManagementFacade {
  constructor(
    private readonly staffService: DispatchAfterSalesStaffService,
    private readonly pricingService: EscortOptionPricingService,
    private readonly catalogRepository: EscortOptionCatalogRepository,
    private readonly priceRepo: EscortOptionPriceRepo,
    private readonly eventPublisher: DomainEventPublisher,
    private readonly dispatchOrderService: EscortDispatchOrderService,
  ) {}

  // ================================================================
  // After-sales Staff
  // ================================================================

  /**
   * Lists all after-sales staff user IDs for a guild.
   */
  async listStaff(guildId: string): Promise<Result<Set<number>, DomainError>> {
    return this.staffService.getStaffUserIds(Number(guildId));
  }

  /**
   * Adds a user to the after-sales staff list.
   * Publishes DispatchAfterSalesConfigChangedEvent on success.
   */
  async addStaff(guildId: string, userId: string): Promise<Result<boolean, DomainError>> {
    const result = await this.staffService.addStaff(Number(guildId), Number(userId));
    if (result.isOk()) {
      this.eventPublisher.publish({
        eventType: 'dispatch_after_sales_config_changed',
        guildId,
      } as DispatchAfterSalesConfigChangedEvent);
      return new Ok(true);
    }
    return new Err(result.getError());
  }

  /**
   * Removes a user from the after-sales staff list.
   * Publishes DispatchAfterSalesConfigChangedEvent on success.
   */
  async removeStaff(guildId: string, userId: string): Promise<Result<boolean, DomainError>> {
    const result = await this.staffService.removeStaff(Number(guildId), Number(userId));
    if (result.isOk()) {
      this.eventPublisher.publish({
        eventType: 'dispatch_after_sales_config_changed',
        guildId,
      } as DispatchAfterSalesConfigChangedEvent);
      return new Ok(true);
    }
    return new Err(result.getError());
  }

  // ================================================================
  // Dispatch Orders
  // ================================================================

  /**
   * Counts active (non-terminal) escort dispatch orders for a guild.
   */
  async countActiveOrders(guildId: string): Promise<Result<number, DomainError>> {
    return this.dispatchOrderService.countActiveOrders(Number(guildId));
  }

  // ================================================================
  // Escort Pricing
  // ================================================================

  /**
   * Lists all escort option prices with guild overrides.
   */
  async listPricing(guildId: string): Promise<Result<OptionPriceView[], DomainError>> {
    return this.pricingService.listOptionPrices(Number(guildId));
  }

  /**
   * Updates a guild-level price override for an escort option.
   * Publishes EscortPricingChangedEvent on success.
   */
  async updatePricing(
    guildId: string,
    actorId: string,
    optionCode: string,
    price: number,
  ): Promise<Result<OptionPriceView, DomainError>> {
    const result = await this.pricingService.updateOptionPrice(
      Number(guildId),
      Number(actorId),
      optionCode,
      price,
    );
    if (result.isOk()) {
      const updated = result.getValue();
      this.eventPublisher.publish({
        eventType: 'escort_pricing_changed',
        guildId,
        optionCode,
        newPrice: price,
      } as EscortPricingChangedEvent);
      return new Ok(updated);
    }
    return new Err(result.getError());
  }

  /**
   * Resets a guild-level price override for an escort option (back to default).
   * Publishes EscortPricingChangedEvent on success.
   */
  async resetPricing(guildId: string, optionCode: string): Promise<Result<void, DomainError>> {
    const result = await this.pricingService.resetOptionPrice(Number(guildId), optionCode);
    if (result.isOk()) {
      this.eventPublisher.publish({
        eventType: 'escort_pricing_changed',
        guildId,
        optionCode,
        newPrice: 0,
      } as EscortPricingChangedEvent);
      return new Ok(undefined);
    }
    return new Err(result.getError());
  }

  // ================================================================
  // Escort Catalog
  // ================================================================

  /**
   * Lists all entries in the global escort option catalog.
   */
  async listCatalog(): Promise<Result<EscortOptionCatalogEntry[], DomainError>> {
    try {
      const entries = await this.catalogRepository.findAll();
      return new Ok(entries);
    } catch (err) {
      return new Err(
        DomainError.persistenceFailure(
          'Failed to list escort catalog entries',
          err instanceof Error ? err : undefined,
        ),
      );
    }
  }

  /**
   * Finds a catalog entry by its code.
   */
  async findCatalogEntry(code: string): Promise<Result<EscortOptionCatalogEntry | null, DomainError>> {
    try {
      const entry = await this.catalogRepository.findByCode(code);
      return new Ok(entry);
    } catch (err) {
      return new Err(
        DomainError.persistenceFailure(
          `Failed to find catalog entry: ${code}`,
          err instanceof Error ? err : undefined,
        ),
      );
    }
  }

  /**
   * Creates a new catalog entry.
   * Publishes EscortCatalogChangedEvent on success.
   */
  async createCatalogEntry(
    guildId: string,
    data: CreateCatalogData,
  ): Promise<Result<EscortOptionCatalogEntry, DomainError>> {
    try {
      const entry = await this.catalogRepository.create(data);
      this.eventPublisher.publish({
        eventType: 'escort_catalog_changed',
        guildId,
        entryCode: data.code,
        operationType: OperationType.CREATED,
      } as EscortCatalogChangedEvent);
      return new Ok(entry);
    } catch (err) {
      return new Err(
        DomainError.persistenceFailure(
          `Failed to create catalog entry: ${data.code}`,
          err instanceof Error ? err : undefined,
        ),
      );
    }
  }

  /**
   * Updates an existing catalog entry.
   * Publishes EscortCatalogChangedEvent only when the entry actually exists and was updated.
   */
  async updateCatalogEntry(
    guildId: string,
    code: string,
    data: UpdateCatalogData,
  ): Promise<Result<EscortOptionCatalogEntry | null, DomainError>> {
    try {
      const updated = await this.catalogRepository.update(code, data);
      if (updated) {
        this.eventPublisher.publish({
          eventType: 'escort_catalog_changed',
          guildId,
          entryCode: code,
          operationType: OperationType.UPDATED,
        } as EscortCatalogChangedEvent);
      }
      return new Ok(updated);
    } catch (err) {
      return new Err(
        DomainError.persistenceFailure(
          `Failed to update catalog entry: ${code}`,
          err instanceof Error ? err : undefined,
        ),
      );
    }
  }

  /**
   * Deletes a catalog entry.
   * Publishes EscortCatalogChangedEvent only when the entry was actually deleted.
   */
  async deleteCatalogEntry(guildId: string, code: string): Promise<Result<boolean, DomainError>> {
    try {
      const deleted = await this.catalogRepository.delete(code);
      if (deleted) {
        this.eventPublisher.publish({
          eventType: 'escort_catalog_changed',
          guildId,
          entryCode: code,
          operationType: OperationType.DELETED,
        } as EscortCatalogChangedEvent);
      }
      return new Ok(deleted);
    } catch (err) {
      return new Err(
        DomainError.persistenceFailure(
          `Failed to delete catalog entry: ${code}`,
          err instanceof Error ? err : undefined,
        ),
      );
    }
  }

  /**
   * Counts how many guilds have price overrides for the given option code.
   * Used to block deletion of catalog entries that are still referenced.
   */
  async checkCatalogRefCount(code: string): Promise<Result<number, DomainError>> {
    try {
      const count = await this.priceRepo.countByOptionCode(code);
      return new Ok(count);
    } catch (err) {
      return new Err(
        DomainError.persistenceFailure(
          `Failed to count price references for: ${code}`,
          err instanceof Error ? err : undefined,
        ),
      );
    }
  }

  /**
   * Returns the guild IDs that have price overrides for the given option code.
   * Used to display specific guild names in deletion-blocked messages.
   */
  async findCatalogRefGuildIds(code: string): Promise<Result<number[], DomainError>> {
    try {
      const ids = await this.priceRepo.findGuildIdsByOptionCode(code);
      return new Ok(ids);
    } catch (err) {
      return new Err(
        DomainError.persistenceFailure(
          `Failed to find guild IDs for option code: ${code}`,
          err instanceof Error ? err : undefined,
        ),
      );
    }
  }
}
