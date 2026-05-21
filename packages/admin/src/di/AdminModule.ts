import { container, TOKENS } from '@ltdjms/shared';
import type { DomainEventPublisher, DiscordRuntimeGateway } from '@ltdjms/shared';
import type { BalanceService, BalanceAdjustmentService, CurrencyConfigService } from '@ltdjms/economy';
import type { CurrencyTransactionService, GameTokenService, GameTokenTransactionService } from '@ltdjms/economy';
import type { DiceConfigService } from '@ltdjms/economy';
import type {
  BalanceHandler,
  CurrencyConfigHandler,
  DiceGame1Handler,
  DiceGame2Handler,
  DiceGame1ConfigHandler,
  DiceGame2ConfigHandler,
  GameTokenAdjustHandler,
} from '@ltdjms/economy';
import { ECONOMY_TOKENS } from '@ltdjms/economy';
import type { RedemptionService, ShopService, RedemptionCodeRepository, RedemptionCodeGenerator, RedemptionTransactionService } from '@ltdjms/shop';
import { SHOP_TOKENS } from '@ltdjms/shop';
import type { AIChannelRestrictionService, AIAgentChannelConfigService } from '@ltdjms/ai';
import { AI_TOKENS } from '@ltdjms/ai';
import type {
  DispatchAfterSalesStaffService,
  EscortOptionPricingService,
  EscortCatalogService,
  EscortDispatchOrderService,
} from '@ltdjms/dispatch';
import { DISPATCH_TOKENS } from '@ltdjms/dispatch';
import type { ShopCommandHandler } from '@ltdjms/shop';

// Facades
import { CurrencyManagementFacade } from '../facades/CurrencyManagementFacade.js';
import { GameTokenManagementFacade } from '../facades/GameTokenManagementFacade.js';
import { GameConfigManagementFacade } from '../facades/GameConfigManagementFacade.js';
import { AIConfigManagementFacade } from '../facades/AIConfigManagementFacade.js';
import { MemberInfoFacade } from '../facades/MemberInfoFacade.js';
import { DispatchManagementFacade } from '../facades/DispatchManagementFacade.js';
import { ProductManagementFacade } from '../facades/ProductManagementFacade.js';

// Session
import { AdminPanelSessionManager } from '../session/AdminPanelSessionManager.js';
import { PanelSessionManager } from '../session/PanelSessionManager.js';

// Infra
import type { CommandHandler } from '../commands/infra/CommandHandler.js';
import { SlashCommandListener } from '../commands/infra/SlashCommandListener.js';
import { SlashCommandMetrics } from '../commands/infra/SlashCommandMetrics.js';
import { BotErrorHandler } from '../commands/infra/BotErrorHandler.js';

// Handlers
import { AdminPanelCommand } from '../panel/admin/AdminPanelCommand.js';
import { AdminPanelRouter } from '../panel/admin/AdminPanelRouter.js';
import { BalanceManagementHandler } from '../panel/admin/handlers/BalanceManagementHandler.js';
import { TokenManagementHandler } from '../panel/admin/handlers/TokenManagementHandler.js';
import { GameSettingsHandler } from '../panel/admin/handlers/GameSettingsHandler.js';
import { AIChannelConfigHandler } from '../panel/admin/handlers/AIChannelConfigHandler.js';
import { AIAgentConfigHandler } from '../panel/admin/handlers/AIAgentConfigHandler.js';
import { DispatchAfterSalesHandler } from '../panel/admin/handlers/DispatchAfterSalesHandler.js';
import { EscortPricingHandler } from '../panel/admin/handlers/EscortPricingHandler.js';
import { EscortCatalogHandler } from '../panel/admin/handlers/EscortCatalogHandler.js';
import { AdminProductPanelHandler } from '../panel/admin/product/AdminProductPanelHandler.js';

// User panel
import { UserPanelCommand } from '../panel/user/UserPanelCommand.js';
import { TransactionHistoryHandler } from '../panel/user/handlers/TransactionHistoryHandler.js';
import { RedemptionCodeHandler } from '../panel/user/handlers/RedemptionCodeHandler.js';

// Listeners
import { AdminPanelUpdateListener } from '../panel/listeners/AdminPanelUpdateListener.js';
import { UserPanelUpdateListener } from '../panel/listeners/UserPanelUpdateListener.js';

// Views
import { AdminPanelViewFactory } from '../panel/admin/views/AdminPanelViewFactory.js';
import { AdminPanelModalFactory } from '../panel/admin/views/AdminPanelModalFactory.js';
import { AdminProductPanelViewFactory } from '../panel/admin/product/AdminProductPanelViewFactory.js';
import { AdminProductPanelModalFactory } from '../panel/admin/product/AdminProductPanelModalFactory.js';
import { UserPanelEmbedBuilder } from '../panel/user/UserPanelEmbedBuilder.js';

/** Module-level handler references for DomainEventPublisher unregister support. */
let _adminUpdateHandler: ((event: unknown) => void) | null = null;
let _userUpdateHandler: ((event: unknown) => void) | null = null;

/**
 * DI tokens for the admin module.
 */
export const ADMIN_TOKENS = {
  // Facades
  CurrencyManagementFacade: Symbol('CurrencyManagementFacade'),
  GameTokenManagementFacade: Symbol('GameTokenManagementFacade'),
  GameConfigManagementFacade: Symbol('GameConfigManagementFacade'),
  AIConfigManagementFacade: Symbol('AIConfigManagementFacade'),
  MemberInfoFacade: Symbol('MemberInfoFacade'),
  DispatchManagementFacade: Symbol('DispatchManagementFacade'),

  // Shop / Product facades
  ProductManagementFacade: Symbol('ProductManagementFacade'),

  // Session
  AdminPanelSessionManager: Symbol('AdminPanelSessionManager'),
  PanelSessionManager: Symbol('PanelSessionManager'),

  // Infra
  SlashCommandListener: Symbol('SlashCommandListener'),
  SlashCommandMetrics: Symbol('SlashCommandMetrics'),
  BotErrorHandler: Symbol('BotErrorHandler'),

  // Admin commands
  AdminPanelCommand: Symbol('AdminPanelCommand'),
  AdminPanelRouter: Symbol('AdminPanelRouter'),

  // Admin handlers
  BalanceManagementHandler: Symbol('BalanceManagementHandler'),
  TokenManagementHandler: Symbol('TokenManagementHandler'),
  GameSettingsHandler: Symbol('GameSettingsHandler'),
  AIChannelConfigHandler: Symbol('AIChannelConfigHandler'),
  AIAgentConfigHandler: Symbol('AIAgentConfigHandler'),
  DispatchAfterSalesHandler: Symbol('DispatchAfterSalesHandler'),
  EscortPricingHandler: Symbol('EscortPricingHandler'),
  EscortCatalogHandler: Symbol('EscortCatalogHandler'),
  AdminProductPanelHandler: Symbol('AdminProductPanelHandler'),

  // User commands
  UserPanelCommand: Symbol('UserPanelCommand'),
  TransactionHistoryHandler: Symbol('TransactionHistoryHandler'),
  RedemptionCodeHandler: Symbol('RedemptionCodeHandler'),

  // Listeners
  AdminPanelUpdateListener: Symbol('AdminPanelUpdateListener'),
  UserPanelUpdateListener: Symbol('UserPanelUpdateListener'),

  // Views
  AdminPanelViewFactory: Symbol('AdminPanelViewFactory'),
  AdminPanelModalFactory: Symbol('AdminPanelModalFactory'),
  AdminProductPanelViewFactory: Symbol('AdminProductPanelViewFactory'),
  AdminProductPanelModalFactory: Symbol('AdminProductPanelModalFactory'),
  UserPanelEmbedBuilder: Symbol('UserPanelEmbedBuilder'),
};

export function configureAdminContainer(): void {
  const eventPublisher = container.resolve<DomainEventPublisher>(
    TOKENS.DomainEventPublisher,
  );

  // ============================================================
  // Infra (no dependencies)
  // ============================================================

  const metrics = new SlashCommandMetrics();
  container.registerInstance(ADMIN_TOKENS.SlashCommandMetrics, metrics);

  const errorHandler = new BotErrorHandler();
  container.registerInstance(ADMIN_TOKENS.BotErrorHandler, errorHandler);

  const slashCommandListener = new SlashCommandListener(metrics, errorHandler);
  container.registerInstance(
    ADMIN_TOKENS.SlashCommandListener,
    slashCommandListener,
  );

  // ============================================================
  // Session
  // ============================================================

  let cacheService: import('@ltdjms/shared').CacheService | undefined;
  try {
    cacheService = container.resolve<import('@ltdjms/shared').CacheService>(TOKENS.CacheService);
  } catch {
    // CacheService not registered; session managers will use in-memory storage only
  }

  const adminSessionManager = new AdminPanelSessionManager(cacheService);
  container.registerInstance(
    ADMIN_TOKENS.AdminPanelSessionManager,
    adminSessionManager,
  );

  const panelSessionManager = new PanelSessionManager(cacheService);
  container.registerInstance(
    ADMIN_TOKENS.PanelSessionManager,
    panelSessionManager,
  );

  adminSessionManager.startCleanupInterval();
  panelSessionManager.startCleanupInterval();

  // ============================================================
  // Views (stateless, no dependencies)
  // ============================================================

  const adminPanelViewFactory = new AdminPanelViewFactory();
  container.registerInstance(ADMIN_TOKENS.AdminPanelViewFactory, adminPanelViewFactory);

  const adminPanelModalFactory = new AdminPanelModalFactory();
  container.registerInstance(ADMIN_TOKENS.AdminPanelModalFactory, adminPanelModalFactory);

  const adminProductPanelViewFactory = new AdminProductPanelViewFactory();
  container.registerInstance(ADMIN_TOKENS.AdminProductPanelViewFactory, adminProductPanelViewFactory);

  const adminProductPanelModalFactory = new AdminProductPanelModalFactory();
  container.registerInstance(ADMIN_TOKENS.AdminProductPanelModalFactory, adminProductPanelModalFactory);

  const userPanelEmbedBuilder = new UserPanelEmbedBuilder();
  container.registerInstance(ADMIN_TOKENS.UserPanelEmbedBuilder, userPanelEmbedBuilder);

  // ============================================================
  // Facades
  // ============================================================

  const balanceService = container.resolve<BalanceService>(ECONOMY_TOKENS.BalanceService);
  const balanceAdjustmentService = container.resolve<BalanceAdjustmentService>(ECONOMY_TOKENS.BalanceAdjustmentService);
  const currencyConfigService = container.resolve<CurrencyConfigService>(ECONOMY_TOKENS.CurrencyConfigService);

  const currencyFacade = new CurrencyManagementFacade(
    balanceService,
    balanceAdjustmentService,
    currencyConfigService,
  );
  container.registerInstance(ADMIN_TOKENS.CurrencyManagementFacade, currencyFacade);

  const gameTokenService = container.resolve<GameTokenService>(ECONOMY_TOKENS.GameTokenService);
  const gameTokenTxService = container.resolve<GameTokenTransactionService>(ECONOMY_TOKENS.GameTokenTransactionService);

  const tokenFacade = new GameTokenManagementFacade(
    gameTokenService,
    gameTokenTxService,
  );
  container.registerInstance(ADMIN_TOKENS.GameTokenManagementFacade, tokenFacade);

  const diceConfigService = container.resolve<DiceConfigService>(ECONOMY_TOKENS.DiceConfigService);

  const gameConfigFacade = new GameConfigManagementFacade(
    diceConfigService,
  );
  container.registerInstance(ADMIN_TOKENS.GameConfigManagementFacade, gameConfigFacade);

  const channelRestrictionService = container.resolve<AIChannelRestrictionService>(AI_TOKENS.AIChannelRestrictionService);
  const agentConfigService = container.resolve<AIAgentChannelConfigService>(AI_TOKENS.AIAgentChannelConfigService);

  const aiConfigFacade = new AIConfigManagementFacade(
    channelRestrictionService,
    agentConfigService,
  );
  container.registerInstance(ADMIN_TOKENS.AIConfigManagementFacade, aiConfigFacade);

  const currencyTxService = container.resolve<CurrencyTransactionService>(ECONOMY_TOKENS.CurrencyTransactionService);
  const redemptionService = container.resolve<RedemptionService>(SHOP_TOKENS.RedemptionService);

  let redemptionTxService: RedemptionTransactionService | undefined;
  try {
    redemptionTxService = container.resolve<RedemptionTransactionService>(SHOP_TOKENS.RedemptionTransactionService);
  } catch {
    // RedemptionTransactionService not available
  }

  const memberInfoFacade = new MemberInfoFacade(
    balanceService,
    gameTokenService,
    currencyTxService,
    gameTokenTxService,
    redemptionService,
    redemptionTxService,
  );
  container.registerInstance(ADMIN_TOKENS.MemberInfoFacade, memberInfoFacade);

  const dispatchManagementFacade = new DispatchManagementFacade(
    container.resolve<DispatchAfterSalesStaffService>(DISPATCH_TOKENS.DispatchAfterSalesStaffService),
    container.resolve<EscortOptionPricingService>(DISPATCH_TOKENS.EscortOptionPricingService),
    container.resolve<EscortCatalogService>(DISPATCH_TOKENS.EscortCatalogService),
    eventPublisher,
    container.resolve<EscortDispatchOrderService>(DISPATCH_TOKENS.EscortDispatchOrderService),
  );
  container.registerInstance(ADMIN_TOKENS.DispatchManagementFacade, dispatchManagementFacade);

  // ============================================================
  // Economy Command Handlers
  // ============================================================

  slashCommandListener.registerCommands([
    container.resolve<BalanceHandler>(ECONOMY_TOKENS.BalanceHandler),
    container.resolve<CurrencyConfigHandler>(ECONOMY_TOKENS.CurrencyConfigHandler),
    container.resolve<DiceGame1Handler>(ECONOMY_TOKENS.DiceGame1Handler),
    container.resolve<DiceGame2Handler>(ECONOMY_TOKENS.DiceGame2Handler),
    container.resolve<DiceGame1ConfigHandler>(ECONOMY_TOKENS.DiceGame1ConfigHandler),
    container.resolve<DiceGame2ConfigHandler>(ECONOMY_TOKENS.DiceGame2ConfigHandler),
    container.resolve<GameTokenAdjustHandler>(ECONOMY_TOKENS.GameTokenAdjustHandler),
  ]);

  // ============================================================
  // Admin Panel Commands
  // ============================================================

  const discordEmbedBuilder = container.resolve<import('@ltdjms/shared').DiscordEmbedBuilder>(
    TOKENS.DiscordEmbedBuilder,
  );

  const adminPanelCommand = new AdminPanelCommand(
    adminSessionManager,
    adminPanelViewFactory,
    currencyFacade,
    dispatchManagementFacade,
    discordEmbedBuilder,
  );
  container.registerInstance(ADMIN_TOKENS.AdminPanelCommand, adminPanelCommand);
  slashCommandListener.registerCommand(adminPanelCommand);

  const adminPanelRouter = new AdminPanelRouter(adminSessionManager);
  container.registerInstance(ADMIN_TOKENS.AdminPanelRouter, adminPanelRouter);
  slashCommandListener.registerInteractionHandler(adminPanelRouter);

  // ============================================================
  // Admin Panel Handlers
  // ============================================================

  const balanceHandler = new BalanceManagementHandler(
    currencyFacade,
    adminPanelModalFactory,
    adminSessionManager,
    errorHandler,
  );
  container.registerInstance(ADMIN_TOKENS.BalanceManagementHandler, balanceHandler);
  slashCommandListener.registerInteractionHandler(balanceHandler);

  const tokenHandler = new TokenManagementHandler(
    tokenFacade,
    adminPanelModalFactory,
    adminSessionManager,
    errorHandler,
  );
  container.registerInstance(ADMIN_TOKENS.TokenManagementHandler, tokenHandler);
  slashCommandListener.registerInteractionHandler(tokenHandler);

  const gameHandler = new GameSettingsHandler(
    gameConfigFacade,
    adminPanelViewFactory,
    adminSessionManager,
    errorHandler,
  );
  container.registerInstance(ADMIN_TOKENS.GameSettingsHandler, gameHandler);
  slashCommandListener.registerInteractionHandler(gameHandler);

  const aiChannelHandler = new AIChannelConfigHandler(
    aiConfigFacade,
    adminSessionManager,
    errorHandler,
  );
  container.registerInstance(ADMIN_TOKENS.AIChannelConfigHandler, aiChannelHandler);
  slashCommandListener.registerInteractionHandler(aiChannelHandler);

  const aiAgentHandler = new AIAgentConfigHandler(
    aiConfigFacade,
    adminSessionManager,
    errorHandler,
  );
  container.registerInstance(ADMIN_TOKENS.AIAgentConfigHandler, aiAgentHandler);
  slashCommandListener.registerInteractionHandler(aiAgentHandler);

  const dispatchHandler = new DispatchAfterSalesHandler(
    adminSessionManager,
    dispatchManagementFacade,
    errorHandler,
  );
  container.registerInstance(ADMIN_TOKENS.DispatchAfterSalesHandler, dispatchHandler);
  slashCommandListener.registerInteractionHandler(dispatchHandler);

  const escortPriceHandler = new EscortPricingHandler(
    adminSessionManager,
    dispatchManagementFacade,
    adminPanelModalFactory,
    errorHandler,
  );
  container.registerInstance(ADMIN_TOKENS.EscortPricingHandler, escortPriceHandler);
  slashCommandListener.registerInteractionHandler(escortPriceHandler);

  const escortCatalogHandler = new EscortCatalogHandler(
    adminSessionManager,
    dispatchManagementFacade,
    adminPanelModalFactory,
    errorHandler,
  );
  container.registerInstance(ADMIN_TOKENS.EscortCatalogHandler, escortCatalogHandler);
  slashCommandListener.registerInteractionHandler(escortCatalogHandler);

  const dispatchPanelCommandHandler = container.resolve<CommandHandler>(
    DISPATCH_TOKENS.DispatchPanelCommandHandler,
  );
  slashCommandListener.registerCommand(dispatchPanelCommandHandler);

  const shopService = container.resolve<ShopService>(SHOP_TOKENS.ShopService);
  const redemptionCodeRepo = container.resolve<RedemptionCodeRepository>(SHOP_TOKENS.RedemptionCodeRepository);
  const redemptionCodeGenerator = container.resolve<RedemptionCodeGenerator>(SHOP_TOKENS.RedemptionCodeGenerator);

  const productRepository = container.resolve<import('@ltdjms/shop').ProductRepository>(
    SHOP_TOKENS.ProductRepository as symbol,
  );

  const productManagementFacade = new ProductManagementFacade(
    shopService,
    productRepository,
    redemptionCodeRepo,
    redemptionCodeGenerator,
    eventPublisher,
  );
  container.registerInstance(ADMIN_TOKENS.ProductManagementFacade, productManagementFacade);

  const adminProductPanelHandler = new AdminProductPanelHandler(
    adminSessionManager,
    productManagementFacade,
    adminProductPanelViewFactory,
    adminProductPanelModalFactory,
    errorHandler,
  );
  container.registerInstance(ADMIN_TOKENS.AdminProductPanelHandler, adminProductPanelHandler);
  slashCommandListener.registerInteractionHandler(adminProductPanelHandler);

  const shopCommandHandler = container.resolve<ShopCommandHandler>(SHOP_TOKENS.ShopCommandHandler);
  slashCommandListener.registerCommand(shopCommandHandler);

  // ============================================================
  // User Panel Commands
  // ============================================================

  const userPanelCommand = new UserPanelCommand(
    memberInfoFacade,
    panelSessionManager,
    userPanelEmbedBuilder,
  );
  container.registerInstance(ADMIN_TOKENS.UserPanelCommand, userPanelCommand);
  slashCommandListener.registerCommand(userPanelCommand);

  const txHistoryHandler = new TransactionHistoryHandler(
    memberInfoFacade,
    panelSessionManager,
  );
  container.registerInstance(ADMIN_TOKENS.TransactionHistoryHandler, txHistoryHandler);
  slashCommandListener.registerInteractionHandler(txHistoryHandler);

  const redeemHandler = new RedemptionCodeHandler(
    memberInfoFacade,
    panelSessionManager,
  );
  container.registerInstance(ADMIN_TOKENS.RedemptionCodeHandler, redeemHandler);
  slashCommandListener.registerInteractionHandler(redeemHandler);

  // ============================================================
  // Listeners (register with DomainEventPublisher)
  // ============================================================

  const discordGateway = container.resolve<DiscordRuntimeGateway>(TOKENS.DiscordRuntimeGateway);

  slashCommandListener.listen(discordGateway);

  const adminUpdateListener = new AdminPanelUpdateListener(
    adminSessionManager,
    discordGateway,
    currencyFacade,
    dispatchManagementFacade,
    adminPanelViewFactory,
  );
  container.registerInstance(ADMIN_TOKENS.AdminPanelUpdateListener, adminUpdateListener);
  _adminUpdateHandler = (event: unknown): void => {
    adminUpdateListener.onEvent(event as any).catch((err: unknown) => {
      console.error('[AdminPanelUpdateListener] Error:', err);
    });
  };
  eventPublisher.register(_adminUpdateHandler);

  const userUpdateListener = new UserPanelUpdateListener(
    panelSessionManager,
    memberInfoFacade,
    discordGateway,
    userPanelEmbedBuilder,
  );
  container.registerInstance(ADMIN_TOKENS.UserPanelUpdateListener, userUpdateListener);
  _userUpdateHandler = (event: unknown): void => {
    userUpdateListener.onEvent(event as any).catch((err: unknown) => {
      console.error('[UserPanelUpdateListener] Error:', err);
    });
  };
  eventPublisher.register(_userUpdateHandler);

}

/**
 * Disposes admin module resources. Should be called during application shutdown.
 * Stops session cleanup intervals and unregisters domain event listeners
 * to prevent memory leaks.
 */
export function disposeAdminContainer(): void {
  try {
    const mgr = container.resolve<AdminPanelSessionManager>(ADMIN_TOKENS.AdminPanelSessionManager);
    mgr.stopCleanupInterval();
  } catch {
    // Session manager not registered; nothing to dispose
  }

  try {
    const mgr = container.resolve<PanelSessionManager>(ADMIN_TOKENS.PanelSessionManager);
    mgr.stopCleanupInterval();
  } catch {
    // Session manager not registered; nothing to dispose
  }

  // Unregister domain event listeners
  try {
    const publisher = container.resolve<DomainEventPublisher>(
      TOKENS.DomainEventPublisher,
    );
    if (_adminUpdateHandler) {
      publisher.unregister(_adminUpdateHandler);
      _adminUpdateHandler = null;
    }
    if (_userUpdateHandler) {
      publisher.unregister(_userUpdateHandler);
      _userUpdateHandler = null;
    }
  } catch {
    // DomainEventPublisher not available
  }
}
