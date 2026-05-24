package ltdjms.discord.membership.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MembershipTierConfigTest {

  @Test
  @DisplayName("should expose finalized threshold list-price M values")
  void shouldExposeThresholds() {
    assertThat(MembershipTierConfig.thresholdListPriceTwd(MembershipTier.NONE)).isZero();
    assertThat(MembershipTierConfig.thresholdListPriceTwd(MembershipTier.BRONZE)).isEqualTo(500L);
    assertThat(MembershipTierConfig.thresholdListPriceTwd(MembershipTier.SILVER)).isEqualTo(14_000L);
    assertThat(MembershipTierConfig.thresholdListPriceTwd(MembershipTier.GOLD)).isEqualTo(33_000L);
    assertThat(MembershipTierConfig.thresholdListPriceTwd(MembershipTier.PLATINUM))
        .isEqualTo(100_000L);
    assertThat(MembershipTierConfig.thresholdListPriceTwd(MembershipTier.DIAMOND))
        .isEqualTo(120_000L);
    assertThat(MembershipTierConfig.thresholdListPriceTwd(MembershipTier.BLACK)).isEqualTo(250_000L);
  }

  @Test
  @DisplayName("should expose finalized discount rates")
  void shouldExposeDiscountRates() {
    assertThat(MembershipTierConfig.discountRate(MembershipTier.NONE))
        .isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(MembershipTierConfig.discountRate(MembershipTier.BRONZE))
        .isEqualByComparingTo(new BigDecimal("0.05"));
    assertThat(MembershipTierConfig.discountRate(MembershipTier.SILVER))
        .isEqualByComparingTo(new BigDecimal("0.10"));
    assertThat(MembershipTierConfig.discountRate(MembershipTier.GOLD))
        .isEqualByComparingTo(new BigDecimal("0.15"));
    assertThat(MembershipTierConfig.discountRate(MembershipTier.PLATINUM))
        .isEqualByComparingTo(new BigDecimal("0.20"));
    assertThat(MembershipTierConfig.discountRate(MembershipTier.DIAMOND))
        .isEqualByComparingTo(new BigDecimal("0.25"));
    assertThat(MembershipTierConfig.discountRate(MembershipTier.BLACK))
        .isEqualByComparingTo(new BigDecimal("0.30"));
  }

  @Test
  @DisplayName("should expose finalized monthly token grants")
  void shouldExposeMonthlyTokenGrants() {
    assertThat(MembershipTierConfig.monthlyTokenGrant(MembershipTier.NONE)).isZero();
    assertThat(MembershipTierConfig.monthlyTokenGrant(MembershipTier.BRONZE)).isZero();
    assertThat(MembershipTierConfig.monthlyTokenGrant(MembershipTier.SILVER)).isEqualTo(100);
    assertThat(MembershipTierConfig.monthlyTokenGrant(MembershipTier.GOLD)).isEqualTo(200);
    assertThat(MembershipTierConfig.monthlyTokenGrant(MembershipTier.PLATINUM)).isEqualTo(500);
    assertThat(MembershipTierConfig.monthlyTokenGrant(MembershipTier.DIAMOND)).isEqualTo(1_000);
    assertThat(MembershipTierConfig.monthlyTokenGrant(MembershipTier.BLACK)).isEqualTo(2_000);
  }
}
