package ltdjms.discord.shop.domain;

import java.time.Instant;
import java.util.Objects;

/** Fiat order tracked until payment callback marks it as paid. */
public record FiatOrder(
    Long id,
    long guildId,
    long buyerUserId,
    long productId,
    String productName,
    String orderNumber,
    String paymentNo,
    long amountTwd,
    Status status,
    String tradeStatus,
    String paymentMessage,
    Instant paidAt,
    Instant fulfilledAt,
    Instant adminNotifiedAt,
    String lastCallbackPayload,
    Instant createdAt,
    Instant updatedAt) {

  public enum Status {
    PENDING_PAYMENT,
    PAID
  }

  public FiatOrder {
    Objects.requireNonNull(productName, "productName must not be null");
    Objects.requireNonNull(orderNumber, "orderNumber must not be null");
    Objects.requireNonNull(paymentNo, "paymentNo must not be null");
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    Objects.requireNonNull(updatedAt, "updatedAt must not be null");

    if (productName.isBlank()) {
      throw new IllegalArgumentException("productName must not be blank");
    }
    if (orderNumber.isBlank()) {
      throw new IllegalArgumentException("orderNumber must not be blank");
    }
    if (paymentNo.isBlank()) {
      throw new IllegalArgumentException("paymentNo must not be blank");
    }
    if (productName.length() > 100) {
      throw new IllegalArgumentException("productName must not exceed 100 characters");
    }
    if (orderNumber.length() > 32) {
      throw new IllegalArgumentException("orderNumber must not exceed 32 characters");
    }
    if (paymentNo.length() > 32) {
      throw new IllegalArgumentException("paymentNo must not exceed 32 characters");
    }
    if (amountTwd <= 0) {
      throw new IllegalArgumentException("amountTwd must be positive");
    }
    if (status == Status.PAID && paidAt == null) {
      throw new IllegalArgumentException("paidAt is required when status is PAID");
    }
  }

  public static FiatOrder createPending(
      long guildId,
      long buyerUserId,
      long productId,
      String productName,
      String orderNumber,
      String paymentNo,
      long amountTwd) {
    Instant now = Instant.now();
    return new FiatOrder(
        null,
        guildId,
        buyerUserId,
        productId,
        productName,
        orderNumber,
        paymentNo,
        amountTwd,
        Status.PENDING_PAYMENT,
        null,
        null,
        null,
        null,
        null,
        null,
        now,
        now);
  }

  public boolean isPaid() {
    return status == Status.PAID;
  }

  public boolean isFulfilled() {
    return fulfilledAt != null;
  }

  public boolean isAdminNotified() {
    return adminNotifiedAt != null;
  }
}
