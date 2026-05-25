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

  /**
   * Returns Discord user IDs whose {@code next_settlement_at} is due on or before {@code before}.
   */
  List<Long> findDueForSettlement(Instant before);

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

  /**
   * Initializes {@code next_settlement_at} from the first spend when join tracking was never
   * recorded. Does not write {@code earliest_guild_join_at}; join events remain authoritative.
   *
   * @return {@code true} when anchors were set
   */
  boolean ensureSettlementAnchor(long discordUserId, Instant anchorFrom, int settlementDay);

  /**
   * Marks bronze qualification when the user has not yet met the threshold.
   *
   * @return {@code true} when the row was updated
   */
  boolean qualifyBronzeIfThreshold(long discordUserId);

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
