package ltdjms.discord.shared.events;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DomainEventPublisher 單元測試")
class DomainEventPublisherTest {

  private static final BalanceChangedEvent TEST_EVENT = new BalanceChangedEvent(1L, 2L, 3L);

  @Nested
  @DisplayName("建構子 listener 註冊")
  class ConstructorRegistrationTests {

    @Test
    @DisplayName("應將事件分發給所有建構時注入的監聽器")
    void shouldDispatchEventToAllConstructorInjectedListeners() {
      AtomicInteger firstInvocations = new AtomicInteger();
      AtomicInteger secondInvocations = new AtomicInteger();

      Consumer<DomainEvent> firstListener = event -> firstInvocations.incrementAndGet();
      Consumer<DomainEvent> secondListener = event -> secondInvocations.incrementAndGet();
      DomainEventPublisher publisher =
          new DomainEventPublisher(List.of(firstListener, secondListener));

      publisher.publish(TEST_EVENT);

      assertEquals(1, firstInvocations.get());
      assertEquals(1, secondInvocations.get());
    }
  }

  @Nested
  @DisplayName("錯誤隔離")
  class FailureIsolationTests {

    @Test
    @DisplayName("單一監聽器失敗時不應阻止其他監聽器")
    void shouldContinueDispatchingWhenOneListenerThrows() {
      AtomicInteger successfulInvocations = new AtomicInteger();

      Consumer<DomainEvent> failingListener =
          event -> {
            throw new IllegalStateException("boom");
          };
      Consumer<DomainEvent> successfulListener = event -> successfulInvocations.incrementAndGet();
      DomainEventPublisher publisher =
          new DomainEventPublisher(List.of(failingListener, successfulListener));

      assertDoesNotThrow(() -> publisher.publish(TEST_EVENT));
      assertEquals(1, successfulInvocations.get());
    }
  }

  @Test
  @DisplayName("沒有任何監聽器時應安全發佈事件")
  void shouldAllowPublishingWithNoListeners() {
    DomainEventPublisher publisher = new DomainEventPublisher(List.of());

    assertDoesNotThrow(() -> publisher.publish(TEST_EVENT));
  }
}
