package ltdjms.discord.membership.services;

/** Admin-facing membership detail for a selected user. */
public record MembershipAdminDetail(
    MembershipPanelSummary summary, boolean hasQualifyingBronzeOrder) {}
