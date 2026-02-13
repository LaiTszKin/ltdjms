package ltdjms.discord.shared.di;

import javax.inject.Singleton;
import javax.sql.DataSource;

import org.jooq.DSLContext;

import dagger.Component;
import ltdjms.discord.aiagent.commands.AgentCompletionListener;
import ltdjms.discord.aiagent.commands.ToolExecutionListener;
import ltdjms.discord.aiagent.services.AgentConfigCacheInvalidationListener;
import ltdjms.discord.aichat.commands.AIChatMentionListener;
import ltdjms.discord.aichat.services.AIChatService;
import ltdjms.discord.currency.bot.SlashCommandListener;
import ltdjms.discord.currency.commands.CurrencyConfigCommandHandler;
import ltdjms.discord.currency.persistence.GuildCurrencyConfigRepository;
import ltdjms.discord.currency.persistence.MemberCurrencyAccountRepository;
import ltdjms.discord.currency.services.BalanceAdjustmentService;
import ltdjms.discord.currency.services.BalanceService;
import ltdjms.discord.currency.services.CurrencyConfigService;
import ltdjms.discord.dispatch.commands.DispatchPanelInteractionHandler;
import ltdjms.discord.gametoken.commands.DiceGame1CommandHandler;
import ltdjms.discord.gametoken.commands.DiceGame2CommandHandler;
import ltdjms.discord.gametoken.persistence.DiceGame1ConfigRepository;
import ltdjms.discord.gametoken.persistence.GameTokenAccountRepository;
import ltdjms.discord.gametoken.persistence.GameTokenTransactionRepository;
import ltdjms.discord.gametoken.services.DiceGame1Service;
import ltdjms.discord.gametoken.services.GameTokenService;
import ltdjms.discord.gametoken.services.GameTokenTransactionService;
import ltdjms.discord.panel.commands.AdminPanelButtonHandler;
import ltdjms.discord.panel.commands.AdminProductPanelHandler;
import ltdjms.discord.panel.commands.UserPanelButtonHandler;
import ltdjms.discord.panel.services.AdminPanelUpdateListener;
import ltdjms.discord.panel.services.UserPanelUpdateListener;
import ltdjms.discord.product.domain.ProductRepository;
import ltdjms.discord.product.services.ProductService;
import ltdjms.discord.redemption.domain.RedemptionCodeRepository;
import ltdjms.discord.redemption.services.RedemptionService;
import ltdjms.discord.shared.DatabaseConfig;
import ltdjms.discord.shared.EnvironmentConfig;
import ltdjms.discord.shared.cache.CacheInvalidationListener;
import ltdjms.discord.shared.cache.CacheKeyGenerator;
import ltdjms.discord.shared.cache.CacheService;
import ltdjms.discord.shared.events.DomainEventPublisher;
import ltdjms.discord.shop.commands.ShopButtonHandler;
import ltdjms.discord.shop.commands.ShopSelectMenuHandler;
import ltdjms.discord.shop.services.CurrencyPurchaseService;

/**
 * Main Dagger component for the LTDJ management system application. Provides all dependencies
 * needed for the bot to operate.
 *
 * <p>Note: Legacy command handlers (BalanceCommandHandler, BalanceAdjustmentCommandHandler,
 * GameTokenAdjustCommandHandler, DiceGame1ConfigCommandHandler, DiceGame2ConfigCommandHandler) have
 * been removed. Their functionality is now available through /user-panel and /admin-panel.
 */
@Singleton
@Component(
    modules = {
      DatabaseModule.class,
      CacheModule.class,
      CurrencyRepositoryModule.class,
      CurrencyServiceModule.class,
      GameTokenRepositoryModule.class,
      GameTokenServiceModule.class,
      ProductRepositoryModule.class,
      ProductServiceModule.class,
      DispatchModule.class,
      CommandHandlerModule.class,
      EventModule.class,
      AIChatModule.class,
      AIAgentModule.class,
      MarkdownValidationModule.class
    })
public interface AppComponent {

  // Configuration
  EnvironmentConfig environmentConfig();

  DatabaseConfig databaseConfig();

  // Cache
  CacheService cacheService();

  CacheKeyGenerator cacheKeyGenerator();

  CacheInvalidationListener cacheInvalidationListener();

  // AI Agent
  AgentConfigCacheInvalidationListener agentConfigCacheInvalidationListener();

  AgentCompletionListener agentCompletionListener();

  ToolExecutionListener toolExecutionListener();

  // Events
  DomainEventPublisher domainEventPublisher();

  UserPanelUpdateListener userPanelUpdateListener();

  AdminPanelUpdateListener adminPanelUpdateListener();

  // Database
  DataSource dataSource();

  DSLContext dslContext();

  // Currency Repositories
  MemberCurrencyAccountRepository memberCurrencyAccountRepository();

  GuildCurrencyConfigRepository guildCurrencyConfigRepository();

  // Game Token Repositories
  GameTokenAccountRepository gameTokenAccountRepository();

  DiceGame1ConfigRepository diceGame1ConfigRepository();

  GameTokenTransactionRepository gameTokenTransactionRepository();

  // Currency Services
  BalanceService balanceService();

  CurrencyConfigService currencyConfigService();

  BalanceAdjustmentService balanceAdjustmentService();

  // Game Token Services
  GameTokenService gameTokenService();

  DiceGame1Service diceGame1Service();

  GameTokenTransactionService gameTokenTransactionService();

  // Product and Redemption
  ProductRepository productRepository();

  RedemptionCodeRepository redemptionCodeRepository();

  ProductService productService();

  RedemptionService redemptionService();

  // Currency Command Handlers
  CurrencyConfigCommandHandler currencyConfigCommandHandler();

  // Game Command Handlers
  DiceGame1CommandHandler diceGame1CommandHandler();

  DiceGame2CommandHandler diceGame2CommandHandler();

  // Panel Handlers
  UserPanelButtonHandler userPanelButtonHandler();

  AdminPanelButtonHandler adminPanelButtonHandler();

  AdminProductPanelHandler adminProductPanelHandler();

  DispatchPanelInteractionHandler dispatchPanelInteractionHandler();

  // Shop Handlers
  ShopButtonHandler shopButtonHandler();

  ShopSelectMenuHandler shopSelectMenuHandler();

  CurrencyPurchaseService currencyPurchaseService();

  // AI Chat
  AIChatService aiChatService();

  AIChatMentionListener aiChatMentionListener();

  // Slash Command Listener
  SlashCommandListener slashCommandListener();
}
