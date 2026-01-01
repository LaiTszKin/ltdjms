package ltdjms.discord.currency.bot;

import java.util.List;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.aichat.commands.AIChatMentionListener;
import ltdjms.discord.panel.commands.AdminPanelButtonHandler;
import ltdjms.discord.panel.commands.AdminProductPanelHandler;
import ltdjms.discord.panel.commands.UserPanelButtonHandler;
import ltdjms.discord.shared.DatabaseConfig;
import ltdjms.discord.shared.DatabaseMigrationRunner;
import ltdjms.discord.shared.EnvironmentConfig;
import ltdjms.discord.shared.di.AppComponent;
import ltdjms.discord.shared.di.AppComponentFactory;
import ltdjms.discord.shared.di.JDAProvider;
import ltdjms.discord.shop.commands.ShopButtonHandler;
import ltdjms.discord.shop.commands.ShopSelectMenuHandler;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;

/**
 * Main entry point for the LTDJ management system. Bootstraps JDA with required intents and wires
 * up command handlers via Dagger DI.
 */
public class DiscordCurrencyBot {

  private static final Logger LOG = LoggerFactory.getLogger(DiscordCurrencyBot.class);

  private final JDA jda;
  private final DatabaseConfig databaseConfig;
  private final AppComponent appComponent;

  public DiscordCurrencyBot(EnvironmentConfig envConfig) throws InterruptedException {
    LOG.info("Starting LTDJ management system...");

    // Build Dagger component with environment config
    this.appComponent = AppComponentFactory.create(envConfig);

    // Register domain event listeners
    this.appComponent.domainEventPublisher().register(this.appComponent.userPanelUpdateListener());
    this.appComponent.domainEventPublisher().register(this.appComponent.adminPanelUpdateListener());
    this.appComponent
        .domainEventPublisher()
        .register(this.appComponent.cacheInvalidationListener());
    this.appComponent
        .domainEventPublisher()
        .register(this.appComponent.agentConfigCacheInvalidationListener());
    this.appComponent.domainEventPublisher().register(this.appComponent.agentCompletionListener());
    this.appComponent.domainEventPublisher().register(this.appComponent.toolExecutionListener());

    // Get database config from Dagger
    this.databaseConfig = appComponent.databaseConfig();
    DataSource dataSource = appComponent.dataSource();

    // Apply database migrations via Flyway before using the database.
    // This ensures the schema is up-to-date with the latest migrations.
    // If migration fails, the bot will not start (SchemaMigrationException is thrown).
    DatabaseMigrationRunner.forDefaultMigrations().migrate(dataSource);

    // Get slash command listener from Dagger
    SlashCommandListener slashCommandListener = appComponent.slashCommandListener();

    // Get button interaction handlers from Dagger
    UserPanelButtonHandler userPanelButtonHandler = appComponent.userPanelButtonHandler();
    AdminPanelButtonHandler adminPanelButtonHandler = appComponent.adminPanelButtonHandler();
    AdminProductPanelHandler adminProductPanelHandler = appComponent.adminProductPanelHandler();
    ShopButtonHandler shopButtonHandler = appComponent.shopButtonHandler();
    ShopSelectMenuHandler shopSelectMenuHandler = appComponent.shopSelectMenuHandler();
    AIChatMentionListener aiChatMentionListener = appComponent.aiChatMentionListener();

    // Build JDA instance with default non-privileged gateway intents to avoid
    // DISALLOWED_INTENTS (4014) errors when the bot token does not have
    // privileged intents such as GUILD_MEMBERS enabled.
    List<Object> eventListeners =
        buildEventListeners(
            slashCommandListener,
            userPanelButtonHandler,
            adminPanelButtonHandler,
            adminProductPanelHandler,
            shopButtonHandler,
            shopSelectMenuHandler,
            aiChatMentionListener);

    this.jda =
        JDABuilder.createLight(envConfig.getDiscordBotToken())
            .addEventListeners(eventListeners.toArray())
            .build();

    // Wait for JDA to be ready
    jda.awaitReady();

    // 設置 JDA 到 Provider，讓 AI Agent 工具可以訪問
    JDAProvider.setJda(jda);

    // Register slash commands globally
    slashCommandListener.registerCommands(jda);

    LOG.info("LTDJ management system started successfully!");
  }

  /**
   * Returns the JDA instance for the bot.
   *
   * @return the JDA instance
   */
  public JDA getJda() {
    return jda;
  }

  /**
   * Returns the Dagger AppComponent for accessing dependencies.
   *
   * @return the AppComponent instance
   */
  public AppComponent getAppComponent() {
    return appComponent;
  }

  /** Shuts down the bot gracefully. */
  public void shutdown() {
    LOG.info("Shutting down LTDJ management system...");
    if (jda != null) {
      jda.shutdown();
    }
    if (databaseConfig != null) {
      databaseConfig.close();
    }
    LOG.info("LTDJ management system shutdown complete.");
  }

  public static void main(String[] args) {
    try {
      EnvironmentConfig envConfig = new EnvironmentConfig();
      DiscordCurrencyBot bot = new DiscordCurrencyBot(envConfig);

      // Add shutdown hook for graceful shutdown
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    LOG.info("Received shutdown signal");
                    bot.shutdown();
                  }));

    } catch (IllegalStateException e) {
      LOG.error("Configuration error: {}", e.getMessage());
      System.exit(1);
    } catch (Exception e) {
      LOG.error("Failed to start LTDJ management system", e);
      System.exit(1);
    }
  }

  /** 封裝事件監聽器的組合，便於測試與維護。 */
  static List<Object> buildEventListeners(
      SlashCommandListener slashCommandListener,
      UserPanelButtonHandler userPanelButtonHandler,
      AdminPanelButtonHandler adminPanelButtonHandler,
      AdminProductPanelHandler adminProductPanelHandler,
      ShopButtonHandler shopButtonHandler,
      ShopSelectMenuHandler shopSelectMenuHandler,
      AIChatMentionListener aiChatMentionListener) {
    return List.of(
        slashCommandListener,
        userPanelButtonHandler,
        adminPanelButtonHandler,
        adminProductPanelHandler,
        shopButtonHandler,
        shopSelectMenuHandler,
        aiChatMentionListener);
  }
}
