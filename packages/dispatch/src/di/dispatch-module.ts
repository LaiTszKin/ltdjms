import { container, TOKENS, type DiscordRuntimeGateway } from '@ltdjms/shared';
import type { NodePgDatabase } from 'drizzle-orm/node-postgres';

// Repositories
import { DrizzleEscortDispatchOrderRepo } from '../repo/drizzle-escort-dispatch-order.repo.js';
import { DrizzleEscortOptionPriceRepo } from '../repo/drizzle-escort-option-price.repo.js';
import { DrizzleDispatchAfterSalesStaffRepo } from '../repo/drizzle-dispatch-after-sales-staff.repo.js';

// Domain
import { EscortDispatchOrderNumberGenerator } from '../domain/order-number-generator.js';

// Services
import { EscortDispatchOrderService } from '../service/escort-dispatch-order.service.js';
import { EscortDispatchHandoffService } from '../service/escort-dispatch-handoff.service.js';
import { DispatchAfterSalesStaffService } from '../service/dispatch-after-sales-staff.service.js';
import {
  EscortOptionPricingService,
  type EscortOptionCatalogRepository,
} from '../service/escort-option-pricing.service.js';

// Notification
import { DispatchNotificationService } from '../notification/DispatchNotificationService.js';

// Panel
import { DispatchPanelCommandHandler } from '../panel/DispatchPanelCommandHandler.js';
import { DispatchPanelInteractionHandler } from '../panel/DispatchPanelInteractionHandler.js';

/**
 * Dispatch module token map for DI registration.
 */
export const DISPATCH_TOKENS = {
  EscortDispatchOrderRepo: Symbol('EscortDispatchOrderRepo'),
  EscortOptionPriceRepo: Symbol('EscortOptionPriceRepo'),
  DispatchAfterSalesStaffRepo: Symbol('DispatchAfterSalesStaffRepo'),
  EscortDispatchOrderService: Symbol('EscortDispatchOrderService'),
  EscortDispatchHandoffService: Symbol('EscortDispatchHandoffService'),
  DispatchAfterSalesStaffService: Symbol('DispatchAfterSalesStaffService'),
  EscortOptionPricingService: Symbol('EscortOptionPricingService'),
  EscortDispatchOrderNumberGenerator: Symbol('EscortDispatchOrderNumberGenerator'),
  EscortOptionCatalogRepository: Symbol('EscortOptionCatalogRepository'),
  DispatchNotificationService: Symbol('DispatchNotificationService'),
  DispatchPanelCommandHandler: Symbol('DispatchPanelCommandHandler'),
  DispatchPanelInteractionHandler: Symbol('DispatchPanelInteractionHandler'),
} as const;

/**
 * Initializes the DI container with all dispatch services and repositories
 * registered as singletons.
 *
 * Call this after shared's initializeContainer().
 * Expected preregistered tokens: TOKENS.DatabasePool, TOKENS.DiscordRuntimeGateway.
 */
export function configureDispatchContainer(): void {
  const db = container.resolve<NodePgDatabase>(TOKENS.DatabasePool);

  // ============================================================
  // Repositories (singleton instances)
  // ============================================================

  const dispatchOrderRepo = new DrizzleEscortDispatchOrderRepo(db);
  const optionPriceRepo = new DrizzleEscortOptionPriceRepo(db);
  const afterSalesStaffRepo = new DrizzleDispatchAfterSalesStaffRepo(db);

  container.registerInstance(DISPATCH_TOKENS.EscortDispatchOrderRepo, dispatchOrderRepo);
  container.registerInstance(DISPATCH_TOKENS.EscortOptionPriceRepo, optionPriceRepo);
  container.registerInstance(DISPATCH_TOKENS.DispatchAfterSalesStaffRepo, afterSalesStaffRepo);

  // ============================================================
  // Domain singletons
  // ============================================================

  const orderNumberGenerator = new EscortDispatchOrderNumberGenerator();
  container.registerInstance(DISPATCH_TOKENS.EscortDispatchOrderNumberGenerator, orderNumberGenerator);

  // ============================================================
  // Services (singleton instances)
  // ============================================================

  const dispatchOrderService = new EscortDispatchOrderService(
    dispatchOrderRepo,
    orderNumberGenerator,
  );
  container.registerInstance(DISPATCH_TOKENS.EscortDispatchOrderService, dispatchOrderService);

  const handoffService = new EscortDispatchHandoffService(dispatchOrderRepo);
  container.registerInstance(DISPATCH_TOKENS.EscortDispatchHandoffService, handoffService);

  const afterSalesStaffService = new DispatchAfterSalesStaffService(afterSalesStaffRepo);
  container.registerInstance(
    DISPATCH_TOKENS.DispatchAfterSalesStaffService,
    afterSalesStaffService,
  );

  // Stub catalog repo until a production implementation is ported.
  const stubCatalogRepo: EscortOptionCatalogRepository = {
    async findAll() { return []; },
    async findByCode() { return null; },
    async existsByCode() { return false; },
  };
  container.registerInstance<EscortOptionCatalogRepository>(
    DISPATCH_TOKENS.EscortOptionCatalogRepository,
    stubCatalogRepo,
  );

  const optionPricingService = new EscortOptionPricingService(optionPriceRepo, stubCatalogRepo);
  container.registerInstance(DISPATCH_TOKENS.EscortOptionPricingService, optionPricingService);

  // ============================================================
  // Notification (singleton instance, depends on DiscordRuntimeGateway)
  // ============================================================

  const discordRuntimeGateway = container.resolve<DiscordRuntimeGateway>(
    TOKENS.DiscordRuntimeGateway,
  );

  const notificationService = new DispatchNotificationService(discordRuntimeGateway);
  container.registerInstance(
    DISPATCH_TOKENS.DispatchNotificationService,
    notificationService,
  );

  // ============================================================
  // Panel handlers (singleton instances)
  // ============================================================

  const panelCommandHandler = new DispatchPanelCommandHandler(dispatchOrderService);
  container.registerInstance(
    DISPATCH_TOKENS.DispatchPanelCommandHandler,
    panelCommandHandler,
  );

  const panelInteractionHandler = new DispatchPanelInteractionHandler(
    dispatchOrderService,
    optionPricingService,
    afterSalesStaffService,
  );
  container.registerInstance(
    DISPATCH_TOKENS.DispatchPanelInteractionHandler,
    panelInteractionHandler,
  );
}
