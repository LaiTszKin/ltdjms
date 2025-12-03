package ltdjms.discord.shared.di;

import dagger.Module;
import dagger.Provides;
import ltdjms.discord.currency.persistence.MemberCurrencyAccountRepository;
import ltdjms.discord.currency.services.CurrencyTransactionService;
import ltdjms.discord.gametoken.persistence.GameTokenAccountRepository;
import ltdjms.discord.gametoken.persistence.GameTokenTransactionRepository;
import ltdjms.discord.gametoken.services.DiceGame1Service;
import ltdjms.discord.gametoken.services.DiceGame2Service;
import ltdjms.discord.gametoken.services.GameTokenService;
import ltdjms.discord.gametoken.services.GameTokenTransactionService;
import ltdjms.discord.shared.events.DomainEventPublisher;

import javax.inject.Singleton;

/**
 * Dagger module providing game token service dependencies.
 */
@Module
public class GameTokenServiceModule {

    @Provides
    @Singleton
    public GameTokenService provideGameTokenService(
            GameTokenAccountRepository accountRepository,
            DomainEventPublisher eventPublisher) {
        return new GameTokenService(accountRepository, eventPublisher);
    }

    @Provides
    @Singleton
    public GameTokenTransactionService provideGameTokenTransactionService(
            GameTokenTransactionRepository transactionRepository) {
        return new GameTokenTransactionService(transactionRepository);
    }

    @Provides
    @Singleton
    public DiceGame1Service provideDiceGame1Service(
            MemberCurrencyAccountRepository currencyRepository,
            CurrencyTransactionService transactionService,
            DomainEventPublisher eventPublisher) {
        return new DiceGame1Service(currencyRepository, transactionService, eventPublisher);
    }

    @Provides
    @Singleton
    public DiceGame2Service provideDiceGame2Service(
            MemberCurrencyAccountRepository currencyRepository,
            CurrencyTransactionService transactionService,
            DomainEventPublisher eventPublisher) {
        return new DiceGame2Service(currencyRepository, transactionService, eventPublisher);
    }
}
