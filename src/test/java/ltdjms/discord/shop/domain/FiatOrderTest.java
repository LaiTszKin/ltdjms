package ltdjms.discord.shop.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ltdjms.discord.product.domain.Product;

@DisplayName("FiatOrder 測試")
class FiatOrderTest {

  private static final Instant EXPIRE_AT = Instant.parse("2026-04-11T12:00:00Z");

  @Test
  @DisplayName("pending order 應保存完整履約快照與到期時間")
  void shouldCaptureFulfillmentSnapshotAndExpireAt() {
    FiatOrder order =
        FiatOrder.createPending(
            123L,
            456L,
            789L,
            "法幣商品",
            Product.RewardType.CURRENCY,
            50L,
            true,
            "ESCORT-A",
            "FD260411000003",
            "CVS999999",
            1200L,
            1200L,
            1200L,
            EXPIRE_AT);

    assertThat(order.productName()).isEqualTo("法幣商品");
    assertThat(order.fulfillmentRewardType()).isEqualTo(Product.RewardType.CURRENCY);
    assertThat(order.fulfillmentRewardAmount()).isEqualTo(50L);
    assertThat(order.fulfillmentAutoCreateEscortOrder()).isTrue();
    assertThat(order.fulfillmentEscortOptionCode()).isEqualTo("ESCORT-A");
    assertThat(order.expireAt()).isEqualTo(EXPIRE_AT);
    assertThat(order.isTerminal()).isFalse();
    assertThat(order.toFulfillmentProduct().name()).isEqualTo("法幣商品");
    assertThat(order.toFulfillmentProduct().rewardType()).isEqualTo(Product.RewardType.CURRENCY);
    assertThat(order.toFulfillmentProduct().rewardAmount()).isEqualTo(50L);
    assertThat(order.toFulfillmentProduct().shouldAutoCreateEscortOrder()).isTrue();
  }

  @Test
  @DisplayName("不完整的履約快照應在建單時失敗")
  void shouldRejectIncompleteSnapshot() {
    assertThatThrownBy(
            () ->
                FiatOrder.createPending(
                    123L,
                    456L,
                    789L,
                    "法幣商品",
                    Product.RewardType.CURRENCY,
                    50L,
                    false,
                    "ESCORT-A",
                    "FD260411000004",
                    "CVS999998",
                    1200L,
                    1200L,
                    1200L,
                    EXPIRE_AT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "fulfillmentEscortOptionCode requires fulfillmentAutoCreateEscortOrder");

    assertThatThrownBy(
            () ->
                FiatOrder.createPending(
                    123L,
                    456L,
                    789L,
                    "法幣商品",
                    Product.RewardType.CURRENCY,
                    null,
                    true,
                    "ESCORT-A",
                    "FD260411000005",
                    "CVS999997",
                    1200L,
                    1200L,
                    1200L,
                    EXPIRE_AT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fulfillmentRewardType and fulfillmentRewardAmount");
  }

  @Test
  @DisplayName("逾期訂單必須帶有終止時間與原因")
  void expiredOrderShouldRequireTerminalDetails() {
    Instant expiredAt = Instant.parse("2026-04-11T12:05:00Z");

    FiatOrder order =
        new FiatOrder(
            1L,
            1L,
            2L,
            3L,
            "商品",
            null,
            null,
            false,
            null,
            "FD260411000001",
            "ABC123456789",
            1200L,
            null,
            null,
            FiatOrder.Status.EXPIRED,
            "0",
            "尚未付款",
            null,
            EXPIRE_AT,
            expiredAt,
            "EXPIRED",
            null,
            null,
            null,
            null,
            null,
            0,
            null,
            expiredAt.minusSeconds(300),
            expiredAt);

    assertThat(order.isExpired()).isTrue();
    assertThat(order.isTerminal()).isTrue();
    assertThat(order.expiredAt()).isEqualTo(expiredAt);
    assertThat(order.terminalReason()).isEqualTo("EXPIRED");
  }

  @Test
  @DisplayName("逾期訂單缺少終止原因時應被拒絕")
  void expiredOrderShouldRejectMissingTerminalReason() {
    Instant expiredAt = Instant.parse("2026-04-11T12:05:00Z");

    assertThatThrownBy(
            () ->
                new FiatOrder(
                    1L,
                    1L,
                    2L,
                    3L,
                    "商品",
                    null,
                    null,
                    false,
                    null,
                    "FD260411000001",
                    "ABC123456789",
                    1200L,
                    null,
                    null,
                    FiatOrder.Status.EXPIRED,
                    "0",
                    "尚未付款",
                    null,
                    EXPIRE_AT,
                    expiredAt,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    0,
                    null,
                    expiredAt.minusSeconds(300),
                    expiredAt))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("terminalReason");
  }
}
