package ltdjms.discord.membership.services;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import ltdjms.discord.membership.domain.MembershipTier;
import ltdjms.discord.membership.domain.MembershipTierEvaluator;
import ltdjms.discord.membership.persistence.MembershipRepository;
import ltdjms.discord.product.domain.EscortProductRules;
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
    return quoteEscortPrice(product, resolveEffectiveTier(userId));
  }

  /**
   * Batch-quotes escort-linked products with a single membership tier lookup.
   *
   * @return map keyed by product id; non-escort products omitted
   */
  public Map<Long, EscortPriceQuote> quoteEscortPrices(
      long userId, List<Product> products, long guildId) {
    if (products == null || products.isEmpty()) {
      return Map.of();
    }

    MembershipTier tier = resolveEffectiveTier(userId);
    Map<Long, EscortPriceQuote> quotes = new HashMap<>();
    for (Product product : products) {
      if (!EscortProductRules.isEscortLinked(product) || product.id() == null) {
        continue;
      }
      quotes.put(product.id(), quoteEscortPrice(product, tier));
    }
    return quotes;
  }

  private MembershipTier resolveEffectiveTier(long userId) {
    return membershipRepository
        .findByUserId(userId)
        .map(
            membership ->
                MembershipTierEvaluator.effectiveTier(
                    membership.currentTier(), membership.hasQualifyingBronzeOrder()))
        .orElse(MembershipTier.NONE);
  }

  private EscortPriceQuote quoteEscortPrice(Product product, MembershipTier tier) {
    long listFiat = product.hasFiatPriceTwd() ? product.fiatPriceTwd() : 0L;
    long listCurrency = product.hasCurrencyPrice() ? product.currencyPrice() : 0L;

    if (!EscortProductRules.isEscortLinked(product)) {
      return new EscortPriceQuote(
          listFiat, listFiat, listCurrency, listCurrency, MembershipTier.NONE, BigDecimal.ZERO);
    }

    BigDecimal discountRate = tier.discountRate();
    if (discountRate.compareTo(BigDecimal.ZERO) == 0) {
      return new EscortPriceQuote(
          listFiat, listFiat, listCurrency, listCurrency, tier, BigDecimal.ZERO);
    }

    long chargedFiat = listFiat > 0 ? applyDiscount(listFiat, discountRate) : 0L;
    long chargedCurrency = listCurrency > 0 ? applyDiscount(listCurrency, discountRate) : 0L;

    return new EscortPriceQuote(
        listFiat, chargedFiat, listCurrency, chargedCurrency, tier, discountRate);
  }

  static long applyDiscount(long listPrice, BigDecimal discountRate) {
    double multiplier = BigDecimal.ONE.subtract(discountRate).doubleValue();
    return Math.round(listPrice * multiplier);
  }
}
