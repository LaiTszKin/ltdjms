package ltdjms.discord.product.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a product that can be redeemed with redemption codes within a specific Discord guild.
 * Products can optionally provide rewards (currency or tokens) when redeemed.
 */
public record Product(
        Long id,
        long guildId,
        String name,
        String description,
        RewardType rewardType,
        Long rewardAmount,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * Type of reward that can be given when a product is redeemed.
     */
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
        // Ensure reward_type and reward_amount are consistent
        if ((rewardType == null) != (rewardAmount == null)) {
            throw new IllegalArgumentException(
                    "rewardType and rewardAmount must both be specified or both be null");
        }
    }

    /**
     * Creates a new product with the given details.
     * The ID will be null until the product is persisted.
     *
     * @param guildId      the Discord guild ID
     * @param name         the product name
     * @param description  the product description (can be null)
     * @param rewardType   the type of reward (can be null for no automatic reward)
     * @param rewardAmount the reward amount (can be null for no automatic reward)
     * @return a new Product instance
     */
    public static Product create(long guildId, String name, String description,
                                 RewardType rewardType, Long rewardAmount) {
        Instant now = Instant.now();
        return new Product(null, guildId, name, description, rewardType, rewardAmount, now, now);
    }

    /**
     * Creates a new product without any automatic reward.
     *
     * @param guildId     the Discord guild ID
     * @param name        the product name
     * @param description the product description (can be null)
     * @return a new Product instance without rewards
     */
    public static Product createWithoutReward(long guildId, String name, String description) {
        return create(guildId, name, description, null, null);
    }

    /**
     * Creates a copy of this product with updated details.
     *
     * @param name         the new name
     * @param description  the new description
     * @param rewardType   the new reward type
     * @param rewardAmount the new reward amount
     * @return a new Product instance with updated values
     */
    public Product withUpdatedDetails(String name, String description,
                                      RewardType rewardType, Long rewardAmount) {
        return new Product(
                this.id,
                this.guildId,
                name,
                description,
                rewardType,
                rewardAmount,
                this.createdAt,
                Instant.now()
        );
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
}
