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
  @DisplayName("缺少 shared secret 時不應允許公開綁定")
  void shouldRejectPublicBindWithoutSharedSecret() {
    EnvironmentConfig config = mock(EnvironmentConfig.class);
    FiatPaymentCallbackService callbackService = mock(FiatPaymentCallbackService.class);
    when(config.getEcpayReturnUrl()).thenReturn("https://merchant.example/ecpay/callback");
    when(config.getEcpayCallbackBindHost()).thenReturn("0.0.0.0");
    when(config.getEcpayCallbackBindPort()).thenReturn(8085);
    when(config.getEcpayCallbackPath()).thenReturn("/ecpay/callback");
    when(config.getEcpayCallbackSharedSecret()).thenReturn("");

    server = new EcpayCallbackHttpServer(config, callbackService);

    assertThatThrownBy(() -> server.start()).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("未授權 callback 應在讀取 payload 前回傳 401")
  void shouldRejectUnauthorizedCallbackBeforeDelegating() throws Exception {
    int port = reserveFreePort();
    EnvironmentConfig config = mock(EnvironmentConfig.class);
    FiatPaymentCallbackService callbackService = mock(FiatPaymentCallbackService.class);
    when(config.getEcpayReturnUrl()).thenReturn("https://merchant.example/ecpay/callback");
    when(config.getEcpayCallbackBindHost()).thenReturn("127.0.0.1");
    when(config.getEcpayCallbackBindPort()).thenReturn(port);
    when(config.getEcpayCallbackPath()).thenReturn("/ecpay/callback");
    when(config.getEcpayCallbackSharedSecret()).thenReturn("shared-secret");

    server = new EcpayCallbackHttpServer(config, callbackService);
    server.start();

    HttpURLConnection connection =
        (HttpURLConnection)
            new URL("http://127.0.0.1:" + port + "/ecpay/callback").openConnection();
    connection.setRequestMethod("POST");
    connection.setDoOutput(true);
    connection.getOutputStream().write("Data=fake".getBytes(StandardCharsets.UTF_8));

    assertThat(connection.getResponseCode()).isEqualTo(401);
    verify(callbackService, never()).handleCallback(any(), any());
  }

  @Test
  @DisplayName("合法 shared secret callback 應委派給 callback service")
  void shouldAcceptAuthorizedCallback() throws Exception {
    int port = reserveFreePort();
    EnvironmentConfig config = mock(EnvironmentConfig.class);
    FiatPaymentCallbackService callbackService = mock(FiatPaymentCallbackService.class);
    when(config.getEcpayReturnUrl()).thenReturn("https://merchant.example/ecpay/callback");
    when(config.getEcpayCallbackBindHost()).thenReturn("127.0.0.1");
    when(config.getEcpayCallbackBindPort()).thenReturn(port);
    when(config.getEcpayCallbackPath()).thenReturn("/ecpay/callback");
    when(config.getEcpayCallbackSharedSecret()).thenReturn("shared-secret");
    when(callbackService.handleCallback("Data=ok", "application/x-www-form-urlencoded"))
        .thenReturn(FiatPaymentCallbackService.CallbackResult.ok());

    server = new EcpayCallbackHttpServer(config, callbackService);
    server.start();

    HttpURLConnection connection =
        (HttpURLConnection)
            new URL("http://127.0.0.1:" + port + "/ecpay/callback?token=shared-secret")
                .openConnection();
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

  private int reserveFreePort() throws Exception {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }
}
