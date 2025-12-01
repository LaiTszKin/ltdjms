package ltdjms.discord.shared.di;

import dagger.Module;
import dagger.Provides;
import ltdjms.discord.currency.persistence.MemberCurrencyAccountRepository;
import ltdjms.discord.gametoken.persistence.GameTokenAccountRepository;
import ltdjms.discord.gametoken.services.DiceGame1Service;
import ltdjms.discord.gametoken.services.DiceGame2Service;
import ltdjms.discord.gametoken.services.GameTokenService;

import javax.inject.Singleton;

/**
 * Dagger module providing game token service dependencies.
 */
@Module
public class GameTokenServiceModule {

    @Provides
    @Singleton
    public GameTokenService provideGameTokenService(GameTokenAccountRepository accountRepository) {
        return new GameTokenService(accountRepository);
    }

    @Provides
    @Singleton
    public DiceGame1Service provideDiceGame1Service(MemberCurrencyAccountRepository currencyRepository) {
        return new DiceGame1Service(currencyRepository);
    }

    @Provides
    @Singleton
    public DiceGame2Service provideDiceGame2Service(MemberCurrencyAccountRepository currencyRepository) {
        return new DiceGame2Service(currencyRepository);
    }
}
