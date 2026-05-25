package ltdjms.discord.membership.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ltdjms.discord.membership.domain.GlobalMemberMembership;
import ltdjms.discord.membership.domain.MembershipTier;
import ltdjms.discord.membership.persistence.MembershipRepository;
import ltdjms.discord.membership.persistence.MembershipSpendRepository;
import ltdjms.discord.membership.persistence.SpendRecordResult;
import ltdjms.discord.product.domain.EscortOptionCatalog;
import ltdjms.discord.product.domain.EscortOptionCatalogRepository;
import ltdjms.discord.product.domain.Product;
import ltdjms.discord.shared.events.DomainEventPublisher;
import ltdjms.discord.shared.events.MembershipTierChangedEvent;
import ltdjms.discord.shop.domain.FiatOrder;
import ltdjms.discord.shop.services.PaidEscortOrderSnapshots;

@ExtendWith(MockitoExtension.class)
class MembershipSpendServiceTest {

  private static final long GUILD_ID = 111L;
  private static final long BUYER_ID = 222L;
  private static final Instant PAID_AT = Instant.parse("2026-04-11T10:00:00Z");
  private static final Instant EXPIRE_AT = PAID_AT.plusSeconds(600);

  @Mock private MembershipSpendRepository spendRepository;
  @Mock private MembershipRepository membershipRepository;
  @Mock private EscortOptionCatalogRepository catalogRepository;
  @Mock private DomainEventPublisher eventPublisher;

  private MembershipSpendService service;

  @BeforeEach
  void setUp() {
    service =
        new MembershipSpendService(
            spendRepository, membershipRepository, catalogRepository, eventPublisher);
  }

  @Nested
  @DisplayName("resolveListPriceM")
  class ResolveListPriceMTests {

    @Test
    @DisplayName("should use catalog default price when escort option code exists")
    void shouldUseCatalogPrice() {
      when(catalogRepository.findByCode("CONF_DAM_300W"))
          .thenReturn(java.util.Optional.of(catalogEntry("CONF_DAM_300W", 3500L)));

      assertThat(service.resolveListPriceM("CONF_DAM_300W", GUILD_ID, null, 900L)).isEqualTo(3500L);
    }

    @Test
    @DisplayName("should prefer product fiat price when catalog code is missing")
    void shouldPreferProductPriceWhenCatalogMissing() {
      when(catalogRepository.findByCode("MISSING_CODE")).thenReturn(java.util.Optional.empty());

      assertThat(service.resolveListPriceM("MISSING_CODE", GUILD_ID, 3500L, 1200L))
          .isEqualTo(1200L);
    }

    @Test
    @DisplayName("should fallback to product fiat price when catalog and order list price missing")
    void shouldFallbackWhenCatalogMissing() {
      when(catalogRepository.findByCode("MISSING_CODE")).thenReturn(java.util.Optional.empty());

      assertThat(service.resolveListPriceM("MISSING_CODE", GUILD_ID, null, 1200L)).isEqualTo(1200L);
    }

    @Test
    @DisplayName("should use product fiat price when no escort option code")
    void shouldUseFiatPriceWithoutOptionCode() {
      when(catalogRepository.findByCode("AUTO_ONLY")).thenReturn(java.util.Optional.empty());

      assertThat(service.resolveListPriceM("AUTO_ONLY", GUILD_ID, null, 800L)).isEqualTo(800L);
    }
  }

  @Nested
  @DisplayName("recordPaidEscortOrder")
  class RecordPaidEscortOrderTests {

    @Test
    @DisplayName("should insert spend entry with catalog list price for escort fiat order")
    void shouldRecordEscortSpend() {
      Product product = escortProduct("CONF_DAM_300W", 900L);
      FiatOrder order = paidEscortOrder(product, 900L, 3500L);
      PaidEscortOrderSnapshot snapshot = PaidEscortOrderSnapshots.fromFiatOrder(order, product);
      when(catalogRepository.findByCode("CONF_DAM_300W"))
          .thenReturn(java.util.Optional.of(catalogEntry("CONF_DAM_300W", 3500L)));
      when(membershipRepository.findOrCreate(BUYER_ID))
          .thenReturn(GlobalMemberMembership.createNew(BUYER_ID));
      when(spendRepository.insertSpendAndQualifyBronzeIfThreshold(
              BUYER_ID,
              GUILD_ID,
              3500L,
              "CONF_DAM_300W",
              MembershipSpendService.SOURCE_TYPE_FIAT_ORDER,
              order.orderNumber(),
              PAID_AT,
              MembershipTier.BRONZE.thresholdListPriceTwd()))
          .thenReturn(new SpendRecordResult(true, false));

      assertThat(service.recordPaidEscortOrder(snapshot)).isTrue();

      verify(membershipRepository).findOrCreate(BUYER_ID);
    }

    @Test
    @DisplayName("should publish tier changed event when bronze promotion succeeds")
    void shouldPublishBronzePromotionEvent() {
      Product product = escortProduct("CONF_DAM_300W", 3500L);
      FiatOrder order = paidEscortOrder(product, 3500L, 3500L);
      PaidEscortOrderSnapshot snapshot = PaidEscortOrderSnapshots.fromFiatOrder(order, product);
      when(catalogRepository.findByCode("CONF_DAM_300W"))
          .thenReturn(java.util.Optional.of(catalogEntry("CONF_DAM_300W", 3500L)));
      when(membershipRepository.findOrCreate(BUYER_ID))
          .thenReturn(GlobalMemberMembership.createNew(BUYER_ID));
      when(spendRepository.insertSpendAndQualifyBronzeIfThreshold(
              anyLong(), anyLong(), anyLong(), any(), anyString(), anyString(), any(), anyLong()))
          .thenReturn(new SpendRecordResult(true, true));

      assertThat(service.recordPaidEscortOrder(snapshot)).isTrue();

      ArgumentCaptor<MembershipTierChangedEvent> captor =
          ArgumentCaptor.forClass(MembershipTierChangedEvent.class);
      verify(eventPublisher).publish(captor.capture());
      assertThat(captor.getValue().previousTierCode()).isEqualTo(MembershipTier.NONE.name());
      assertThat(captor.getValue().currentTierCode()).isEqualTo(MembershipTier.BRONZE.name());
    }

    @Test
    @DisplayName("should not set bronze flag when list price is below threshold")
    void shouldNotSetBronzeForSmallListPrice() {
      Product product = escortProduct("SMALL", 400L);
      FiatOrder order = paidEscortOrder(product, 400L, 400L);
      PaidEscortOrderSnapshot snapshot = PaidEscortOrderSnapshots.fromFiatOrder(order, product);
      when(catalogRepository.findByCode("SMALL"))
          .thenReturn(java.util.Optional.of(catalogEntry("SMALL", 400L)));
      when(membershipRepository.findOrCreate(BUYER_ID))
          .thenReturn(GlobalMemberMembership.createNew(BUYER_ID));
      when(spendRepository.insertSpendAndQualifyBronzeIfThreshold(
              eq(BUYER_ID),
              eq(GUILD_ID),
              eq(400L),
              eq("SMALL"),
              eq(MembershipSpendService.SOURCE_TYPE_FIAT_ORDER),
              eq(order.orderNumber()),
              eq(PAID_AT),
              eq(MembershipTier.BRONZE.thresholdListPriceTwd())))
          .thenReturn(new SpendRecordResult(true, false));

      service.recordPaidEscortOrder(snapshot);

      verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("should skip duplicate spend entries")
    void shouldSkipDuplicateSpendEntry() {
      Product product = escortProduct("CONF_DAM_300W", 3500L);
      FiatOrder order = paidEscortOrder(product, 3500L, 3500L);
      PaidEscortOrderSnapshot snapshot = PaidEscortOrderSnapshots.fromFiatOrder(order, product);
      when(catalogRepository.findByCode("CONF_DAM_300W"))
          .thenReturn(java.util.Optional.of(catalogEntry("CONF_DAM_300W", 3500L)));
      when(membershipRepository.findOrCreate(BUYER_ID))
          .thenReturn(GlobalMemberMembership.createNew(BUYER_ID));
      when(spendRepository.insertSpendAndQualifyBronzeIfThreshold(
              eq(BUYER_ID),
              eq(GUILD_ID),
              eq(3500L),
              eq("CONF_DAM_300W"),
              eq(MembershipSpendService.SOURCE_TYPE_FIAT_ORDER),
              eq(order.orderNumber()),
              eq(PAID_AT),
              eq(MembershipTier.BRONZE.thresholdListPriceTwd())))
          .thenReturn(new SpendRecordResult(false, false));

      assertThat(service.recordPaidEscortOrder(snapshot)).isTrue();
    }

    @Test
    @DisplayName("should not record spend for non escort-linked products")
    void shouldSkipNonEscortProduct() {
      Product product = Product.createWithFiatPriceTwd(GUILD_ID, "一般商品", "desc", 500L);
      FiatOrder order = paidEscortOrder(product, 500L, 500L);
      PaidEscortOrderSnapshot snapshot = PaidEscortOrderSnapshots.fromFiatOrder(order, product);

      assertThat(service.recordPaidEscortOrder(snapshot)).isTrue();

      verify(spendRepository, never())
          .insertSpendAndQualifyBronzeIfThreshold(
              anyLong(), anyLong(), anyLong(), any(), anyString(), anyString(), any(), anyLong());
    }

    @Test
    @DisplayName("should return false when repository throws")
    void shouldReturnFalseOnRepositoryFailure() {
      Product product = escortProduct("CONF_DAM_300W", 3500L);
      FiatOrder order = paidEscortOrder(product, 3500L, 3500L);
      PaidEscortOrderSnapshot snapshot = PaidEscortOrderSnapshots.fromFiatOrder(order, product);
      when(catalogRepository.findByCode("CONF_DAM_300W"))
          .thenReturn(java.util.Optional.of(catalogEntry("CONF_DAM_300W", 3500L)));
      when(membershipRepository.findOrCreate(BUYER_ID))
          .thenReturn(GlobalMemberMembership.createNew(BUYER_ID));
      when(spendRepository.insertSpendAndQualifyBronzeIfThreshold(
              anyLong(), anyLong(), anyLong(), any(), anyString(), anyString(), any(), anyLong()))
          .thenThrow(new RuntimeException("db down"));

      assertThat(service.recordPaidEscortOrder(snapshot)).isFalse();
    }
  }

  @Test
  @DisplayName("bronze threshold should match tier config")
  void bronzeThresholdShouldMatchTierConfig() {
    assertThat(MembershipTier.BRONZE.thresholdListPriceTwd()).isEqualTo(500L);
  }

  private static Product escortProduct(String optionCode, long fiatPriceTwd) {
    return Product.create(
        GUILD_ID, "護航商品", "desc", null, null, null, fiatPriceTwd, true, optionCode);
  }

  private static EscortOptionCatalog catalogEntry(String code, long priceTwd) {
    return EscortOptionCatalog.create(code, "包本單", "機密護", "不限", "目標", priceTwd);
  }

  private static FiatOrder paidEscortOrder(Product product, long chargedAmount, Long listPriceTwd) {
    return new FiatOrder(
        1L,
        GUILD_ID,
        BUYER_ID,
        product.id() == null ? 99L : product.id(),
        product.name(),
        null,
        null,
        product.autoCreateEscortOrder(),
        product.escortOptionCode(),
        "FD260411000001",
        "CVS123456",
        chargedAmount,
        listPriceTwd,
        chargedAmount,
        null,
        FiatOrder.Status.PAID,
        "1",
        "付款成功",
        PAID_AT,
        EXPIRE_AT,
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
  }
}
