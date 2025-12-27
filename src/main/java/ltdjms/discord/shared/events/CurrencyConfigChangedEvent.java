package ltdjms.discord.shared.events;

/**
 * Event fired when a guild's currency configuration changes.
 *
 * @param guildId the Discord guild ID
 * @param currencyName the new currency name
 * @param currencyIcon the new currency icon
 */
public record CurrencyConfigChangedEvent(long guildId, String currencyName, String currencyIcon)
    implements DomainEvent {}
