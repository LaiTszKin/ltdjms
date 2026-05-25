package ltdjms.discord.membership.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ltdjms.discord.currency.integration.PostgresIntegrationTestBase;
import ltdjms.discord.gametoken.persistence.JdbcGameTokenAccountRepository;
import ltdjms.discord.gametoken.persistence.JdbcGameTokenTransactionRepository;
import ltdjms.discord.gametoken.services.GameTokenService;
import ltdjms.discord.gametoken.services.GameTokenTransactionService;
import ltdjms.discord.membership.domain.GlobalMemberMembership;
import ltdjms.discord.membership.domain.MembershipTier;
import ltdjms.discord.membership.persistence.JdbcMembershipRepository;
import ltdjms.discord.membership.persistence.JdbcMembershipSpendRepository;
import ltdjms.discord.membership.persistence.JdbcMembershipTokenGrantRepository;
import ltdjms.discord.membership.persistence.MembershipRepository;
import ltdjms.discord.membership.persistence.MembershipSpendRepository;
import ltdjms.discord.membership.services.MembershipJoinService;
import ltdjms.discord.membership.services.MembershipSettlementService;
import ltdjms.discord.membership.services.MembershipTokenGrantService;
import ltdjms.discord.shared.cache.DefaultCacheKeyGenerator;
import ltdjms.discord.shared.cache.NoOpCacheService;
import ltdjms.discord.shared.events.DomainEventPublisher;
import ltdjms.discord.shared.events.MembershipTierChangedEvent;

/** End-to-end integration tests for membership settlement. */
class MembershipSettlementIntegrationTest extends PostgresIntegrationTestBase {

  private static final long TEST_USER_ID = 987654321098765432L;
  private static final long TEST_GUILD_ID = 111222333444555666L;
  private static final Instant PERIOD_START = Instant.parse("2026-03-15T00:00:00+08:00");
  private static final Instant PERIOD_END = Instant.parse("2026-04-15T00:00:00+08:00");
  private static final Instant SETTLE_NOW = Instant.parse("2026-04-15T08:00:00Z");
  private static final Instant NEXT_SETTLEMENT = Instant.parse("2026-05-15T00:00:00+08:00");

  private MembershipRepository membershipRepository;
  private MembershipSpendRepository spendRepository;
  private MembershipTokenGrantService tokenGrantService;
  private MembershipSettlementService settlementService;
  private GameTokenService gameTokenService;
  private RecordingEventPublisher eventPublisher;

  @BeforeEach
  void setUp() {
    membershipRepository = new JdbcMembershipRepository(dataSource);
    spendRepository = new JdbcMembershipSpendRepository(dataSource);
    eventPublisher = new RecordingEventPublisher();
    var grantRepository = new JdbcMembershipTokenGrantRepository(dataSource);
    var accountRepository = new JdbcGameTokenAccountRepository(dataSource);
    var transactionRepository = new JdbcGameTokenTransactionRepository(dataSource);
    gameTokenService =
        new GameTokenService(
            accountRepository,
            eventPublisher,
            NoOpCacheService.getInstance(),
            new DefaultCacheKeyGenerator());
    GameTokenTransactionService transactionService =
        new GameTokenTransactionService(transactionRepository);
    tokenGrantService =
        new MembershipTokenGrantService(
            grantRepository,
            spendRepository,
            membershipRepository,
            gameTokenService,
            transactionService);
    Clock clock = Clock.fixed(SETTLE_NOW, MembershipJoinService.SETTLEMENT_ZONE);
    settlementService =
        new MembershipSettlementService(
            membershipRepository, spendRepository, tokenGrantService, eventPublisher, clock);
  }

  @Test
  @DisplayName("should settle tier from spend ledger and advance settlement dates")
  void shouldSettleFromSpendLedger() {
    seedMembership(MembershipTier.BRONZE, true);
    insertSpend(14_000L, Instant.parse("2026-03-20T10:00:00Z"));
    insertSpend(1_000L, Instant.parse("2026-04-10T10:00:00Z"));

    assertThat(settlementService.settle(TEST_USER_ID)).isTrue();

    GlobalMemberMembership settled = membershipRepository.findByUserId(TEST_USER_ID).orElseThrow();
    assertThat(settled.currentTier()).isEqualTo(MembershipTier.SILVER);
    assertThat(settled.lastSettlementAt()).isEqualTo(PERIOD_END);
    assertThat(settled.nextSettlementAt()).isEqualTo(NEXT_SETTLEMENT);
    assertThat(eventPublisher.tierChanges()).hasSize(1);
    assertThat(eventPublisher.tierChanges().get(0).previous()).isEqualTo(MembershipTier.BRONZE);
    assertThat(eventPublisher.tierChanges().get(0).current()).isEqualTo(MembershipTier.SILVER);
    assertThat(eventPublisher.tierChanges().get(0).periodAvgListPriceM()).isEqualTo(15_000L);
  }

  @Test
  @DisplayName("should include spend paid between period end and settle execution")
  void shouldIncludeSpendBetweenPeriodEndAndSettleExecution() {
    seedMembership(MembershipTier.BRONZE, true);
    insertSpend(14_000L, Instant.parse("2026-03-20T10:00:00Z"));
    insertSpend(1_000L, Instant.parse("2026-04-15T02:00:00+08:00"));

    assertThat(settlementService.settle(TEST_USER_ID)).isTrue();

    GlobalMemberMembership settled = membershipRepository.findByUserId(TEST_USER_ID).orElseThrow();
    assertThat(settled.currentTier()).isEqualTo(MembershipTier.SILVER);
    assertThat(settled.lastSettlementAt()).isEqualTo(PERIOD_END);
  }

  @Test
  @DisplayName("should find due users and keep bronze floor with zero spend")
  void shouldFindDueAndKeepBronzeFloor() {
    seedMembership(MembershipTier.SILVER, true);
    insertSpend(20_000L, Instant.parse("2026-01-20T10:00:00Z"));

    List<Long> due = membershipRepository.findDueForSettlement(SETTLE_NOW);
    assertThat(due).contains(TEST_USER_ID);

    assertThat(settlementService.settle(TEST_USER_ID)).isTrue();

    GlobalMemberMembership settled = membershipRepository.findByUserId(TEST_USER_ID).orElseThrow();
    assertThat(settled.currentTier()).isEqualTo(MembershipTier.BRONZE);
    assertThat(settled.nextSettlementAt()).isEqualTo(NEXT_SETTLEMENT);
  }

  @Test
  @DisplayName("should grant game tokens after settlement to SILVER")
  void shouldGrantTokensAfterSettlement() {
    seedMembership(MembershipTier.BRONZE, true);
    insertSpend(15_000L, Instant.parse("2026-04-01T10:00:00Z"));

    assertThat(settlementService.settle(TEST_USER_ID)).isTrue();

    assertThat(gameTokenService.getBalance(TEST_GUILD_ID, TEST_USER_ID)).isEqualTo(100L);
    assertThat(
            new JdbcMembershipTokenGrantRepository(dataSource)
                .findClaimState(TEST_USER_ID, PERIOD_END)
                .map(state -> "COMPLETED".equals(state.status()))
                .orElse(false))
        .isTrue();
  }

  @Test
  @DisplayName("should skip settlement when next_settlement_at is null")
  void shouldSkipWhenNotInitialized() {
    membershipRepository.findOrCreate(TEST_USER_ID);

    assertThat(settlementService.settle(TEST_USER_ID)).isFalse();
    assertThat(membershipRepository.findDueForSettlement(SETTLE_NOW)).isEmpty();
  }

  private void seedMembership(MembershipTier tier, boolean bronzeFlag) {
    GlobalMemberMembership created = membershipRepository.findOrCreate(TEST_USER_ID);
    membershipRepository.save(
        new GlobalMemberMembership(
            TEST_USER_ID,
            tier,
            PERIOD_START,
            15,
            PERIOD_START,
            PERIOD_END,
            bronzeFlag,
            created.createdAt(),
            created.updatedAt()));
  }

  private void insertSpend(long listPriceTwd, Instant paidAt) {
    spendRepository.insertSpendAndQualifyBronzeIfThreshold(
        TEST_USER_ID,
        TEST_GUILD_ID,
        listPriceTwd,
        "ESCORT_A",
        "FIAT_ORDER",
        "order-" + paidAt.toEpochMilli(),
        paidAt,
        Long.MAX_VALUE);
  }

  private static final class RecordingEventPublisher extends DomainEventPublisher {
    private final java.util.ArrayList<MembershipTierChangedEvent> tierChanges =
        new java.util.ArrayList<>();

    @Override
    public void publish(ltdjms.discord.shared.events.DomainEvent event) {
      if (event instanceof MembershipTierChangedEvent tierChanged) {
        tierChanges.add(tierChanged);
      }
    }

    List<MembershipTierChangedEvent> tierChanges() {
      return List.copyOf(tierChanges);
    }
  }
}
