// ============================================================
// Schema
// ============================================================

export {
  escortDispatchOrder,
  guildEscortOptionPrice,
  dispatchAfterSalesStaff,
} from './schema/index.js';

export type {
  EscortDispatchOrderSelect,
  EscortDispatchOrderInsert,
  GuildEscortOptionPriceSelect,
  GuildEscortOptionPriceInsert,
  DispatchAfterSalesStaffSelect,
  DispatchAfterSalesStaffInsert,
} from './schema/index.js';

// ============================================================
// Domain
// ============================================================

export {
  EscortDispatchOrderStatus,
  SourceType,
  CUSTOMER_CONFIRM_TIMEOUT_MS,
  createPending,
  createManualOpenOrder,
  createPendingFull,
  createAutoHandoff,
  fromDbRow,
  isPendingEscortConfirmation,
  isConfirmed,
  isPendingCustomerConfirmation,
  isAfterSalesRequested,
  isAfterSalesInProgress,
  isCompleted,
  canBeConfirmedBy,
  canBeCompletedByEscort,
  canBeConfirmedByCustomer,
  isAfterSalesAssignee,
  isManualSource,
  isAutoSource,
  hasCustomerConfirmationTimedOut,
  withAssignedEscort,
  withConfirmed,
  withCompletionRequested,
  withCompleted,
  withAfterSalesRequested,
  withAfterSalesInProgress,
  withAfterSalesClosed,
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

export {
  DrizzleEscortDispatchOrderRepo,
  DrizzleEscortOptionPriceRepo,
  DrizzleDispatchAfterSalesStaffRepo,
} from './repo/index.js';

// ============================================================
// Services
// ============================================================

export {
  EscortDispatchOrderService,
  EscortDispatchHandoffService,
  type HandoffProductSnapshot,
  DispatchAfterSalesStaffService,
  EscortOptionPricingService,
  type EscortOptionCatalogEntry,
  type EscortOptionCatalogRepository,
} from './service/index.js';

// ============================================================
// DI
// ============================================================

export { DISPATCH_TOKENS, configureDispatchContainer } from './di/index.js';
// Events (re-exported from shared for convenience)
export type {
  DispatchAfterSalesConfigChangedEvent,
  EscortPricingChangedEvent,
  EscortCatalogChangedEvent,
} from "@ltdjms/shared";
