package ltdjms.discord.shared.di;

import dagger.Module;
import dagger.Provides;
import ltdjms.discord.currency.domain.CurrencyTransactionRepository;
import ltdjms.discord.currency.persistence.GuildCurrencyConfigRepository;
import ltdjms.discord.currency.persistence.MemberCurrencyAccountRepository;
import ltdjms.discord.currency.services.BalanceAdjustmentService;
import ltdjms.discord.currency.services.BalanceService;
import ltdjms.discord.currency.services.CurrencyConfigService;
import ltdjms.discord.currency.services.CurrencyTransactionService;
import ltdjms.discord.currency.services.DefaultBalanceService;
import ltdjms.discord.currency.services.EmojiValidator;
import ltdjms.discord.currency.services.JdaEmojiValidator;

import javax.inject.Singleton;

/**
 * Dagger module providing currency service dependencies.
 */
@Module
public class CurrencyServiceModule {

    @Provides
    @Singleton
    public EmojiValidator provideEmojiValidator() {
        return new JdaEmojiValidator();
    }

    @Provides
    @Singleton
    public BalanceService provideBalanceService(
            MemberCurrencyAccountRepository accountRepository,
            GuildCurrencyConfigRepository configRepository) {
        return new DefaultBalanceService(accountRepository, configRepository);
    }

    @Provides
    @Singleton
    public CurrencyConfigService provideCurrencyConfigService(
            GuildCurrencyConfigRepository configRepository,
            EmojiValidator emojiValidator) {
        return new CurrencyConfigService(configRepository, emojiValidator);
    }

    @Provides
    @Singleton
    public CurrencyTransactionService provideCurrencyTransactionService(
            CurrencyTransactionRepository transactionRepository) {
        return new CurrencyTransactionService(transactionRepository);
    }

    @Provides
    @Singleton
    public BalanceAdjustmentService provideBalanceAdjustmentService(
            MemberCurrencyAccountRepository accountRepository,
            GuildCurrencyConfigRepository configRepository,
            CurrencyTransactionService transactionService) {
        return new BalanceAdjustmentService(accountRepository, configRepository, transactionService);
    }
}
