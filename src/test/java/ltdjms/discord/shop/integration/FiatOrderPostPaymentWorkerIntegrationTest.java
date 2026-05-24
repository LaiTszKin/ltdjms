package ltdjms.discord.shop.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import ltdjms.discord.dispatch.domain.EscortDispatchOrder;
import ltdjms.discord.dispatch.persistence.JdbcEscortDispatchOrderRepository;
import ltdjms.discord.dispatch.services.EscortDispatchHandoffService;
import ltdjms.discord.membership.services.MembershipSpendServiceFixtures;
import ltdjms.discord.product.domain.Product;
import ltdjms.discord.product.persistence.JdbcProductRepository;
import ltdjms.discord.product.services.ProductRewardService;
import ltdjms.discord.product.services.ProductService;
import ltdjms.discord.redemption.persistence.JdbcRedemptionCodeRepository;
import ltdjms.discord.shared.DatabaseMigrationRunner;
import ltdjms.discord.shared.events.DomainEventPublisher;
import ltdjms.discord.shop.domain.FiatOrder;
import ltdjms.discord.shop.domain.FiatOrderRepository;
import ltdjms.discord.shop.persistence.JdbcFiatOrderRepository;
import ltdjms.discord.shop.services.EscortOrderBuyerNotificationService;
import ltdjms.discord.shop.services.FiatOrderBuyerNotificationService;
import ltdjms.discord.shop.services.FiatOrderPostPaymentWorker;
import ltdjms.discord.shop.services.ShopAdminNotificationService;

@Testcontainers(disabledWithoutDocker = true)
@DisplayName("FiatOrderPostPaymentWorker 整合測試")
class FiatOrderPostPaymentWorkerIntegrationTest {

  @Container
  @SuppressWarnings("resource")
  private static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("fiat_worker_handoff_test")
          .withUsername("test")
          .withPassword("test");

  private HikariDataSource dataSource;
  private ProductService productService;
  private FiatOrderRepository fiatOrderRepository;
  private JdbcEscortDispatchOrderRepository dispatchRepository;
  private FiatOrderPostPaymentWorker worker;
  private ShopAdminNotificationService adminNotificationService;

  @BeforeEach
  void setUp() {
    dataSource = createDataSource();
    DatabaseMigrationRunner.forDefaultMigrations().migrate(dataSource);
    truncateTables(dataSource);

    productService =
        new ProductService(
            new JdbcProductRepository(dataSource),
            new JdbcRedemptionCodeRepository(dataSource),
            new DomainEventPublisher());
    fiatOrderRepository = new JdbcFiatOrderRepository(dataSource);
    dispatchRepository = new JdbcEscortDispatchOrderRepository(dataSource);
    adminNotificationService = mock(ShopAdminNotificationService.class);
    worker =
        new FiatOrderPostPaymentWorker(
            fiatOrderRepository,
            mock(ProductRewardService.class),
            new EscortDispatchHandoffService(dispatchRepository),
            adminNotificationService,
            mock(FiatOrderBuyerNotificationService.class),
            mock(EscortOrderBuyerNotificationService.class),
            MembershipSpendServiceFixtures.noop());
  }

  @AfterEach
  void tearDown() {
    if (dataSource != null && !dataSource.isClosed()) {
      dataSource.close();
    }
  }

  @Test
  @DisplayName("管理員通知失敗時仍應保留已建立的 dispatch work item 與可重試的 fiat order")
  void shouldPreserveDispatchWorkItemWhenAdminNotificationFails() {
    Product product = createAutoEscortProduct(123456789L, "法幣護航商品", null, 1200L, "CONF_DAM_300W");
    FiatOrder order =
        savePaidOrder(
            123456789L,
            987654321L,
            product.id(),
            product.name(),
            "FD260411000001",
            product.fiatPriceTwd());
    doThrow(new IllegalStateException("boom"))
        .when(adminNotificationService)
        .notifyAdminsOrderCreated(anyLong(), anyLong(), any(EscortDispatchOrder.class));

    worker.processPendingOrders();

    EscortDispatchOrder dispatchOrder =
        dispatchRepository
            .findBySourceIdentity(EscortDispatchOrder.SourceType.FIAT_PAYMENT, order.orderNumber())
            .orElseThrow();
    FiatOrder persistedOrder =
        fiatOrderRepository.findByOrderNumber(order.orderNumber()).orElseThrow();

    assertThat(dispatchOrder.sourceReference()).isEqualTo(order.orderNumber());
    assertThat(dispatchOrder.sourceProductName()).isEqualTo(product.name());
    assertThat(persistedOrder.isBuyerNotified()).isTrue();
    assertThat(persistedOrder.isAdminNotified()).isFalse();
    assertThat(persistedOrder.isFulfilled()).isFalse();
    assertThat(fiatOrderRepository.findOrdersPendingPostPayment(10))
        .extracting(FiatOrder::orderNumber)
        .contains(order.orderNumber());
  }

  private HikariDataSource createDataSource() {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(postgres.getJdbcUrl());
    config.setUsername(postgres.getUsername());
    config.setPassword(postgres.getPassword());
    config.setMaximumPoolSize(5);
    config.setMinimumIdle(1);
    config.setPoolName("FiatWorkerHandoffIntegrationPool");
    return new HikariDataSource(config);
  }

  private void truncateTables(DataSource ds) {
    try (Connection conn = ds.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("TRUNCATE TABLE escort_dispatch_order CASCADE");
      stmt.execute("TRUNCATE TABLE fiat_order CASCADE");
      stmt.execute("TRUNCATE TABLE product CASCADE");
      stmt.execute("TRUNCATE TABLE redemption_code CASCADE");
    } catch (Exception e) {
      throw new RuntimeException("Failed to clean test tables", e);
    }
  }

  private Product createAutoEscortProduct(
      long guildId, String name, Long currencyPrice, Long fiatPriceTwd, String escortOptionCode) {
    var result =
        productService.createProduct(
            guildId, name, "desc", null, null, currencyPrice, fiatPriceTwd, true, escortOptionCode);
    assertThat(result.isOk()).isTrue();
    return result.getValue();
  }

  private FiatOrder savePaidOrder(
      long guildId,
      long buyerUserId,
      long productId,
      String productName,
      String orderNumber,
      long amountTwd) {
    FiatOrder order =
        new FiatOrder(
            null,
            guildId,
            buyerUserId,
            productId,
            productName,
            null,
            null,
            true,
            "CONF_DAM_300W",
            orderNumber,
            "CVS123456",
            amountTwd,
            null,
            null,
            FiatOrder.Status.PAID,
            "1",
            "付款成功",
            Instant.parse("2026-04-11T10:00:00Z"),
            Instant.parse("2026-04-12T10:00:00Z"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            0,
            null,
            Instant.parse("2026-04-11T10:00:00Z"),
            Instant.parse("2026-04-11T10:00:00Z"));
    return fiatOrderRepository.save(order);
  }
}
