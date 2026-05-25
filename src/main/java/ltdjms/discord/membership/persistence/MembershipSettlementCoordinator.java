package ltdjms.discord.membership.persistence;

import java.time.Instant;
import java.util.Optional;

/** Port for atomic membership settlement with row-level locking. */
public interface MembershipSettlementCoordinator {

  /**
   * Locks the membership row, sums period spend, applies {@code decisionMaker}, and writes
   * settlement when due.
   *
   * @return settlement outcome when applied, empty when skipped
   */
  Optional<SettlementApplyResult> applyIfDue(
      long discordUserId, Instant now, SettlementDecisionMaker decisionMaker);
}
