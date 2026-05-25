package ltdjms.discord.membership.services;

import java.math.BigDecimal;

import ltdjms.discord.membership.domain.MembershipTier;
import ltdjms.discord.membership.domain.MembershipTierLabels;
import ltdjms.discord.product.domain.EscortProductRules;
import ltdjms.discord.product.domain.Product;

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
  public String formatSelectDescription(Product product) {
    if (product == null || !EscortProductRules.isEscortLinked(product)) {
      return formatUndiscountedSelectDescription(product);
    }
    if (!hasFiatDiscount() && !hasCurrencyDiscount()) {
      return formatUndiscountedSelectDescription(product);
    }

    StringBuilder sb = new StringBuilder();
    if (product.hasCurrencyPrice()) {
      if (hasCurrencyDiscount()) {
        sb.append(String.format("%,d 幣", chargedCurrencyPrice));
      } else {
        sb.append(product.formatCurrencyPrice());
      }
    }
    if (product.hasFiatPriceTwd()) {
      if (!sb.isEmpty()) {
        sb.append("/");
      }
      if (hasFiatDiscount()) {
        sb.append(String.format("NT$%,d", chargedPriceTwd));
      } else {
        sb.append(product.formatFiatPriceTwd());
      }
    }

    String originalPart = formatOriginalPricePart(product);
    if (!originalPart.isEmpty() && sb.length() + originalPart.length() <= SELECT_DESCRIPTION_MAX_LENGTH) {
      sb.append(originalPart);
    } else {
      String discountSuffix = compactDiscountSuffix();
      if (!discountSuffix.isEmpty()) {
        sb.append(" ").append(discountSuffix);
      }
    }

    return truncateSelectDescription(sb.toString());
  }

  public String formatFiatPriceLine() {
    return formatFiatEmbedLine();
  }

  public String formatCurrencyPriceLine() {
    return formatCurrencyEmbedLine();
  }

  private String formatUndiscountedSelectDescription(Product product) {
    if (product == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    if (product.hasCurrencyPrice()) {
      sb.append(product.formatCurrencyPrice());
    }
    if (product.hasFiatPriceTwd()) {
      if (!sb.isEmpty()) {
        sb.append("/");
      }
      sb.append(product.formatFiatPriceTwd());
    }
    return truncateSelectDescription(sb.toString());
  }

  private String formatOriginalPricePart(Product product) {
    StringBuilder part = new StringBuilder(" (");
    boolean first = true;
    if (hasCurrencyDiscount() && product.hasCurrencyPrice()) {
      part.append("原").append(String.format("%,d", listCurrencyPrice));
      first = false;
    }
    if (hasFiatDiscount() && product.hasFiatPriceTwd()) {
      if (!first) {
        part.append(",");
      }
      part.append("原NT$").append(String.format("%,d", listPriceTwd));
    }
    String discountLabel = MembershipTierLabels.discountLabel(appliedTier);
    if (!"無折扣".equals(discountLabel)) {
      part.append(",").append(discountLabel.replace("護航 ", "").replace(" ", ""));
    }
    part.append(")");
    return part.toString();
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
