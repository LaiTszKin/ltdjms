package ltdjms.discord.shop.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.EnvironmentConfig;
import ltdjms.discord.shared.Result;

@ExtendWith(MockitoExtension.class)
@DisplayName("EcpayCvsPaymentService 測試")
class EcpayCvsPaymentServiceTest {

  @Mock private EnvironmentConfig config;

  private EcpayCvsPaymentService service;

  @BeforeEach
  void setUp() {
    service = new EcpayCvsPaymentService(config);
  }

  @Test
  @DisplayName("金額小於等於零應回傳 INVALID_INPUT")
  void shouldReturnInvalidInputWhenAmountInvalid() {
    Result<EcpayCvsPaymentService.CvsPaymentCode, DomainError> result =
        service.generateCvsPaymentCode(0, "商品", "測試");

    assertThat(result.isErr()).isTrue();
    assertThat(result.getError().category()).isEqualTo(DomainError.Category.INVALID_INPUT);
  }

  @Test
  @DisplayName("綠界設定缺漏應回傳 INVALID_INPUT")
  void shouldReturnInvalidInputWhenConfigMissing() {
    when(config.getEcpayMerchantId()).thenReturn("");
    when(config.getEcpayHashKey()).thenReturn("");
    when(config.getEcpayHashIv()).thenReturn("");
    when(config.getEcpayReturnUrl()).thenReturn("");

    Result<EcpayCvsPaymentService.CvsPaymentCode, DomainError> result =
        service.generateCvsPaymentCode(100, "商品", "測試");

    assertThat(result.isErr()).isTrue();
    assertThat(result.getError().category()).isEqualTo(DomainError.Category.INVALID_INPUT);
    assertThat(result.getError().message()).contains("綠界金流尚未完成設定");
  }
}
