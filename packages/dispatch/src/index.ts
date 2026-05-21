// ============================================================
// Domain
// ============================================================

export {
  EscortDispatchOrderStatus,
  SourceType,
  EscortDispatchOrderNumberGenerator,
  optionPriceToDisplayLine,
} from './domain/index.js';

export type {
  EscortDispatchOrder,
  EscortOrderOption,
  OptionPriceView,
} from './domain/index.js';

// ============================================================
// Repositories
// ============================================================

export type {
  EscortDispatchOrderRepo,
  EscortOptionPriceRepo,
  DispatchAfterSalesStaffRepo,
} from './repo/index.js';

// ============================================================
// Services
// ============================================================

export {
  EscortDispatchOrderService,
  EscortDispatchHandoffService,
  DispatchAfterSalesStaffService,
  EscortOptionPricingService,
  EscortCatalogService,
  type EscortOptionCatalogEntry,
  type CreateCatalogData,
  type UpdateCatalogData,
} from './service/index.js';

// ============================================================
// DI
// ============================================================

export { DISPATCH_TOKENS, configureDispatchContainer } from './di/index.js';

// ============================================================
// Events
// ============================================================

export type {
  DispatchAfterSalesConfigChangedEvent,
  EscortPricingChangedEvent,
  EscortCatalogChangedEvent,
} from './events/index.js';
