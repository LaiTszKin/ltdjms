package ltdjms.discord.dispatch.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ltdjms.discord.dispatch.domain.EscortDispatchOrder;
import ltdjms.discord.dispatch.domain.EscortDispatchOrderRepository;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;

@ExtendWith(MockitoExtension.class)
class EscortDispatchOrderServiceTest {

  private static final long TEST_GUILD_ID = 123456789L;
  private static final long TEST_ADMIN_ID = 10001L;
  private static final long TEST_ESCORT_USER_ID = 40001L;
  private static final long TEST_CUSTOMER_USER_ID = 50001L;

  @Mock private EscortDispatchOrderRepository repository;
  @Mock private EscortDispatchOrderNumberGenerator orderNumberGenerator;

  private EscortDispatchOrderService service;

  @BeforeEach
  void setUp() {
    service = new EscortDispatchOrderService(repository, orderNumberGenerator);
  }

  @Nested
  @DisplayName("createOrder")
  class CreateOrderTests {

    @Test
    @DisplayName("should reject when escort and customer are the same user")
    void shouldRejectWhenEscortAndCustomerAreTheSameUser() {
      Result<EscortDispatchOrder, DomainError> result =
          service.createOrder(
              TEST_GUILD_ID, TEST_ADMIN_ID, TEST_ESCORT_USER_ID, TEST_ESCORT_USER_ID);

      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().category()).isEqualTo(DomainError.Category.INVALID_INPUT);
      assertThat(result.getError().message()).contains("不能是同一人");
      verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("should create order successfully")
    void shouldCreateOrderSuccessfully() {
      when(orderNumberGenerator.generate()).thenReturn("ESC-20260213-ABC123");
      when(repository.existsByOrderNumber("ESC-20260213-ABC123")).thenReturn(false);

      EscortDispatchOrder savedOrder =
          new EscortDispatchOrder(
              1L,
              "ESC-20260213-ABC123",
              TEST_GUILD_ID,
              TEST_ADMIN_ID,
              TEST_ESCORT_USER_ID,
              TEST_CUSTOMER_USER_ID,
              EscortDispatchOrder.Status.PENDING_CONFIRMATION,
              Instant.now(),
              null,
              Instant.now());
      when(repository.save(org.mockito.ArgumentMatchers.any(EscortDispatchOrder.class)))
          .thenReturn(savedOrder);

      Result<EscortDispatchOrder, DomainError> result =
          service.createOrder(
              TEST_GUILD_ID, TEST_ADMIN_ID, TEST_ESCORT_USER_ID, TEST_CUSTOMER_USER_ID);

      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().id()).isEqualTo(1L);
      assertThat(result.getValue().orderNumber()).isEqualTo("ESC-20260213-ABC123");
    }

    @Test
    @DisplayName("should retry order number generation when duplicate exists")
    void shouldRetryOrderNumberGenerationWhenDuplicateExists() {
      when(orderNumberGenerator.generate())
          .thenReturn("ESC-20260213-AAAAAA")
          .thenReturn("ESC-20260213-BBBBBB");
      when(repository.existsByOrderNumber("ESC-20260213-AAAAAA")).thenReturn(true);
      when(repository.existsByOrderNumber("ESC-20260213-BBBBBB")).thenReturn(false);
      when(repository.save(org.mockito.ArgumentMatchers.any(EscortDispatchOrder.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      Result<EscortDispatchOrder, DomainError> result =
          service.createOrder(
              TEST_GUILD_ID, TEST_ADMIN_ID, TEST_ESCORT_USER_ID, TEST_CUSTOMER_USER_ID);

      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().orderNumber()).isEqualTo("ESC-20260213-BBBBBB");
    }

    @Test
    @DisplayName("should return persistence failure when unable to generate unique order number")
    void shouldReturnPersistenceFailureWhenUnableToGenerateUniqueOrderNumber() {
      when(orderNumberGenerator.generate()).thenReturn("ESC-20260213-AAAAAA");
      when(repository.existsByOrderNumber("ESC-20260213-AAAAAA")).thenReturn(true);

      Result<EscortDispatchOrder, DomainError> result =
          service.createOrder(
              TEST_GUILD_ID, TEST_ADMIN_ID, TEST_ESCORT_USER_ID, TEST_CUSTOMER_USER_ID);

      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().category()).isEqualTo(DomainError.Category.PERSISTENCE_FAILURE);
    }
  }

  @Nested
  @DisplayName("confirmOrder")
  class ConfirmOrderTests {

    @Test
    @DisplayName("should reject blank order number")
    void shouldRejectBlankOrderNumber() {
      Result<EscortDispatchOrder, DomainError> result =
          service.confirmOrder("   ", TEST_ESCORT_USER_ID);

      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().category()).isEqualTo(DomainError.Category.INVALID_INPUT);
    }

    @Test
    @DisplayName("should reject when order not found")
    void shouldRejectWhenOrderNotFound() {
      when(repository.findByOrderNumber("ESC-20260213-ABC123")).thenReturn(Optional.empty());

      Result<EscortDispatchOrder, DomainError> result =
          service.confirmOrder("ESC-20260213-ABC123", TEST_ESCORT_USER_ID);

      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().category()).isEqualTo(DomainError.Category.INVALID_INPUT);
      assertThat(result.getError().message()).contains("找不到");
    }

    @Test
    @DisplayName("should reject when confirmer is not assigned escort")
    void shouldRejectWhenConfirmerIsNotAssignedEscort() {
      EscortDispatchOrder order =
          EscortDispatchOrder.createPending(
              "ESC-20260213-ABC123",
              TEST_GUILD_ID,
              TEST_ADMIN_ID,
              TEST_ESCORT_USER_ID,
              TEST_CUSTOMER_USER_ID);
      when(repository.findByOrderNumber("ESC-20260213-ABC123")).thenReturn(Optional.of(order));

      Result<EscortDispatchOrder, DomainError> result =
          service.confirmOrder("ESC-20260213-ABC123", 999999L);

      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().message()).contains("只有被指派的護航者");
      verify(repository, never()).update(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("should reject when order already confirmed")
    void shouldRejectWhenOrderAlreadyConfirmed() {
      EscortDispatchOrder confirmedOrder =
          new EscortDispatchOrder(
              99L,
              "ESC-20260213-ABC123",
              TEST_GUILD_ID,
              TEST_ADMIN_ID,
              TEST_ESCORT_USER_ID,
              TEST_CUSTOMER_USER_ID,
              EscortDispatchOrder.Status.CONFIRMED,
              Instant.now().minusSeconds(3600),
              Instant.now().minusSeconds(1800),
              Instant.now().minusSeconds(1800));
      when(repository.findByOrderNumber("ESC-20260213-ABC123"))
          .thenReturn(Optional.of(confirmedOrder));

      Result<EscortDispatchOrder, DomainError> result =
          service.confirmOrder("ESC-20260213-ABC123", TEST_ESCORT_USER_ID);

      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().message()).contains("已確認");
      verify(repository, never()).update(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("should confirm order successfully")
    void shouldConfirmOrderSuccessfully() {
      EscortDispatchOrder pendingOrder =
          new EscortDispatchOrder(
              10L,
              "ESC-20260213-ABC123",
              TEST_GUILD_ID,
              TEST_ADMIN_ID,
              TEST_ESCORT_USER_ID,
              TEST_CUSTOMER_USER_ID,
              EscortDispatchOrder.Status.PENDING_CONFIRMATION,
              Instant.now().minusSeconds(3600),
              null,
              Instant.now().minusSeconds(3600));
      when(repository.findByOrderNumber("ESC-20260213-ABC123"))
          .thenReturn(Optional.of(pendingOrder));
      when(repository.update(org.mockito.ArgumentMatchers.any(EscortDispatchOrder.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      Result<EscortDispatchOrder, DomainError> result =
          service.confirmOrder("ESC-20260213-ABC123", TEST_ESCORT_USER_ID);

      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().status()).isEqualTo(EscortDispatchOrder.Status.CONFIRMED);
      assertThat(result.getValue().confirmedAt()).isNotNull();
    }
  }
}
