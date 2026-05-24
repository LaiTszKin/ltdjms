package ltdjms.discord.shop.services;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.dispatch.domain.EscortDispatchOrder;
import ltdjms.discord.dispatch.services.EscortDispatchHandoffService;
import ltdjms.discord.membership.services.MembershipSpendService;
import ltdjms.discord.product.services.ProductRewardService;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shop.domain.FiatOrder;
import ltdjms.discord.shop.domain.FiatOrderRepository;

/** Processes paid fiat orders asynchronously and idempotently. */
public class FiatOrderPostPaymentWorker {

  private static final Logger LOG = LoggerFactory.getLogger(FiatOrderPostPaymentWorker.class);
  private static final int DEFAULT_BATCH_SIZE = 20;

  private final FiatOrderRepository fiatOrderRepository;
  private final ProductRewardService productRewardService;
  private final EscortDispatchHandoffService escortDispatchHandoffService;
  private final ShopAdminNotificationService adminNotificationService;
  private final FiatOrderBuyerNotificationService buyerNotificationService;
  private final EscortOrderBuyerNotificationService escortOrderBuyerNotificationService;
  private final MembershipSpendService membershipSpendService;
  private final Clock clock;

  public FiatOrderPostPaymentWorker(
      FiatOrderRepository fiatOrderRepository,
      ProductRewardService productRewardService,
      EscortDispatchHandoffService escortDispatchHandoffService,
      ShopAdminNotificationService adminNotificationService,
      FiatOrderBuyerNotificationService buyerNotificationService,
      EscortOrderBuyerNotificationService escortOrderBuyerNotificationService,
      MembershipSpendService membershipSpendService) {
    this(
        fiatOrderRepository,
        productRewardService,
        escortDispatchHandoffService,
        adminNotificationService,
        buyerNotificationService,
        escortOrderBuyerNotificationService,
        membershipSpendService,
        Clock.systemUTC());
  }

  FiatOrderPostPaymentWorker(
      FiatOrderRepository fiatOrderRepository,
      ProductRewardService productRewardService,
      EscortDispatchHandoffService escortDispatchHandoffService,
      ShopAdminNotificationService adminNotificationService,
      FiatOrderBuyerNotificationService buyerNotificationService,
      EscortOrderBuyerNotificationService escortOrderBuyerNotificationService,
      MembershipSpendService membershipSpendService,
      Clock clock) {
    this.fiatOrderRepository = Objects.requireNonNull(fiatOrderRepository);
    this.productRewardService = Objects.requireNonNull(productRewardService);
    this.escortDispatchHandoffService = Objects.requireNonNull(escortDispatchHandoffService);
    this.adminNotificationService = Objects.requireNonNull(adminNotificationService);
    this.buyerNotificationService = Objects.requireNonNull(buyerNotificationService);
    this.escortOrderBuyerNotificationService =
        Objects.requireNonNull(escortOrderBuyerNotificationService);
    this.membershipSpendService = Objects.requireNonNull(membershipSpendService);
    this.clock = Objects.requireNonNull(clock);
  }

  public void processPendingOrders() {
    List<FiatOrder> orders = fiatOrderRepository.findOrdersPendingPostPayment(DEFAULT_BATCH_SIZE);
    for (FiatOrder order : orders) {
      processSingleOrder(order);
    }
  }

  void processSingleOrder(FiatOrder order) {
    Instant claimTime = Instant.now(clock);
    if (!fiatOrderRepository.claimFulfillmentProcessing(order.orderNumber(), claimTime)) {
      return;
    }

    try {
      var fulfillmentProduct = order.toFulfillmentProduct();

      if (!order.isBuyerNotified()) {
        buyerNotificationService.notifyPaymentSucceeded(order);
        fiatOrderRepository.markBuyerNotifiedIfNeeded(order.orderNumber(), Instant.now(clock));
      }

      if (order.shouldAutoCreateEscortOrder() && !order.isAdminNotified()) {
        Result<EscortDispatchOrder, DomainError> handoffResult =
            escortDispatchHandoffService.handoffFromFiatPayment(
                order.guildId(), order.buyerUserId(), fulfillmentProduct, order.orderNumber());
        if (handoffResult.isErr()) {
          throw new IllegalStateException(handoffResult.getError().message());
        }

        EscortDispatchOrder dispatchOrder = handoffResult.getValue();
        Instant adminClaimTime = Instant.now(clock);
        if (fiatOrderRepository.claimAdminNotificationProcessing(
            order.orderNumber(), adminClaimTime)) {
          try {
            // 買家通知放在 admin claim 成功之後，避免重試時重複通知買家
            escortOrderBuyerNotificationService.notifyEscortOrderCreated(dispatchOrder);
            adminNotificationService.notifyAdminsOrderCreated(
                dispatchOrder.guildId(), dispatchOrder.customerUserId(), dispatchOrder);
            fiatOrderRepository.markAdminNotifiedIfNeeded(order.orderNumber(), adminClaimTime);
          } catch (Exception e) {
            fiatOrderRepository.releaseAdminNotificationProcessing(order.orderNumber());
            throw e;
          }
        } else {
          throw new IllegalStateException(
              "Fiat admin notification is already being processed: orderNumber="
                  + order.orderNumber());
        }
      }

      if (order.hasFulfillmentReward() && !order.isRewardGranted()) {
        Result<ProductRewardService.RewardGrantResult, DomainError> rewardResult =
            productRewardService.grantReward(
                new ProductRewardService.RewardGrantRequest(
                    order.guildId(),
                    order.buyerUserId(),
                    fulfillmentProduct,
                    fulfillmentProduct.rewardAmount(),
                    String.format("法幣商品獎勵: %s", fulfillmentProduct.name()),
                    ltdjms.discord.currency.domain.CurrencyTransaction.Source.PRODUCT_REWARD,
                    ltdjms.discord.gametoken.domain.GameTokenTransaction.Source.PRODUCT_REWARD));
        if (rewardResult.isErr()) {
          throw new IllegalStateException(rewardResult.getError().message());
        }
        fiatOrderRepository.markRewardGrantedIfNeeded(order.orderNumber(), Instant.now(clock));
      }

      membershipSpendService.recordFiatEscortPayment(order, fulfillmentProduct);
      fiatOrderRepository.markFulfilledIfNeeded(order.orderNumber(), Instant.now(clock));
    } catch (Exception e) {
      fiatOrderRepository.releaseFulfillmentProcessing(order.orderNumber());
      LOG.warn("Failed to process paid fiat order: orderNumber={}", order.orderNumber(), e);
    }
  }
}
