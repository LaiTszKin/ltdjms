package ltdjms.discord.membership.services;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.discord.domain.DiscordRuntimeGateway;
import ltdjms.discord.membership.persistence.MembershipRepository;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

/**
 * Backfills earliest guild join timestamps from Discord for members created before join tracking
 * existed or before the join listener recorded their join date.
 */
public class MembershipGuildJoinBackfillService {

  private static final Logger LOG =
      LoggerFactory.getLogger(MembershipGuildJoinBackfillService.class);

  private final MembershipRepository membershipRepository;
  private final MembershipJoinService membershipJoinService;
  private final DiscordRuntimeGateway discordRuntimeGateway;

  public MembershipGuildJoinBackfillService(
      MembershipRepository membershipRepository,
      MembershipJoinService membershipJoinService,
      DiscordRuntimeGateway discordRuntimeGateway) {
    this.membershipRepository = Objects.requireNonNull(membershipRepository);
    this.membershipJoinService = Objects.requireNonNull(membershipJoinService);
    this.discordRuntimeGateway = Objects.requireNonNull(discordRuntimeGateway);
  }

  /**
   * Ensures the member's Discord guild join time is stored when missing.
   *
   * @param guildId guild where the member is being viewed
   * @param userId Discord user snowflake
   */
  public void ensureRecordedFromGuild(long guildId, long userId) {
    if (!needsBackfill(userId)) {
      return;
    }
    resolveMemberJoinTime(guildId, userId).ifPresent(joinedAt -> recordJoin(userId, joinedAt));
  }

  /** Backfills missing join anchors for all members currently loaded in each guild. */
  public void backfillLoadedGuildMembers(JDA jda) {
    Objects.requireNonNull(jda, "jda");
    int updated = 0;
    for (Guild guild : jda.getGuilds()) {
      updated += backfillGuildMembers(guild);
    }
    if (updated > 0) {
      LOG.info("Backfilled membership join anchors for {} members", updated);
    } else {
      LOG.debug("No membership join anchors required backfill");
    }
  }

  private int backfillGuildMembers(Guild guild) {
    try {
      guild.loadMembers().get();
    } catch (RuntimeException e) {
      LOG.warn("Failed to load guild members for join backfill: guildId={}", guild.getIdLong(), e);
      return 0;
    }

    int updated = 0;
    for (Member member : guild.getMembers()) {
      long userId = member.getIdLong();
      if (!needsBackfill(userId)) {
        continue;
      }
      Instant joinedAt = memberJoinInstant(member);
      if (joinedAt == null) {
        continue;
      }
      if (recordJoin(userId, joinedAt)) {
        updated++;
      }
    }
    return updated;
  }

  private boolean needsBackfill(long userId) {
    return membershipRepository
        .findByUserId(userId)
        .map(membership -> membership.earliestGuildJoinAt() == null)
        .orElse(true);
  }

  private Optional<Instant> resolveMemberJoinTime(long guildId, long userId) {
    if (!discordRuntimeGateway.isReady()) {
      LOG.debug("Skipping join backfill because Discord runtime is not ready: userId={}", userId);
      return Optional.empty();
    }

    Optional<Guild> guild = discordRuntimeGateway.findGuild(guildId);
    if (guild.isEmpty()) {
      LOG.debug("Skipping join backfill because guild was not found: guildId={}", guildId);
      return Optional.empty();
    }

    try {
      Member member = guild.get().retrieveMemberById(userId).complete();
      Instant joinedAt = memberJoinInstant(member);
      if (joinedAt == null) {
        LOG.debug("Skipping join backfill because join time is unavailable: userId={}", userId);
        return Optional.empty();
      }
      return Optional.of(joinedAt);
    } catch (RuntimeException e) {
      LOG.warn(
          "Failed to resolve guild join time for backfill: guildId={}, userId={}",
          guildId,
          userId,
          e);
      return Optional.empty();
    }
  }

  private boolean recordJoin(long userId, Instant joinedAt) {
    try {
      membershipJoinService.onMemberJoin(userId, joinedAt);
      return true;
    } catch (RuntimeException e) {
      LOG.warn("Failed to record backfilled guild join for userId={}", userId, e);
      return false;
    }
  }

  private static Instant memberJoinInstant(Member member) {
    if (member == null || member.getTimeJoined() == null) {
      return null;
    }
    return member.getTimeJoined().toInstant();
  }
}
