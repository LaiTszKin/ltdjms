package ltdjms.discord.membership.services;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ltdjms.discord.membership.domain.MembershipTier;

@DisplayName("EscortPriceQuote 測試")
class EscortPriceQuoteTest {

  @Test
  @DisplayName("UT-01: formatFiatEmbedLine 有折扣時含劃線與折扣率")
  void formatFiatEmbedLineWithDiscountShouldIncludeStrikethroughAndRate() {
    EscortPriceQuote quote =
        new EscortPriceQuote(
            3500L, 3150L, 0L, 0L, MembershipTier.SILVER, MembershipTier.SILVER.discountRate());

    String line = quote.formatFiatEmbedLine();

    assertThat(line).contains("~~NT$3,500~~");
    assertThat(line).contains("NT$3,150");
    assertThat(line).contains("9 折");
  }

  @Test
  @DisplayName("UT-02: formatFiatEmbedLine 無折扣時不含劃線")
  void formatFiatEmbedLineWithoutDiscountShouldNotIncludeStrikethrough() {
    EscortPriceQuote quote =
        new EscortPriceQuote(
            3500L, 3500L, 0L, 0L, MembershipTier.NONE, BigDecimal.ZERO);

    String line = quote.formatFiatEmbedLine();

    assertThat(line).isEqualTo("NT$3,500");
    assertThat(line).doesNotContain("~~");
  }

  @Test
  @DisplayName("UT-03: formatFiatSelectDescription 長度 ≤ 100 且無 markdown")
  void formatFiatSelectDescriptionShouldBeCompactWithoutMarkdown() {
    EscortPriceQuote quote =
        new EscortPriceQuote(
            3500L, 3150L, 100L, 90L, MembershipTier.SILVER, MembershipTier.SILVER.discountRate());

    assertThat(quote.formatFiatSelectDescription()).hasSizeLessThanOrEqualTo(100);
    assertThat(quote.formatFiatSelectDescription()).doesNotContain("~~");
    assertThat(quote.formatCurrencySelectDescription()).hasSizeLessThanOrEqualTo(100);
    assertThat(quote.formatCurrencySelectDescription()).doesNotContain("~~");
  }

  @Test
  @DisplayName("formatFiatPriceLine 應 delegate 至 embed 版")
  void formatFiatPriceLineShouldDelegateToEmbedLine() {
    EscortPriceQuote quote =
        new EscortPriceQuote(
            3500L, 3150L, 0L, 0L, MembershipTier.SILVER, MembershipTier.SILVER.discountRate());

    assertThat(quote.formatFiatPriceLine()).isEqualTo(quote.formatFiatEmbedLine());
    assertThat(quote.formatCurrencyPriceLine()).isEqualTo(quote.formatCurrencyEmbedLine());
  }
}
