package ltdjms.discord.membership.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ltdjms.discord.membership.domain.GlobalMemberMembership;
import ltdjms.discord.membership.domain.MembershipTier;
import ltdjms.discord.membership.persistence.MembershipRepository;
import ltdjms.discord.product.domain.Product;

@ExtendWith(MockitoExtension.class)
@DisplayName("MembershipPricingService 測試")
class MembershipPricingServiceTest {

  private static final long USER_ID = 42L;
  private static final long GUILD_ID = 1L;

  @Mock private MembershipRepository membershipRepository;

  private MembershipPricingService service;

  @BeforeEach
  void setUp() {
    service = new MembershipPricingService(membershipRepository);
  }

  @Test
  @DisplayName("非 escort 商品不套用折扣")
  void shouldNotDiscountNonEscortProduct() {
    Product product = fiatOnlyProduct(3500L, false, null);

    EscortPriceQuote quote = service.quoteEscortPrice(USER_ID, product, GUILD_ID);

    assertThat(quote.listPriceTwd()).isEqualTo(3500L);
    assertThat(quote.chargedPriceTwd()).isEqualTo(3500L);
    assertThat(quote.appliedTier()).isEqualTo(MembershipTier.NONE);
    assertThat(quote.discountRate()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  @DisplayName("無 membership 記錄時 NONE tier 不折扣")
  void shouldNotDiscountWhenNoMembershipRecord() {
    Product product = escortFiatProduct(3500L);
    when(membershipRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

    EscortPriceQuote quote = service.quoteEscortPrice(USER_ID, product, GUILD_ID);

    assertThat(quote.chargedPriceTwd()).isEqualTo(3500L);
    assertThat(quote.appliedTier()).isEqualTo(MembershipTier.NONE);
  }

  @Test
  @DisplayName("白銀會員 escort 法幣商品 9 折")
  void shouldApplySilverDiscountToEscortFiatProduct() {
    Product product = escortFiatProduct(3500L);
    when(membershipRepository.findByUserId(USER_ID))
        .thenReturn(Optional.of(membershipWithTier(MembershipTier.SILVER)));

    EscortPriceQuote quote = service.quoteEscortPrice(USER_ID, product, GUILD_ID);

    assertThat(quote.listPriceTwd()).isEqualTo(3500L);
    assertThat(quote.chargedPriceTwd()).isEqualTo(3150L);
    assertThat(quote.appliedTier()).isEqualTo(MembershipTier.SILVER);
    assertThat(quote.discountRate()).isEqualByComparingTo(new BigDecimal("0.10"));
  }

  @Test
  @DisplayName("黃金會員 escort 貨幣商品 85 折")
  void shouldApplyGoldDiscountToEscortCurrencyProduct() {
    Product product = escortCurrencyProduct(1000L);
    when(membershipRepository.findByUserId(USER_ID))
        .thenReturn(Optional.of(membershipWithTier(MembershipTier.GOLD)));

    EscortPriceQuote quote = service.quoteEscortPrice(USER_ID, product, GUILD_ID);

    assertThat(quote.listCurrencyPrice()).isEqualTo(1000L);
    assertThat(quote.chargedCurrencyPrice()).isEqualTo(850L);
    assertThat(quote.appliedTier()).isEqualTo(MembershipTier.GOLD);
  }

  @Test
  @DisplayName("各 tier 折扣四捨五入")
  void shouldRoundDiscountedPrices() {
    assertThat(MembershipPricingService.applyDiscount(999L, MembershipTier.BRONZE.discountRate()))
        .isEqualTo(949L);
    assertThat(MembershipPricingService.applyDiscount(3500L, MembershipTier.SILVER.discountRate()))
        .isEqualTo(3150L);
    assertThat(MembershipPricingService.applyDiscount(333L, MembershipTier.BLACK.discountRate()))
        .isEqualTo(233L);
  }

  private static Product fiatOnlyProduct(long fiatPrice, boolean escort, String optionCode) {
    return new Product(
        1L,
        GUILD_ID,
        "商品",
        null,
        null,
        null,
        null,
        fiatPrice,
        escort,
        optionCode,
        Instant.now(),
        Instant.now());
  }

  private static Product escortFiatProduct(long fiatPrice) {
    return fiatOnlyProduct(fiatPrice, true, "ESCORT-A");
  }

  private static Product escortCurrencyProduct(long currencyPrice) {
    return new Product(
        2L,
        GUILD_ID,
        "護航商品",
        null,
        null,
        null,
        currencyPrice,
        null,
        true,
        "ESCORT-B",
        Instant.now(),
        Instant.now());
  }

  private static GlobalMemberMembership membershipWithTier(MembershipTier tier) {
    Instant now = Instant.now();
    return new GlobalMemberMembership(
        USER_ID, tier, null, null, null, null, false, now, now);
  }
}
