package ltdjms.discord.shared.events;

/**
 * Event fired when a product is created, updated, or deleted.
 *
 * @param guildId the Discord guild ID
 * @param productId the product ID
 * @param operationType the type of operation performed
 */
public record ProductChangedEvent(long guildId, long productId, OperationType operationType)
    implements DomainEvent {

  /** The type of product operation. */
  public enum OperationType {
    CREATED,
    UPDATED,
    DELETED
  }
}
