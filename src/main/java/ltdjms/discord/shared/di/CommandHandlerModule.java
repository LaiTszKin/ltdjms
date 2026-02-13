package ltdjms.discord.shared.di;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import ltdjms.discord.aiagent.services.AIAgentChannelConfigService;
import ltdjms.discord.aichat.services.AIChannelRestrictionService;
import ltdjms.discord.currency.bot.SlashCommandListener;
import ltdjms.discord.currency.commands.CurrencyConfigCommandHandler;
import ltdjms.discord.currency.persistence.GuildCurrencyConfigRepository;
import ltdjms.discord.currency.services.BalanceAdjustmentService;
import ltdjms.discord.currency.services.BalanceService;
import ltdjms.discord.currency.services.CurrencyConfigService;
import ltdjms.discord.currency.services.CurrencyTransactionService;
import ltdjms.discord.dispatch.commands.DispatchPanelCommandHandler;
import ltdjms.discord.gametoken.commands.DiceGame1CommandHandler;
import ltdjms.discord.gametoken.commands.DiceGame2CommandHandler;
import ltdjms.discord.gametoken.persistence.DiceGame1ConfigRepository;
import ltdjms.discord.gametoken.persistence.DiceGame2ConfigRepository;
import ltdjms.discord.gametoken.services.DiceGame1Service;
import ltdjms.discord.gametoken.services.DiceGame2Service;
import ltdjms.discord.gametoken.services.GameTokenService;
import ltdjms.discord.gametoken.services.GameTokenTransactionService;
import ltdjms.discord.panel.commands.AdminPanelButtonHandler;
import ltdjms.discord.panel.commands.AdminPanelCommandHandler;
import ltdjms.discord.panel.commands.AdminProductPanelHandler;
import ltdjms.discord.panel.commands.UserPanelButtonHandler;
import ltdjms.discord.panel.commands.UserPanelCommandHandler;
import ltdjms.discord.panel.services.AIConfigManagementFacade;
import ltdjms.discord.panel.services.AdminPanelService;
import ltdjms.discord.panel.services.AdminPanelSessionManager;
import ltdjms.discord.panel.services.AdminPanelUpdateListener;
import ltdjms.discord.panel.services.CurrencyManagementFacade;
import ltdjms.discord.panel.services.GameConfigManagementFacade;
import ltdjms.discord.panel.services.GameTokenManagementFacade;
import ltdjms.discord.panel.services.MemberInfoFacade;
import ltdjms.discord.panel.services.PanelSessionManager;
import ltdjms.discord.panel.services.ProductRedemptionUpdateListener;
import ltdjms.discord.panel.services.UserPanelService;
import ltdjms.discord.panel.services.UserPanelUpdateListener;
import ltdjms.discord.product.domain.ProductRepository;
import ltdjms.discord.product.services.ProductService;
import ltdjms.discord.redemption.services.ProductRedemptionTransactionService;
import ltdjms.discord.redemption.services.RedemptionService;
import ltdjms.discord.shared.events.DomainEventPublisher;
import ltdjms.discord.shop.commands.ShopButtonHandler;
import ltdjms.discord.shop.commands.ShopCommandHandler;
import ltdjms.discord.shop.commands.ShopSelectMenuHandler;
import ltdjms.discord.shop.services.ShopService;

/**
 * Dagger module providing command handler dependencies.
 *
 * <p>Note: Legacy handlers (BalanceCommandHandler, BalanceAdjustmentCommandHandler,
 * GameTokenAdjustCommandHandler, DiceGame1ConfigCommandHandler, DiceGame2ConfigCommandHandler) have
 * been removed. Their functionality is now available through /user-panel and /admin-panel.
 */
@Module
public class CommandHandlerModule {

  @Provides
  @Singleton
  public CurrencyConfigCommandHandler provideCurrencyConfigCommandHandler(
      CurrencyConfigService configService) {
    return new CurrencyConfigCommandHandler(configService);
  }

  @Provides
  @Singleton
  public DiceGame1CommandHandler provideDiceGame1CommandHandler(
      GameTokenService tokenService,
      DiceGame1Service diceGameService,
      DiceGame1ConfigRepository configRepository,
      GuildCurrencyConfigRepository currencyConfigRepository,
      GameTokenTransactionService transactionService) {
    return new DiceGame1CommandHandler(
        tokenService,
        diceGameService,
        configRepository,
        currencyConfigRepository,
        transactionService);
  }

  @Provides
  @Singleton
  public DiceGame2CommandHandler provideDiceGame2CommandHandler(
      GameTokenService tokenService,
      DiceGame2Service diceGameService,
      DiceGame2ConfigRepository configRepository,
      GuildCurrencyConfigRepository currencyConfigRepository,
      GameTokenTransactionService transactionService) {
    return new DiceGame2CommandHandler(
        tokenService,
        diceGameService,
        configRepository,
        currencyConfigRepository,
        transactionService);
  }

  // ========== Facade Services ==========

  @Provides
  @Singleton
  public CurrencyManagementFacade provideCurrencyManagementFacade(
      BalanceService balanceService,
      BalanceAdjustmentService balanceAdjustmentService,
      CurrencyConfigService currencyConfigService) {
    return new CurrencyManagementFacade(
        balanceService, balanceAdjustmentService, currencyConfigService);
  }

  @Provides
  @Singleton
  public GameTokenManagementFacade provideGameTokenManagementFacade(
      GameTokenService gameTokenService, GameTokenTransactionService transactionService) {
    return new GameTokenManagementFacade(gameTokenService, transactionService);
  }

  @Provides
  @Singleton
  public GameConfigManagementFacade provideGameConfigManagementFacade(
      DiceGame1ConfigRepository diceGame1ConfigRepository,
      DiceGame2ConfigRepository diceGame2ConfigRepository,
      DomainEventPublisher eventPublisher) {
    return new GameConfigManagementFacade(
        diceGame1ConfigRepository, diceGame2ConfigRepository, eventPublisher);
  }

  @Provides
  @Singleton
  public AIConfigManagementFacade provideAIConfigManagementFacade(
      AIChannelRestrictionService aiChannelRestrictionService,
      AIAgentChannelConfigService aiAgentChannelConfigService) {
    return new AIConfigManagementFacade(aiChannelRestrictionService, aiAgentChannelConfigService);
  }

  @Provides
  @Singleton
  public MemberInfoFacade provideMemberInfoFacade(
      BalanceService balanceService,
      GameTokenService gameTokenService,
      GameTokenTransactionService gameTokenTransactionService,
      CurrencyTransactionService currencyTransactionService,
      RedemptionService redemptionService,
      ProductRedemptionTransactionService productRedemptionTransactionService) {
    return new MemberInfoFacade(
        balanceService,
        gameTokenService,
        gameTokenTransactionService,
        currencyTransactionService,
        redemptionService,
        productRedemptionTransactionService);
  }

  // ========== Panel Services ==========

  @Provides
  @Singleton
  public UserPanelService provideUserPanelService(MemberInfoFacade memberInfoFacade) {
    return new UserPanelService(memberInfoFacade);
  }

  @Provides
  @Singleton
  public PanelSessionManager providePanelSessionManager() {
    return new PanelSessionManager();
  }

  @Provides
  @Singleton
  public AdminPanelSessionManager provideAdminPanelSessionManager() {
    return new AdminPanelSessionManager();
  }

  @Provides
  @Singleton
  public UserPanelUpdateListener provideUserPanelUpdateListener(
      PanelSessionManager sessionManager, UserPanelService userPanelService) {
    return new UserPanelUpdateListener(sessionManager, userPanelService);
  }

  @Provides
  @Singleton
  public AdminPanelUpdateListener provideAdminPanelUpdateListener(
      AdminPanelSessionManager sessionManager, AdminProductPanelHandler adminProductPanelHandler) {
    return new AdminPanelUpdateListener(sessionManager, adminProductPanelHandler);
  }

  @Provides
  @Singleton
  public ProductRedemptionUpdateListener provideProductRedemptionUpdateListener(
      PanelSessionManager sessionManager) {
    return new ProductRedemptionUpdateListener(sessionManager);
  }

  @Provides
  @Singleton
  public AdminPanelService provideAdminPanelService(
      CurrencyManagementFacade currencyFacade,
      GameTokenManagementFacade gameTokenFacade,
      GameConfigManagementFacade gameConfigFacade,
      AIConfigManagementFacade aiConfigFacade) {
    return new AdminPanelService(currencyFacade, gameTokenFacade, gameConfigFacade, aiConfigFacade);
  }

  @Provides
  @Singleton
  public UserPanelCommandHandler provideUserPanelCommandHandler(
      UserPanelService userPanelService, PanelSessionManager panelSessionManager) {
    return new UserPanelCommandHandler(userPanelService, panelSessionManager);
  }

  @Provides
  @Singleton
  public UserPanelButtonHandler provideUserPanelButtonHandler(
      UserPanelService userPanelService,
      ProductRedemptionTransactionService productRedemptionTransactionService) {
    return new UserPanelButtonHandler(userPanelService, productRedemptionTransactionService);
  }

  @Provides
  @Singleton
  public AdminPanelCommandHandler provideAdminPanelCommandHandler(
      AdminPanelService adminPanelService, AdminPanelSessionManager adminPanelSessionManager) {
    return new AdminPanelCommandHandler(adminPanelService, adminPanelSessionManager);
  }

  @Provides
  @Singleton
  public AdminPanelButtonHandler provideAdminPanelButtonHandler(
      AdminPanelService adminPanelService, AdminPanelSessionManager adminPanelSessionManager) {
    return new AdminPanelButtonHandler(adminPanelService, adminPanelSessionManager);
  }

  @Provides
  @Singleton
  public AdminProductPanelHandler provideAdminProductPanelHandler(
      ProductService productService,
      RedemptionService redemptionService,
      AdminPanelSessionManager adminPanelSessionManager) {
    return new AdminProductPanelHandler(
        productService, redemptionService, adminPanelSessionManager);
  }

  // ========== Shop Services ==========

  @Provides
  @Singleton
  public ShopService provideShopService(ProductRepository productRepository) {
    return new ShopService(productRepository);
  }

  @Provides
  @Singleton
  public ShopCommandHandler provideShopCommandHandler(
      ShopService shopService, ProductService productService) {
    return new ShopCommandHandler(shopService, productService);
  }

  @Provides
  @Singleton
  public ShopButtonHandler provideShopButtonHandler(
      ShopService shopService, ProductService productService) {
    return new ShopButtonHandler(shopService, productService);
  }

  @Provides
  @Singleton
  public ltdjms.discord.shop.services.CurrencyPurchaseService provideCurrencyPurchaseService(
      ProductService productService,
      BalanceService balanceService,
      BalanceAdjustmentService balanceAdjustmentService,
      CurrencyTransactionService currencyTransactionService) {
    return new ltdjms.discord.shop.services.CurrencyPurchaseService(
        productService, balanceService, balanceAdjustmentService, currencyTransactionService);
  }

  @Provides
  @Singleton
  public ShopSelectMenuHandler provideShopSelectMenuHandler(
      ProductService productService,
      BalanceService balanceService,
      ltdjms.discord.shop.services.CurrencyPurchaseService currencyPurchaseService) {
    return new ShopSelectMenuHandler(productService, balanceService, currencyPurchaseService);
  }

  @Provides
  @Singleton
  public SlashCommandListener provideSlashCommandListener(
      CurrencyConfigCommandHandler configHandler,
      DiceGame1CommandHandler diceGame1Handler,
      DiceGame2CommandHandler diceGame2Handler,
      UserPanelCommandHandler userPanelHandler,
      AdminPanelCommandHandler adminPanelHandler,
      ShopCommandHandler shopHandler,
      DispatchPanelCommandHandler dispatchPanelHandler) {
    return new SlashCommandListener(
        configHandler,
        diceGame1Handler,
        diceGame2Handler,
        userPanelHandler,
        adminPanelHandler,
        shopHandler,
        dispatchPanelHandler);
  }
}
