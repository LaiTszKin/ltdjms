package ltdjms.discord.shared.di;

import dagger.Module;
import dagger.Provides;
import ltdjms.discord.gametoken.persistence.DiceGame1ConfigRepository;
import ltdjms.discord.gametoken.persistence.GameTokenAccountRepository;
import ltdjms.discord.gametoken.persistence.JdbcDiceGame1ConfigRepository;
import ltdjms.discord.gametoken.persistence.JdbcGameTokenAccountRepository;

import javax.inject.Singleton;
import javax.sql.DataSource;

/**
 * Dagger module providing game token repository dependencies.
 * Uses JDBC-based implementations for game token repositories.
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
    public DiceGame1ConfigRepository provideDiceGame1ConfigRepository(DataSource dataSource) {
        return new JdbcDiceGame1ConfigRepository(dataSource);
    }
}
