import { describe, it, expect, vi } from 'vitest';
import { DomainEventPublisher } from '../domain-event-publisher.js';
import type { DomainEvent } from '../../../types/events/domain-event.js';

/** Test helper: create a minimal DomainEvent. */
function testEvent(guildId: string): DomainEvent {
  return { guildId, eventType: 'test' };
}

/** Helper: wait for setImmediate to flush. */
function tick(): Promise<void> {
  return new Promise((resolve) => setImmediate(resolve));
}

describe('DomainEventPublisher', () => {
  it('publishes event to registered listeners', async () => {
    const publisher = new DomainEventPublisher();
    const listener = vi.fn();

    publisher.register(listener);
    const event = testEvent('123');
    publisher.publish(event);
    await tick();

    expect(listener).toHaveBeenCalledTimes(1);
    expect(listener).toHaveBeenCalledWith(event);
  });

  it('publishes to multiple listeners', async () => {
    const publisher = new DomainEventPublisher();
    const listener1 = vi.fn();
    const listener2 = vi.fn();

    publisher.register(listener1);
    publisher.register(listener2);
    publisher.publish(testEvent('456'));
    await tick();

    expect(listener1).toHaveBeenCalledTimes(1);
    expect(listener2).toHaveBeenCalledTimes(1);
  });

  it('isolates listener errors without propagating', async () => {
    const publisher = new DomainEventPublisher();
    const throwingListener = vi.fn(() => {
      throw new Error('listener error');
    });
    const normalListener = vi.fn();

    publisher.register(throwingListener);
    publisher.register(normalListener);

    expect(() => publisher.publish(testEvent('789'))).not.toThrow();
    await tick();

    expect(throwingListener).toHaveBeenCalledTimes(1);
    expect(normalListener).toHaveBeenCalledTimes(1);
  });

  it('handles no listeners gracefully', () => {
    const publisher = new DomainEventPublisher();
    expect(() => publisher.publish(testEvent('1'))).not.toThrow();
  });

  it('tracks last published event', () => {
    const publisher = new DomainEventPublisher();
    const event = testEvent('999');
    publisher.publish(event);
    expect(publisher.getLastPublishedEvent()).toBe(event);
  });

  it('reports correct listener count', () => {
    const publisher = new DomainEventPublisher();
    expect(publisher.listenerCount()).toBe(0);

    publisher.register(() => {});
    expect(publisher.listenerCount()).toBe(1);

    publisher.register(() => {});
    expect(publisher.listenerCount()).toBe(2);
  });

  it('clears all listeners', async () => {
    const publisher = new DomainEventPublisher();
    const listener = vi.fn();
    publisher.register(listener);
    publisher.clearListeners();
    publisher.publish(testEvent('1'));
    await tick();
    expect(listener).not.toHaveBeenCalled();
  });
});
