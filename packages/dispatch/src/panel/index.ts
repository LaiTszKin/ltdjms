export { DispatchPanelCommandHandler } from './DispatchPanelCommandHandler.js';
export { DispatchPanelInteractionHandler } from './DispatchPanelInteractionHandler.js';
export { DispatchPanelSessionManager, type DispatchSessionState } from './DispatchPanelSessionManager.js';

export { COLOR_INFO, COLOR_WARNING, COLOR_ERROR } from '../constants.js';

export {
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
} from './DispatchPanelView.js';

export {
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
} from './DispatchPanelMessageFactory.js';
