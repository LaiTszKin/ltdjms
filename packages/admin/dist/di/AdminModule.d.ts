/**
 * DI tokens for the admin module.
 */
export declare const ADMIN_TOKENS: {
    CurrencyManagementFacade: symbol;
    GameTokenManagementFacade: symbol;
    GameConfigManagementFacade: symbol;
    AIConfigManagementFacade: symbol;
    MemberInfoFacade: symbol;
    AdminPanelSessionManager: symbol;
    PanelSessionManager: symbol;
    SlashCommandListener: symbol;
    SlashCommandMetrics: symbol;
    BotErrorHandler: symbol;
    AdminPanelCommand: symbol;
    AdminPanelRouter: symbol;
    BalanceManagementHandler: symbol;
    TokenManagementHandler: symbol;
    GameSettingsHandler: symbol;
    ProductManagementHandler: symbol;
    AIChannelConfigHandler: symbol;
    AIAgentConfigHandler: symbol;
    DispatchAfterSalesHandler: symbol;
    EscortPricingHandler: symbol;
    EscortCatalogHandler: symbol;
    AdminProductPanelHandler: symbol;
    UserPanelCommand: symbol;
    TransactionHistoryHandler: symbol;
    RedemptionCodeHandler: symbol;
    AdminPanelUpdateListener: symbol;
    UserPanelUpdateListener: symbol;
    AdminPanelViewFactory: symbol;
    AdminPanelModalFactory: symbol;
    AdminProductPanelViewFactory: symbol;
    AdminProductPanelModalFactory: symbol;
    UserPanelEmbedBuilder: symbol;
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
 * - SHOP_TOKENS.RedemptionService
 * - AI_TOKENS.AIChannelRestrictionService, AIAgentChannelConfigService
 */
export declare function configureAdminContainer(): void;
