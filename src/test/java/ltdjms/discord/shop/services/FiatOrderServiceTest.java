package ltdjms.discord.shop.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ltdjms.discord.product.domain.Product;
import ltdjms.discord.product.services.ProductService;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;

@ExtendWith(MockitoExtension.class)
@DisplayName("FiatOrderService 測試")
class FiatOrderServiceTest {

  private static final long TEST_GUILD_ID = 1L;
  private static final long TEST_USER_ID = 2L;
  private static final long TEST_PRODUCT_ID = 3L;

  @Mock private ProductService productService;

  @Mock private EcpayCvsPaymentService ecpayCvsPaymentService;

  private FiatOrderService service;

  @BeforeEach
  void setUp() {
    service = new FiatOrderService(productService, ecpayCvsPaymentService);
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
    when(ecpayCvsPaymentService.generateCvsPaymentCode(1200L, "VIP", "Discord 商品下單 user:2"))
        .thenReturn(
            Result.ok(
                new EcpayCvsPaymentService.CvsPaymentCode(
                    "FD260224000001",
                    "ABC123456789",
                    "2026/02/26 23:59:59",
                    "https://example.com")));

    Result<FiatOrderService.FiatOrderResult, DomainError> result =
        service.createFiatOnlyOrder(TEST_GUILD_ID, TEST_USER_ID, TEST_PRODUCT_ID);

    assertThat(result.isOk()).isTrue();
    assertThat(result.getValue().orderNumber()).isEqualTo("FD260224000001");
    assertThat(result.getValue().paymentNo()).isEqualTo("ABC123456789");
    assertThat(result.getValue().formatDirectMessage()).contains("訂單編號");
    assertThat(result.getValue().formatDirectMessage()).contains("超商代碼");
  }
}
