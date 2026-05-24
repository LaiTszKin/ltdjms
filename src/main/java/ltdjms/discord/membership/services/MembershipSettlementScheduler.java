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
      List<Long> dueUserIds =
          membershipRepository.findDueForSettlement(clock.instant(), SETTLEMENT_BATCH_LIMIT);
      for (long userId : dueUserIds) {
        try {
          settlementService.settle(userId);
        } catch (Exception e) {
          LOG.warn("Membership settlement failed for userId={}", userId, e);
        }
      }
    } catch (Exception e) {
      LOG.warn("Membership settlement scheduler tick failed", e);
    }
  }
}
