package ltdjms.discord.membership.integration;

import static org.assertj.core.api.Assertions.assertThat;
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

import ltdjms.discord.dispatch.persistence.JdbcEscortDispatchOrderRepository;
import ltdjms.discord.dispatch.services.EscortDispatchHandoffService;
import ltdjms.discord.membership.persistence.JdbcMembershipRepository;
import ltdjms.discord.membership.persistence.JdbcMembershipSpendRepository;
import ltdjms.discord.membership.persistence.JdbcMembershipSpendRetryRepository;
import ltdjms.discord.membership.services.MembershipSpendRetryService;
import ltdjms.discord.membership.services.MembershipSpendService;
import ltdjms.discord.product.domain.EscortOptionCatalog;
import ltdjms.discord.product.domain.Product;
import ltdjms.discord.product.persistence.JdbcEscortOptionCatalogRepository;
import ltdjms.discord.product.persistence.JdbcProductRepository;
import ltdjms.discord.product.services.ProductRewardService;
import ltdjms.discord.product.services.ProductService;
import ltdjms.discord.redemption.persistence.JdbcRedemptionCodeRepository;
import ltdjms.discord.shared.DatabaseMigrationRunner;
import ltdjms.discord.shared.events.DomainEventPublisher;
import ltdjms.discord.shop.domain.FiatOrder;
import ltdjms.discord.shop.persistence.JdbcFiatOrderRepository;
import ltdjms.discord.shop.services.EscortOrderBuyerNotificationService;
import ltdjms.discord.shop.services.FiatOrderBuyerNotificationService;
import ltdjms.discord.shop.services.FiatOrderPostPaymentWorker;
import ltdjms.discord.shop.services.ShopAdminNotificationService;

@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Membership spend recording integration")
class MembershipSpendRecordingIntegrationTest {

  private static final Instant PAID_AT = Instant.parse("2026-04-11T10:00:00Z");

  @Container
  @SuppressWarnings("resource")
  private static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("membership_spend_test")
          .withUsername("test")
          .withPassword("test");

  private HikariDataSource dataSource;
  private ProductService productService;
  private JdbcFiatOrderRepository fiatOrderRepository;
  private JdbcMembershipSpendRepository spendRepository;
  private JdbcMembershipRepository membershipRepository;
  private JdbcEscortOptionCatalogRepository catalogRepository;
  private FiatOrderPostPaymentWorker worker;

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
    spendRepository = new JdbcMembershipSpendRepository(dataSource);
    membershipRepository = new JdbcMembershipRepository(dataSource);
    catalogRepository = new JdbcEscortOptionCatalogRepository(dataSource);

    catalogRepository.save(
        EscortOptionCatalog.create("CONF_DAM_300W", "包本單", "機密護", "不限", "目標", 3500L));

    MembershipSpendService membershipSpendService =
        new MembershipSpendService(
            spendRepository,
            membershipRepository,
            catalogRepository,
            new DomainEventPublisher());
    MembershipSpendRetryService spendRetryService =
        new MembershipSpendRetryService(
            new JdbcMembershipSpendRetryRepository(dataSource),
            fiatOrderRepository,
            membershipSpendService);

    worker =
        new FiatOrderPostPaymentWorker(
            fiatOrderRepository,
            mock(ProductRewardService.class),
            new EscortDispatchHandoffService(new JdbcEscortDispatchOrderRepository(dataSource)),
            mock(ShopAdminNotificationService.class),
            mock(FiatOrderBuyerNotificationService.class),
            mock(EscortOrderBuyerNotificationService.class),
            membershipSpendService,
            spendRetryService);
  }

  @AfterEach
  void tearDown() {
    if (dataSource != null && !dataSource.isClosed()) {
      dataSource.close();
    }
  }

  @Test
  @DisplayName("should record escort fiat spend once and mark bronze qualifying order")
  void shouldRecordSpendIdempotently() {
    Product product = createAutoEscortProduct(123456789L, "法幣護航商品", null, 3150L, "CONF_DAM_300W");
    FiatOrder order =
        savePaidOrder(
            123456789L, 987654321L, product.id(), product.name(), "FD260411000001", 3150L);

    worker.processPendingOrders();
    worker.processPendingOrders();

    assertThat(
            spendRepository.sumListPriceInPeriod(
                order.buyerUserId(), PAID_AT.minusSeconds(1), PAID_AT.plusSeconds(1)))
        .isEqualTo(3500L);
    assertThat(
            membershipRepository
                .findByUserId(order.buyerUserId())
                .orElseThrow()
                .hasQualifyingBronzeOrder())
        .isTrue();
    assertThat(
            fiatOrderRepository.findByOrderNumber(order.orderNumber()).orElseThrow().isFulfilled())
        .isTrue();
  }

  private HikariDataSource createDataSource() {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(postgres.getJdbcUrl());
    config.setUsername(postgres.getUsername());
    config.setPassword(postgres.getPassword());
    config.setMaximumPoolSize(5);
    config.setMinimumIdle(1);
    config.setPoolName("MembershipSpendIntegrationPool");
    return new HikariDataSource(config);
  }

  private void truncateTables(DataSource ds) {
    try (Connection conn = ds.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("TRUNCATE TABLE membership_spend_entry CASCADE");
      stmt.execute("TRUNCATE TABLE global_member_membership CASCADE");
      stmt.execute("TRUNCATE TABLE escort_dispatch_order CASCADE");
      stmt.execute("TRUNCATE TABLE fiat_order CASCADE");
      stmt.execute("TRUNCATE TABLE product CASCADE");
      stmt.execute("TRUNCATE TABLE redemption_code CASCADE");
      stmt.execute("TRUNCATE TABLE escort_option_catalog CASCADE");
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
            null,
            FiatOrder.Status.PAID,
            "1",
            "付款成功",
            PAID_AT,
            PAID_AT.plusSeconds(86400),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            0,
            null,
            PAID_AT,
            PAID_AT);
    return fiatOrderRepository.save(order);
  }
}
