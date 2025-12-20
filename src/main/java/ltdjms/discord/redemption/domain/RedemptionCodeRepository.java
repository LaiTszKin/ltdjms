package ltdjms.discord.redemption.domain;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for redemption code persistence operations.
 */
public interface RedemptionCodeRepository {

    /**
     * Saves a new redemption code to the database.
     *
     * @param code the redemption code to save
     * @return the saved code with its generated ID
     */
    RedemptionCode save(RedemptionCode code);

    /**
     * Saves multiple redemption codes to the database in batch.
     *
     * @param codes the redemption codes to save
     * @return the saved codes with their generated IDs
     */
    List<RedemptionCode> saveAll(List<RedemptionCode> codes);

    /**
     * Updates a redemption code (typically to mark it as redeemed).
     *
     * @param code the code with updated values
     * @return the updated code
     */
    RedemptionCode update(RedemptionCode code);

    /**
     * Finds a redemption code by its code string.
     *
     * @param code the code string
     * @return an Optional containing the code if found
     */
    Optional<RedemptionCode> findByCode(String code);

    /**
     * Finds a redemption code by its ID.
     *
     * @param id the code ID
     * @return an Optional containing the code if found
     */
    Optional<RedemptionCode> findById(long id);

    /**
     * Checks if a code string already exists in the database.
     *
     * @param code the code string to check
     * @return true if the code exists
     */
    boolean existsByCode(String code);

    /**
     * Finds all codes for a product with pagination.
     *
     * @param productId the product ID
     * @param limit     the maximum number of codes to return
     * @param offset    the number of codes to skip
     * @return a list of redemption codes
     */
    List<RedemptionCode> findByProductId(long productId, int limit, int offset);

    /**
     * Counts the total number of codes for a product.
     *
     * @param productId the product ID
     * @return the total count
     */
    long countByProductId(long productId);

    /**
     * Counts the number of redeemed codes for a product.
     *
     * @param productId the product ID
     * @return the count of redeemed codes
     */
    long countRedeemedByProductId(long productId);

    /**
     * Counts the number of unused codes for a product.
     *
     * @param productId the product ID
     * @return the count of unused codes
     */
    long countUnusedByProductId(long productId);

    /**
     * Deletes all unused (not redeemed) codes for a product.
     *
     * @param productId the product ID
     * @return the number of deleted codes
     */
    int deleteUnusedByProductId(long productId);

    /**
     * Gets statistics for a product's redemption codes.
     *
     * @param productId the product ID
     * @return code statistics
     */
    CodeStats getStatsByProductId(long productId);

    /**
     * Statistics about a product's redemption codes.
     */
    record CodeStats(
            long totalCount,
            long redeemedCount,
            long unusedCount,
            long expiredCount
    ) {
        public CodeStats {
            if (totalCount < 0 || redeemedCount < 0 || unusedCount < 0 || expiredCount < 0) {
                throw new IllegalArgumentException("Counts cannot be negative");
            }
        }

        /**
         * Creates a zero stats instance.
         */
        public static CodeStats zero() {
            return new CodeStats(0, 0, 0, 0);
        }
    }
}
