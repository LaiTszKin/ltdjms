package ltdjms.discord.shop.services;

import ltdjms.discord.membership.services.PaidEscortOrderSnapshot;
import ltdjms.discord.product.domain.EscortProductRules;
import ltdjms.discord.product.domain.Product;
import ltdjms.discord.shop.domain.FiatOrder;

/** Builds membership spend snapshots from shop orders. */
public final class PaidEscortOrderSnapshots {

  private PaidEscortOrderSnapshots() {}

  public static PaidEscortOrderSnapshot fromFiatOrder(FiatOrder order, Product product) {
    return new PaidEscortOrderSnapshot(
        order.orderNumber(),
        order.buyerUserId(),
        order.guildId(),
        order.paidAt(),
        order.listPriceTwd(),
        product.escortOptionCode(),
        product.fiatPriceTwd(),
        EscortProductRules.isEscortLinked(product));
  }
}
