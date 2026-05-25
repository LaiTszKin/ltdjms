package ltdjms.discord.membership.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Lightweight counters for membership spend recording failures. */
public final class MembershipSpendMetrics {

  private static final Logger LOG = LoggerFactory.getLogger(MembershipSpendMetrics.class);

  private MembershipSpendMetrics() {}

  /** Records a spend write failure and logs a structured metric line. */
  public static void recordFailure(String source, String reference) {
    LOG.error("metric=membership_spend_record_failure source={} reference={}", source, reference);
  }
}
