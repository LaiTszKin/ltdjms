package ltdjms.discord.membership.services;

import java.math.BigDecimal;

import ltdjms.discord.membership.domain.MembershipTier;

/** Resolved shop prices for escort-linked products with optional membership discount. */
public record EscortPriceQuote(
    long listPriceTwd,
    long chargedPriceTwd,
    long listCurrencyPrice,
    long chargedCurrencyPrice,
    MembershipTier appliedTier,
    BigDecimal discountRate) {

  public boolean hasFiatDiscount() {
    return listPriceTwd > 0 && chargedPriceTwd < listPriceTwd;
  }

  public boolean hasCurrencyDiscount() {
    return listCurrencyPrice > 0 && chargedCurrencyPrice < listCurrencyPrice;
  }

  public String formatFiatPriceLine() {
    if (!hasFiatDiscount()) {
      return String.format("NT$%,d", listPriceTwd);
    }
    return String.format(
        "會員價 NT$%,d（原價 NT$%,d）", chargedPriceTwd, listPriceTwd);
  }

  public String formatCurrencyPriceLine() {
    if (!hasCurrencyDiscount()) {
      return String.format("%,d 貨幣", listCurrencyPrice);
    }
    return String.format(
        "會員價 %,d 貨幣（原價 %,d 貨幣）", chargedCurrencyPrice, listCurrencyPrice);
  }
}
