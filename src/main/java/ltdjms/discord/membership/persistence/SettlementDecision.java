package ltdjms.discord.membership.persistence;

import ltdjms.discord.membership.domain.MembershipTier;

/** Tier outcome computed by the settlement service from {@link SettlementContext}. */
public record SettlementDecision(MembershipTier previousTier, MembershipTier newTier) {}
