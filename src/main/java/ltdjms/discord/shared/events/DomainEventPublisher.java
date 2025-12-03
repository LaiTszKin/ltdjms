package ltdjms.discord.shared.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Publishes domain events to registered listeners.
 * Events are dispatched synchronously.
 */
public class DomainEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(DomainEventPublisher.class);

    private final List<Consumer<DomainEvent>> listeners = new CopyOnWriteArrayList<>();

    /**
     * Registers a listener for domain events.
     *
     * @param listener the listener to register
     */
    public void register(Consumer<DomainEvent> listener) {
        listeners.add(listener);
    }

    /**
     * Publishes an event to all registered listeners.
     * Exceptions thrown by listeners are caught and logged, preventing them from propagating
     * to the publisher.
     *
     * @param event the event to publish
     */
    public void publish(DomainEvent event) {
        LOG.debug("Publishing event: {}", event);
        for (Consumer<DomainEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                LOG.error("Error handling domain event: {}", event, e);
            }
        }
    }
}
