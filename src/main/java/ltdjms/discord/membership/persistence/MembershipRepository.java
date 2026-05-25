package ltdjms.discord.membership.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import ltdjms.discord.membership.domain.GlobalMemberMembership;

/** Persistence port for global member membership state. */
public interface MembershipRepository {

  Optional<GlobalMemberMembership> findByUserId(long discordUserId);

  GlobalMemberMembership findOrCreate(long discordUserId);

  GlobalMemberMembership save(GlobalMemberMembership membership);

  /**
   * Returns Discord user IDs whose {@code next_settlement_at} is due on or before {@code before},
   * up to {@code limit} rows.
   */
  List<Long> findDueForSettlement(Instant before, int limit);

  /**
   * Atomically records an earlier guild join when {@code joinedAt} is before the stored value.
   *
   * @return {@code true} when the row was updated
   */
  boolean mergeEarliestGuildJoin(
      long discordUserId, Instant joinedAt, int settlementDay, Instant nextSettlementAt);
}
