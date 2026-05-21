/**
 * Base interface for all domain events.
 * All events must have a guildId to identify the server context.
 * Matches the Java DomainEvent sealed interface pattern.
 */
export interface DomainEvent {
  readonly guildId: string;
  /** Discriminant for event type identification. */
  readonly eventType: string;
}
