package ltdjms.discord.membership.persistence;

import java.time.Instant;

import ltdjms.discord.membership.domain.GlobalMemberMembership;

/** Locked membership row and period spend inputs for settlement tier decisions. */
public record SettlementContext(
    GlobalMemberMembership membership,
    Instant periodStart,
    Instant periodEnd,
    long periodAvgListPriceM) {}
