package ltdjms.discord.membership.services;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ltdjms.discord.membership.domain.MembershipTier;
import ltdjms.discord.product.domain.Product;

@DisplayName("EscortPriceQuote 測試")
class EscortPriceQuoteTest {

  private static final long GUILD_ID = 1L;

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
  @DisplayName("UT-03: formatSelectDescription 長度 ≤ 100 且無 markdown")
  void formatSelectDescriptionShouldBeCompactWithoutMarkdown() {
    EscortPriceQuote quote =
        new EscortPriceQuote(
            3500L, 3150L, 100L, 90L, MembershipTier.SILVER, MembershipTier.SILVER.discountRate());
    Product product = escortProduct(100L, 3500L);

    String description = quote.formatSelectDescription(product);

    assertThat(description).hasSizeLessThanOrEqualTo(100);
    assertThat(description).doesNotContain("~~");
    assertThat(description).contains("9折");
  }

  @Test
  @DisplayName("formatSelectDescription 有空間時顯示原價")
  void formatSelectDescriptionShouldIncludeOriginalPriceWhenSpaceAllows() {
    EscortPriceQuote quote =
        new EscortPriceQuote(
            0L, 0L, 100L, 90L, MembershipTier.SILVER, MembershipTier.SILVER.discountRate());
    Product product = escortProduct(100L, null);

    assertThat(quote.formatSelectDescription(product)).contains("(原100,9折)");
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

  private static Product escortProduct(Long currencyPrice, Long fiatPrice) {
    Instant now = Instant.now();
    return new Product(
        1L,
        GUILD_ID,
        "護航商品",
        null,
        null,
        null,
        currencyPrice,
        fiatPrice,
        true,
        "ESCORT-A",
        now,
        now);
  }
}
