package ltdjms.discord.shop.services;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import ltdjms.discord.shared.EnvironmentConfig;

/** Embedded HTTP server for receiving ECPay ReturnURL callback pushes. */
public class EcpayCallbackHttpServer {

  private static final Logger LOG = LoggerFactory.getLogger(EcpayCallbackHttpServer.class);
  private static final int CALLBACK_WORKER_THREADS = 8;
  private static final int MAX_CALLBACK_BODY_BYTES = 64 * 1024;

  private final EnvironmentConfig config;
  private final FiatPaymentCallbackService callbackService;
  private HttpServer server;
  private ExecutorService executor;

  public EcpayCallbackHttpServer(
      EnvironmentConfig config, FiatPaymentCallbackService callbackService) {
    this.config = Objects.requireNonNull(config, "config must not be null");
    this.callbackService =
        Objects.requireNonNull(callbackService, "callbackService must not be null");
  }

  public synchronized void start() {
    if (server != null) {
      return;
    }
    if (config.getEcpayReturnUrl() == null || config.getEcpayReturnUrl().isBlank()) {
      LOG.info("Skip starting ECPay callback server because ECPAY_RETURN_URL is not configured");
      return;
    }
    String bindHost = sanitizeBindHost(config.getEcpayCallbackBindHost());
    int bindPort = normalizeBindPort(config.getEcpayCallbackBindPort());
    String callbackPath = normalizePath(config.getEcpayCallbackPath());

    try {
      server = HttpServer.create(new InetSocketAddress(bindHost, bindPort), 0);
      server.createContext(callbackPath, this::handleCallbackRequest);
      executor = Executors.newFixedThreadPool(CALLBACK_WORKER_THREADS);
      server.setExecutor(executor);
      server.start();
      LOG.info(
          "ECPay callback server started: host={}, port={}, path={}",
          bindHost,
          bindPort,
          callbackPath);
    } catch (IOException e) {
      LOG.error(
          "Failed to start ECPay callback server: host={}, port={}, path={}",
          bindHost,
          bindPort,
          callbackPath,
          e);
      shutdownExecutor();
      throw new IllegalStateException("無法啟動綠界回推伺服器", e);
    }
  }

  public synchronized void stop() {
    if (server == null) {
      return;
    }
    server.stop(0);
    server = null;
    shutdownExecutor();
    LOG.info("ECPay callback server stopped");
  }

  private void handleCallbackRequest(HttpExchange exchange) throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      writeResponse(exchange, 405, "Method Not Allowed");
      return;
    }

    String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
    String requestBody;
    try (InputStream requestBodyStream = exchange.getRequestBody()) {
      requestBody = readRequestBodyWithLimit(requestBodyStream, MAX_CALLBACK_BODY_BYTES);
    } catch (PayloadTooLargeException e) {
      LOG.warn("ECPay callback payload exceeded limit: limit={} bytes", MAX_CALLBACK_BODY_BYTES);
      writeResponse(exchange, 413, "Payload Too Large");
      return;
    }

    FiatPaymentCallbackService.CallbackResult result =
        callbackService.handleCallback(requestBody, contentType);
    writeResponse(exchange, result.httpStatus(), result.responseBody());
  }

  private void writeResponse(HttpExchange exchange, int statusCode, String body)
      throws IOException {
    byte[] response = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
    exchange.sendResponseHeaders(statusCode, response.length);
    try (OutputStream output = exchange.getResponseBody()) {
      output.write(response);
    }
  }

  private String sanitizeBindHost(String bindHost) {
    if (bindHost == null || bindHost.isBlank()) {
      return "0.0.0.0";
    }
    return bindHost.trim();
  }

  private int normalizeBindPort(int port) {
    if (port < 1 || port > 65535) {
      return 8085;
    }
    return port;
  }

  private String normalizePath(String path) {
    if (path == null || path.isBlank()) {
      return "/ecpay/callback";
    }
    String normalized = path.trim();
    if (!normalized.startsWith("/")) {
      normalized = "/" + normalized;
    }
    return normalized;
  }

  private String readRequestBodyWithLimit(InputStream inputStream, int maxBytes)
      throws IOException, PayloadTooLargeException {
    byte[] buffer = new byte[4096];
    int totalBytes = 0;

    try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      int read;
      while ((read = inputStream.read(buffer)) != -1) {
        totalBytes += read;
        if (totalBytes > maxBytes) {
          throw new PayloadTooLargeException();
        }
        output.write(buffer, 0, read);
      }
      return output.toString(StandardCharsets.UTF_8);
    }
  }

  private void shutdownExecutor() {
    if (executor == null) {
      return;
    }
    executor.shutdown();
    executor = null;
  }

  private static final class PayloadTooLargeException extends Exception {}
}
