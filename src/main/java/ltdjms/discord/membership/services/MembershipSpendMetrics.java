package ltdjms.discord.membership.services;

import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Lightweight counters for membership spend recording failures. */
public final class MembershipSpendMetrics {

  private static final Logger LOG = LoggerFactory.getLogger(MembershipSpendMetrics.class);
  private static final AtomicLong RECORD_FAILURE_TOTAL = new AtomicLong();

  private MembershipSpendMetrics() {}

  /** Records a spend write failure and logs a structured metric line. */
  public static void recordFailure(String source, String reference) {
    long total = RECORD_FAILURE_TOTAL.incrementAndGet();
    LOG.error(
        "metric=membership_spend_record_failure_total total={} source={} reference={}",
        total,
        source,
        reference);
  }

  /** Returns the total failure count (primarily for tests). */
  public static long failureTotal() {
    return RECORD_FAILURE_TOTAL.get();
  }

  /** Resets counters (primarily for tests). */
  public static void reset() {
    RECORD_FAILURE_TOTAL.set(0L);
  }
}
