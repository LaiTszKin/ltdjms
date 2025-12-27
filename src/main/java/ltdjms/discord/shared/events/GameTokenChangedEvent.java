package ltdjms.discord.shared.events;

/**
 * Event fired when a user's game token balance changes.
 *
 * @param guildId the Discord guild ID
 * @param userId the Discord user ID
 * @param newTokens the new token count after the change
 */
public record GameTokenChangedEvent(long guildId, long userId, long newTokens)
    implements DomainEvent {}
