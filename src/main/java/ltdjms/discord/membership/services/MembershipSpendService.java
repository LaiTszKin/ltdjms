package ltdjms.discord.membership.services;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.membership.domain.MembershipTier;
import ltdjms.discord.membership.persistence.MembershipRepository;
import ltdjms.discord.membership.persistence.MembershipSpendRepository;
import ltdjms.discord.membership.persistence.SpendRecordResult;
import ltdjms.discord.product.domain.EscortOptionCatalogRepository;
import ltdjms.discord.shared.events.DomainEventPublisher;
import ltdjms.discord.shared.events.MembershipTierChangedEvent;

/** Records escort fiat payment catalog list prices into the global spend ledger. */
public class MembershipSpendService implements MembershipSpendRecorder {

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

  @Override
  public boolean recordPaidEscortOrder(PaidEscortOrderSnapshot snapshot) {
    if (!snapshot.escortLinked()) {
      return true;
    }
    if (snapshot.paidAt() == null) {
      LOG.warn(
          "Skipping membership spend for paid order without paidAt: orderNumber={}",
          snapshot.orderNumber());
      return false;
    }

    try {
      long listPriceM =
          resolveListPriceM(
              snapshot.escortOptionCode(),
              snapshot.guildId(),
              snapshot.orderListPriceTwd(),
              snapshot.productFiatPriceTwd());
      if (listPriceM <= 0) {
        LOG.warn(
            "Skipping membership spend with non-positive list price: orderNumber={}, listPriceM={}",
            snapshot.orderNumber(),
            listPriceM);
        return false;
      }

      membershipRepository.findOrCreate(snapshot.buyerUserId());

      SpendRecordResult result =
          spendRepository.insertSpendAndQualifyBronzeIfThreshold(
              snapshot.buyerUserId(),
              snapshot.guildId(),
              listPriceM,
              snapshot.escortOptionCode(),
              SOURCE_TYPE_FIAT_ORDER,
              snapshot.orderNumber(),
              snapshot.paidAt(),
              MembershipTier.BRONZE.thresholdListPriceTwd());

      if (result.inserted() && result.bronzePromoted()) {
        eventPublisher.publish(
            new MembershipTierChangedEvent(
                snapshot.buyerUserId(),
                MembershipTier.NONE.name(),
                MembershipTier.BRONZE.name(),
                listPriceM,
                snapshot.paidAt()));
      }

      return true;
    } catch (Exception e) {
      MembershipSpendMetrics.recordFailure(SOURCE_TYPE_FIAT_ORDER, snapshot.orderNumber());
      LOG.error("Failed to record membership spend for orderNumber={}", snapshot.orderNumber(), e);
      return false;
    }
  }

  /**
   * Resolves catalog list price M for membership spend. Uses global catalog default price, not
   * guild overrides or discounted charged amounts.
   */
  public long resolveListPriceM(
      String escortOptionCode, long guildId, Long orderListPriceTwd, Long productFiatPriceTwd) {
    if (escortOptionCode != null && !escortOptionCode.isBlank()) {
      return catalogRepository
          .findByCode(escortOptionCode)
          .map(entry -> entry.priceTwd())
          .orElseGet(
              () -> {
                LOG.warn(
                    "Escort catalog code not found for membership spend: code={}, guildId={}",
                    escortOptionCode,
                    guildId);
                return fallbackListPrice(orderListPriceTwd, productFiatPriceTwd, escortOptionCode);
              });
    }
    return fallbackListPrice(orderListPriceTwd, productFiatPriceTwd, escortOptionCode);
  }

  private static long fallbackListPrice(
      Long orderListPriceTwd, Long productFiatPriceTwd, String escortOptionCode) {
    if (productFiatPriceTwd != null && productFiatPriceTwd > 0) {
      return productFiatPriceTwd;
    }
    if (orderListPriceTwd != null && orderListPriceTwd > 0) {
      return orderListPriceTwd;
    }
    LOG.warn(
        "Membership spend fallback using non-positive product.fiatPriceTwd: code={}",
        escortOptionCode);
    return productFiatPriceTwd == null ? 0L : productFiatPriceTwd;
  }
}
