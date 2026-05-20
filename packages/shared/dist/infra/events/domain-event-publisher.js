import { EventEmitter } from 'node:events';
import pino from 'pino';
const EVENT_CHANNEL = 'domain-event';
/**
 * Publishes domain events to registered listeners.
 * Events are dispatched synchronously.
 * Errors in listeners are caught and logged per listener without propagation.
 * Matches Java DomainEventPublisher.
 */
export class DomainEventPublisher {
    emitter = new EventEmitter();
    /** Captured for testing — tracks the last published event. */
    _lastEvent = null;
    logger;
    constructor(logger) {
        this.logger = logger ?? pino({ level: 'warn' });
    }
    /**
     * Registers a listener for all domain events.
     * @param listener - function to call when any domain event is published
     */
    register(listener) {
        this.emitter.on(EVENT_CHANNEL, listener);
    }
    /**
     * Publishes an event to all registered listeners.
     * Exceptions from individual listeners are caught and logged but do not propagate.
     * @param event - the domain event to publish
     */
    publish(event) {
        this._lastEvent = event;
        const listeners = this.emitter.listeners(EVENT_CHANNEL);
        for (const listener of listeners) {
            try {
                listener(event);
            }
            catch (err) {
                // Log but don't propagate — this mirrors Java behavior
                this.logger.error({ eventName: event.constructor?.name ?? typeof event, err }, '[DomainEventPublisher] Error handling event');
            }
        }
    }
    /** Returns the last published event (for testing). */
    getLastPublishedEvent() {
        return this._lastEvent;
    }
    /** Returns the number of registered listeners. */
    listenerCount() {
        return this.emitter.listenerCount(EVENT_CHANNEL);
    }
    /** Removes all registered listeners. */
    clearListeners() {
        this.emitter.removeAllListeners(EVENT_CHANNEL);
    }
}
//# sourceMappingURL=domain-event-publisher.js.map