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
  /** Maps original listener → wrapped function for unregister support. */
  private readonly wrapperMap = new WeakMap<(event: DomainEvent) => void | Promise<void>, (event: DomainEvent) => void>();

  constructor(logger?: pino.Logger) {
    this.logger = logger ?? pino({ level: 'warn' });
  }

  /**
   * Registers a listener for all domain events.
   * Accepts both sync and async listeners.
   * Async listeners: rejections are caught and logged, never propagated.
   */
  register(listener: (event: DomainEvent) => void | Promise<void>): void {
    const wrapped = (event: DomainEvent): void => {
      try {
        const result = listener(event);
        if (result instanceof Promise) {
          result.catch((err) => {
            this.logger.error(
              { eventName: typeof event, err },
              '[DomainEventPublisher] Async listener rejected',
            );
          });
        }
      } catch (err) {
        this.logger.error(
          { eventName: typeof event, err },
          '[DomainEventPublisher] Error handling event',
        );
      }
    };
    this.wrapperMap.set(listener, wrapped);
    this.emitter.on(EVENT_CHANNEL, wrapped);
  }

  /**
   * Unregisters a previously registered listener.
   * Safe to call even if the listener was never registered.
   */
  unregister(listener: (event: DomainEvent) => void | Promise<void>): void {
    const wrapped = this.wrapperMap.get(listener);
    if (wrapped) {
      this.emitter.off(EVENT_CHANNEL, wrapped);
      this.wrapperMap.delete(listener);
    }
  }

  /**
   * Publishes an event to all registered listeners synchronously.
   * Listeners are invoked in registration order.
   * Exceptions from individual listeners are caught and logged but do not propagate.
   * @param event - the domain event to publish
   */
  publish(event: DomainEvent): void {
    this._lastEvent = event;
    const listeners = this.emitter.listeners(EVENT_CHANNEL) as Array<
      (event: DomainEvent) => void
    >;

    this.logger.debug({ event }, 'Publishing event');

    for (const listener of listeners) {
      try {
        listener(event);
      } catch (err) {
        // Log but don't propagate — sync error from wrapped listener
        this.logger.error(
          { eventName: typeof event, err },
          '[DomainEventPublisher] Error handling event',
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
