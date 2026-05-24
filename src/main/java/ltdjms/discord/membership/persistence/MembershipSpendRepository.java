package ltdjms.discord.membership.persistence;

import java.time.Instant;
import java.util.Optional;

/** Persistence port for membership spend ledger entries. */
public interface MembershipSpendRepository {

  /**
   * Inserts a spend entry if no row exists for the same source identity.
   *
   * @return {@code true} when a new row was inserted, {@code false} when skipped as duplicate
   */
  boolean insertIfAbsent(
      long discordUserId,
      long guildId,
      long listPriceTwd,
      String escortOptionCode,
      String sourceType,
      String sourceReference,
      Instant paidAt);

  /** Returns the sum of catalog list prices M for a user within {@code [from, to)}. */
  long sumListPriceInPeriod(long discordUserId, Instant from, Instant to);

  /** Returns the guild ID from the user's most recent spend entry, if any. */
  Optional<Long> findMostRecentGuildId(long discordUserId);
}
