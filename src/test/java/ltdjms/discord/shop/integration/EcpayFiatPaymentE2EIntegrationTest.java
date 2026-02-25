package ltdjms.discord.shop.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import ltdjms.discord.product.domain.Product;
import ltdjms.discord.product.persistence.JdbcProductRepository;
import ltdjms.discord.product.services.ProductService;
import ltdjms.discord.redemption.persistence.JdbcRedemptionCodeRepository;
import ltdjms.discord.shared.DatabaseMigrationRunner;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.EnvironmentConfig;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.events.DomainEventPublisher;
import ltdjms.discord.shop.services.EcpayCvsPaymentService;
import ltdjms.discord.shop.services.FiatOrderService;

/**
 * 綠界法幣付款端到端測試（手動觸發）。
 *
 * <p>此測試會使用綠界 Stage API（GenPaymentCode）實際取號，驗證：
 *
 * <ul>
 *   <li>商品建立（限定法幣商品）
 *   <li>法幣下單
 *   <li>取得訂單編號與超商代碼
 * </ul>
 *
 * <p>執行前請設定環境變數：RUN_ECPAY_E2E=true
 */
@Testcontainers(disabledWithoutDocker = true)
@EnabledIfEnvironmentVariable(named = "RUN_ECPAY_E2E", matches = "true")
@DisplayName("綠界法幣付款 E2E 整合測試")
class EcpayFiatPaymentE2EIntegrationTest {

  @Container
  @SuppressWarnings("resource")
  private static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("ecpay_e2e_test")
          .withUsername("test")
          .withPassword("test");

  private static final long TEST_GUILD_ID = 5566778899L;
  private static final long TEST_USER_ID = 1122334455L;

  private HikariDataSource dataSource;
  private Path tempEnvDir;

  @BeforeEach
  void setUp() throws IOException {
    dataSource = createDataSource();
    DatabaseMigrationRunner.forDefaultMigrations().migrate(dataSource);
    truncateBusinessTables(dataSource);

    tempEnvDir = Files.createTempDirectory("ecpay-e2e-config");
    writeEcpayDotEnv(tempEnvDir.resolve(".env"));
  }

  @AfterEach
  void tearDown() throws IOException {
    if (dataSource != null && !dataSource.isClosed()) {
      dataSource.close();
    }
    if (tempEnvDir != null) {
      deleteDirectoryRecursively(tempEnvDir);
    }
  }

  @Test
  @Timeout(120)
  @DisplayName("應可從商品下單到取得綠界超商代碼")
  void shouldCreateFiatOrderAndReceiveCvsCodeFromEcpay() throws Exception {
    ProductService productService =
        new ProductService(
            new JdbcProductRepository(dataSource),
            new JdbcRedemptionCodeRepository(dataSource),
            new DomainEventPublisher());
    EnvironmentConfig config = new EnvironmentConfig(tempEnvDir);
    Assumptions.assumeTrue(config.getEcpayStageMode(), "E2E 測試僅允許綠界 Stage 模式");

    EcpayCvsPaymentService paymentService = new EcpayCvsPaymentService(config);
    FiatOrderService fiatOrderService = new FiatOrderService(productService, paymentService);

    Result<Product, DomainError> productResult =
        productService.createProduct(
            TEST_GUILD_ID, "E2E 法幣商品", "綠界端到端測試商品", null, null, null, 1234L);
    assertThat(productResult.isOk()).isTrue();

    long productId = productResult.getValue().id();
    Result<FiatOrderService.FiatOrderResult, DomainError> orderResult =
        fiatOrderService.createFiatOnlyOrder(TEST_GUILD_ID, TEST_USER_ID, productId);

    assertThat(orderResult.isOk())
        .as(orderResult.isErr() ? orderResult.getError().message() : "下單成功")
        .isTrue();

    FiatOrderService.FiatOrderResult order = orderResult.getValue();
    assertThat(order.orderNumber()).isNotBlank();
    assertThat(order.paymentNo()).isNotBlank();
    assertThat(order.formatDirectMessage())
        .contains(order.orderNumber())
        .contains(order.paymentNo());

    if (order.paymentUrl() != null && !order.paymentUrl().isBlank()) {
      HttpResponse<Void> paymentPageResponse =
          HttpClient.newHttpClient()
              .send(
                  HttpRequest.newBuilder()
                      .uri(URI.create(order.paymentUrl()))
                      .timeout(Duration.ofSeconds(15))
                      .GET()
                      .build(),
                  HttpResponse.BodyHandlers.discarding());
      assertThat(paymentPageResponse.statusCode()).isBetween(200, 399);
    }
  }

  private HikariDataSource createDataSource() {
    HikariConfig hikariConfig = new HikariConfig();
    hikariConfig.setJdbcUrl(postgres.getJdbcUrl());
    hikariConfig.setUsername(postgres.getUsername());
    hikariConfig.setPassword(postgres.getPassword());
    hikariConfig.setMaximumPoolSize(5);
    hikariConfig.setMinimumIdle(1);
    hikariConfig.setPoolName("EcpayE2ETestPool");
    return new HikariDataSource(hikariConfig);
  }

  private void truncateBusinessTables(DataSource ds) {
    try (Connection conn = ds.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("TRUNCATE TABLE redemption_code CASCADE");
      stmt.execute("TRUNCATE TABLE product CASCADE");
    } catch (Exception e) {
      throw new RuntimeException("Failed to clean test tables", e);
    }
  }

  private void writeEcpayDotEnv(Path envPath) throws IOException {
    String merchantId = valueOrDefault(System.getenv("ECPAY_MERCHANT_ID"), "3002607");
    String hashKey = valueOrDefault(System.getenv("ECPAY_HASH_KEY"), "pwFHCqoQZGmho4w6");
    String hashIv = valueOrDefault(System.getenv("ECPAY_HASH_IV"), "EkRm7iFT261dpevs");
    String returnUrl =
        valueOrDefault(System.getenv("ECPAY_RETURN_URL"), "https://example.com/ecpay/callback");
    String stageMode = valueOrDefault(System.getenv("ECPAY_STAGE_MODE"), "true");
    String expireMinutes = valueOrDefault(System.getenv("ECPAY_CVS_EXPIRE_MINUTES"), "60");

    String content =
        """
        ECPAY_MERCHANT_ID=%s
        ECPAY_HASH_KEY=%s
        ECPAY_HASH_IV=%s
        ECPAY_RETURN_URL=%s
        ECPAY_STAGE_MODE=%s
        ECPAY_CVS_EXPIRE_MINUTES=%s
        """
            .formatted(merchantId, hashKey, hashIv, returnUrl, stageMode, expireMinutes);

    Files.writeString(envPath, content);
  }

  private String valueOrDefault(String value, String defaultValue) {
    return value != null && !value.isBlank() ? value : defaultValue;
  }

  private void deleteDirectoryRecursively(Path path) throws IOException {
    if (!Files.exists(path)) {
      return;
    }
    try (var walk = Files.walk(path)) {
      walk.sorted((a, b) -> b.getNameCount() - a.getNameCount())
          .forEach(
              p -> {
                try {
                  Files.deleteIfExists(p);
                } catch (IOException e) {
                  throw new RuntimeException("Failed to delete: " + p, e);
                }
              });
    } catch (RuntimeException e) {
      if (e.getCause() instanceof IOException ioException) {
        throw ioException;
      }
      throw e;
    }
  }
}
