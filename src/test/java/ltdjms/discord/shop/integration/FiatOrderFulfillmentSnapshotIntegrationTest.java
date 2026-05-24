package ltdjms.discord.shop.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import ltdjms.discord.currency.domain.CurrencyTransaction;
import ltdjms.discord.dispatch.domain.EscortDispatchOrder;
import ltdjms.discord.dispatch.services.EscortDispatchHandoffService;
import ltdjms.discord.gametoken.domain.GameTokenTransaction;
import ltdjms.discord.product.domain.Product;
import ltdjms.discord.product.persistence.JdbcProductRepository;
import ltdjms.discord.product.services.ProductRewardService;
import ltdjms.discord.product.services.ProductService;
import ltdjms.discord.membership.persistence.JdbcMembershipRepository;
import ltdjms.discord.membership.services.MembershipPricingService;
import ltdjms.discord.redemption.persistence.JdbcRedemptionCodeRepository;
import ltdjms.discord.shared.DatabaseMigrationRunner;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.events.DomainEventPublisher;
import ltdjms.discord.shop.domain.FiatOrder;
import ltdjms.discord.shop.persistence.JdbcFiatOrderRepository;
import ltdjms.discord.shop.services.EcpayCvsPaymentService;
import ltdjms.discord.shop.services.FiatOrderBuyerNotificationService;
import ltdjms.discord.shop.services.FiatOrderPostPaymentWorker;
import ltdjms.discord.shop.services.FiatOrderService;
import ltdjms.discord.shop.services.ShopAdminNotificationService;

@ExtendWith(MockitoExtension.class)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Fiat order fulfillment snapshot integration test")
class FiatOrderFulfillmentSnapshotIntegrationTest {

  @Container
  @SuppressWarnings("resource")
  private static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("fiat_order_snapshot_test")
          .withUsername("test")
          .withPassword("test");

  private static final long TEST_GUILD_ID = 5566778899L;
  private static final long TEST_USER_ID = 1122334455L;
  private static final Instant FIXED_NOW = Instant.parse("2026-04-11T12:00:00Z");

  @Mock private EcpayCvsPaymentService ecpayCvsPaymentService;
  @Mock private ProductRewardService productRewardService;
  @Mock private EscortDispatchHandoffService escortDispatchHandoffService;
  @Mock private ShopAdminNotificationService adminNotificationService;
  @Mock private FiatOrderBuyerNotificationService buyerNotificationService;

  private HikariDataSource dataSource;
  private ProductService productService;
  private JdbcFiatOrderRepository fiatOrderRepository;
  private FiatOrderService fiatOrderService;
  private FiatOrderPostPaymentWorker worker;

  @BeforeEach
  void setUp() {
    dataSource = createDataSource();
    DatabaseMigrationRunner.forDefaultMigrations().migrate(dataSource);
    truncateBusinessTables(dataSource);

    productService =
        new ProductService(
            new JdbcProductRepository(dataSource),
            new JdbcRedemptionCodeRepository(dataSource),
            new DomainEventPublisher());
    fiatOrderRepository = new JdbcFiatOrderRepository(dataSource);
    MembershipPricingService membershipPricingService =
        new MembershipPricingService(new JdbcMembershipRepository(dataSource));
    fiatOrderService =
        new FiatOrderService(
            productService,
            ecpayCvsPaymentService,
            fiatOrderRepository,
            membershipPricingService);
    worker =
        new FiatOrderPostPaymentWorker(
            fiatOrderRepository,
            productRewardService,
            escortDispatchHandoffService,
            adminNotificationService,
            buyerNotificationService);
  }

  @AfterEach
  void tearDown() {
    if (dataSource != null && !dataSource.isClosed()) {
      dataSource.close();
    }
  }

  @Test
  @Timeout(60)
  @DisplayName("repository 應持久化法幣履約快照欄位")
  void shouldPersistFulfillmentSnapshotFields() {
    Product product = createSnapshotProduct();
    stubPaymentCode(product);

    Result<FiatOrderService.FiatOrderResult, DomainError> orderResult =
        fiatOrderService.createFiatOnlyOrder(TEST_GUILD_ID, TEST_USER_ID, product.id());

    assertThat(orderResult.isOk()).isTrue();
    FiatOrder savedOrder =
        fiatOrderRepository.findByOrderNumber(orderResult.getValue().orderNumber()).orElseThrow();

    assertThat(savedOrder.productName()).isEqualTo("法幣快照商品");
    assertThat(savedOrder.fulfillmentRewardType()).isEqualTo(Product.RewardType.CURRENCY);
    assertThat(savedOrder.fulfillmentRewardAmount()).isEqualTo(50L);
    assertThat(savedOrder.fulfillmentAutoCreateEscortOrder()).isTrue();
    assertThat(savedOrder.fulfillmentEscortOptionCode()).isEqualTo("CONF_DAM_300W");
    assertThat(savedOrder.toFulfillmentProduct().name()).isEqualTo("法幣快照商品");
  }

  @Test
  @Timeout(60)
  @DisplayName("商品被編輯並刪除後，paid worker 仍應 replay 訂單快照")
  void shouldReplaySnapshotAfterProductEditAndDelete() {
    Product originalProduct = createSnapshotProduct();
    stubPaymentCode(originalProduct);

    Result<FiatOrderService.FiatOrderResult, DomainError> orderResult =
        fiatOrderService.createFiatOnlyOrder(TEST_GUILD_ID, TEST_USER_ID, originalProduct.id());
    assertThat(orderResult.isOk()).isTrue();
    String orderNumber = orderResult.getValue().orderNumber();
    Product expectedSnapshot = orderResult.getValue().product();

    Result<Product, DomainError> updateResult =
        productService.updateProduct(
            originalProduct.id(),
            "法幣快照商品 v2",
            "updated",
            Product.RewardType.TOKEN,
            99L,
            null,
            1800L,
            false,
            null);
    assertThat(updateResult.isOk()).isTrue();

    assertThat(productService.deleteProduct(originalProduct.id()).isOk()).isTrue();
    assertThat(productService.getProduct(originalProduct.id())).isEmpty();

    fiatOrderRepository.markPaidIfPending(
        orderNumber, "1", "付款成功", "{\"MerchantTradeNo\":\"" + orderNumber + "\"}", FIXED_NOW);

    when(productRewardService.grantReward(any()))
        .thenReturn(Result.ok(new ProductRewardService.RewardGrantResult(50L, 150L, null)));
    EscortDispatchOrder dispatchOrder =
        EscortDispatchOrder.createAutoHandoff(
            "ESC-20260411-ABC123",
            TEST_GUILD_ID,
            0L,
            0L,
            TEST_USER_ID,
            EscortDispatchOrder.SourceType.FIAT_PAYMENT,
            orderNumber,
            expectedSnapshot.id(),
            expectedSnapshot.name(),
            expectedSnapshot.currencyPrice(),
            expectedSnapshot.fiatPriceTwd(),
            expectedSnapshot.escortOptionCode());
    when(escortDispatchHandoffService.handoffFromFiatPayment(
            eq(TEST_GUILD_ID), eq(TEST_USER_ID), any(Product.class), eq(orderNumber)))
        .thenReturn(Result.ok(dispatchOrder));

    worker.processPendingOrders();

    ArgumentCaptor<ProductRewardService.RewardGrantRequest> rewardCaptor =
        ArgumentCaptor.forClass(ProductRewardService.RewardGrantRequest.class);
    ArgumentCaptor<Product> handoffProductCaptor = ArgumentCaptor.forClass(Product.class);
    verify(productRewardService).grantReward(rewardCaptor.capture());
    assertThat(rewardCaptor.getValue().product())
        .usingRecursiveComparison()
        .ignoringFields("updatedAt")
        .isEqualTo(expectedSnapshot);
    assertThat(rewardCaptor.getValue().amount()).isEqualTo(50L);
    assertThat(rewardCaptor.getValue().currencySource())
        .isEqualTo(CurrencyTransaction.Source.PRODUCT_REWARD);
    assertThat(rewardCaptor.getValue().tokenSource())
        .isEqualTo(GameTokenTransaction.Source.PRODUCT_REWARD);

    verify(escortDispatchHandoffService)
        .handoffFromFiatPayment(
            eq(TEST_GUILD_ID), eq(TEST_USER_ID), handoffProductCaptor.capture(), eq(orderNumber));
    assertThat(handoffProductCaptor.getValue())
        .usingRecursiveComparison()
        .ignoringFields("updatedAt")
        .isEqualTo(expectedSnapshot);
    verify(adminNotificationService)
        .notifyAdminsOrderCreated(eq(TEST_GUILD_ID), eq(TEST_USER_ID), eq(dispatchOrder));
    verify(buyerNotificationService).notifyPaymentSucceeded(any());

    FiatOrder fulfilled = fiatOrderRepository.findByOrderNumber(orderNumber).orElseThrow();
    assertThat(fulfilled.isBuyerNotified()).isTrue();
    assertThat(fulfilled.isRewardGranted()).isTrue();
    assertThat(fulfilled.isFulfilled()).isTrue();
  }

  private Product createSnapshotProduct() {
    Result<Product, DomainError> result =
        productService.createProduct(
            TEST_GUILD_ID,
            "法幣快照商品",
            "desc",
            Product.RewardType.CURRENCY,
            50L,
            null,
            1200L,
            true,
            "CONF_DAM_300W");
    assertThat(result.isOk()).isTrue();
    return result.getValue();
  }

  private void stubPaymentCode(Product product) {
    when(ecpayCvsPaymentService.generateCvsPaymentCode(
            product.fiatPriceTwd(), product.name(), "Discord 商品下單 user:1122334455"))
        .thenReturn(
            Result.ok(
                new EcpayCvsPaymentService.CvsPaymentCode(
                    "FD260411000001",
                    "CVS123456789",
                    "2026/04/12 23:59:59",
                    Instant.parse("2026-04-12T15:59:59Z"),
                    "https://example.com/pay")));
  }

  private HikariDataSource createDataSource() {
    HikariConfig hikariConfig = new HikariConfig();
    hikariConfig.setJdbcUrl(postgres.getJdbcUrl());
    hikariConfig.setUsername(postgres.getUsername());
    hikariConfig.setPassword(postgres.getPassword());
    hikariConfig.setMaximumPoolSize(5);
    hikariConfig.setMinimumIdle(1);
    hikariConfig.setPoolName("FiatOrderSnapshotTestPool");
    return new HikariDataSource(hikariConfig);
  }

  private void truncateBusinessTables(DataSource ds) {
    try (Connection conn = ds.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("TRUNCATE TABLE redemption_code CASCADE");
      stmt.execute("TRUNCATE TABLE fiat_order CASCADE");
      stmt.execute("TRUNCATE TABLE product CASCADE");
    } catch (Exception e) {
      throw new RuntimeException("Failed to clean test tables", e);
    }
  }
}
