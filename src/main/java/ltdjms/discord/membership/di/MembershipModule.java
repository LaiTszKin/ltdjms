package ltdjms.discord.membership.di;

import java.time.Clock;

import javax.inject.Singleton;
import javax.sql.DataSource;

import dagger.Module;
import dagger.Provides;
import ltdjms.discord.membership.listeners.GuildMemberJoinListener;
import ltdjms.discord.membership.persistence.JdbcMembershipRepository;
import ltdjms.discord.membership.persistence.JdbcMembershipSpendRepository;
import ltdjms.discord.membership.persistence.MembershipRepository;
import ltdjms.discord.membership.persistence.MembershipSpendRepository;
import ltdjms.discord.membership.services.MembershipJoinService;
import ltdjms.discord.membership.services.MembershipPricingService;
import ltdjms.discord.membership.services.MembershipSettlementScheduler;
import ltdjms.discord.membership.services.MembershipSettlementService;
import ltdjms.discord.membership.services.MembershipSpendService;
import ltdjms.discord.product.domain.EscortOptionCatalogRepository;
import ltdjms.discord.shared.events.DomainEventPublisher;

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
  public MembershipSpendRepository provideMembershipSpendRepository(DataSource dataSource) {
    return new JdbcMembershipSpendRepository(dataSource);
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

  @Provides
  @Singleton
  public MembershipPricingService provideMembershipPricingService(
      MembershipRepository membershipRepository) {
    return new MembershipPricingService(membershipRepository);
  }

  @Provides
  @Singleton
  public MembershipSpendService provideMembershipSpendService(
      MembershipSpendRepository membershipSpendRepository,
      MembershipRepository membershipRepository,
      EscortOptionCatalogRepository escortOptionCatalogRepository) {
    return new MembershipSpendService(
        membershipSpendRepository, membershipRepository, escortOptionCatalogRepository);
  }

  @Provides
  @Singleton
  public MembershipSettlementService provideMembershipSettlementService(
      MembershipRepository membershipRepository,
      MembershipSpendRepository membershipSpendRepository,
      DomainEventPublisher eventPublisher,
      Clock clock) {
    return new MembershipSettlementService(
        membershipRepository, membershipSpendRepository, eventPublisher, clock);
  }

  @Provides
  @Singleton
  public MembershipSettlementScheduler provideMembershipSettlementScheduler(
      MembershipRepository membershipRepository,
      MembershipSettlementService settlementService,
      Clock clock) {
    return new MembershipSettlementScheduler(membershipRepository, settlementService, clock);
  }
}
