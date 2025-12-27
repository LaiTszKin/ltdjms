package ltdjms.discord.shared.events;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProductChangedEventTest {

  @Test
  void shouldCreateEventWithCreatedOperation() {
    // Given
    long guildId = 123456789L;
    long productId = 1L;
    ProductChangedEvent.OperationType operationType = ProductChangedEvent.OperationType.CREATED;

    // When
    ProductChangedEvent event = new ProductChangedEvent(guildId, productId, operationType);

    // Then
    assertThat(event.guildId()).isEqualTo(guildId);
    assertThat(event.productId()).isEqualTo(productId);
    assertThat(event.operationType()).isEqualTo(operationType);
  }

  @Test
  void shouldCreateEventWithUpdatedOperation() {
    // Given
    long guildId = 123456789L;
    long productId = 2L;
    ProductChangedEvent.OperationType operationType = ProductChangedEvent.OperationType.UPDATED;

    // When
    ProductChangedEvent event = new ProductChangedEvent(guildId, productId, operationType);

    // Then
    assertThat(event.guildId()).isEqualTo(guildId);
    assertThat(event.productId()).isEqualTo(productId);
    assertThat(event.operationType()).isEqualTo(operationType);
  }

  @Test
  void shouldCreateEventWithDeletedOperation() {
    // Given
    long guildId = 123456789L;
    long productId = 3L;
    ProductChangedEvent.OperationType operationType = ProductChangedEvent.OperationType.DELETED;

    // When
    ProductChangedEvent event = new ProductChangedEvent(guildId, productId, operationType);

    // Then
    assertThat(event.guildId()).isEqualTo(guildId);
    assertThat(event.productId()).isEqualTo(productId);
    assertThat(event.operationType()).isEqualTo(operationType);
  }

  @Test
  void shouldImplementDomainEvent() {
    // Given
    ProductChangedEvent event =
        new ProductChangedEvent(123L, 1L, ProductChangedEvent.OperationType.CREATED);

    // Then
    assertThat(event).isInstanceOf(DomainEvent.class);
    assertThat(event.guildId()).isEqualTo(123L);
  }
}
