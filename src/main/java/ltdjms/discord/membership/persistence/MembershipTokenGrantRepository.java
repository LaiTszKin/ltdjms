package ltdjms.discord.membership.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import ltdjms.discord.membership.domain.MembershipTier;

/** Persistence port for settlement token grant idempotency log. */
public interface MembershipTokenGrantRepository {

  /**
   * Claims a grant slot for the settlement period. Only the caller that receives {@code true}
   * should credit tokens. Reclaims rows in {@code FAILED} status.
   */
  boolean tryClaimGrantLog(
      long discordUserId, Instant settlementPeriodEnd, MembershipTier tier, int tokensGranted);

  /** Returns a reclaimable grant claim for retry, if any. */
  Optional<GrantClaimState> findClaimState(long discordUserId, Instant settlementPeriodEnd);

  /** Removes a failed grant claim before any tokens were credited. */
  void releaseGrantClaim(long discordUserId, Instant settlementPeriodEnd);

  /** Marks that game tokens were credited for an in-progress claim. */
  void markTokensAdjusted(long discordUserId, Instant settlementPeriodEnd);

  /** Marks a grant as fully completed after audit logging succeeds. */
  void completeGrantClaim(long discordUserId, Instant settlementPeriodEnd);

  /** Marks a grant as retryable after tokens were credited but audit logging failed. */
  void markGrantFailed(long discordUserId, Instant settlementPeriodEnd);

  /** Returns settled periods that still need token grants, up to {@code limit} rows. */
  List<PendingMembershipGrant> findPendingGrants(int limit);
}
