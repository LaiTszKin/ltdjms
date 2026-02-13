package ltdjms.discord.dispatch.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * 派單系統的護航訂單實體。
 *
 * <p>訂單建立後初始為 {@link Status#PENDING_CONFIRMATION}，由指定護航者在私訊確認後轉為 {@link Status#CONFIRMED}。
 */
public record EscortDispatchOrder(
    Long id,
    String orderNumber,
    long guildId,
    long assignedByUserId,
    long escortUserId,
    long customerUserId,
    Status status,
    Instant createdAt,
    Instant confirmedAt,
    Instant updatedAt) {

  /** 訂單狀態。 */
  public enum Status {
    /** 已建立，等待護航者確認。 */
    PENDING_CONFIRMATION,
    /** 護航者已確認接單。 */
    CONFIRMED
  }

  public EscortDispatchOrder {
    Objects.requireNonNull(orderNumber, "orderNumber must not be null");
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    Objects.requireNonNull(updatedAt, "updatedAt must not be null");

    if (orderNumber.isBlank()) {
      throw new IllegalArgumentException("orderNumber must not be blank");
    }
    if (orderNumber.length() > 32) {
      throw new IllegalArgumentException("orderNumber must not exceed 32 characters");
    }
    if (escortUserId == customerUserId) {
      throw new IllegalArgumentException("escortUserId and customerUserId must be different");
    }
    if (status == Status.CONFIRMED && confirmedAt == null) {
      throw new IllegalArgumentException("confirmed order must have confirmedAt");
    }
  }

  /** 建立待確認的新訂單（尚未持久化，id 為 null）。 */
  public static EscortDispatchOrder createPending(
      String orderNumber,
      long guildId,
      long assignedByUserId,
      long escortUserId,
      long customerUserId) {
    Instant now = Instant.now();
    return new EscortDispatchOrder(
        null,
        orderNumber,
        guildId,
        assignedByUserId,
        escortUserId,
        customerUserId,
        Status.PENDING_CONFIRMATION,
        now,
        null,
        now);
  }

  /** 由指定護航者確認後回傳新狀態實體。 */
  public EscortDispatchOrder withConfirmed(Instant confirmedAt) {
    Objects.requireNonNull(confirmedAt, "confirmedAt must not be null");
    return new EscortDispatchOrder(
        id,
        orderNumber,
        guildId,
        assignedByUserId,
        escortUserId,
        customerUserId,
        Status.CONFIRMED,
        createdAt,
        confirmedAt,
        Instant.now());
  }

  public boolean isPendingConfirmation() {
    return status == Status.PENDING_CONFIRMATION;
  }

  public boolean canBeConfirmedBy(long userId) {
    return escortUserId == userId;
  }
}
