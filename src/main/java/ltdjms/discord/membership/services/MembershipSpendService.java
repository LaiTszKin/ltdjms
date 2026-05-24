package ltdjms.discord.membership.services;

import java.time.Instant;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.membership.domain.GlobalMemberMembership;
import ltdjms.discord.membership.domain.MembershipTier;
import ltdjms.discord.membership.persistence.MembershipRepository;
import ltdjms.discord.membership.persistence.MembershipSpendRepository;
import ltdjms.discord.product.domain.EscortOptionCatalog;
import ltdjms.discord.product.domain.EscortOptionCatalogRepository;
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

  /** No-op instance for legacy worker constructors that omit membership spend recording. */
  public static MembershipSpendService noop() {
    return new MembershipSpendService(
        new MembershipSpendRepository() {
          @Override
          public boolean insertIfAbsent(
              long discordUserId,
              long guildId,
              long listPriceTwd,
              String escortOptionCode,
              String sourceType,
              String sourceReference,
              Instant paidAt) {
            return false;
          }

          @Override
          public long sumListPriceInPeriod(long discordUserId, Instant from, Instant to) {
            return 0L;
          }
        },
        new MembershipRepository() {
          @Override
          public java.util.Optional<GlobalMemberMembership> findByUserId(long discordUserId) {
            return java.util.Optional.empty();
          }

          @Override
          public GlobalMemberMembership findOrCreate(long discordUserId) {
            return GlobalMemberMembership.createNew(discordUserId);
          }

          @Override
          public GlobalMemberMembership save(GlobalMemberMembership membership) {
            return membership;
          }
        },
        new EscortOptionCatalogRepository() {
          @Override
          public java.util.List<EscortOptionCatalog> findAll() {
            return java.util.List.of();
          }

          @Override
          public java.util.Optional<EscortOptionCatalog> findByCode(String code) {
            return java.util.Optional.empty();
          }

          @Override
          public EscortOptionCatalog save(EscortOptionCatalog catalog) {
            return catalog;
          }

          @Override
          public EscortOptionCatalog update(EscortOptionCatalog catalog) {
            return catalog;
          }

          @Override
          public boolean deleteByCode(String code) {
            return false;
          }

          @Override
          public boolean existsByCode(String code) {
            return false;
          }

          @Override
          public long count() {
            return 0L;
          }
        });
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
      if (!isEscortLinked(product)) {
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

      boolean inserted =
          spendRepository.insertIfAbsent(
              order.buyerUserId(),
              order.guildId(),
              listPriceM,
              product.escortOptionCode(),
              SOURCE_TYPE_FIAT_ORDER,
              order.orderNumber(),
              order.paidAt());

      if (inserted && listPriceM >= MembershipTier.BRONZE.thresholdListPriceTwd()) {
        markQualifyingBronzeOrder(order.buyerUserId());
      }
    } catch (Exception e) {
      LOG.error(
          "Failed to record membership spend for orderNumber={}", order.orderNumber(), e);
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

  static boolean isEscortLinked(Product product) {
    return product.shouldAutoCreateEscortOrder()
        || (product.escortOptionCode() != null && !product.escortOptionCode().isBlank());
  }

  private void markQualifyingBronzeOrder(long discordUserId) {
    GlobalMemberMembership membership = membershipRepository.findOrCreate(discordUserId);
    if (membership.hasQualifyingBronzeOrder()) {
      return;
    }

    membershipRepository.save(
        new GlobalMemberMembership(
            membership.discordUserId(),
            membership.currentTier(),
            membership.earliestGuildJoinAt(),
            membership.settlementDayOfMonth(),
            membership.lastSettlementAt(),
            membership.nextSettlementAt(),
            true,
            membership.createdAt(),
            membership.updatedAt()));
  }

  private static long fallbackListPrice(Product product) {
    Long fiatPrice = product.fiatPriceTwd();
    return fiatPrice == null ? 0L : fiatPrice;
  }
}
