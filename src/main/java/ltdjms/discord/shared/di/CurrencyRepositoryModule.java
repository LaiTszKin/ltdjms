package ltdjms.discord.shared.di;

import dagger.Module;
import dagger.Provides;
import ltdjms.discord.currency.persistence.GuildCurrencyConfigRepository;
import ltdjms.discord.currency.persistence.JooqGuildCurrencyConfigRepository;
import ltdjms.discord.currency.persistence.JooqMemberCurrencyAccountRepository;
import ltdjms.discord.currency.persistence.MemberCurrencyAccountRepository;
import org.jooq.DSLContext;

import javax.inject.Singleton;

/**
 * Dagger module providing currency repository dependencies.
 * Uses JOOQ-based implementations for all repositories.
 */
@Module
public class CurrencyRepositoryModule {

    @Provides
    @Singleton
    public MemberCurrencyAccountRepository provideMemberCurrencyAccountRepository(DSLContext dsl) {
        return new JooqMemberCurrencyAccountRepository(dsl);
    }

    @Provides
    @Singleton
    public GuildCurrencyConfigRepository provideGuildCurrencyConfigRepository(DSLContext dsl) {
        return new JooqGuildCurrencyConfigRepository(dsl);
    }
}
