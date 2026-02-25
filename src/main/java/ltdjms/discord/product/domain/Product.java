package ltdjms.discord.product.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a product that can be redeemed with redemption codes within a specific Discord guild.
 * Products can optionally provide rewards (currency or tokens) when redeemed. Products can also be
 * purchased directly with currency if a currency price is set.
 */
public record Product(
    Long id,
    long guildId,
    String name,
    String description,
    RewardType rewardType,
    Long rewardAmount,
    Long currencyPrice,
    Long fiatPriceTwd,
    Instant createdAt,
    Instant updatedAt) {
  /** Type of reward that can be given when a product is redeemed. */
  public enum RewardType {
    CURRENCY,
    TOKEN
  }

  public Product {
    Objects.requireNonNull(name, "name must not be null");
    if (name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    if (name.length() > 100) {
      throw new IllegalArgumentException("name must not exceed 100 characters");
    }
    if (description != null && description.length() > 1000) {
      throw new IllegalArgumentException("description must not exceed 1000 characters");
    }
    if (rewardAmount != null && rewardAmount < 0) {
      throw new IllegalArgumentException("rewardAmount must not be negative");
    }
    if (currencyPrice != null && currencyPrice < 0) {
      throw new IllegalArgumentException("currencyPrice must not be negative");
    }
    if (fiatPriceTwd != null && fiatPriceTwd < 0) {
      throw new IllegalArgumentException("fiatPriceTwd must not be negative");
    }
    // Ensure reward_type and reward_amount are consistent
    if ((rewardType == null) != (rewardAmount == null)) {
      throw new IllegalArgumentException(
          "rewardType and rewardAmount must both be specified or both be null");
    }
  }

  public Product(
      Long id,
      long guildId,
      String name,
      String description,
      RewardType rewardType,
      Long rewardAmount,
      Long currencyPrice,
      Instant createdAt,
      Instant updatedAt) {
    this(
        id,
        guildId,
        name,
        description,
        rewardType,
        rewardAmount,
        currencyPrice,
        null,
        createdAt,
        updatedAt);
  }

  /**
   * Creates a new product with the given details. The ID will be null until the product is
   * persisted.
   *
   * @param guildId the Discord guild ID
   * @param name the product name
   * @param description the product description (can be null)
   * @param rewardType the type of reward (can be null for no automatic reward)
   * @param rewardAmount the reward amount (can be null for no automatic reward)
   * @param currencyPrice the currency price for direct purchase (can be null for currency purchase
   *     not available)
   * @param fiatPriceTwd the product actual value in TWD (can be null for not configured)
   * @return a new Product instance
   */
  public static Product create(
      long guildId,
      String name,
      String description,
      RewardType rewardType,
      Long rewardAmount,
      Long currencyPrice,
      Long fiatPriceTwd) {
    Instant now = Instant.now();
    return new Product(
        null,
        guildId,
        name,
        description,
        rewardType,
        rewardAmount,
        currencyPrice,
        fiatPriceTwd,
        now,
        now);
  }

  public static Product create(
      long guildId,
      String name,
      String description,
      RewardType rewardType,
      Long rewardAmount,
      Long currencyPrice) {
    return create(guildId, name, description, rewardType, rewardAmount, currencyPrice, null);
  }

  /**
   * Creates a new product without any automatic reward.
   *
   * @param guildId the Discord guild ID
   * @param name the product name
   * @param description the product description (can be null)
   * @return a new Product instance without rewards
   */
  public static Product createWithoutReward(long guildId, String name, String description) {
    return create(guildId, name, description, null, null, null, null);
  }

  /**
   * Creates a new product without any automatic reward, with a specified currency price.
   *
   * @param guildId the Discord guild ID
   * @param name the product name
   * @param description the product description (can be null)
   * @param currencyPrice the currency price for direct purchase
   * @return a new Product instance with currency price but no automatic reward
   */
  public static Product createWithCurrencyPrice(
      long guildId, String name, String description, long currencyPrice) {
    return create(guildId, name, description, null, null, currencyPrice, null);
  }

  /**
   * Creates a new product without automatic reward, with a specified fiat value in TWD.
   *
   * @param guildId the Discord guild ID
   * @param name the product name
   * @param description the product description (can be null)
   * @param fiatPriceTwd the product actual value in TWD
   * @return a new Product instance with fiat value
   */
  public static Product createWithFiatPriceTwd(
      long guildId, String name, String description, long fiatPriceTwd) {
    return create(guildId, name, description, null, null, null, fiatPriceTwd);
  }

  /**
   * Creates a copy of this product with updated details.
   *
   * @param name the new name
   * @param description the new description
   * @param rewardType the new reward type
   * @param rewardAmount the new reward amount
   * @param currencyPrice the new currency price
   * @param fiatPriceTwd the new fiat value in TWD
   * @return a new Product instance with updated values
   */
  public Product withUpdatedDetails(
      String name,
      String description,
      RewardType rewardType,
      Long rewardAmount,
      Long currencyPrice,
      Long fiatPriceTwd) {
    return new Product(
        this.id,
        this.guildId,
        name,
        description,
        rewardType,
        rewardAmount,
        currencyPrice,
        fiatPriceTwd,
        this.createdAt,
        Instant.now());
  }

  public Product withUpdatedDetails(
      String name,
      String description,
      RewardType rewardType,
      Long rewardAmount,
      Long currencyPrice) {
    return withUpdatedDetails(
        name, description, rewardType, rewardAmount, currencyPrice, this.fiatPriceTwd);
  }

  /**
   * Checks if this product has an automatic reward.
   *
   * @return true if the product provides a reward when redeemed
   */
  public boolean hasReward() {
    return rewardType != null && rewardAmount != null;
  }

  /**
   * Formats the reward for display.
   *
   * @return a formatted string describing the reward, or null if no reward
   */
  public String formatReward() {
    if (!hasReward()) {
      return null;
    }
    return switch (rewardType) {
      case CURRENCY -> String.format("%,d 貨幣", rewardAmount);
      case TOKEN -> String.format("%,d 代幣", rewardAmount);
    };
  }

  /**
   * Checks if this product has a currency price set for direct purchase.
   *
   * @return true if the product can be purchased with currency
   */
  public boolean hasCurrencyPrice() {
    return currencyPrice != null && currencyPrice > 0;
  }

  /**
   * Formats the currency price for display.
   *
   * @return a formatted string describing the currency price, or null if no price is set
   */
  public String formatCurrencyPrice() {
    if (!hasCurrencyPrice()) {
      return null;
    }
    return String.format("%,d 貨幣", currencyPrice);
  }

  /**
   * Checks if this product has a fiat value in TWD configured.
   *
   * @return true if fiat value in TWD is configured
   */
  public boolean hasFiatPriceTwd() {
    return fiatPriceTwd != null && fiatPriceTwd > 0;
  }

  /**
   * Formats the fiat value in TWD for display.
   *
   * @return a formatted string describing the TWD value, or null if not configured
   */
  public String formatFiatPriceTwd() {
    if (!hasFiatPriceTwd()) {
      return null;
    }
    return String.format("NT$%,d", fiatPriceTwd);
  }

  /**
   * Checks whether the product is limited to fiat payment only.
   *
   * @return true when fiat value is configured and currency purchase is unavailable
   */
  public boolean isFiatOnly() {
    return hasFiatPriceTwd() && !hasCurrencyPrice();
  }
}
