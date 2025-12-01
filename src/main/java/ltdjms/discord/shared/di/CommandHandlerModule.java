package ltdjms.discord.shared.di;

import dagger.Module;
import dagger.Provides;
import ltdjms.discord.currency.bot.SlashCommandListener;
import ltdjms.discord.currency.commands.BalanceAdjustmentCommandHandler;
import ltdjms.discord.currency.commands.BalanceCommandHandler;
import ltdjms.discord.currency.commands.CurrencyConfigCommandHandler;
import ltdjms.discord.currency.persistence.GuildCurrencyConfigRepository;
import ltdjms.discord.currency.services.BalanceAdjustmentService;
import ltdjms.discord.currency.services.BalanceService;
import ltdjms.discord.currency.services.CurrencyConfigService;
import ltdjms.discord.gametoken.commands.DiceGame1CommandHandler;
import ltdjms.discord.gametoken.commands.DiceGame1ConfigCommandHandler;
import ltdjms.discord.gametoken.commands.GameTokenAdjustCommandHandler;
import ltdjms.discord.gametoken.persistence.DiceGame1ConfigRepository;
import ltdjms.discord.gametoken.services.DiceGame1Service;
import ltdjms.discord.gametoken.services.GameTokenService;

import javax.inject.Singleton;

/**
 * Dagger module providing command handler dependencies.
 */
@Module
public class CommandHandlerModule {

    @Provides
    @Singleton
    public BalanceCommandHandler provideBalanceCommandHandler(BalanceService balanceService) {
        return new BalanceCommandHandler(balanceService);
    }

    @Provides
    @Singleton
    public CurrencyConfigCommandHandler provideCurrencyConfigCommandHandler(CurrencyConfigService configService) {
        return new CurrencyConfigCommandHandler(configService);
    }

    @Provides
    @Singleton
    public BalanceAdjustmentCommandHandler provideBalanceAdjustmentCommandHandler(
            BalanceAdjustmentService adjustmentService) {
        return new BalanceAdjustmentCommandHandler(adjustmentService);
    }

    @Provides
    @Singleton
    public GameTokenAdjustCommandHandler provideGameTokenAdjustCommandHandler(GameTokenService tokenService) {
        return new GameTokenAdjustCommandHandler(tokenService);
    }

    @Provides
    @Singleton
    public DiceGame1CommandHandler provideDiceGame1CommandHandler(
            GameTokenService tokenService,
            DiceGame1Service diceGameService,
            DiceGame1ConfigRepository configRepository,
            GuildCurrencyConfigRepository currencyConfigRepository) {
        return new DiceGame1CommandHandler(tokenService, diceGameService, configRepository, currencyConfigRepository);
    }

    @Provides
    @Singleton
    public DiceGame1ConfigCommandHandler provideDiceGame1ConfigCommandHandler(
            DiceGame1ConfigRepository configRepository) {
        return new DiceGame1ConfigCommandHandler(configRepository);
    }

    @Provides
    @Singleton
    public SlashCommandListener provideSlashCommandListener(
            BalanceCommandHandler balanceHandler,
            CurrencyConfigCommandHandler configHandler,
            BalanceAdjustmentCommandHandler adjustmentHandler,
            GameTokenAdjustCommandHandler gameTokenAdjustHandler,
            DiceGame1CommandHandler diceGame1Handler,
            DiceGame1ConfigCommandHandler diceGame1ConfigHandler) {
        return new SlashCommandListener(
                balanceHandler, configHandler, adjustmentHandler,
                gameTokenAdjustHandler, diceGame1Handler, diceGame1ConfigHandler);
    }
}
