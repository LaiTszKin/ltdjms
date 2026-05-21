// ============================================================
// i18n
// ============================================================
export { ZhTwStrings } from './i18n/index.js';
export type { ZhTwStringsType } from './i18n/index.js';

// ============================================================
// Facades
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
// Command Infrastructure
// ============================================================
export type { CommandHandler, InteractionHandler } from './commands/infra/CommandHandler.js';
export { SlashCommandListener } from './commands/infra/SlashCommandListener.js';
export { SlashCommandMetrics } from './commands/infra/SlashCommandMetrics.js';
export type { CommandStats } from './commands/infra/SlashCommandMetrics.js';
export { BotErrorHandler } from './commands/infra/BotErrorHandler.js';

// ============================================================
// Admin Panel
// ============================================================
export { AdminPanelCommand } from './panel/admin/AdminPanelCommand.js';
export { AdminPanelRouter } from './panel/admin/AdminPanelRouter.js';
export { AdminPanelSlashCommand } from './panel/admin/definitions/AdminPanelSlashCommand.js';

export {
  BalanceManagementHandler,
  TokenManagementHandler,
  GameSettingsHandler,
  AIChannelConfigHandler,
  AIAgentConfigHandler,
  DispatchAfterSalesHandler,
  EscortPricingHandler,
  EscortCatalogHandler,
} from './panel/admin/handlers/index.js';

export { AdminPanelViewFactory } from './panel/admin/views/AdminPanelViewFactory.js';
export { AdminPanelModalFactory } from './panel/admin/views/AdminPanelModalFactory.js';

export { AdminProductPanelHandler } from './panel/admin/product/AdminProductPanelHandler.js';
export { AdminProductPanelViewFactory } from './panel/admin/product/AdminProductPanelViewFactory.js';
export { AdminProductPanelModalFactory } from './panel/admin/product/AdminProductPanelModalFactory.js';

// ============================================================
// User Panel
// ============================================================
export { UserPanelCommand } from './panel/user/UserPanelCommand.js';
export { UserPanelEmbedBuilder } from './panel/user/UserPanelEmbedBuilder.js';
export { TransactionHistoryHandler } from './panel/user/handlers/TransactionHistoryHandler.js';
export { RedemptionCodeHandler } from './panel/user/handlers/RedemptionCodeHandler.js';
export { UserPanelSlashCommand } from './panel/user/definitions/UserPanelSlashCommand.js';

// ============================================================
// Listeners
// ============================================================
export { AdminPanelUpdateListener, UserPanelUpdateListener } from './panel/listeners/index.js';

// ============================================================
// DI
// ============================================================
export { configureAdminContainer, ADMIN_TOKENS } from './di/index.js';

// ============================================================
// Slash Command Registration
// ============================================================
export { SlashCommandRegistrar } from './commands/registration/SlashCommandRegistrar.js';
