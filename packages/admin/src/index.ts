// ============================================================
// i18n
// ============================================================
export { ZhTwStrings } from './i18n/index.js';
export type { ZhTwStringsType } from './i18n/index.js';

// ============================================================
// Facades (7 facades)
// ============================================================
export {
  CurrencyManagementFacade,
  GameTokenManagementFacade,
  GameConfigManagementFacade,
  type DiceGame1ConfigUpdate,
  type DiceGame2ConfigUpdate,
  AIConfigManagementFacade,
  MemberInfoFacade,
  type MemberPanelView,
  DispatchManagementFacade,
  ProductManagementFacade,
} from './facades/index.js';

// ============================================================
// Session
// ============================================================
export {
  AdminPanelViewState,
  AdminPanelSessionManager,
  PanelSessionManager,
} from './session/index.js';
export type { AdminPanelSessionData, PanelSessionData } from './session/index.js';

// ============================================================
// Listeners
// ============================================================
export { AdminPanelUpdateListener, UserPanelUpdateListener } from './panel/listeners/index.js';

// ============================================================
// DI
// ============================================================
export { configureAdminContainer, ADMIN_TOKENS } from './di/index.js';
