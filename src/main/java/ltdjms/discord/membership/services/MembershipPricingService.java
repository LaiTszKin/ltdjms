package ltdjms.discord.membership.services;

import java.math.BigDecimal;
import java.util.Objects;

import ltdjms.discord.membership.domain.MembershipTier;
import ltdjms.discord.membership.persistence.MembershipRepository;
import ltdjms.discord.product.domain.Product;

/** Applies membership tier discounts to escort-linked shop products at payment time. */
public class MembershipPricingService {

  private final MembershipRepository membershipRepository;

  public MembershipPricingService(MembershipRepository membershipRepository) {
    this.membershipRepository =
        Objects.requireNonNull(membershipRepository, "membershipRepository must not be null");
  }

  /**
   * Quotes list and charged prices for a shop product based on the buyer's current tier.
   *
   * @param userId Discord user ID
   * @param product shop product
   * @param guildId Discord guild ID (reserved for future guild-scoped pricing)
   * @return price quote with discount applied only for escort-linked products
   */
  public EscortPriceQuote quoteEscortPrice(long userId, Product product, long guildId) {
    Objects.requireNonNull(product, "product must not be null");

    long listFiat = product.hasFiatPriceTwd() ? product.fiatPriceTwd() : 0L;
    long listCurrency = product.hasCurrencyPrice() ? product.currencyPrice() : 0L;

    if (!isEscortLinked(product)) {
      return new EscortPriceQuote(
          listFiat,
          listFiat,
          listCurrency,
          listCurrency,
          MembershipTier.NONE,
          BigDecimal.ZERO);
    }

    MembershipTier tier =
        membershipRepository
            .findByUserId(userId)
            .map(membership -> membership.currentTier())
            .orElse(MembershipTier.NONE);

    BigDecimal discountRate = tier.discountRate();
    if (discountRate.compareTo(BigDecimal.ZERO) == 0) {
      return new EscortPriceQuote(
          listFiat, listFiat, listCurrency, listCurrency, tier, BigDecimal.ZERO);
    }

    long chargedFiat = listFiat > 0 ? applyDiscount(listFiat, discountRate) : 0L;
    long chargedCurrency =
        listCurrency > 0 ? applyDiscount(listCurrency, discountRate) : 0L;

    return new EscortPriceQuote(
        listFiat, chargedFiat, listCurrency, chargedCurrency, tier, discountRate);
  }

  private static boolean isEscortLinked(Product product) {
    return product.shouldAutoCreateEscortOrder();
  }

  static long applyDiscount(long listPrice, BigDecimal discountRate) {
    double multiplier = BigDecimal.ONE.subtract(discountRate).doubleValue();
    return Math.round(listPrice * multiplier);
  }
}
