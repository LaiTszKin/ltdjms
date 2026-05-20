import { type DomainEvent } from '../../types/events/domain-event.js';
import pino from 'pino';
/**
 * Publishes domain events to registered listeners.
 * Events are dispatched synchronously.
 * Errors in listeners are caught and logged per listener without propagation.
 * Matches Java DomainEventPublisher.
 */
export declare class DomainEventPublisher {
    private readonly emitter;
    /** Captured for testing — tracks the last published event. */
    private _lastEvent;
    private readonly logger;
    constructor(logger?: pino.Logger);
    /**
     * Registers a listener for all domain events.
     * @param listener - function to call when any domain event is published
     */
    register(listener: (event: DomainEvent) => void): void;
    /**
     * Publishes an event to all registered listeners.
     * Exceptions from individual listeners are caught and logged but do not propagate.
     * @param event - the domain event to publish
     */
    publish(event: DomainEvent): void;
    /** Returns the last published event (for testing). */
    getLastPublishedEvent(): DomainEvent | null;
    /** Returns the number of registered listeners. */
    listenerCount(): number;
    /** Removes all registered listeners. */
    clearListeners(): void;
}
