package ltdjms.discord.shop.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ltdjms.discord.shared.EnvironmentConfig;

@DisplayName("EcpayCallbackHttpServer 測試")
class EcpayCallbackHttpServerTest {

  private EcpayCallbackHttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop();
    }
  }

  @Test
  @DisplayName("測試環境不應允許公開綁定 callback")
  void shouldRejectPublicBindWhenStageModeEnabled() {
    EnvironmentConfig config = mock(EnvironmentConfig.class);
    FiatPaymentCallbackService callbackService = mock(FiatPaymentCallbackService.class);
    when(config.getEcpayReturnUrl()).thenReturn("https://merchant.example/ecpay/callback");
    when(config.getEcpayCallbackBindHost()).thenReturn("0.0.0.0");
    when(config.getEcpayCallbackBindPort()).thenReturn(8085);
    when(config.getEcpayCallbackPath()).thenReturn("/ecpay/callback");
    when(config.getEcpayStageMode()).thenReturn(true);

    server = new EcpayCallbackHttpServer(config, callbackService);

    assertThatThrownBy(() -> server.start()).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("正式環境仍允許公開綁定 callback")
  void shouldAllowPublicBindWhenStageModeDisabled() throws Exception {
    int port = reserveFreePort();
    EnvironmentConfig config = mock(EnvironmentConfig.class);
    FiatPaymentCallbackService callbackService = mock(FiatPaymentCallbackService.class);
    when(config.getEcpayReturnUrl()).thenReturn("https://merchant.example/ecpay/callback");
    when(config.getEcpayCallbackBindHost()).thenReturn("0.0.0.0");
    when(config.getEcpayCallbackBindPort()).thenReturn(port);
    when(config.getEcpayCallbackPath()).thenReturn("/ecpay/callback");
    when(config.getEcpayStageMode()).thenReturn(false);

    server = new EcpayCallbackHttpServer(config, callbackService);

    server.start();

    assertThat(server).isNotNull();
  }

  @Test
  @DisplayName("callback 路徑不應與首頁路徑衝突")
  void shouldRejectCallbackPathConflictWithLandingPage() {
    EnvironmentConfig config = mock(EnvironmentConfig.class);
    FiatPaymentCallbackService callbackService = mock(FiatPaymentCallbackService.class);
    when(config.getEcpayReturnUrl()).thenReturn("https://merchant.example/");
    when(config.getEcpayCallbackBindHost()).thenReturn("127.0.0.1");
    when(config.getEcpayCallbackBindPort()).thenReturn(8085);
    when(config.getEcpayCallbackPath()).thenReturn("/");
    when(config.getEcpayStageMode()).thenReturn(false);

    server = new EcpayCallbackHttpServer(config, callbackService);

    assertThatThrownBy(() -> server.start()).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("callback 不應再依賴 query token 授權")
  void shouldAcceptCallbackWithoutQueryToken() throws Exception {
    int port = reserveFreePort();
    EnvironmentConfig config = mock(EnvironmentConfig.class);
    FiatPaymentCallbackService callbackService = mock(FiatPaymentCallbackService.class);
    when(config.getEcpayReturnUrl()).thenReturn("https://merchant.example/ecpay/callback");
    when(config.getEcpayCallbackBindHost()).thenReturn("127.0.0.1");
    when(config.getEcpayCallbackBindPort()).thenReturn(port);
    when(config.getEcpayCallbackPath()).thenReturn("/ecpay/callback");
    when(config.getEcpayCallbackSharedSecret()).thenReturn("shared-secret");
    when(config.getEcpayStageMode()).thenReturn(true);
    when(callbackService.handleCallback("Data=ok", "application/x-www-form-urlencoded"))
        .thenReturn(FiatPaymentCallbackService.CallbackResult.ok());

    server = new EcpayCallbackHttpServer(config, callbackService);
    server.start();

    HttpURLConnection connection =
        (HttpURLConnection)
            new URL("http://127.0.0.1:" + port + "/ecpay/callback").openConnection();
    connection.setRequestMethod("POST");
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
    connection.getOutputStream().write("Data=ok".getBytes(StandardCharsets.UTF_8));

    assertThat(connection.getResponseCode()).isEqualTo(200);
    try (InputStream inputStream = connection.getInputStream()) {
      assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("1|OK");
    }
    verify(callbackService).handleCallback("Data=ok", "application/x-www-form-urlencoded");
  }

  @Test
  @DisplayName("根路徑應回傳整合進來的首頁 HTML")
  void shouldServeLandingPageAtRootPath() throws Exception {
    int port = reserveFreePort();
    EnvironmentConfig config = mock(EnvironmentConfig.class);
    FiatPaymentCallbackService callbackService = mock(FiatPaymentCallbackService.class);
    when(config.getEcpayReturnUrl()).thenReturn("https://merchant.example/ecpay/callback");
    when(config.getEcpayCallbackBindHost()).thenReturn("127.0.0.1");
    when(config.getEcpayCallbackBindPort()).thenReturn(port);
    when(config.getEcpayCallbackPath()).thenReturn("/ecpay/callback");
    when(config.getEcpayCallbackSharedSecret()).thenReturn("shared-secret");
    when(config.getEcpayStageMode()).thenReturn(true);

    server = new EcpayCallbackHttpServer(config, callbackService);
    server.start();

    HttpURLConnection connection =
        (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/").openConnection();
    connection.setRequestMethod("GET");

    assertThat(connection.getResponseCode()).isEqualTo(200);
    assertThat(connection.getContentType()).startsWith("text/html");
    try (InputStream inputStream = connection.getInputStream()) {
      String html = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
      assertThat(html).contains("LTDJ Recruitment");
      assertThat(html).contains("<!DOCTYPE html>");
    }
    verify(callbackService, never()).handleCallback(any(), any());
  }

  private int reserveFreePort() throws Exception {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }
}
