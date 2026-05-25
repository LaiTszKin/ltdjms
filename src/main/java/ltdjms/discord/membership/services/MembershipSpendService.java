package ltdjms.discord.membership.services;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.membership.domain.MembershipTier;
import ltdjms.discord.membership.persistence.MembershipRepository;
import ltdjms.discord.membership.persistence.MembershipSpendRepository;
import ltdjms.discord.membership.persistence.SpendRecordResult;
import ltdjms.discord.product.domain.EscortOptionCatalogRepository;
import ltdjms.discord.product.domain.EscortProductRules;
import ltdjms.discord.product.domain.Product;
import ltdjms.discord.shared.events.DomainEventPublisher;
import ltdjms.discord.shared.events.MembershipTierChangedEvent;
import ltdjms.discord.shop.domain.FiatOrder;

/** Records escort fiat payment catalog list prices into the global spend ledger. */
public class MembershipSpendService {

  public static final String SOURCE_TYPE_FIAT_ORDER = "FIAT_ORDER";

  private static final Logger LOG = LoggerFactory.getLogger(MembershipSpendService.class);

  private final MembershipSpendRepository spendRepository;
  private final MembershipRepository membershipRepository;
  private final EscortOptionCatalogRepository catalogRepository;
  private final DomainEventPublisher eventPublisher;

  public MembershipSpendService(
      MembershipSpendRepository spendRepository,
      MembershipRepository membershipRepository,
      EscortOptionCatalogRepository catalogRepository,
      DomainEventPublisher eventPublisher) {
    this.spendRepository = Objects.requireNonNull(spendRepository);
    this.membershipRepository = Objects.requireNonNull(membershipRepository);
    this.catalogRepository = Objects.requireNonNull(catalogRepository);
    this.eventPublisher = Objects.requireNonNull(eventPublisher);
  }

  /**
   * Records catalog list price M for a paid escort-linked fiat order.
   *
   * @return {@code true} when spend was recorded, intentionally skipped, or already present;
   *     {@code false} on persistence failure
   */
  public boolean recordFiatEscortPayment(FiatOrder order, Product product) {
    if (!order.isPaid()) {
      return true;
    }
    if (!EscortProductRules.isEscortLinked(product)) {
      return true;
    }
    if (order.paidAt() == null) {
      LOG.warn(
          "Skipping membership spend for paid order without paidAt: orderNumber={}",
          order.orderNumber());
      return false;
    }

    try {
      long listPriceM = resolveListPriceM(product, order.guildId(), order.listPriceTwd());
      if (listPriceM <= 0) {
        LOG.warn(
            "Skipping membership spend with non-positive list price: orderNumber={}, listPriceM={}",
            order.orderNumber(),
            listPriceM);
        return false;
      }

      membershipRepository.findOrCreate(order.buyerUserId());

      SpendRecordResult result =
          spendRepository.insertSpendAndQualifyBronzeIfThreshold(
              order.buyerUserId(),
              order.guildId(),
              listPriceM,
              product.escortOptionCode(),
              SOURCE_TYPE_FIAT_ORDER,
              order.orderNumber(),
              order.paidAt(),
              MembershipTier.BRONZE.thresholdListPriceTwd());

      if (result.inserted() && result.bronzePromoted()) {
        eventPublisher.publish(
            new MembershipTierChangedEvent(
                order.buyerUserId(),
                MembershipTier.NONE,
                MembershipTier.BRONZE,
                listPriceM,
                order.paidAt()));
      }

      return true;
    } catch (Exception e) {
      LOG.error("Failed to record membership spend for orderNumber={}", order.orderNumber(), e);
      return false;
    }
  }

  /**
   * Resolves catalog list price M for membership spend. Uses global catalog default price, not
   * guild overrides or discounted charged amounts.
   */
  public long resolveListPriceM(Product product, long guildId, Long orderListPriceTwd) {
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
                return fallbackListPrice(orderListPriceTwd, product);
              });
    }
    return fallbackListPrice(orderListPriceTwd, product);
  }

  private static long fallbackListPrice(Long orderListPriceTwd, Product product) {
    if (orderListPriceTwd != null && orderListPriceTwd > 0) {
      return orderListPriceTwd;
    }
    Long fiatPrice = product.fiatPriceTwd();
    if (fiatPrice == null || fiatPrice <= 0) {
      LOG.warn(
          "Membership spend fallback using non-positive product.fiatPriceTwd: code={}",
          product.escortOptionCode());
      return fiatPrice == null ? 0L : fiatPrice;
    }
    return fiatPrice;
  }
}
