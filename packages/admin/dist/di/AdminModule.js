import { container, TOKENS } from '@ltdjms/shared';
import { ECONOMY_TOKENS } from '@ltdjms/economy';
import { SHOP_TOKENS } from '@ltdjms/shop';
import { AI_TOKENS } from '@ltdjms/ai';
import { DISPATCH_TOKENS } from '@ltdjms/dispatch';
// Facades
import { CurrencyManagementFacade } from '../facades/CurrencyManagementFacade.js';
import { GameTokenManagementFacade } from '../facades/GameTokenManagementFacade.js';
import { GameConfigManagementFacade } from '../facades/GameConfigManagementFacade.js';
import { AIConfigManagementFacade } from '../facades/AIConfigManagementFacade.js';
import { MemberInfoFacade } from '../facades/MemberInfoFacade.js';
// Session
import { AdminPanelSessionManager } from '../session/AdminPanelSessionManager.js';
import { PanelSessionManager } from '../session/PanelSessionManager.js';
// Infra
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
    ProductManagementHandler: Symbol('ProductManagementHandler'),
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
/**
 * Initializes the DI container with all admin module services.
 * Call this after shared's initializeContainer() and other module configurations.
 *
 * Expected preregistered tokens from other modules:
 * - TOKENS.DomainEventPublisher
 * - ECONOMY_TOKENS.BalanceService, BalanceAdjustmentService, CurrencyConfigService
 * - ECONOMY_TOKENS.CurrencyTransactionService, GameTokenService, GameTokenTransactionService
 * - ECONOMY_TOKENS.DiceConfigRepository
 * - SHOP_TOKENS.RedemptionService, SHOP_TOKENS.ShopService
 * - AI_TOKENS.AIChannelRestrictionService, AIAgentChannelConfigService
 * - DISPATCH_TOKENS.DispatchAfterSalesStaffService, EscortOptionPricingService
 */
export function configureAdminContainer() {
    const eventPublisher = container.resolve(TOKENS.DomainEventPublisher);
    // ============================================================
    // Infra (no dependencies)
    // ============================================================
    const metrics = new SlashCommandMetrics();
    container.registerInstance(ADMIN_TOKENS.SlashCommandMetrics, metrics);
    const errorHandler = new BotErrorHandler();
    container.registerInstance(ADMIN_TOKENS.BotErrorHandler, errorHandler);
    const slashCommandListener = new SlashCommandListener(metrics, errorHandler);
    container.registerInstance(ADMIN_TOKENS.SlashCommandListener, slashCommandListener);
    // ============================================================
    // Session
    // ============================================================
    const adminSessionManager = new AdminPanelSessionManager();
    container.registerInstance(ADMIN_TOKENS.AdminPanelSessionManager, adminSessionManager);
    const panelSessionManager = new PanelSessionManager();
    container.registerInstance(ADMIN_TOKENS.PanelSessionManager, panelSessionManager);
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
    // CurrencyManagementFacade
    const balanceService = container.resolve(ECONOMY_TOKENS.BalanceService);
    const balanceAdjustmentService = container.resolve(ECONOMY_TOKENS.BalanceAdjustmentService);
    const currencyConfigService = container.resolve(ECONOMY_TOKENS.CurrencyConfigService);
    const currencyFacade = new CurrencyManagementFacade(balanceService, balanceAdjustmentService, currencyConfigService);
    container.registerInstance(ADMIN_TOKENS.CurrencyManagementFacade, currencyFacade);
    // GameTokenManagementFacade
    const gameTokenService = container.resolve(ECONOMY_TOKENS.GameTokenService);
    const gameTokenTxService = container.resolve(ECONOMY_TOKENS.GameTokenTransactionService);
    const tokenFacade = new GameTokenManagementFacade(gameTokenService, gameTokenTxService);
    container.registerInstance(ADMIN_TOKENS.GameTokenManagementFacade, tokenFacade);
    // GameConfigManagementFacade
    const diceConfigRepo = container.resolve(ECONOMY_TOKENS.DiceConfigRepository);
    const gameConfigFacade = new GameConfigManagementFacade(diceConfigRepo, eventPublisher);
    container.registerInstance(ADMIN_TOKENS.GameConfigManagementFacade, gameConfigFacade);
    // AIConfigManagementFacade
    const channelRestrictionService = container.resolve(AI_TOKENS.AIChannelRestrictionService);
    const agentConfigService = container.resolve(AI_TOKENS.AIAgentChannelConfigService);
    const aiConfigFacade = new AIConfigManagementFacade(channelRestrictionService, agentConfigService);
    container.registerInstance(ADMIN_TOKENS.AIConfigManagementFacade, aiConfigFacade);
    // MemberInfoFacade
    const currencyTxService = container.resolve(ECONOMY_TOKENS.CurrencyTransactionService);
    const redemptionService = container.resolve(SHOP_TOKENS.RedemptionService);
    const memberInfoFacade = new MemberInfoFacade(balanceService, gameTokenService, currencyTxService, gameTokenTxService, redemptionService);
    container.registerInstance(ADMIN_TOKENS.MemberInfoFacade, memberInfoFacade);
    // ============================================================
    // Economy Command Handlers (register with SlashCommandListener)
    // ============================================================
    // These handlers are resolved from the economy container (where
    // configureEconomyContainer() already instantiated them) and then
    // registered with the centralized SlashCommandListener for runtime dispatch.
    slashCommandListener.registerCommands([
        container.resolve(ECONOMY_TOKENS.BalanceHandler),
        container.resolve(ECONOMY_TOKENS.CurrencyConfigHandler),
        container.resolve(ECONOMY_TOKENS.DiceGame1Handler),
        container.resolve(ECONOMY_TOKENS.DiceGame2Handler),
        container.resolve(ECONOMY_TOKENS.DiceGame1ConfigHandler),
        container.resolve(ECONOMY_TOKENS.DiceGame2ConfigHandler),
        container.resolve(ECONOMY_TOKENS.GameTokenAdjustHandler),
    ]);
    // ============================================================
    // Admin Panel Commands
    // ============================================================
    const adminPanelCommand = new AdminPanelCommand(adminSessionManager, adminPanelViewFactory, currencyFacade);
    container.registerInstance(ADMIN_TOKENS.AdminPanelCommand, adminPanelCommand);
    slashCommandListener.registerCommand(adminPanelCommand);
    const adminPanelRouter = new AdminPanelRouter(adminSessionManager);
    container.registerInstance(ADMIN_TOKENS.AdminPanelRouter, adminPanelRouter);
    slashCommandListener.registerInteractionHandler(adminPanelRouter);
    // ============================================================
    // Admin Panel Handlers
    // ============================================================
    const balanceHandler = new BalanceManagementHandler(currencyFacade, adminSessionManager, errorHandler);
    container.registerInstance(ADMIN_TOKENS.BalanceManagementHandler, balanceHandler);
    slashCommandListener.registerInteractionHandler(balanceHandler);
    const tokenHandler = new TokenManagementHandler(tokenFacade, adminSessionManager);
    container.registerInstance(ADMIN_TOKENS.TokenManagementHandler, tokenHandler);
    slashCommandListener.registerInteractionHandler(tokenHandler);
    const gameHandler = new GameSettingsHandler(gameConfigFacade, adminSessionManager);
    container.registerInstance(ADMIN_TOKENS.GameSettingsHandler, gameHandler);
    slashCommandListener.registerInteractionHandler(gameHandler);
    const aiChannelHandler = new AIChannelConfigHandler(aiConfigFacade, adminSessionManager);
    container.registerInstance(ADMIN_TOKENS.AIChannelConfigHandler, aiChannelHandler);
    slashCommandListener.registerInteractionHandler(aiChannelHandler);
    const aiAgentHandler = new AIAgentConfigHandler(aiConfigFacade, adminSessionManager);
    container.registerInstance(ADMIN_TOKENS.AIAgentConfigHandler, aiAgentHandler);
    slashCommandListener.registerInteractionHandler(aiAgentHandler);
    // Resolve dispatch services
    const afterSalesStaffService = container.resolve(DISPATCH_TOKENS.DispatchAfterSalesStaffService);
    const escortPricingService = container.resolve(DISPATCH_TOKENS.EscortOptionPricingService);
    const dispatchHandler = new DispatchAfterSalesHandler(adminSessionManager, afterSalesStaffService);
    container.registerInstance(ADMIN_TOKENS.DispatchAfterSalesHandler, dispatchHandler);
    slashCommandListener.registerInteractionHandler(dispatchHandler);
    const escortPriceHandler = new EscortPricingHandler(adminSessionManager, escortPricingService);
    container.registerInstance(ADMIN_TOKENS.EscortPricingHandler, escortPriceHandler);
    slashCommandListener.registerInteractionHandler(escortPriceHandler);
    // EscortOptionCatalogRepository is now registered by the dispatch module
    // via DISPATCH_TOKENS.EscortOptionCatalogRepository (a stub until a
    // production implementation is ported).
    const escortCatalogHandler = new EscortCatalogHandler(adminSessionManager, container.resolve(DISPATCH_TOKENS.EscortOptionCatalogRepository));
    container.registerInstance(ADMIN_TOKENS.EscortCatalogHandler, escortCatalogHandler);
    slashCommandListener.registerInteractionHandler(escortCatalogHandler);
    // DispatchPanelCommandHandler (slash command)
    const dispatchPanelCommandHandler = container.resolve(DISPATCH_TOKENS.DispatchPanelCommandHandler);
    slashCommandListener.registerCommand(dispatchPanelCommandHandler);
    const shopService = container.resolve(SHOP_TOKENS.ShopService);
    const adminProductPanelHandler = new AdminProductPanelHandler(adminSessionManager, shopService);
    container.registerInstance(ADMIN_TOKENS.AdminProductPanelHandler, adminProductPanelHandler);
    slashCommandListener.registerInteractionHandler(adminProductPanelHandler);
    // ShopCommandHandler (slash command)
    const shopCommandHandler = container.resolve(SHOP_TOKENS.ShopCommandHandler);
    slashCommandListener.registerCommand(shopCommandHandler);
    // ============================================================
    // User Panel Commands
    // ============================================================
    const userPanelCommand = new UserPanelCommand(memberInfoFacade, panelSessionManager, userPanelEmbedBuilder);
    container.registerInstance(ADMIN_TOKENS.UserPanelCommand, userPanelCommand);
    slashCommandListener.registerCommand(userPanelCommand);
    const txHistoryHandler = new TransactionHistoryHandler(memberInfoFacade, panelSessionManager);
    container.registerInstance(ADMIN_TOKENS.TransactionHistoryHandler, txHistoryHandler);
    slashCommandListener.registerInteractionHandler(txHistoryHandler);
    const redeemHandler = new RedemptionCodeHandler(memberInfoFacade, panelSessionManager);
    container.registerInstance(ADMIN_TOKENS.RedemptionCodeHandler, redeemHandler);
    slashCommandListener.registerInteractionHandler(redeemHandler);
    // ============================================================
    // Listeners (register with DomainEventPublisher)
    // ============================================================
    const adminUpdateListener = new AdminPanelUpdateListener(adminSessionManager);
    container.registerInstance(ADMIN_TOKENS.AdminPanelUpdateListener, adminUpdateListener);
    eventPublisher.register((event) => {
        adminUpdateListener.onEvent(event).catch((err) => {
            console.error('[AdminPanelUpdateListener] Error:', err);
        });
    });
    const userUpdateListener = new UserPanelUpdateListener(panelSessionManager, memberInfoFacade);
    container.registerInstance(ADMIN_TOKENS.UserPanelUpdateListener, userUpdateListener);
    eventPublisher.register((event) => {
        userUpdateListener.onEvent(event).catch((err) => {
            console.error('[UserPanelUpdateListener] Error:', err);
        });
    });
}
//# sourceMappingURL=AdminModule.js.map