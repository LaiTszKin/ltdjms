package ltdjms.discord.shared.events;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.inject.Singleton;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dagger.Component;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import ltdjms.discord.shared.di.EventModule;

@DisplayName("DomainEventPublisher Dagger 接線測試")
class DomainEventPublisherDaggerWiringTest {

  private static final BalanceChangedEvent TEST_EVENT = new BalanceChangedEvent(11L, 22L, 33L);

  @Test
  @DisplayName("Dagger 應自動組裝事件監聽器到統一事件管道")
  void shouldAssembleListenersIntoUnifiedEventPipeline() {
    TestEventComponent component = DaggerTestEventComponent.create();

    component.domainEventPublisher().publish(TEST_EVENT);

    assertEquals(1, component.recordingListener().invocationCount());
    assertTrue(component.throwingListener().wasInvoked());
  }

  @Test
  @DisplayName("沒有 listener 綁定時仍應可建立事件管道")
  void shouldBuildPublisherWhenNoListenersAreBound() {
    EmptyEventComponent component = DaggerEmptyEventComponent.create();

    assertDoesNotThrow(() -> component.domainEventPublisher().publish(TEST_EVENT));
  }
}

@Singleton
@Component(modules = {EventModule.class, TestDomainEventListenersModule.class})
interface TestEventComponent {

  DomainEventPublisher domainEventPublisher();

  RecordingListener recordingListener();

  ThrowingListener throwingListener();
}

@Singleton
@Component(modules = EventModule.class)
interface EmptyEventComponent {

  DomainEventPublisher domainEventPublisher();
}

@Module
class TestDomainEventListenersModule {

  @Provides
  @Singleton
  static RecordingListener provideRecordingListener() {
    return new RecordingListener();
  }

  @Provides
  @Singleton
  static ThrowingListener provideThrowingListener() {
    return new ThrowingListener();
  }

  @Provides
  @IntoSet
  static Consumer<DomainEvent> provideRecordingDomainEventListener(RecordingListener listener) {
    return listener;
  }

  @Provides
  @IntoSet
  static Consumer<DomainEvent> provideThrowingDomainEventListener(ThrowingListener listener) {
    return listener;
  }
}

final class RecordingListener implements Consumer<DomainEvent> {

  private final AtomicInteger invocationCount = new AtomicInteger();

  @Override
  public void accept(DomainEvent event) {
    invocationCount.incrementAndGet();
  }

  int invocationCount() {
    return invocationCount.get();
  }
}

final class ThrowingListener implements Consumer<DomainEvent> {

  private final AtomicBoolean invoked = new AtomicBoolean();

  @Override
  public void accept(DomainEvent event) {
    invoked.set(true);
    throw new IllegalStateException("boom");
  }

  boolean wasInvoked() {
    return invoked.get();
  }
}
