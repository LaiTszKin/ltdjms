package ltdjms.discord.membership.services;

import java.math.BigDecimal;

import ltdjms.discord.membership.domain.MembershipTier;
import ltdjms.discord.membership.domain.MembershipTierLabels;

/** Resolved shop prices for escort-linked products with optional membership discount. */
public record EscortPriceQuote(
    long listPriceTwd,
    long chargedPriceTwd,
    long listCurrencyPrice,
    long chargedCurrencyPrice,
    MembershipTier appliedTier,
    BigDecimal discountRate) {

  private static final int SELECT_DESCRIPTION_MAX_LENGTH = 100;

  public boolean hasFiatDiscount() {
    return listPriceTwd > 0 && chargedPriceTwd < listPriceTwd;
  }

  public boolean hasCurrencyDiscount() {
    return listCurrencyPrice > 0 && chargedCurrencyPrice < listCurrencyPrice;
  }

  /** Embed: {@code ~~NT$3,500~~ NT$3,150（護航 9 折）} */
  public String formatFiatEmbedLine() {
    if (!hasFiatDiscount()) {
      return String.format("NT$%,d", listPriceTwd);
    }
    return String.format(
        "~~NT$%,d~~ NT$%,d（%s）",
        listPriceTwd, chargedPriceTwd, MembershipTierLabels.discountLabel(appliedTier));
  }

  /** Embed: {@code ~~100 貨幣~~ 90 貨幣（護航 9 折）} */
  public String formatCurrencyEmbedLine() {
    if (!hasCurrencyDiscount()) {
      return String.format("%,d 貨幣", listCurrencyPrice);
    }
    return String.format(
        "~~%,d 貨幣~~ %,d 貨幣（%s）",
        listCurrencyPrice, chargedCurrencyPrice, MembershipTierLabels.discountLabel(appliedTier));
  }

  /** Select menu: plain text, max 100 chars, no markdown. */
  public String formatFiatSelectDescription() {
    if (!hasFiatDiscount()) {
      return truncateSelectDescription(String.format("NT$%,d", listPriceTwd));
    }
    return truncateSelectDescription(
        String.format("NT$%,d %s", chargedPriceTwd, compactDiscountSuffix()));
  }

  /** Select menu: plain text, max 100 chars, no markdown. */
  public String formatCurrencySelectDescription() {
    if (!hasCurrencyDiscount()) {
      return truncateSelectDescription(String.format("%,d 貨幣", listCurrencyPrice));
    }
    return truncateSelectDescription(
        String.format("%,d 幣 %s", chargedCurrencyPrice, compactDiscountSuffix()));
  }

  public String formatFiatPriceLine() {
    return formatFiatEmbedLine();
  }

  public String formatCurrencyPriceLine() {
    return formatCurrencyEmbedLine();
  }

  private String compactDiscountSuffix() {
    String label = MembershipTierLabels.discountLabel(appliedTier);
    if ("無折扣".equals(label)) {
      return "";
    }
    return "(" + label.replace("護航 ", "").replace(" ", "") + ")";
  }

  private static String truncateSelectDescription(String text) {
    if (text.length() <= SELECT_DESCRIPTION_MAX_LENGTH) {
      return text;
    }
    return text.substring(0, SELECT_DESCRIPTION_MAX_LENGTH);
  }
}
