package ltdjms.discord.membership.services;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.membership.persistence.MembershipRepository;
import ltdjms.discord.membership.persistence.MembershipSettlementTickGuard;

/** Periodically settles global membership tiers for users whose settlement date is due. */
public class MembershipSettlementScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(MembershipSettlementScheduler.class);
  private static final long SETTLEMENT_INTERVAL_SECONDS = 3600L;
  static final int SETTLEMENT_BATCH_LIMIT = 100;
  static final long SETTLEMENT_TICK_TIME_BUDGET_MS = 55L * 60L * 1000L;

  private final MembershipRepository membershipRepository;
  private final MembershipSettlementService settlementService;
  private final MembershipTokenGrantService tokenGrantService;
  private final MembershipSpendRetryService spendRetryService;
  private final MembershipSettlementTickGuard tickGuard;
  private final Clock clock;
  private java.util.concurrent.ScheduledExecutorService executorService;

  public MembershipSettlementScheduler(
      MembershipRepository membershipRepository,
      MembershipSettlementService settlementService,
      MembershipTokenGrantService tokenGrantService,
      MembershipSpendRetryService spendRetryService,
      MembershipSettlementTickGuard tickGuard,
      Clock clock) {
    this.membershipRepository = Objects.requireNonNull(membershipRepository);
    this.settlementService = Objects.requireNonNull(settlementService);
    this.tokenGrantService = Objects.requireNonNull(tokenGrantService);
    this.spendRetryService = Objects.requireNonNull(spendRetryService);
    this.tickGuard = Objects.requireNonNull(tickGuard);
    this.clock = Objects.requireNonNull(clock);
  }

  public synchronized void start() {
    if (executorService != null) {
      return;
    }
    executorService = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
    executorService.scheduleWithFixedDelay(
        this::runSettlement, 30L, SETTLEMENT_INTERVAL_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
    LOG.info("Started membership settlement scheduler");
  }

  public synchronized void stop() {
    if (executorService == null) {
      return;
    }
    executorService.shutdownNow();
    executorService = null;
    LOG.info("Stopped membership settlement scheduler");
  }

  void runSettlement() {
    tokenGrantService.retryPendingGrants();
    spendRetryService.retryPendingSpends();
    tickGuard.runGuarded(this::runSettlementBatches);
    tokenGrantService.retryPendingGrants();
  }

  private void runSettlementBatches() {
    try {
      long tickStartedAt = System.currentTimeMillis();
      List<Long> batch;
      do {
        batch =
            membershipRepository.findDueForSettlement(clock.instant(), SETTLEMENT_BATCH_LIMIT);
        for (long userId : batch) {
          try {
            settlementService.settle(userId);
          } catch (Exception e) {
            LOG.warn("Membership settlement failed for userId={}", userId, e);
          }
        }
        if (System.currentTimeMillis() - tickStartedAt >= SETTLEMENT_TICK_TIME_BUDGET_MS) {
          if (batch.size() == SETTLEMENT_BATCH_LIMIT) {
            LOG.warn(
                "Membership settlement backlog remains after tick time budget ({} ms)",
                SETTLEMENT_TICK_TIME_BUDGET_MS);
          }
          break;
        }
      } while (batch.size() == SETTLEMENT_BATCH_LIMIT);
    } catch (Exception e) {
      LOG.warn("Membership settlement scheduler tick failed", e);
    }
  }
}
