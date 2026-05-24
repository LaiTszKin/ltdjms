package ltdjms.discord.membership.persistence;

import java.time.Instant;

import ltdjms.discord.membership.domain.MembershipTier;

/** Persistence port for settlement token grant idempotency log. */
public interface MembershipTokenGrantRepository {

  /** Returns whether a grant was already recorded for the settlement period. */
  boolean hasGrantForPeriod(long discordUserId, Instant settlementPeriodEnd);

  /** Records a successful grant; caller must ensure idempotency via {@link #hasGrantForPeriod}. */
  void insertGrantLog(
      long discordUserId, Instant settlementPeriodEnd, MembershipTier tier, int tokensGranted);
}
