package ltdjms.discord.membership.persistence;

import java.time.Instant;

import ltdjms.discord.membership.domain.MembershipTier;

/** A settled period that still needs token grant retry. */
public record PendingMembershipGrant(
    long discordUserId, Instant settlementPeriodEnd, MembershipTier tier) {}
