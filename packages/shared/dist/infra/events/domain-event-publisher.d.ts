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
     * Registers a synchronous listener for all domain events.
     * @param listener - function to call when any domain event is published
     */
    register(listener: (event: DomainEvent) => void): void;
    /**
     * Registers an async listener for all domain events.
     * Async listeners are awaited via Promise.allSettled() during publish().
     * @param listener - async function to call when any domain event is published
     */
    registerAsync(listener: (event: DomainEvent) => Promise<void>): void;
    /**
     * Publishes an event to all registered listeners.
     * Sync listeners are invoked in order; async listeners are awaited via Promise.allSettled().
     * Exceptions from individual listeners are caught and logged but do not propagate.
     * @param event - the domain event to publish
     */
    publish(event: DomainEvent): Promise<void>;
    /** Returns the last published event (for testing). */
    getLastPublishedEvent(): DomainEvent | null;
    /** Returns the number of registered listeners. */
    listenerCount(): number;
    /** Removes all registered listeners. */
    clearListeners(): void;
}
