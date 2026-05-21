// ============================================================
// i18n
// ============================================================
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
export { AdminPanelViewState } from './session/index.js';
export type { AdminPanelSessionData, PanelSessionData } from './session/index.js';

// ============================================================
// DI
// ============================================================
export { configureAdminContainer, ADMIN_TOKENS } from './di/index.js';
