package ltdjms.discord.shop.domain;

import java.time.Instant;
import java.util.Optional;

/** Repository for fiat order lifecycle updates. */
public interface FiatOrderRepository {

  FiatOrder save(FiatOrder order);

  Optional<FiatOrder> findByOrderNumber(String orderNumber);

  Optional<FiatOrder> updateCallbackStatus(
      String orderNumber, String tradeStatus, String paymentMessage, String callbackPayload);

  Optional<FiatOrder> markPaidIfPending(
      String orderNumber,
      String tradeStatus,
      String paymentMessage,
      String callbackPayload,
      Instant paidAt);

  Optional<FiatOrder> markFulfilledIfNeeded(String orderNumber, Instant fulfilledAt);

  Optional<FiatOrder> markAdminNotifiedIfNeeded(String orderNumber, Instant notifiedAt);

  boolean claimFulfillmentProcessing(String orderNumber, Instant claimedAt);

  void releaseFulfillmentProcessing(String orderNumber);

  boolean claimAdminNotificationProcessing(String orderNumber, Instant claimedAt);

  void releaseAdminNotificationProcessing(String orderNumber);
}
