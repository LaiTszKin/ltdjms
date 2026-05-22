// ============================================================
// i18n
// ============================================================
export type { ZhTwStringsType } from './i18n/index.js';

// ============================================================
// Facades (7 facades)
// ============================================================
export {
  CurrencyManagementFacade,
  AIConfigManagementFacade,
  MemberInfoFacade,
  type MemberPanelView,
  DispatchManagementFacade,
  ProductManagementFacade,
} from './facades/index.js';

export { AgentMode } from './facades/agent-mode.js';

// ============================================================
// Session
// ============================================================
export { AdminPanelViewState } from './session/index.js';
export type { AdminPanelSessionData, PanelSessionData } from './session/index.js';

// ============================================================
// DI
// ============================================================
export { configureAdminContainer, disposeAdminContainer, ADMIN_TOKENS } from './di/index.js';

// ============================================================
// Commands
// ============================================================
export { SlashCommandRegistrar } from './commands/registration/SlashCommandRegistrar.js';
