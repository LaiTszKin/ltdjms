import { container, TOKENS } from '@ltdjms/shared';
// Repositories
import { DrizzleEscortDispatchOrderRepo } from '../repo/drizzle-escort-dispatch-order.repo.js';
import { DrizzleEscortOptionPriceRepo } from '../repo/drizzle-escort-option-price.repo.js';
import { DrizzleDispatchAfterSalesStaffRepo } from '../repo/drizzle-dispatch-after-sales-staff.repo.js';
// Services
import { EscortDispatchOrderService } from '../service/escort-dispatch-order.service.js';
import { EscortDispatchHandoffService } from '../service/escort-dispatch-handoff.service.js';
import { DispatchAfterSalesStaffService } from '../service/dispatch-after-sales-staff.service.js';
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
};
/**
 * Initializes the DI container with all dispatch services and repositories
 * registered as singletons.
 *
 * Call this after shared's initializeContainer().
 * Expected preregistered tokens: TOKENS.DatabasePool.
 */
export function configureDispatchContainer() {
    const db = container.resolve(TOKENS.DatabasePool);
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
    // Services (singleton instances)
    // ============================================================
    const dispatchOrderService = new EscortDispatchOrderService(dispatchOrderRepo);
    container.registerInstance(DISPATCH_TOKENS.EscortDispatchOrderService, dispatchOrderService);
    const handoffService = new EscortDispatchHandoffService(dispatchOrderRepo);
    container.registerInstance(DISPATCH_TOKENS.EscortDispatchHandoffService, handoffService);
    const afterSalesStaffService = new DispatchAfterSalesStaffService(afterSalesStaffRepo);
    container.registerInstance(DISPATCH_TOKENS.DispatchAfterSalesStaffService, afterSalesStaffService);
}
//# sourceMappingURL=dispatch-module.js.map