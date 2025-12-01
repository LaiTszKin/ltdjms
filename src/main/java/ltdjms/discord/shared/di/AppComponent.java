package ltdjms.discord.shared.di;

import dagger.Component;
import ltdjms.discord.currency.bot.SlashCommandListener;
import ltdjms.discord.currency.commands.CurrencyConfigCommandHandler;
import ltdjms.discord.currency.persistence.GuildCurrencyConfigRepository;
import ltdjms.discord.currency.persistence.MemberCurrencyAccountRepository;
import ltdjms.discord.currency.services.BalanceAdjustmentService;
import ltdjms.discord.currency.services.BalanceService;
import ltdjms.discord.currency.services.CurrencyConfigService;
import ltdjms.discord.gametoken.commands.DiceGame1CommandHandler;
import ltdjms.discord.gametoken.commands.DiceGame2CommandHandler;
import ltdjms.discord.gametoken.persistence.DiceGame1ConfigRepository;
import ltdjms.discord.gametoken.persistence.GameTokenAccountRepository;
import ltdjms.discord.gametoken.persistence.GameTokenTransactionRepository;
import ltdjms.discord.gametoken.services.DiceGame1Service;
import ltdjms.discord.gametoken.services.GameTokenService;
import ltdjms.discord.gametoken.services.GameTokenTransactionService;
import ltdjms.discord.panel.commands.AdminPanelButtonHandler;
import ltdjms.discord.panel.commands.UserPanelButtonHandler;
import ltdjms.discord.shared.DatabaseConfig;
import ltdjms.discord.shared.EnvironmentConfig;
import org.jooq.DSLContext;

import javax.inject.Singleton;
import javax.sql.DataSource;

/**
 * Main Dagger component for the LTDJ management system application.
 * Provides all dependencies needed for the bot to operate.
 *
 * <p>Note: Legacy command handlers (BalanceCommandHandler, BalanceAdjustmentCommandHandler,
 * GameTokenAdjustCommandHandler, DiceGame1ConfigCommandHandler, DiceGame2ConfigCommandHandler)
 * have been removed. Their functionality is now available through /user-panel and /admin-panel.</p>
 */
@Singleton
@Component(modules = {
        DatabaseModule.class,
        CurrencyRepositoryModule.class,
        CurrencyServiceModule.class,
        GameTokenRepositoryModule.class,
        GameTokenServiceModule.class,
        CommandHandlerModule.class
})
public interface AppComponent {

    // Configuration
    EnvironmentConfig environmentConfig();
    DatabaseConfig databaseConfig();

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

    // Currency Command Handlers
    CurrencyConfigCommandHandler currencyConfigCommandHandler();

    // Game Command Handlers
    DiceGame1CommandHandler diceGame1CommandHandler();
    DiceGame2CommandHandler diceGame2CommandHandler();

    // Panel Handlers
    UserPanelButtonHandler userPanelButtonHandler();
    AdminPanelButtonHandler adminPanelButtonHandler();

    // Slash Command Listener
    SlashCommandListener slashCommandListener();
}
