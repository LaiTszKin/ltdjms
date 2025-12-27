package ltdjms.discord.shared.di;

import javax.inject.Singleton;
import javax.sql.DataSource;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import dagger.Module;
import dagger.Provides;
import ltdjms.discord.shared.DatabaseConfig;
import ltdjms.discord.shared.EnvironmentConfig;

/** Dagger module providing database-related dependencies. */
@Module
public class DatabaseModule {

  private final EnvironmentConfig envConfig;

  public DatabaseModule(EnvironmentConfig envConfig) {
    this.envConfig = envConfig;
  }

  @Provides
  @Singleton
  public EnvironmentConfig provideEnvironmentConfig() {
    return envConfig;
  }

  @Provides
  @Singleton
  public DatabaseConfig provideDatabaseConfig(EnvironmentConfig config) {
    return new DatabaseConfig(config);
  }

  @Provides
  @Singleton
  public DataSource provideDataSource(DatabaseConfig databaseConfig) {
    return databaseConfig.getDataSource();
  }

  @Provides
  @Singleton
  public DSLContext provideDSLContext(DataSource dataSource) {
    return DSL.using(dataSource, SQLDialect.POSTGRES);
  }
}
