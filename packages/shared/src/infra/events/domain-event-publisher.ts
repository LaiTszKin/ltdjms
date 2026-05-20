import { EventEmitter } from 'node:events';
import { type DomainEvent } from '../../types/events/domain-event.js';
import pino from 'pino';

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
  private readonly logger: pino.Logger;

  constructor(logger?: pino.Logger) {
    this.logger = logger ?? pino({ level: 'warn' });
  }

  /**
   * Registers a synchronous listener for all domain events.
   * @param listener - function to call when any domain event is published
   */
  register(listener: (event: DomainEvent) => void): void {
    this.emitter.on(EVENT_CHANNEL, listener);
  }

  /**
   * Publishes an event to all registered listeners.
   * Sync listeners are invoked in order; async listeners are awaited via Promise.allSettled().
   * Exceptions from individual listeners are caught and logged but do not propagate.
   * @param event - the domain event to publish
   */
  async publish(event: DomainEvent): Promise<void> {
    this._lastEvent = event;
    const listeners = this.emitter.listeners(EVENT_CHANNEL) as Array<
      ((event: DomainEvent) => void) | ((event: DomainEvent) => Promise<void>)
    >;

    this.logger.debug({ event }, 'Publishing event');

    const asyncTasks: Promise<void>[] = [];

    for (const listener of listeners) {
      try {
        const result = listener(event);
        if (result && typeof (result as Promise<void>).then === 'function') {
          asyncTasks.push(result as Promise<void>);
        }
      } catch (err) {
        // Log but don't propagate — this mirrors Java behavior
        this.logger.error(
          { eventName: event.constructor?.name ?? typeof event, err },
          '[DomainEventPublisher] Error handling event',
        );
      }
    }

    if (asyncTasks.length > 0) {
      const results = await Promise.allSettled(asyncTasks);
      for (const r of results) {
        if (r.status === 'rejected') {
          this.logger.error(
            { eventName: event.constructor?.name ?? typeof event, err: r.reason },
            '[DomainEventPublisher] Async event handler rejected',
          );
        }
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
