package ltdjms.discord.membership.services;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.membership.persistence.MembershipRepository;

/** Periodically settles global membership tiers for users whose settlement date is due. */
public class MembershipSettlementScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(MembershipSettlementScheduler.class);
  private static final long SETTLEMENT_INTERVAL_SECONDS = 3600L;
  static final int SETTLEMENT_BATCH_LIMIT = 100;
  static final int MAX_BATCHES_PER_TICK = 10;

  private final MembershipRepository membershipRepository;
  private final MembershipSettlementService settlementService;
  private final MembershipTokenGrantService tokenGrantService;
  private final Clock clock;
  private ScheduledExecutorService executorService;

  public MembershipSettlementScheduler(
      MembershipRepository membershipRepository,
      MembershipSettlementService settlementService,
      MembershipTokenGrantService tokenGrantService,
      Clock clock) {
    this.membershipRepository = Objects.requireNonNull(membershipRepository);
    this.settlementService = Objects.requireNonNull(settlementService);
    this.tokenGrantService = Objects.requireNonNull(tokenGrantService);
    this.clock = Objects.requireNonNull(clock);
  }

  public synchronized void start() {
    if (executorService != null) {
      return;
    }
    executorService = Executors.newSingleThreadScheduledExecutor();
    executorService.scheduleWithFixedDelay(
        this::runSettlement, 30L, SETTLEMENT_INTERVAL_SECONDS, TimeUnit.SECONDS);
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
    try {
      tokenGrantService.retryPendingGrants();
      int batchesProcessed = 0;
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
        batchesProcessed++;
      } while (batch.size() == SETTLEMENT_BATCH_LIMIT && batchesProcessed < MAX_BATCHES_PER_TICK);

      if (batch.size() == SETTLEMENT_BATCH_LIMIT) {
        LOG.warn(
            "Membership settlement backlog remains after {} batches ({} users per batch)",
            MAX_BATCHES_PER_TICK,
            SETTLEMENT_BATCH_LIMIT);
      }
    } catch (Exception e) {
      LOG.warn("Membership settlement scheduler tick failed", e);
    }
  }
}
