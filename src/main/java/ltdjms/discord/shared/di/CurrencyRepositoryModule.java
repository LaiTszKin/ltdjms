package ltdjms.discord.shared.di;

import javax.inject.Singleton;
import javax.sql.DataSource;

import org.jooq.DSLContext;

import dagger.Module;
import dagger.Provides;
import ltdjms.discord.currency.domain.CurrencyTransactionRepository;
import ltdjms.discord.currency.persistence.GuildCurrencyConfigRepository;
import ltdjms.discord.currency.persistence.JdbcCurrencyTransactionRepository;
import ltdjms.discord.currency.persistence.JooqGuildCurrencyConfigRepository;
import ltdjms.discord.currency.persistence.JooqMemberCurrencyAccountRepository;
import ltdjms.discord.currency.persistence.MemberCurrencyAccountRepository;

/**
 * Dagger module providing currency repository dependencies. Uses JOOQ-based implementations for
 * account/config repositories, and JDBC-based implementation for transaction repository.
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

  @Provides
  @Singleton
  public CurrencyTransactionRepository provideCurrencyTransactionRepository(DataSource dataSource) {
    return new JdbcCurrencyTransactionRepository(dataSource);
  }
}
