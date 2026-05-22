import { container, TOKENS, type DiscordRuntimeGateway, type TokenMap } from '@ltdjms/shared';
import { drizzle } from 'drizzle-orm/node-postgres';
import { type Pool } from 'pg';

// Repositories
import { DrizzleEscortDispatchOrderRepo } from '../repo/drizzle-escort-dispatch-order.repo.js';
import { DrizzleEscortOptionPriceRepo } from '../repo/drizzle-escort-option-price.repo.js';
import { DrizzleDispatchAfterSalesStaffRepo } from '../repo/drizzle-dispatch-after-sales-staff.repo.js';
import { DrizzleEscortOptionCatalogRepo } from '../repo/drizzle-escort-option-catalog.repo.js';

// Domain
import { EscortDispatchOrderNumberGenerator } from '../domain/order-number-generator.js';

// Services
import { EscortDispatchOrderService } from '../service/escort-dispatch-order.service.js';
import { EscortDispatchHandoffService } from '../service/escort-dispatch-handoff.service.js';
import { DispatchAfterSalesStaffService } from '../service/dispatch-after-sales-staff.service.js';
import { EscortCatalogService } from '../service/escort-catalog.service.js';
import { EscortOptionPricingService } from '../service/escort-option-pricing.service.js';
import type { EscortOptionCatalogRepository } from '../repo/escort-option-catalog.repo.js';

// Notification
import { DispatchNotificationService } from '../notification/DispatchNotificationService.js';

// Panel
import { DispatchPanelCommandHandler } from '../panel/DispatchPanelCommandHandler.js';
import { DispatchPanelInteractionHandler } from '../panel/DispatchPanelInteractionHandler.js';
import { DispatchPanelSessionManager } from '../panel/DispatchPanelSessionManager.js';

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
  EscortCatalogService: Symbol('EscortCatalogService'),
  EscortDispatchOrderNumberGenerator: Symbol('EscortDispatchOrderNumberGenerator'),
  EscortOptionCatalogRepository: Symbol('EscortOptionCatalogRepository'),
  DispatchNotificationService: Symbol('DispatchNotificationService'),
  DispatchPanelCommandHandler: Symbol('DispatchPanelCommandHandler'),
  DispatchPanelInteractionHandler: Symbol('DispatchPanelInteractionHandler'),
  DispatchPanelSessionManager: Symbol('DispatchPanelSessionManager'),
} as const;

/**
 * Initializes the DI container with all dispatch services and repositories
 * registered as singletons.
 *
 * Call this after shared's initializeContainer().
 * Expected preregistered tokens: TOKENS.DatabasePool, TOKENS.DiscordRuntimeGateway.
 */
export function configureDispatchContainer(): void {
  const rawPool = container.resolve<Pool>(TOKENS.DatabasePool);
  const db = drizzle(rawPool);

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
  container.registerInstance(
    DISPATCH_TOKENS.EscortDispatchOrderNumberGenerator,
    orderNumberGenerator,
  );

  // ============================================================
  // Real catalog repo backed by escort_option_catalog table (V028)
  // ============================================================

  const catalogRepo = new DrizzleEscortOptionCatalogRepo(db);
  container.registerInstance<EscortOptionCatalogRepository>(
    DISPATCH_TOKENS.EscortOptionCatalogRepository,
    catalogRepo,
  );

  const escortCatalogService = new EscortCatalogService(catalogRepo, optionPriceRepo);
  container.registerInstance(DISPATCH_TOKENS.EscortCatalogService, escortCatalogService);

  // ============================================================
  // Shared services (resolved from container)
  // ============================================================

  const discordRuntimeGateway = container.resolve<DiscordRuntimeGateway>(
    TOKENS.DiscordRuntimeGateway,
  );
  const logger = container.resolve<TokenMap['Logger']>(TOKENS.Logger);

  // ============================================================
  // Notification (singleton instance, depends on DiscordRuntimeGateway)
  // Must be created before EscortDispatchOrderService (P2-2).
  // ============================================================

  const afterSalesStaffService = new DispatchAfterSalesStaffService(afterSalesStaffRepo);
  container.registerInstance(
    DISPATCH_TOKENS.DispatchAfterSalesStaffService,
    afterSalesStaffService,
  );

  const notificationService = new DispatchNotificationService(
    discordRuntimeGateway,
    afterSalesStaffService,
  );
  container.registerInstance(DISPATCH_TOKENS.DispatchNotificationService, notificationService);

  const dispatchOrderService = new EscortDispatchOrderService(
    dispatchOrderRepo,
    catalogRepo,
    orderNumberGenerator,
    undefined, // clock — use default Date.now
    afterSalesStaffService,
    logger,
    notificationService,
    discordRuntimeGateway,
  );
  container.registerInstance(DISPATCH_TOKENS.EscortDispatchOrderService, dispatchOrderService);

  const handoffService = new EscortDispatchHandoffService(dispatchOrderRepo);
  container.registerInstance(DISPATCH_TOKENS.EscortDispatchHandoffService, handoffService);

  const optionPricingService = new EscortOptionPricingService(optionPriceRepo, catalogRepo);
  container.registerInstance(DISPATCH_TOKENS.EscortOptionPricingService, optionPricingService);

  // ============================================================
  // Panel handlers (singleton instances)
  // ============================================================

  const panelCommandHandler = new DispatchPanelCommandHandler(dispatchOrderService);
  container.registerInstance(DISPATCH_TOKENS.DispatchPanelCommandHandler, panelCommandHandler);

  const sessionManager = new DispatchPanelSessionManager();
  container.registerInstance(DISPATCH_TOKENS.DispatchPanelSessionManager, sessionManager);

  const panelInteractionHandler = new DispatchPanelInteractionHandler(
    dispatchOrderService,
    optionPricingService,
    afterSalesStaffService,
    notificationService,
    sessionManager,
    discordRuntimeGateway,
  );
  container.registerInstance(
    DISPATCH_TOKENS.DispatchPanelInteractionHandler,
    panelInteractionHandler,
  );
}
