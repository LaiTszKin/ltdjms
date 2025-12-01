package ltdjms.discord.currency.bot;

import ltdjms.discord.shared.DatabaseConfig;
import ltdjms.discord.shared.DatabaseSchemaMigrator;
import ltdjms.discord.shared.EnvironmentConfig;
import ltdjms.discord.shared.di.AppComponent;
import ltdjms.discord.shared.di.AppComponentFactory;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

/**
 * Main entry point for the Discord Currency Bot.
 * Bootstraps JDA with required intents and wires up command handlers via Dagger DI.
 */
public class DiscordCurrencyBot {

    private static final Logger LOG = LoggerFactory.getLogger(DiscordCurrencyBot.class);

    private final JDA jda;
    private final DatabaseConfig databaseConfig;
    private final AppComponent appComponent;

    public DiscordCurrencyBot(EnvironmentConfig envConfig) throws InterruptedException {
        LOG.info("Starting Discord Currency Bot...");

        // Build Dagger component with environment config
        this.appComponent = AppComponentFactory.create(envConfig);

        // Get database config from Dagger
        this.databaseConfig = appComponent.databaseConfig();
        DataSource dataSource = appComponent.dataSource();

        // Apply non-destructive schema migrations before using the database.
        // This keeps the actual schema in sync with src/main/resources/db/schema.sql
        // for both local development and container environments.
        DatabaseSchemaMigrator.forDefaultSchema().migrate(dataSource);

        // Get slash command listener from Dagger
        SlashCommandListener slashCommandListener = appComponent.slashCommandListener();

        // Build JDA instance with default non-privileged gateway intents to avoid
        // DISALLOWED_INTENTS (4014) errors when the bot token does not have
        // privileged intents such as GUILD_MEMBERS enabled.
        this.jda = JDABuilder.createLight(envConfig.getDiscordBotToken())
                .addEventListeners(slashCommandListener)
                .build();

        // Wait for JDA to be ready
        jda.awaitReady();

        // Register slash commands globally
        slashCommandListener.registerCommands(jda);

        LOG.info("Discord Currency Bot started successfully!");
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

    /**
     * Shuts down the bot gracefully.
     */
    public void shutdown() {
        LOG.info("Shutting down Discord Currency Bot...");
        if (jda != null) {
            jda.shutdown();
        }
        if (databaseConfig != null) {
            databaseConfig.close();
        }
        LOG.info("Discord Currency Bot shutdown complete.");
    }

    public static void main(String[] args) {
        try {
            EnvironmentConfig envConfig = new EnvironmentConfig();
            DiscordCurrencyBot bot = new DiscordCurrencyBot(envConfig);

            // Add shutdown hook for graceful shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOG.info("Received shutdown signal");
                bot.shutdown();
            }));

        } catch (IllegalStateException e) {
            LOG.error("Configuration error: {}", e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            LOG.error("Failed to start Discord Currency Bot", e);
            System.exit(1);
        }
    }
}
