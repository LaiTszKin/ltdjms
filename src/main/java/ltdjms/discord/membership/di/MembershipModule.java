package ltdjms.discord.membership.di;

import javax.inject.Singleton;
import javax.sql.DataSource;

import dagger.Module;
import dagger.Provides;
import ltdjms.discord.membership.persistence.JdbcMembershipRepository;
import ltdjms.discord.membership.persistence.MembershipRepository;

/** Dagger module providing membership repository dependencies. */
@Module
public class MembershipModule {

  @Provides
  @Singleton
  public MembershipRepository provideMembershipRepository(DataSource dataSource) {
    return new JdbcMembershipRepository(dataSource);
  }
}
