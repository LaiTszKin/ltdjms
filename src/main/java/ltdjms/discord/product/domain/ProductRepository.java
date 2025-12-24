package ltdjms.discord.product.domain;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for product persistence operations.
 */
public interface ProductRepository {

    /**
     * Saves a new product to the database.
     *
     * @param product the product to save
     * @return the saved product with its generated ID
     */
    Product save(Product product);

    /**
     * Updates an existing product.
     *
     * @param product the product with updated values
     * @return the updated product
     */
    Product update(Product product);

    /**
     * Finds a product by its ID.
     *
     * @param id the product ID
     * @return an Optional containing the product if found
     */
    Optional<Product> findById(long id);

    /**
     * Finds a product by guild ID and name.
     *
     * @param guildId the Discord guild ID
     * @param name    the product name
     * @return an Optional containing the product if found
     */
    Optional<Product> findByGuildIdAndName(long guildId, String name);

    /**
     * Finds all products for a guild.
     *
     * @param guildId the Discord guild ID
     * @return a list of products for the guild
     */
    List<Product> findByGuildId(long guildId);

    /**
     * Finds products for a guild with pagination.
     *
     * @param guildId the Discord guild ID
     * @param page    zero-based page number
     * @param size    the number of items per page
     * @return a list of products for the specified page
     */
    List<Product> findByGuildIdPaginated(long guildId, int page, int size);

    /**
     * Counts the number of products for a guild.
     *
     * @param guildId the Discord guild ID
     * @return the number of products
     */
    long countByGuildId(long guildId);

    /**
     * Deletes a product by its ID.
     *
     * @param id the product ID
     * @return true if the product was deleted, false if it didn't exist
     */
    boolean deleteById(long id);

    /**
     * Checks if a product name already exists in a guild.
     *
     * @param guildId the Discord guild ID
     * @param name    the product name to check
     * @return true if the name already exists
     */
    boolean existsByGuildIdAndName(long guildId, String name);

    /**
     * Checks if a product name already exists in a guild, excluding a specific product.
     * Useful for update operations.
     *
     * @param guildId   the Discord guild ID
     * @param name      the product name to check
     * @param excludeId the product ID to exclude from the check
     * @return true if the name already exists for another product
     */
    boolean existsByGuildIdAndNameExcludingId(long guildId, String name, long excludeId);
}
