import {
  type Result,
  ok,
  err,
  DomainError,
  type DomainEventPublisher,
  safeSnowflakeToNumber,
} from '@ltdjms/shared';
import {
  type DispatchAfterSalesConfigChangedEvent,
  type EscortPricingChangedEvent,
  type EscortCatalogChangedEvent,
} from '@ltdjms/dispatch';
import { OperationType } from '@ltdjms/shared';
import {
  type DispatchAfterSalesStaffService,
  type EscortOptionPricingService,
  type EscortCatalogService,
  type CreateCatalogData,
  type UpdateCatalogData,
  type EscortOptionCatalogEntry,
  type OptionPriceView,
  type EscortDispatchOrderService,
} from '@ltdjms/dispatch';

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
    private readonly catalogService: EscortCatalogService,
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
    const result = await this.staffService.addStaff(Number(guildId), userId);
    if (result.isOk()) {
      this.eventPublisher.publish({
        eventType: 'dispatch_after_sales_config_changed',
        guildId,
      } as DispatchAfterSalesConfigChangedEvent);
      return ok(true);
    }
    return err(result.getError());
  }

  /**
   * Removes a user from the after-sales staff list.
   * Publishes DispatchAfterSalesConfigChangedEvent on success.
   */
  async removeStaff(guildId: string, userId: string): Promise<Result<boolean, DomainError>> {
    const result = await this.staffService.removeStaff(Number(guildId), userId);
    if (result.isOk()) {
      this.eventPublisher.publish({
        eventType: 'dispatch_after_sales_config_changed',
        guildId,
      } as DispatchAfterSalesConfigChangedEvent);
      return ok(true);
    }
    return err(result.getError());
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
      safeSnowflakeToNumber(actorId),
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
      return ok(updated);
    }
    return err(result.getError());
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
      return ok(undefined);
    }
    return err(result.getError());
  }

  // ================================================================
  // Escort Catalog
  // ================================================================

  /**
   * Lists all entries in the global escort option catalog.
   */
  async listCatalog(): Promise<Result<EscortOptionCatalogEntry[], DomainError>> {
    try {
      const entries = await this.catalogService.findAll();
      return ok(entries);
    } catch (e) {
      return err(
        DomainError.persistenceFailure(
          'Failed to list escort catalog entries',
          e instanceof Error ? e : undefined,
        ),
      );
    }
  }

  /**
   * Finds a catalog entry by its code.
   */
  async findCatalogEntry(code: string): Promise<Result<EscortOptionCatalogEntry | null, DomainError>> {
    try {
      const entry = await this.catalogService.findByCode(code);
      return ok(entry);
    } catch (e) {
      return err(
        DomainError.persistenceFailure(
          `Failed to find catalog entry: ${code}`,
          e instanceof Error ? e : undefined,
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
      const entry = await this.catalogService.create(data);
      this.eventPublisher.publish({
        eventType: 'escort_catalog_changed',
        guildId,
        entryCode: data.code,
        operationType: OperationType.CREATED,
      } as EscortCatalogChangedEvent);
      return ok(entry);
    } catch (e) {
      return err(
        DomainError.persistenceFailure(
          `Failed to create catalog entry: ${data.code}`,
          e instanceof Error ? e : undefined,
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
      const updated = await this.catalogService.update(code, data);
      if (updated) {
        this.eventPublisher.publish({
          eventType: 'escort_catalog_changed',
          guildId,
          entryCode: code,
          operationType: OperationType.UPDATED,
        } as EscortCatalogChangedEvent);
      }
      return ok(updated);
    } catch (e) {
      return err(
        DomainError.persistenceFailure(
          `Failed to update catalog entry: ${code}`,
          e instanceof Error ? e : undefined,
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
      const deleted = await this.catalogService.delete(code);
      if (deleted) {
        this.eventPublisher.publish({
          eventType: 'escort_catalog_changed',
          guildId,
          entryCode: code,
          operationType: OperationType.DELETED,
        } as EscortCatalogChangedEvent);
      }
      return ok(deleted);
    } catch (e) {
      return err(
        DomainError.persistenceFailure(
          `Failed to delete catalog entry: ${code}`,
          e instanceof Error ? e : undefined,
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
      const count = await this.catalogService.countByOptionCode(code);
      return ok(count);
    } catch (e) {
      return err(
        DomainError.persistenceFailure(
          `Failed to count price references for: ${code}`,
          e instanceof Error ? e : undefined,
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
      const ids = await this.catalogService.findGuildIdsByOptionCode(code);
      return ok(ids);
    } catch (e) {
      return err(
        DomainError.persistenceFailure(
          `Failed to find guild IDs for option code: ${code}`,
          e instanceof Error ? e : undefined,
        ),
      );
    }
  }
}
