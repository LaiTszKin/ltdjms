package ltdjms.discord.membership.persistence;

/** Result of inserting a membership spend entry with optional bronze promotion. */
public record SpendRecordResult(boolean inserted, boolean bronzePromoted) {}
