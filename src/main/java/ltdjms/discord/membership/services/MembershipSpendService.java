package ltdjms.discord.membership.services;

import java.time.Instant;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.membership.domain.MembershipTier;
import ltdjms.discord.membership.persistence.MembershipRepository;
import ltdjms.discord.membership.persistence.MembershipSpendRepository;
import ltdjms.discord.product.domain.EscortOptionCatalogRepository;
import ltdjms.discord.product.domain.EscortProductRules;
import ltdjms.discord.product.domain.Product;
import ltdjms.discord.shop.domain.FiatOrder;

/** Records escort fiat payment catalog list prices into the global spend ledger. */
public class MembershipSpendService {

  public static final String SOURCE_TYPE_FIAT_ORDER = "FIAT_ORDER";

  private static final Logger LOG = LoggerFactory.getLogger(MembershipSpendService.class);

  private final MembershipSpendRepository spendRepository;
  private final MembershipRepository membershipRepository;
  private final EscortOptionCatalogRepository catalogRepository;

  public MembershipSpendService(
      MembershipSpendRepository spendRepository,
      MembershipRepository membershipRepository,
      EscortOptionCatalogRepository catalogRepository) {
    this.spendRepository = Objects.requireNonNull(spendRepository);
    this.membershipRepository = Objects.requireNonNull(membershipRepository);
    this.catalogRepository = Objects.requireNonNull(catalogRepository);
  }

  /**
   * Records catalog list price M for a paid escort-linked fiat order. Best-effort: failures are
   * logged and do not propagate to callers.
   */
  public void recordFiatEscortPayment(FiatOrder order, Product product) {
    try {
      if (!order.isPaid()) {
        return;
      }
      if (!EscortProductRules.isEscortLinked(product)) {
        return;
      }
      if (order.paidAt() == null) {
        LOG.warn(
            "Skipping membership spend for paid order without paidAt: orderNumber={}",
            order.orderNumber());
        return;
      }

      long listPriceM = resolveListPriceM(product, order.guildId());
      if (listPriceM <= 0) {
        LOG.warn(
            "Skipping membership spend with non-positive list price: orderNumber={}, listPriceM={}",
            order.orderNumber(),
            listPriceM);
        return;
      }

      membershipRepository.findOrCreate(order.buyerUserId());

      boolean inserted =
          spendRepository.insertSpendAndQualifyBronzeIfThreshold(
              order.buyerUserId(),
              order.guildId(),
              listPriceM,
              product.escortOptionCode(),
              SOURCE_TYPE_FIAT_ORDER,
              order.orderNumber(),
              order.paidAt(),
              MembershipTier.BRONZE.thresholdListPriceTwd());

      if (inserted) {
        ensureSettlementAnchor(order.buyerUserId(), order.paidAt());
      }
    } catch (Exception e) {
      LOG.error("Failed to record membership spend for orderNumber={}", order.orderNumber(), e);
    }
  }

  /** Returns total catalog list price M for a user within {@code [from, to)}. */
  public long sumListPriceInPeriod(long discordUserId, Instant from, Instant to) {
    return spendRepository.sumListPriceInPeriod(discordUserId, from, to);
  }

  /**
   * Resolves catalog list price M for membership spend. Uses global catalog default price, not
   * guild overrides or discounted charged amounts.
   */
  public long resolveListPriceM(Product product, long guildId) {
    Objects.requireNonNull(product, "product must not be null");
    if (product.escortOptionCode() != null && !product.escortOptionCode().isBlank()) {
      return catalogRepository
          .findByCode(product.escortOptionCode())
          .map(entry -> entry.priceTwd())
          .orElseGet(
              () -> {
                LOG.warn(
                    "Escort catalog code not found for membership spend: code={}, guildId={}",
                    product.escortOptionCode(),
                    guildId);
                return fallbackListPrice(product);
              });
    }
    return fallbackListPrice(product);
  }

  private void ensureSettlementAnchor(long discordUserId, Instant paidAt) {
    int settlementDay =
        MembershipJoinService.clampDayOfMonth(paidAt, MembershipJoinService.SETTLEMENT_ZONE);
    membershipRepository.ensureSettlementAnchor(discordUserId, paidAt, settlementDay);
  }

  private static long fallbackListPrice(Product product) {
    Long fiatPrice = product.fiatPriceTwd();
    return fiatPrice == null ? 0L : fiatPrice;
  }
}
