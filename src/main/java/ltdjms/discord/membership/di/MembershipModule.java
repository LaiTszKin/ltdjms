package ltdjms.discord.membership.di;

import java.time.Clock;

import javax.inject.Singleton;
import javax.sql.DataSource;

import dagger.Module;
import dagger.Provides;
import ltdjms.discord.membership.listeners.GuildMemberJoinListener;
import ltdjms.discord.membership.persistence.JdbcMembershipRepository;
import ltdjms.discord.membership.persistence.MembershipRepository;
import ltdjms.discord.membership.services.MembershipJoinService;

/** Dagger module providing membership repository dependencies. */
@Module
public class MembershipModule {

  @Provides
  @Singleton
  public MembershipRepository provideMembershipRepository(DataSource dataSource) {
    return new JdbcMembershipRepository(dataSource);
  }

  @Provides
  @Singleton
  public Clock provideMembershipClock() {
    return Clock.system(MembershipJoinService.SETTLEMENT_ZONE);
  }

  @Provides
  @Singleton
  public MembershipJoinService provideMembershipJoinService(
      MembershipRepository membershipRepository, Clock clock) {
    return new MembershipJoinService(membershipRepository, clock);
  }

  @Provides
  @Singleton
  public GuildMemberJoinListener provideGuildMemberJoinListener(
      MembershipJoinService membershipJoinService) {
    return new GuildMemberJoinListener(membershipJoinService);
  }
}
