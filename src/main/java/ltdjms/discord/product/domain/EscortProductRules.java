package ltdjms.discord.product.domain;

/** Shared rules for whether a shop product counts as escort-linked for membership. */
public final class EscortProductRules {

  private EscortProductRules() {}

  /** Returns whether the product is escort-linked for spend ledger and pricing. */
  public static boolean isEscortLinked(Product product) {
    return product.shouldAutoCreateEscortOrder()
        || (product.escortOptionCode() != null && !product.escortOptionCode().isBlank());
  }
}
