package ltdjms.discord.shared.events;

/**
 * Event fired when a user's currency balance changes.
 *
 * @param guildId the Discord guild ID
 * @param userId the Discord user ID
 * @param newBalance the new balance after the change
 */
public record BalanceChangedEvent(long guildId, long userId, long newBalance)
    implements DomainEvent {}
