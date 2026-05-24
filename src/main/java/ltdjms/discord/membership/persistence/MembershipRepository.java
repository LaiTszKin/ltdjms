package ltdjms.discord.membership.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import ltdjms.discord.membership.domain.GlobalMemberMembership;
import ltdjms.discord.membership.domain.MembershipTier;

/** Persistence port for global member membership state. */
public interface MembershipRepository {

  Optional<GlobalMemberMembership> findByUserId(long discordUserId);

  GlobalMemberMembership findOrCreate(long discordUserId);

  GlobalMemberMembership save(GlobalMemberMembership membership);

  /** Returns Discord user IDs whose {@code next_settlement_at} is due on or before {@code before}. */
  List<Long> findDueForSettlement(Instant before);

  /**
   * Applies settlement updates only when {@code expectedNextSettlementAt} still matches the row.
   *
   * @return {@code true} when the row was updated, {@code false} when skipped (already settled)
   */
  boolean saveSettlementResult(
      long discordUserId,
      MembershipTier newTier,
      Instant lastSettlementAt,
      Instant newNextSettlementAt,
      Instant expectedNextSettlementAt);
}
