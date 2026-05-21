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
  EscortCatalogService,
  type EscortOptionCatalogEntry,
  type EscortOptionCatalogRepository,
  type CreateCatalogData,
  type UpdateCatalogData,
} from './service/index.js';

// ============================================================
// Notification
// ============================================================

export { DispatchNotificationService } from './notification/index.js';

// ============================================================
// Panel
// ============================================================

export {
  DispatchPanelCommandHandler,
  DispatchPanelInteractionHandler,
  DispatchPanelSessionManager,
  type DispatchSessionState,
  // View constants
  MODE_SELECT,
  BUTTON_CREATE_MODE,
  BUTTON_ASSIGN_MODE,
  BUTTON_VIEW_ORDERS,
  BUTTON_VIEW_HISTORY,
  BUTTON_BACK_TO_MODE,
  BUTTON_CONFIRM_ORDER,
  BUTTON_REQUEST_COMPLETION,
  BUTTON_CONFIRM_COMPLETION,
  BUTTON_REQUEST_AFTER_SALES,
  BUTTON_CLAIM_AFTER_SALES,
  BUTTON_CLOSE_AFTER_SALES,
  SELECT_ESCORT_OPTION,
  SELECT_ESCORT_OPTION_EXTRA,
  SELECT_PENDING_ORDER,
  // Colors
  COLOR_INFO,
  COLOR_WARNING,
  COLOR_ERROR,
  // Embed builders
  buildModeSelectEmbed,
  buildCreateModeEmbed,
  buildAssignModeEmbed,
  buildNoPendingOrdersEmbed,
  buildQueryFailedEmbed,
  // Button builders
  buildCreateModeButton,
  buildAssignModeButton,
  buildViewOrdersButton,
  buildViewHistoryButton,
  buildBackToModeButton,
  buildConfirmOrderButton,
  buildRequestCompletionButton,
  buildConfirmCompletionButton,
  buildRequestAfterSalesButton,
  buildClaimAfterSalesButton,
  buildCloseAfterSalesButton,
  // Select menu builders
  buildEscortOptionSelectMenu,
  splitSelectMenuOptions,
  type SelectOptionView,
  // Row builders
  buildModeSelectActionRow,
  buildOrderDetailActionRow,
  // Conversion utilities
  embedViewToApiEmbed,
  buttonsToComponents,
  buildPanelReplyPayload,
  // Message factory
  buildOrderCreatedEmbed,
  buildOrderAssignedEmbed,
  buildOrderConfirmedEmbed,
  buildPendingCustomerConfirmationEmbed,
  buildOrderCompletedEmbed,
  buildAfterSalesRequestedEmbed,
  buildAfterSalesClaimedEmbed,
  buildAfterSalesClosedEmbed,
  buildOrderDetailEmbed,
  buildOrderListEmbed,
  buildErrorEmbed,
} from './panel/index.js';

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
