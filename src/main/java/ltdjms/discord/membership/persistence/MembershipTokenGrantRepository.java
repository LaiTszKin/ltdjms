package ltdjms.discord.membership.persistence;

import java.time.Instant;
import java.util.List;

import ltdjms.discord.membership.domain.MembershipTier;

/** Persistence port for settlement token grant idempotency log. */
public interface MembershipTokenGrantRepository {

  /** Returns whether a grant was already recorded for the settlement period. */
  boolean hasGrantForPeriod(long discordUserId, Instant settlementPeriodEnd);

  /**
   * Claims a grant slot for the settlement period. Only the caller that receives {@code true}
   * should credit tokens.
   */
  boolean tryClaimGrantLog(
      long discordUserId, Instant settlementPeriodEnd, MembershipTier tier, int tokensGranted);

  /** Removes a failed grant claim so a later retry can reclaim the settlement period. */
  void releaseGrantClaim(long discordUserId, Instant settlementPeriodEnd);

  /** Records a successful grant; prefer {@link #tryClaimGrantLog} for idempotent flows. */
  void insertGrantLog(
      long discordUserId, Instant settlementPeriodEnd, MembershipTier tier, int tokensGranted);

  /** Returns settled periods that still need token grants, up to {@code limit} rows. */
  List<PendingMembershipGrant> findPendingGrants(int limit);
}
