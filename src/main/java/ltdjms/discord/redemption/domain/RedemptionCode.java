package ltdjms.discord.redemption.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a redemption code that can be used to redeem a product.
 * Each code is unique and can only be used once.
 */
public record RedemptionCode(
        Long id,
        String code,
        long productId,
        long guildId,
        Instant expiresAt,
        Long redeemedBy,
        Instant redeemedAt,
        Instant createdAt
) {
    /**
     * Length of the generated redemption code.
     */
    public static final int CODE_LENGTH = 16;

    /**
     * Characters used for generating redemption codes.
     * Excludes confusing characters: 0/O, 1/I/L
     */
    public static final String CODE_CHARACTERS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    public RedemptionCode {
        Objects.requireNonNull(code, "code must not be null");
        if (code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (code.length() > 32) {
            throw new IllegalArgumentException("code must not exceed 32 characters");
        }
        // Ensure redeemed_by and redeemed_at are consistent
        if ((redeemedBy == null) != (redeemedAt == null)) {
            throw new IllegalArgumentException(
                    "redeemedBy and redeemedAt must both be specified or both be null");
        }
    }

    /**
     * Creates a new redemption code for a product.
     * The ID will be null until the code is persisted.
     *
     * @param code      the redemption code string
     * @param productId the ID of the product this code redeems
     * @param guildId   the Discord guild ID
     * @param expiresAt the expiration time (can be null for no expiration)
     * @return a new RedemptionCode instance
     */
    public static RedemptionCode create(String code, long productId, long guildId, Instant expiresAt) {
        return new RedemptionCode(
                null,
                code.toUpperCase(),
                productId,
                guildId,
                expiresAt,
                null,
                null,
                Instant.now()
        );
    }

    /**
     * Creates a copy of this code marked as redeemed.
     *
     * @param userId the user ID who redeemed the code
     * @return a new RedemptionCode instance marked as redeemed
     */
    public RedemptionCode withRedeemed(long userId) {
        if (isRedeemed()) {
            throw new IllegalStateException("Code has already been redeemed");
        }
        return new RedemptionCode(
                this.id,
                this.code,
                this.productId,
                this.guildId,
                this.expiresAt,
                userId,
                Instant.now(),
                this.createdAt
        );
    }

    /**
     * Checks if this code has been redeemed.
     *
     * @return true if the code has been used
     */
    public boolean isRedeemed() {
        return redeemedBy != null;
    }

    /**
     * Checks if this code has expired.
     *
     * @return true if the code has an expiration date and it has passed
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Checks if this code is valid for use (not redeemed and not expired).
     *
     * @return true if the code can be redeemed
     */
    public boolean isValid() {
        return !isRedeemed() && !isExpired();
    }

    /**
     * Checks if this code belongs to the specified guild.
     *
     * @param checkGuildId the guild ID to check
     * @return true if this code belongs to the guild
     */
    public boolean belongsToGuild(long checkGuildId) {
        return this.guildId == checkGuildId;
    }

    /**
     * Gets a masked version of the code for display purposes.
     * Shows only the first 4 and last 4 characters.
     *
     * @return masked code string
     */
    public String getMaskedCode() {
        if (code.length() <= 8) {
            return code;
        }
        return code.substring(0, 4) + "****" + code.substring(code.length() - 4);
    }
}
