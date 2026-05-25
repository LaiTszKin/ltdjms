package ltdjms.discord.membership.persistence;

/** In-progress or retryable membership token grant claim. */
public record GrantClaimState(String status, boolean tokensAdjusted, boolean auditRecorded) {}
