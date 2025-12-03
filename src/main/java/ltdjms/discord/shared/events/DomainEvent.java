package ltdjms.discord.shared.events;

/**
 * Base interface for domain events.
 */
public sealed interface DomainEvent permits BalanceChangedEvent, GameTokenChangedEvent {
    /**
     * @return the Discord guild ID where the event occurred
     */
    long guildId();

    /**
     * @return the Discord user ID associated with the event
     */
    long userId();
}
