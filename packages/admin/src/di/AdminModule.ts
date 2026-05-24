import { container, TOKENS } from '@ltdjms/shared';
import type { DomainEventPublisher, DiscordRuntimeGateway, DomainEvent } from '@ltdjms/shared';
import type {
  BalanceService,
  BalanceAdjustmentService,
  CurrencyConfigService,
} from '@ltdjms/economy';
import { ECONOMY_TOKENS } from '@ltdjms/economy';
import type {
  DiceGame1Handler,
  DiceGame2Handler,
  DiceGame1ConfigHandler,
  DiceGame2ConfigHandler,
  GameTokenAdjustHandler,
  GameConfigManagementFacade,
  GameTokenManagementFacade,
} from '@ltdjms/games';
import { GAMES_TOKENS } from '@ltdjms/games';
import type { BalanceHandler, CurrencyConfigHandler } from '@ltdjms/economy';
import type { ShopService, RedemptionCodeRepository, RedemptionCodeGenerator } from '@ltdjms/shop';
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
import { AIConfigManagementFacade } from '../facades/AIConfigManagementFacade.js';
import { DispatchManagementFacade } from '../facades/DispatchManagementFacade.js';
import { ProductManagementFacade } from '../facades/ProductManagementFacade.js';

// Session
import { AdminPanelSessionManager } from '../session/AdminPanelSessionManager.js';

// Infra
import type { CommandHandler, InteractionHandler } from '@ltdjms/shared';
import { SlashCommandListener } from '../commands/infra/SlashCommandListener.js';
import { SlashCommandMetrics } from '../commands/infra/SlashCommandMetrics.js';
import { BotErrorHandler } from '../commands/infra/BotErrorHandler.js';

// Handlers
import { AdminPanelCommand } from '../panel/admin/AdminPanelCommand.js';
import { AdminPanelFallbackHandler } from '../panel/admin/AdminPanelRouter.js';
import { BalanceManagementHandler } from '../panel/admin/handlers/BalanceManagementHandler.js';
import { TokenManagementHandler } from '../panel/admin/handlers/TokenManagementHandler.js';
import { GameSettingsHandler } from '../panel/admin/handlers/GameSettingsHandler.js';
import { AIChannelConfigHandler } from '../panel/admin/handlers/AIChannelConfigHandler.js';
import { AIAgentConfigHandler } from '../panel/admin/handlers/AIAgentConfigHandler.js';
import { DispatchAfterSalesHandler } from '../panel/admin/handlers/DispatchAfterSalesHandler.js';
import { EscortPricingHandler } from '../panel/admin/handlers/EscortPricingHandler.js';
import { EscortCatalogHandler } from '../panel/admin/handlers/EscortCatalogHandler.js';
import { AdminProductPanelHandler } from '../panel/admin/product/AdminProductPanelHandler.js';

// Listeners
import { AdminPanelUpdateListener } from '../panel/listeners/AdminPanelUpdateListener.js';

// Views
import { AdminPanelViewFactory } from '../panel/admin/views/AdminPanelViewFactory.js';
import { AdminPanelModalFactory } from '../panel/admin/views/AdminPanelModalFactory.js';
import { AdminProductPanelViewFactory } from '../panel/admin/product/AdminProductPanelViewFactory.js';
import { AdminProductPanelModalFactory } from '../panel/admin/product/AdminProductPanelModalFactory.js';

/** Module-level handler references for DomainEventPublisher unregister support. */
let _adminUpdateHandler: ((event: unknown) => void) | null = null;
let _adminConfigured = false;

/**
 * DI tokens for the admin module.
 */
export const ADMIN_TOKENS = {
  // Facades
  CurrencyManagementFacade: Symbol('CurrencyManagementFacade'),
  GameTokenManagementFacade: Symbol('GameTokenManagementFacade'),
  GameConfigManagementFacade: Symbol('GameConfigManagementFacade'),
  AIConfigManagementFacade: Symbol('AIConfigManagementFacade'),
  DispatchManagementFacade: Symbol('DispatchManagementFacade'),

  // Shop / Product facades
  ProductManagementFacade: Symbol('ProductManagementFacade'),

  // Session
  AdminPanelSessionManager: Symbol('AdminPanelSessionManager'),

  // Infra
  SlashCommandListener: Symbol('SlashCommandListener'),
  SlashCommandMetrics: Symbol('SlashCommandMetrics'),
  BotErrorHandler: Symbol('BotErrorHandler'),

  // Admin commands
  AdminPanelCommand: Symbol('AdminPanelCommand'),
  AdminPanelFallbackHandler: Symbol('AdminPanelFallbackHandler'),

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

  // Listeners
  AdminPanelUpdateListener: Symbol('AdminPanelUpdateListener'),

  // Views
  AdminPanelViewFactory: Symbol('AdminPanelViewFactory'),
  AdminPanelModalFactory: Symbol('AdminPanelModalFactory'),
  AdminProductPanelViewFactory: Symbol('AdminProductPanelViewFactory'),
  AdminProductPanelModalFactory: Symbol('AdminProductPanelModalFactory'),
};

export function configureAdminContainer(): void {
  if (_adminConfigured) return;

  const eventPublisher = container.resolve<DomainEventPublisher>(TOKENS.DomainEventPublisher);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const logger: any = container.resolve<any>(TOKENS.Logger);

  // ============================================================
  // Infra (no dependencies)
  // ============================================================

  const metrics = new SlashCommandMetrics();
  container.registerInstance(ADMIN_TOKENS.SlashCommandMetrics, metrics);

  const errorHandler = new BotErrorHandler();
  container.registerInstance(ADMIN_TOKENS.BotErrorHandler, errorHandler);

  const slashCommandListener = new SlashCommandListener(metrics, errorHandler, logger);
  container.registerInstance(ADMIN_TOKENS.SlashCommandListener, slashCommandListener);

  // ============================================================
  // Session
  // ============================================================

  const adminSessionManager = new AdminPanelSessionManager();
  container.registerInstance(ADMIN_TOKENS.AdminPanelSessionManager, adminSessionManager);
  adminSessionManager.startCleanupInterval();

  // ============================================================
  // Views (stateless, no dependencies)
  // ============================================================

  const adminPanelViewFactory = new AdminPanelViewFactory();
  container.registerInstance(ADMIN_TOKENS.AdminPanelViewFactory, adminPanelViewFactory);

  const adminPanelModalFactory = new AdminPanelModalFactory();
  container.registerInstance(ADMIN_TOKENS.AdminPanelModalFactory, adminPanelModalFactory);

  const adminProductPanelViewFactory = new AdminProductPanelViewFactory();
  container.registerInstance(
    ADMIN_TOKENS.AdminProductPanelViewFactory,
    adminProductPanelViewFactory,
  );

  const adminProductPanelModalFactory = new AdminProductPanelModalFactory();
  container.registerInstance(
    ADMIN_TOKENS.AdminProductPanelModalFactory,
    adminProductPanelModalFactory,
  );

  // ============================================================
  // Facades
  // ============================================================

  const balanceService = container.resolve<BalanceService>(ECONOMY_TOKENS.BalanceService);
  const balanceAdjustmentService = container.resolve<BalanceAdjustmentService>(
    ECONOMY_TOKENS.BalanceAdjustmentService,
  );
  const currencyConfigService = container.resolve<CurrencyConfigService>(
    ECONOMY_TOKENS.CurrencyConfigService,
  );

  const currencyFacade = new CurrencyManagementFacade(
    balanceService,
    balanceAdjustmentService,
    currencyConfigService,
  );
  container.registerInstance(ADMIN_TOKENS.CurrencyManagementFacade, currencyFacade);

  const tokenFacade = container.resolve<GameTokenManagementFacade>(
    GAMES_TOKENS.GameTokenManagementFacade as symbol,
  );
  container.registerInstance(ADMIN_TOKENS.GameTokenManagementFacade, tokenFacade);

  const gameConfigFacade = container.resolve<GameConfigManagementFacade>(
    GAMES_TOKENS.GameConfigManagementFacade as symbol,
  );
  container.registerInstance(ADMIN_TOKENS.GameConfigManagementFacade, gameConfigFacade);

  const channelRestrictionService = container.resolve<AIChannelRestrictionService>(
    AI_TOKENS.AIChannelRestrictionService,
  );
  const agentConfigService = container.resolve<AIAgentChannelConfigService>(
    AI_TOKENS.AIAgentChannelConfigService,
  );

  const aiConfigFacade = new AIConfigManagementFacade(
    channelRestrictionService,
    agentConfigService,
    eventPublisher,
  );
  container.registerInstance(ADMIN_TOKENS.AIConfigManagementFacade, aiConfigFacade);

  const dispatchManagementFacade = new DispatchManagementFacade(
    container.resolve<DispatchAfterSalesStaffService>(
      DISPATCH_TOKENS.DispatchAfterSalesStaffService,
    ),
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
    container.resolve<DiceGame1Handler>(GAMES_TOKENS.DiceGame1Handler as symbol),
    container.resolve<DiceGame2Handler>(GAMES_TOKENS.DiceGame2Handler as symbol),
    container.resolve<DiceGame1ConfigHandler>(GAMES_TOKENS.DiceGame1ConfigHandler as symbol),
    container.resolve<DiceGame2ConfigHandler>(GAMES_TOKENS.DiceGame2ConfigHandler as symbol),
    container.resolve<GameTokenAdjustHandler>(GAMES_TOKENS.GameTokenAdjustHandler as symbol),
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

  const adminPanelFallbackHandler = new AdminPanelFallbackHandler(adminSessionManager);
  container.registerInstance(ADMIN_TOKENS.AdminPanelFallbackHandler, adminPanelFallbackHandler);
  slashCommandListener.registerInteractionHandler(adminPanelFallbackHandler);

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
  slashCommandListener.registerInteractionHandler(
    container.resolve<InteractionHandler>(DISPATCH_TOKENS.DispatchPanelInteractionHandler),
  );

  const shopService = container.resolve<ShopService>(SHOP_TOKENS.ShopService);
  const redemptionCodeRepo = container.resolve<RedemptionCodeRepository>(
    SHOP_TOKENS.RedemptionCodeRepository,
  );
  const redemptionCodeGenerator = container.resolve<RedemptionCodeGenerator>(
    SHOP_TOKENS.RedemptionCodeGenerator,
  );

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
  slashCommandListener.registerInteractionHandler({
    customIdPrefix: 'shop_',
    execute: (interaction, context) =>
      shopCommandHandler.handleInteraction(interaction, context, interaction.getCustomId()),
  });

  // ============================================================
  // Listeners (register with DomainEventPublisher)
  // ============================================================

  const discordGateway = container.resolve<DiscordRuntimeGateway>(TOKENS.DiscordRuntimeGateway);

  const adminUpdateListener = new AdminPanelUpdateListener(
    adminSessionManager,
    discordGateway,
    currencyFacade,
    dispatchManagementFacade,
    adminPanelViewFactory,
  );
  container.registerInstance(ADMIN_TOKENS.AdminPanelUpdateListener, adminUpdateListener);
  _adminUpdateHandler = (event: unknown): void => {
    adminUpdateListener.onEvent(event as DomainEvent).catch((err: unknown) => {
      console.error('[AdminPanelUpdateListener] Error:', err);
    });
  };
  eventPublisher.register(_adminUpdateHandler);

  _adminConfigured = true;
}

/**
 * Wires the admin SlashCommandListener to Discord after all handlers are registered.
 * Call from apps/bot after member-facing handlers (e.g. user-panel) are registered.
 */
export function startAdminSlashCommandListener(): void {
  const discordGateway = container.resolve<DiscordRuntimeGateway>(TOKENS.DiscordRuntimeGateway);
  const slashCommandListener = container.resolve<SlashCommandListener>(
    ADMIN_TOKENS.SlashCommandListener,
  );
  slashCommandListener.listen(discordGateway);
}

/**
 * Disposes admin module resources. Should be called during application shutdown.
 */
export function disposeAdminContainer(): void {
  try {
    const mgr = container.resolve<AdminPanelSessionManager>(ADMIN_TOKENS.AdminPanelSessionManager);
    mgr.stopCleanupInterval();
  } catch {
    // Session manager not registered; nothing to dispose
  }

  // Unregister domain event listeners
  try {
    const publisher = container.resolve<DomainEventPublisher>(TOKENS.DomainEventPublisher);
    if (_adminUpdateHandler) {
      publisher.unregister(_adminUpdateHandler);
      _adminUpdateHandler = null;
    }
  } catch {
    // DomainEventPublisher not available
  }

  _adminConfigured = false;
}
