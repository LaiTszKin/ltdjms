package ltdjms.discord.membership.persistence;

import java.time.Instant;

import ltdjms.discord.membership.domain.MembershipTier;

/** Outcome of an atomic membership settlement write. */
public record SettlementApplyResult(
    long discordUserId,
    MembershipTier previousTier,
    MembershipTier newTier,
    long periodAvgListPriceM,
    Instant periodStart,
    Instant periodEnd,
    Instant settledAt,
    Instant newNextSettlementAt) {}
