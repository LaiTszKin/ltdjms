package ltdjms.discord.membership.di;

import java.time.Clock;
import javax.inject.Singleton;
import javax.sql.DataSource;

import dagger.Module;
import dagger.Provides;
import ltdjms.discord.gametoken.services.GameTokenService;
import ltdjms.discord.gametoken.services.GameTokenTransactionService;
import ltdjms.discord.membership.listeners.GuildMemberJoinListener;
import ltdjms.discord.membership.persistence.JdbcMembershipRepository;
import ltdjms.discord.membership.persistence.JdbcMembershipSettlementCoordinator;
import ltdjms.discord.membership.persistence.JdbcMembershipSettlementTickGuard;
import ltdjms.discord.membership.persistence.JdbcMembershipSpendRepository;
import ltdjms.discord.membership.persistence.JdbcMembershipSpendRetryRepository;
import ltdjms.discord.membership.persistence.JdbcMembershipTokenGrantRepository;
import ltdjms.discord.membership.persistence.MembershipRepository;
import ltdjms.discord.membership.persistence.MembershipSettlementCoordinator;
import ltdjms.discord.membership.persistence.MembershipSettlementTickGuard;
import ltdjms.discord.membership.persistence.MembershipSpendRepository;
import ltdjms.discord.membership.persistence.MembershipSpendRetryRepository;
import ltdjms.discord.membership.persistence.MembershipTokenGrantRepository;
import ltdjms.discord.membership.services.MembershipJoinService;
import ltdjms.discord.membership.services.MembershipPricingService;
import ltdjms.discord.membership.services.MembershipQueryService;
import ltdjms.discord.membership.services.MembershipSettlementScheduler;
import ltdjms.discord.membership.services.MembershipSettlementService;
import ltdjms.discord.membership.services.MembershipSpendRetryService;
import ltdjms.discord.membership.services.MembershipSpendService;
import ltdjms.discord.membership.services.MembershipTokenGrantService;
import ltdjms.discord.product.domain.EscortOptionCatalogRepository;
import ltdjms.discord.shared.events.DomainEventPublisher;
import ltdjms.discord.shop.domain.FiatOrderRepository;

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
  public MembershipSpendRetryRepository provideMembershipSpendRetryRepository(
      DataSource dataSource) {
    return new JdbcMembershipSpendRetryRepository(dataSource);
  }

  @Provides
  @Singleton
  public MembershipTokenGrantRepository provideMembershipTokenGrantRepository(
      DataSource dataSource) {
    return new JdbcMembershipTokenGrantRepository(dataSource);
  }

  @Provides
  @Singleton
  public MembershipSettlementCoordinator provideMembershipSettlementCoordinator(
      DataSource dataSource) {
    return new JdbcMembershipSettlementCoordinator(dataSource);
  }

  @Provides
  @Singleton
  public MembershipSettlementTickGuard provideMembershipSettlementTickGuard(
      DataSource dataSource) {
    return new JdbcMembershipSettlementTickGuard(dataSource);
  }

  @Provides
  @Singleton
  @SettlementClock
  public Clock provideSettlementClock() {
    return Clock.system(MembershipJoinService.SETTLEMENT_ZONE);
  }

  @Provides
  @Singleton
  public MembershipJoinService provideMembershipJoinService(
      MembershipRepository membershipRepository, @SettlementClock Clock clock) {
    return new MembershipJoinService(membershipRepository, clock);
  }

  @Provides
  @Singleton
  public MembershipQueryService provideMembershipQueryService(
      MembershipRepository membershipRepository,
      MembershipSpendRepository membershipSpendRepository,
      @SettlementClock Clock clock) {
    return new MembershipQueryService(membershipRepository, membershipSpendRepository, clock);
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
      EscortOptionCatalogRepository escortOptionCatalogRepository,
      DomainEventPublisher eventPublisher) {
    return new MembershipSpendService(
        membershipSpendRepository,
        membershipRepository,
        escortOptionCatalogRepository,
        eventPublisher);
  }

  @Provides
  @Singleton
  public MembershipSpendRetryService provideMembershipSpendRetryService(
      MembershipSpendRetryRepository retryRepository,
      FiatOrderRepository fiatOrderRepository,
      MembershipSpendService membershipSpendService) {
    return new MembershipSpendRetryService(
        retryRepository, fiatOrderRepository, membershipSpendService);
  }

  @Provides
  @Singleton
  public MembershipTokenGrantService provideMembershipTokenGrantService(
      MembershipTokenGrantRepository grantRepository,
      MembershipSpendRepository spendRepository,
      MembershipRepository membershipRepository,
      GameTokenService gameTokenService,
      GameTokenTransactionService gameTokenTransactionService) {
    return new MembershipTokenGrantService(
        grantRepository,
        spendRepository,
        membershipRepository,
        gameTokenService,
        gameTokenTransactionService);
  }

  @Provides
  @Singleton
  public MembershipSettlementService provideMembershipSettlementService(
      MembershipSettlementCoordinator settlementCoordinator,
      MembershipTokenGrantService tokenGrantService,
      DomainEventPublisher eventPublisher,
      @SettlementClock Clock clock) {
    return new MembershipSettlementService(
        settlementCoordinator, tokenGrantService, eventPublisher, clock);
  }

  @Provides
  @Singleton
  public MembershipSettlementScheduler provideMembershipSettlementScheduler(
      MembershipRepository membershipRepository,
      MembershipSettlementService settlementService,
      MembershipTokenGrantService tokenGrantService,
      MembershipSpendRetryService spendRetryService,
      MembershipSettlementTickGuard tickGuard,
      @SettlementClock Clock clock) {
    return new MembershipSettlementScheduler(
        membershipRepository,
        settlementService,
        tokenGrantService,
        spendRetryService,
        tickGuard,
        clock);
  }
}
