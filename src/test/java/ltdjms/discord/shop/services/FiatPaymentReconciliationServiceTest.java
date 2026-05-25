package ltdjms.discord.shop.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

import com.fasterxml.jackson.databind.ObjectMapper;

import ltdjms.discord.shared.Result;
import ltdjms.discord.shop.domain.FiatOrder;
import ltdjms.discord.shop.domain.FiatOrderRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("FiatPaymentReconciliationService 測試")
class FiatPaymentReconciliationServiceTest {

  private static final Instant NOW = Instant.parse("2026-04-11T11:00:00Z");
  private static final Instant EXPIRE_AT = NOW.plusSeconds(600);

  @Mock private FiatOrderRepository fiatOrderRepository;
  @Mock private EcpayTradeQueryService ecpayTradeQueryService;

  private FiatPaymentReconciliationService service;

  @BeforeEach
  void setUp() {
    service =
        new FiatPaymentReconciliationService(
            fiatOrderRepository,
            ecpayTradeQueryService,
            Clock.fixed(NOW, ZoneOffset.UTC),
            new ObjectMapper());
  }

  @Test
  @DisplayName("官方查單確認已付款時應轉成 paid")
  void shouldMarkPaidWhenTradeQueryConfirmsPaid() {
    FiatOrder order = pendingOrder();
    when(fiatOrderRepository.claimReconciliationProcessing(eq(order.orderNumber()), eq(NOW)))
        .thenReturn(true);
    when(ecpayTradeQueryService.queryTrade(order.orderNumber()))
        .thenReturn(
            Result.ok(
                new EcpayTradeQueryService.QueryTradeResult(
                    order.orderNumber(), true, "1", "TN123", 1200L, "付款成功")));

    service.reconcileSingleOrder(order, NOW);

    verify(fiatOrderRepository)
        .markPaidIfPending(eq(order.orderNumber()), eq("1"), eq("付款成功"), any(), eq(NOW));
    verify(fiatOrderRepository, never())
        .markReconciliationAttempted(
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt(), any());
  }

  @Test
  @DisplayName("付款寫入敗給 race 時應釋放 reconciliation claim")
  void shouldReleaseClaimWhenPaidTransitionIsLostToRace() {
    FiatOrder order = pendingOrder();
    when(fiatOrderRepository.claimReconciliationProcessing(eq(order.orderNumber()), eq(NOW)))
        .thenReturn(true);
    when(ecpayTradeQueryService.queryTrade(order.orderNumber()))
        .thenReturn(
            Result.ok(
                new EcpayTradeQueryService.QueryTradeResult(
                    order.orderNumber(), true, "1", "TN123", 1200L, "付款成功")));
    when(fiatOrderRepository.markPaidIfPending(
            eq(order.orderNumber()), eq("1"), eq("付款成功"), any(), eq(NOW)))
        .thenReturn(java.util.Optional.empty());

    service.reconcileSingleOrder(order, NOW);

    verify(fiatOrderRepository).releaseReconciliationProcessing(order.orderNumber());
  }

  @Test
  @DisplayName("查單未付款時應排入下一次重試")
  void shouldScheduleRetryWhenTradeStillUnpaid() {
    FiatOrder order = pendingOrderWithAttempt(2);
    when(fiatOrderRepository.claimReconciliationProcessing(eq(order.orderNumber()), eq(NOW)))
        .thenReturn(true);
    when(ecpayTradeQueryService.queryTrade(order.orderNumber()))
        .thenReturn(
            Result.ok(
                new EcpayTradeQueryService.QueryTradeResult(
                    order.orderNumber(), false, "0", null, -1L, "尚未付款")));

    service.reconcileSingleOrder(order, NOW);

    verify(fiatOrderRepository)
        .markReconciliationAttempted(order.orderNumber(), 3, NOW.plusSeconds(90));
    verify(fiatOrderRepository, never()).markPaidIfPending(any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("已逾期且未付款時應轉成 expired")
  void shouldExpireWhenTradeStillUnpaidAfterDeadline() {
    FiatOrder order = expiredPendingOrder();
    when(fiatOrderRepository.claimReconciliationProcessing(eq(order.orderNumber()), eq(NOW)))
        .thenReturn(true);
    when(ecpayTradeQueryService.queryTrade(order.orderNumber()))
        .thenReturn(
            Result.ok(
                new EcpayTradeQueryService.QueryTradeResult(
                    order.orderNumber(), false, "0", null, -1L, "尚未付款")));

    service.reconcileSingleOrder(order, NOW);

    verify(fiatOrderRepository).markExpiredIfPending(order.orderNumber(), NOW, "EXPIRED");
    verify(fiatOrderRepository, never())
        .markReconciliationAttempted(
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt(), any());
  }

  @Test
  @DisplayName("expiry sweep 應先轉換已過期 pending 訂單")
  void shouldExpirePendingOrdersBeforeReconciliation() {
    FiatOrder order = expiredPendingOrder();
    when(fiatOrderRepository.findOrdersPendingExpiry(eq(NOW), eq(20)))
        .thenReturn(java.util.List.of(order));
    when(fiatOrderRepository.markExpiredIfPending(eq(order.orderNumber()), eq(NOW), eq("EXPIRED")))
        .thenReturn(java.util.Optional.of(expiredOrder()));

    service.expirePendingOrders(NOW);

    verify(fiatOrderRepository).markExpiredIfPending(order.orderNumber(), NOW, "EXPIRED");
  }

  @Test
  @DisplayName("未取得 reconciliation claim 時不應查單")
  void shouldSkipWhenClaimFails() {
    FiatOrder order = pendingOrder();
    when(fiatOrderRepository.claimReconciliationProcessing(eq(order.orderNumber()), eq(NOW)))
        .thenReturn(false);

    service.reconcileSingleOrder(order, NOW);

    verify(ecpayTradeQueryService, never()).queryTrade(any());
  }

  @Test
  @DisplayName("查單 payload 應標記為 synthetic query source")
  void shouldBuildSyntheticQueryPayload() {
    FiatOrder order = pendingOrder();
    when(fiatOrderRepository.claimReconciliationProcessing(eq(order.orderNumber()), eq(NOW)))
        .thenReturn(true);
    when(ecpayTradeQueryService.queryTrade(order.orderNumber()))
        .thenReturn(
            Result.ok(
                new EcpayTradeQueryService.QueryTradeResult(
                    order.orderNumber(), true, "1", "TN123", 1200L, "付款成功")));

    org.mockito.ArgumentCaptor<String> payloadCaptor =
        org.mockito.ArgumentCaptor.forClass(String.class);

    service.reconcileSingleOrder(order, NOW);

    verify(fiatOrderRepository)
        .markPaidIfPending(
            eq(order.orderNumber()), eq("1"), eq("付款成功"), payloadCaptor.capture(), eq(NOW));
    assertThat(payloadCaptor.getValue()).contains("ECPAY_QUERY_TRADE_INFO");
    assertThat(payloadCaptor.getValue()).contains(order.orderNumber());
  }

  private FiatOrder pendingOrder() {
    return pendingOrderWithAttempt(0);
  }

  private FiatOrder pendingOrderWithAttempt(int attemptCount) {
    return new FiatOrder(
        1L,
        123L,
        456L,
        789L,
        "法幣商品",
        null,
        null,
        false,
        null,
        "FD260411000002",
        "CVS654321",
        1200L,
        null,
        null,
        null,
        FiatOrder.Status.PENDING_PAYMENT,
        null,
        null,
        null,
        EXPIRE_AT,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        attemptCount,
        null,
        NOW.minusSeconds(60),
        NOW.minusSeconds(60));
  }

  private FiatOrder expiredPendingOrder() {
    return new FiatOrder(
        1L,
        123L,
        456L,
        789L,
        "法幣商品",
        null,
        null,
        false,
        null,
        "FD260411000003",
        "CVS654321",
        1200L,
        null,
        null,
        null,
        FiatOrder.Status.PENDING_PAYMENT,
        null,
        null,
        null,
        NOW.minusSeconds(60),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        0,
        null,
        NOW.minusSeconds(120),
        NOW.minusSeconds(120));
  }

  private FiatOrder expiredOrder() {
    Instant expiredAt = NOW.minusSeconds(10);
    return new FiatOrder(
        1L,
        123L,
        456L,
        789L,
        "法幣商品",
        null,
        null,
        false,
        null,
        "FD260411000004",
        "CVS654322",
        1200L,
        null,
        null,
        null,
        FiatOrder.Status.EXPIRED,
        "0",
        "尚未付款",
        null,
        NOW.minusSeconds(60),
        expiredAt,
        "EXPIRED",
        null,
        null,
        null,
        null,
        null,
        0,
        null,
        NOW.minusSeconds(120),
        NOW.minusSeconds(10));
  }
}
