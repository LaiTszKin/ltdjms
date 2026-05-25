package ltdjms.discord.shop.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ltdjms.discord.dispatch.domain.EscortDispatchOrder;
import ltdjms.discord.dispatch.services.EscortDispatchHandoffService;
import ltdjms.discord.membership.services.MembershipSpendService;
import ltdjms.discord.product.domain.Product;
import ltdjms.discord.product.services.ProductRewardService;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shop.domain.FiatOrder;
import ltdjms.discord.shop.domain.FiatOrderRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("FiatOrderPostPaymentWorker 測試")
class FiatOrderPostPaymentWorkerTest {

  private static final Instant NOW = Instant.parse("2026-04-11T10:00:00Z");
  private static final Instant EXPIRE_AT = NOW.plusSeconds(600);

  @Mock private FiatOrderRepository fiatOrderRepository;
  @Mock private ProductRewardService productRewardService;
  @Mock private EscortDispatchHandoffService escortDispatchHandoffService;
  @Mock private ShopAdminNotificationService adminNotificationService;
  @Mock private FiatOrderBuyerNotificationService buyerNotificationService;
  @Mock private EscortOrderBuyerNotificationService escortOrderBuyerNotificationService;
  @Mock private MembershipSpendService membershipSpendService;

  private FiatOrderPostPaymentWorker worker;

  @BeforeEach
  void setUp() {
    worker =
        new FiatOrderPostPaymentWorker(
            fiatOrderRepository,
            productRewardService,
            escortDispatchHandoffService,
            adminNotificationService,
            buyerNotificationService,
            escortOrderBuyerNotificationService,
            membershipSpendService,
            Clock.fixed(NOW, ZoneOffset.UTC));
    lenient().when(membershipSpendService.recordFiatEscortPayment(any(), any())).thenReturn(true);
  }

  @Test
  @DisplayName("應完成已付款訂單的通知、護航交接、獎勵與 fulfilled 標記")
  void shouldProcessPaidOrderSuccessfully() {
    FiatOrder order = paidOrder();
    Product product = order.toFulfillmentProduct();
    EscortDispatchOrder dispatchOrder = autoDispatchOrder(order, product);
    when(fiatOrderRepository.claimFulfillmentProcessing(eq(order.orderNumber()), any()))
        .thenReturn(true);
    when(escortDispatchHandoffService.handoffFromFiatPayment(
            eq(order.guildId()), eq(order.buyerUserId()), eq(product), eq(order.orderNumber())))
        .thenReturn(Result.ok(dispatchOrder));
    when(fiatOrderRepository.claimAdminNotificationProcessing(eq(order.orderNumber()), any()))
        .thenReturn(true);
    when(productRewardService.grantReward(any()))
        .thenReturn(Result.ok(new ProductRewardService.RewardGrantResult(50L, 150L, null)));

    worker.processSingleOrder(order);

    var callOrder =
        inOrder(
            buyerNotificationService,
            membershipSpendService,
            escortDispatchHandoffService,
            escortOrderBuyerNotificationService,
            adminNotificationService);
    callOrder.verify(buyerNotificationService).notifyPaymentSucceeded(order);
    callOrder.verify(membershipSpendService).recordFiatEscortPayment(order, product);
    callOrder
        .verify(escortDispatchHandoffService)
        .handoffFromFiatPayment(order.guildId(), order.buyerUserId(), product, order.orderNumber());
    callOrder.verify(escortOrderBuyerNotificationService).notifyEscortOrderCreated(dispatchOrder);
    callOrder
        .verify(adminNotificationService)
        .notifyAdminsOrderCreated(eq(order.guildId()), eq(order.buyerUserId()), eq(dispatchOrder));
    verify(fiatOrderRepository).markBuyerNotifiedIfNeeded(eq(order.orderNumber()), any());
    verify(fiatOrderRepository).markAdminNotifiedIfNeeded(eq(order.orderNumber()), any());
    verify(productRewardService)
        .grantReward(
            eq(
                new ProductRewardService.RewardGrantRequest(
                    order.guildId(),
                    order.buyerUserId(),
                    product,
                    product.rewardAmount(),
                    "法幣商品獎勵: " + product.name(),
                    ltdjms.discord.currency.domain.CurrencyTransaction.Source.PRODUCT_REWARD,
                    ltdjms.discord.gametoken.domain.GameTokenTransaction.Source.PRODUCT_REWARD)));
    verify(fiatOrderRepository).markRewardGrantedIfNeeded(eq(order.orderNumber()), any());
    verify(fiatOrderRepository).markFulfilledIfNeeded(eq(order.orderNumber()), any());
    verify(fiatOrderRepository, never()).releaseFulfillmentProcessing(order.orderNumber());
  }

  @Test
  @DisplayName("無法取得處理 claim 時不應執行任何副作用")
  void shouldSkipWhenFulfillmentClaimFails() {
    FiatOrder order = paidOrder();
    when(fiatOrderRepository.claimFulfillmentProcessing(eq(order.orderNumber()), any()))
        .thenReturn(false);

    worker.processSingleOrder(order);

    verify(buyerNotificationService, never()).notifyPaymentSucceeded(any());
    verify(fiatOrderRepository, never()).markFulfilledIfNeeded(any(), any());
  }

  @Test
  @DisplayName("管理員通知失敗時應釋放 claim 並保留 fulfilled 未完成")
  void shouldReleaseClaimsWhenAdminNotificationFails() {
    FiatOrder order = paidOrder();
    Product product = order.toFulfillmentProduct();
    when(fiatOrderRepository.claimFulfillmentProcessing(eq(order.orderNumber()), any()))
        .thenReturn(true);
    when(escortDispatchHandoffService.handoffFromFiatPayment(
            eq(order.guildId()), eq(order.buyerUserId()), eq(product), eq(order.orderNumber())))
        .thenReturn(Result.ok(autoDispatchOrder(order, product)));
    when(fiatOrderRepository.claimAdminNotificationProcessing(eq(order.orderNumber()), any()))
        .thenReturn(true);
    org.mockito.Mockito.doThrow(new IllegalStateException("boom"))
        .when(adminNotificationService)
        .notifyAdminsOrderCreated(anyLong(), anyLong(), any(EscortDispatchOrder.class));

    worker.processSingleOrder(order);

    verify(fiatOrderRepository).releaseAdminNotificationProcessing(order.orderNumber());
    verify(fiatOrderRepository).releaseFulfillmentProcessing(order.orderNumber());
    verify(fiatOrderRepository, never()).markFulfilledIfNeeded(any(), any());
    verify(escortDispatchHandoffService)
        .handoffFromFiatPayment(order.guildId(), order.buyerUserId(), product, order.orderNumber());
  }

  @Test
  @DisplayName("UT-04: 護航 handoff 失敗時不應呼叫買家護航通知")
  void shouldNotNotifyBuyerEscortWhenHandoffFails() {
    FiatOrder order = paidOrder();
    Product product = order.toFulfillmentProduct();
    when(fiatOrderRepository.claimFulfillmentProcessing(eq(order.orderNumber()), any()))
        .thenReturn(true);
    when(escortDispatchHandoffService.handoffFromFiatPayment(
            eq(order.guildId()), eq(order.buyerUserId()), eq(product), eq(order.orderNumber())))
        .thenReturn(
            Result.err(new DomainError(DomainError.Category.INVALID_INPUT, "handoff 失敗", null)));

    worker.processSingleOrder(order);

    verify(escortOrderBuyerNotificationService, never()).notifyEscortOrderCreated(any());
    verify(adminNotificationService, never()).notifyAdminsOrderCreated(anyLong(), anyLong(), any());
    verify(fiatOrderRepository).releaseFulfillmentProcessing(order.orderNumber());
  }

  @Test
  @DisplayName("管理員通知 claim 已被佔用時不應標記 fulfilled")
  void shouldReleaseFulfillmentWhenAdminNotificationClaimFails() {
    FiatOrder order = paidOrder();
    Product product = order.toFulfillmentProduct();
    when(fiatOrderRepository.claimFulfillmentProcessing(eq(order.orderNumber()), any()))
        .thenReturn(true);
    when(escortDispatchHandoffService.handoffFromFiatPayment(
            eq(order.guildId()), eq(order.buyerUserId()), eq(product), eq(order.orderNumber())))
        .thenReturn(Result.ok(autoDispatchOrder(order, product)));
    when(fiatOrderRepository.claimAdminNotificationProcessing(eq(order.orderNumber()), any()))
        .thenReturn(false);

    worker.processSingleOrder(order);

    verify(adminNotificationService, never()).notifyAdminsOrderCreated(anyLong(), anyLong(), any());
    verify(fiatOrderRepository).releaseFulfillmentProcessing(order.orderNumber());
    verify(fiatOrderRepository, never()).markAdminNotifiedIfNeeded(any(), any());
    verify(fiatOrderRepository, never()).markFulfilledIfNeeded(any(), any());
  }

  @Test
  @DisplayName("spend 記錄失敗時不應標記 fulfilled")
  void shouldNotMarkFulfilledWhenSpendRecordingFails() {
    FiatOrder order = paidOrder();
    when(fiatOrderRepository.claimFulfillmentProcessing(eq(order.orderNumber()), any()))
        .thenReturn(true);
    when(membershipSpendService.recordFiatEscortPayment(any(), any())).thenReturn(false);

    worker.processSingleOrder(order);

    verify(fiatOrderRepository).releaseFulfillmentProcessing(order.orderNumber());
    verify(fiatOrderRepository, never()).markFulfilledIfNeeded(any(), any());
  }

  @Test
  @DisplayName("handoff 失敗時 spend 仍應已寫入")
  void shouldRecordSpendBeforeHandoffFailure() {
    FiatOrder order = paidOrder();
    Product product = order.toFulfillmentProduct();
    when(fiatOrderRepository.claimFulfillmentProcessing(eq(order.orderNumber()), any()))
        .thenReturn(true);
    when(membershipSpendService.recordFiatEscortPayment(order, product)).thenReturn(true);
    when(escortDispatchHandoffService.handoffFromFiatPayment(
            eq(order.guildId()), eq(order.buyerUserId()), eq(product), eq(order.orderNumber())))
        .thenReturn(
            Result.err(new DomainError(DomainError.Category.INVALID_INPUT, "handoff 失敗", null)));

    worker.processSingleOrder(order);

    verify(membershipSpendService).recordFiatEscortPayment(order, product);
    verify(fiatOrderRepository).releaseFulfillmentProcessing(order.orderNumber());
    verify(fiatOrderRepository, never()).markFulfilledIfNeeded(any(), any());
  }

  private FiatOrder paidOrder() {
    return new FiatOrder(
        1L,
        123L,
        456L,
        789L,
        "護航商品",
        Product.RewardType.CURRENCY,
        50L,
        true,
        "ESCORT-A",
        "FD260411000001",
        "CVS123456",
        1200L,
        null,
        null,
        null,
        FiatOrder.Status.PAID,
        "1",
        "付款成功",
        NOW,
        EXPIRE_AT,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        0,
        null,
        NOW,
        NOW);
  }

  private EscortDispatchOrder autoDispatchOrder(FiatOrder order, Product product) {
    return EscortDispatchOrder.createAutoHandoff(
        "ESC-20260411-ABC123",
        order.guildId(),
        0L,
        0L,
        order.buyerUserId(),
        EscortDispatchOrder.SourceType.FIAT_PAYMENT,
        order.orderNumber(),
        product.id(),
        product.name(),
        product.currencyPrice(),
        product.fiatPriceTwd(),
        product.escortOptionCode());
  }
}
