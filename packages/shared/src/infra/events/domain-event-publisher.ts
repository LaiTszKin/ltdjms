import { EventEmitter } from 'node:events';
import { type DomainEvent } from '../../types/events/domain-event.js';

const EVENT_CHANNEL = 'domain-event';

/**
 * Publishes domain events to registered listeners.
 * Events are dispatched synchronously.
 * Errors in listeners are caught and logged per listener without propagation.
 * Matches Java DomainEventPublisher.
 */
export class DomainEventPublisher {
  private readonly emitter = new EventEmitter();
  /** Captured for testing — tracks the last published event. */
  private _lastEvent: DomainEvent | null = null;

  /**
   * Registers a listener for all domain events.
   * @param listener - function to call when any domain event is published
   */
  register(listener: (event: DomainEvent) => void): void {
    this.emitter.on(EVENT_CHANNEL, listener);
  }

  /**
   * Publishes an event to all registered listeners.
   * Exceptions from individual listeners are caught and logged but do not propagate.
   * @param event - the domain event to publish
   */
  publish(event: DomainEvent): void {
    this._lastEvent = event;
    const listeners = this.emitter.listeners(EVENT_CHANNEL) as Array<
      (event: DomainEvent) => void
    >;

    for (const listener of listeners) {
      try {
        listener(event);
      } catch (err) {
        // Log but don't propagate — this mirrors Java behavior
        console.error(
          `[DomainEventPublisher] Error handling event ${event.constructor?.name ?? typeof event}:`,
          err,
        );
      }
    }
  }

  /** Returns the last published event (for testing). */
  getLastPublishedEvent(): DomainEvent | null {
    return this._lastEvent;
  }

  /** Returns the number of registered listeners. */
  listenerCount(): number {
    return this.emitter.listenerCount(EVENT_CHANNEL);
  }

  /** Removes all registered listeners. */
  clearListeners(): void {
    this.emitter.removeAllListeners(EVENT_CHANNEL);
  }
}
