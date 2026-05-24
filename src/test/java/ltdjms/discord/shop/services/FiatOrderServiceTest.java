package ltdjms.discord.shop.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ltdjms.discord.membership.domain.MembershipTier;
import ltdjms.discord.membership.services.EscortPriceQuote;
import ltdjms.discord.membership.services.MembershipPricingService;
import ltdjms.discord.product.domain.Product;
import ltdjms.discord.product.services.ProductService;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shop.domain.FiatOrder;
import ltdjms.discord.shop.domain.FiatOrderRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("FiatOrderService 測試")
class FiatOrderServiceTest {

  private static final long TEST_GUILD_ID = 1L;
  private static final long TEST_USER_ID = 2L;
  private static final long TEST_PRODUCT_ID = 3L;

  @Mock private ProductService productService;

  @Mock private EcpayCvsPaymentService ecpayCvsPaymentService;

  @Mock private FiatOrderRepository fiatOrderRepository;

  @Mock private MembershipPricingService membershipPricingService;

  private FiatOrderService service;

  @BeforeEach
  void setUp() {
    service =
        new FiatOrderService(
            productService,
            ecpayCvsPaymentService,
            fiatOrderRepository,
            membershipPricingService);
  }

  @Test
  @DisplayName("商品不存在應回傳錯誤")
  void shouldReturnErrorWhenProductNotFound() {
    when(productService.getProduct(TEST_PRODUCT_ID)).thenReturn(Optional.empty());

    Result<FiatOrderService.FiatOrderResult, DomainError> result =
        service.createFiatOnlyOrder(TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID);

    assertThat(result.isErr()).isTrue();
    assertThat(result.getError().message()).contains("找不到該商品");
  }

  @Test
  @DisplayName("非限定法幣商品應回傳錯誤")
  void shouldReturnErrorWhenProductIsNotFiatOnly() {
    Product product =
        new Product(
            TEST_PRODUCT_ID,
            TEST_GUILD_ID,
            "VIP",
            null,
            null,
            null,
            100L,
            500L,
            Instant.now(),
            Instant.now());
    when(productService.getProduct(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));

    Result<FiatOrderService.FiatOrderResult, DomainError> result =
        service.createFiatOnlyOrder(TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID);

    assertThat(result.isErr()).isTrue();
    assertThat(result.getError().message()).contains("限定法幣");
  }

  @Test
  @DisplayName("綠界取號成功應回傳訂單資料")
  void shouldReturnOrderDataWhenEcpaySuccess() {
    Product product =
        new Product(
            TEST_PRODUCT_ID,
            TEST_GUILD_ID,
            "VIP",
            "desc",
            null,
            null,
            null,
            1200L,
            Instant.now(),
            Instant.now());
    when(productService.getProduct(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));
    when(membershipPricingService.quoteEscortPrice(TEST_USER_ID, product, TEST_GUILD_ID))
        .thenReturn(noDiscountQuote(1200L, 0L));
    when(ecpayCvsPaymentService.generateCvsPaymentCode(1200L, "VIP", "Discord 商品下單 user:2"))
        .thenReturn(
            Result.ok(
                new EcpayCvsPaymentService.CvsPaymentCode(
                    "FD260224000001",
                    "ABC123456789",
                    "2026/02/26 23:59:59",
                    Instant.parse("2026-02-26T15:59:59Z"),
                    "https://example.com")));
    when(fiatOrderRepository.save(any(FiatOrder.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Result<FiatOrderService.FiatOrderResult, DomainError> result =
        service.createFiatOnlyOrder(TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID);

    assertThat(result.isOk()).isTrue();
    assertThat(result.getValue().orderNumber()).isEqualTo("FD260224000001");
    assertThat(result.getValue().paymentNo()).isEqualTo("ABC123456789");
    assertThat(result.getValue().formatDirectMessage()).contains("訂單編號");
    assertThat(result.getValue().formatDirectMessage()).contains("超商代碼");
    assertThat(result.getValue().formatDirectMessage()).contains("2026/02/26 23:59:59");
    assertThat(result.getValue().formatDirectMessage()).contains("請在付款期限內完成付款");
    assertThat(result.getValue().formatDirectMessage()).contains("逾期取消狀態");
  }

  @Test
  @DisplayName("建立法幣訂單時應落庫為待付款")
  void shouldPersistPendingFiatOrder() {
    Product product =
        new Product(
            TEST_PRODUCT_ID,
            TEST_GUILD_ID,
            "VIP",
            "desc",
            Product.RewardType.CURRENCY,
            50L,
            null,
            1200L,
            true,
            "ESCORT-A",
            Instant.now(),
            Instant.now());
    when(productService.getProduct(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));
    when(membershipPricingService.quoteEscortPrice(TEST_USER_ID, product, TEST_GUILD_ID))
        .thenReturn(noDiscountQuote(1200L, 0L));
    when(ecpayCvsPaymentService.generateCvsPaymentCode(1200L, "VIP", "Discord 商品下單 user:2"))
        .thenReturn(
            Result.ok(
                new EcpayCvsPaymentService.CvsPaymentCode(
                    "FD260224000001",
                    "ABC123456789",
                    "2026/02/26 23:59:59",
                    Instant.parse("2026-02-26T15:59:59Z"),
                    "https://example.com")));
    ArgumentCaptor<FiatOrder> orderCaptor = ArgumentCaptor.forClass(FiatOrder.class);
    when(fiatOrderRepository.save(orderCaptor.capture()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Result<FiatOrderService.FiatOrderResult, DomainError> result =
        service.createFiatOnlyOrder(TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID);

    assertThat(result.isOk()).isTrue();
    assertThat(result.getValue().fulfillmentWarning()).isNull();
    assertThat(orderCaptor.getValue().productName()).isEqualTo("VIP");
    assertThat(orderCaptor.getValue().fulfillmentRewardType())
        .isEqualTo(Product.RewardType.CURRENCY);
    assertThat(orderCaptor.getValue().fulfillmentRewardAmount()).isEqualTo(50L);
    assertThat(orderCaptor.getValue().fulfillmentAutoCreateEscortOrder()).isTrue();
    assertThat(orderCaptor.getValue().fulfillmentEscortOptionCode()).isEqualTo("ESCORT-A");
    assertThat(orderCaptor.getValue().expireAt()).isEqualTo(Instant.parse("2026-02-26T15:59:59Z"));
    assertThat(result.getValue().product().name()).isEqualTo("VIP");
    assertThat(result.getValue().product().formatFiatPriceTwd()).isEqualTo("NT$1,200");
    verify(fiatOrderRepository).save(any(FiatOrder.class));
  }

  @Test
  @DisplayName("白銀會員 escort 法幣訂單應以折後價取號並落庫")
  void shouldUseDiscountedAmountForEscortFiatOrder() {
    Product product =
        new Product(
            TEST_PRODUCT_ID,
            TEST_GUILD_ID,
            "護航",
            "desc",
            null,
            null,
            null,
            3500L,
            true,
            "ESCORT-A",
            Instant.now(),
            Instant.now());
    EscortPriceQuote quote =
        new EscortPriceQuote(
            3500L,
            3150L,
            0L,
            0L,
            MembershipTier.SILVER,
            new BigDecimal("0.10"));
    when(productService.getProduct(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));
    when(membershipPricingService.quoteEscortPrice(TEST_USER_ID, product, TEST_GUILD_ID))
        .thenReturn(quote);
    when(ecpayCvsPaymentService.generateCvsPaymentCode(
            eq(3150L), eq("護航"), eq("Discord 商品下單 user:2")))
        .thenReturn(
            Result.ok(
                new EcpayCvsPaymentService.CvsPaymentCode(
                    "FD260224000002",
                    "ABC123456789",
                    "2026/02/26 23:59:59",
                    Instant.parse("2026-02-26T15:59:59Z"),
                    "https://example.com")));
    ArgumentCaptor<FiatOrder> orderCaptor = ArgumentCaptor.forClass(FiatOrder.class);
    when(fiatOrderRepository.save(orderCaptor.capture()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Result<FiatOrderService.FiatOrderResult, DomainError> result =
        service.createFiatOnlyOrder(TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID);

    assertThat(result.isOk()).isTrue();
    assertThat(orderCaptor.getValue().amountTwd()).isEqualTo(3150L);
    assertThat(orderCaptor.getValue().listPriceTwd()).isEqualTo(3500L);
    assertThat(orderCaptor.getValue().chargedAmountTwd()).isEqualTo(3150L);
    assertThat(result.getValue().formatDirectMessage()).contains("會員價 NT$3,150");
  }

  private static EscortPriceQuote noDiscountQuote(long fiat, long currency) {
    return new EscortPriceQuote(
        fiat, fiat, currency, currency, MembershipTier.NONE, BigDecimal.ZERO);
  }
}
