package ltdjms.discord.shared.di;

import dagger.Component;
import ltdjms.discord.currency.bot.SlashCommandListener;
import ltdjms.discord.currency.commands.BalanceAdjustmentCommandHandler;
import ltdjms.discord.currency.commands.BalanceCommandHandler;
import ltdjms.discord.currency.commands.CurrencyConfigCommandHandler;
import ltdjms.discord.currency.persistence.GuildCurrencyConfigRepository;
import ltdjms.discord.currency.persistence.MemberCurrencyAccountRepository;
import ltdjms.discord.currency.services.BalanceAdjustmentService;
import ltdjms.discord.currency.services.BalanceService;
import ltdjms.discord.currency.services.CurrencyConfigService;
import ltdjms.discord.gametoken.commands.DiceGame1CommandHandler;
import ltdjms.discord.gametoken.commands.DiceGame1ConfigCommandHandler;
import ltdjms.discord.gametoken.commands.GameTokenAdjustCommandHandler;
import ltdjms.discord.gametoken.persistence.DiceGame1ConfigRepository;
import ltdjms.discord.gametoken.persistence.GameTokenAccountRepository;
import ltdjms.discord.gametoken.services.DiceGame1Service;
import ltdjms.discord.gametoken.services.GameTokenService;
import ltdjms.discord.shared.DatabaseConfig;
import ltdjms.discord.shared.EnvironmentConfig;
import org.jooq.DSLContext;

import javax.inject.Singleton;
import javax.sql.DataSource;

/**
 * Main Dagger component for the Discord Currency Bot application.
 * Provides all dependencies needed for the bot to operate.
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

    // Currency Services
    BalanceService balanceService();
    CurrencyConfigService currencyConfigService();
    BalanceAdjustmentService balanceAdjustmentService();

    // Game Token Services
    GameTokenService gameTokenService();
    DiceGame1Service diceGame1Service();

    // Currency Command Handlers
    BalanceCommandHandler balanceCommandHandler();
    CurrencyConfigCommandHandler currencyConfigCommandHandler();
    BalanceAdjustmentCommandHandler balanceAdjustmentCommandHandler();

    // Game Token Command Handlers
    GameTokenAdjustCommandHandler gameTokenAdjustCommandHandler();
    DiceGame1CommandHandler diceGame1CommandHandler();
    DiceGame1ConfigCommandHandler diceGame1ConfigCommandHandler();

    // Slash Command Listener
    SlashCommandListener slashCommandListener();
}
