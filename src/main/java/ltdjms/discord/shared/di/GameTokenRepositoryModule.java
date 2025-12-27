package ltdjms.discord.shared.di;

import javax.inject.Singleton;
import javax.sql.DataSource;

import dagger.Module;
import dagger.Provides;
import ltdjms.discord.gametoken.persistence.DiceGame1ConfigRepository;
import ltdjms.discord.gametoken.persistence.DiceGame2ConfigRepository;
import ltdjms.discord.gametoken.persistence.GameTokenAccountRepository;
import ltdjms.discord.gametoken.persistence.GameTokenTransactionRepository;
import ltdjms.discord.gametoken.persistence.JdbcDiceGame1ConfigRepository;
import ltdjms.discord.gametoken.persistence.JdbcDiceGame2ConfigRepository;
import ltdjms.discord.gametoken.persistence.JdbcGameTokenAccountRepository;
import ltdjms.discord.gametoken.persistence.JdbcGameTokenTransactionRepository;

/**
 * Dagger module providing game token repository dependencies. Uses JDBC-based implementations for
 * game token repositories.
 */
@Module
public class GameTokenRepositoryModule {

  @Provides
  @Singleton
  public GameTokenAccountRepository provideGameTokenAccountRepository(DataSource dataSource) {
    return new JdbcGameTokenAccountRepository(dataSource);
  }

  @Provides
  @Singleton
  public GameTokenTransactionRepository provideGameTokenTransactionRepository(
      DataSource dataSource) {
    return new JdbcGameTokenTransactionRepository(dataSource);
  }

  @Provides
  @Singleton
  public DiceGame1ConfigRepository provideDiceGame1ConfigRepository(DataSource dataSource) {
    return new JdbcDiceGame1ConfigRepository(dataSource);
  }

  @Provides
  @Singleton
  public DiceGame2ConfigRepository provideDiceGame2ConfigRepository(DataSource dataSource) {
    return new JdbcDiceGame2ConfigRepository(dataSource);
  }
}
