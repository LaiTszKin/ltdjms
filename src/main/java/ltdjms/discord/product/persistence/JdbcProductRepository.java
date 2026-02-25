package ltdjms.discord.product.persistence;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.currency.persistence.RepositoryException;
import ltdjms.discord.product.domain.Product;
import ltdjms.discord.product.domain.ProductRepository;

/** JDBC-based implementation of ProductRepository. */
public class JdbcProductRepository implements ProductRepository {

  private static final Logger LOG = LoggerFactory.getLogger(JdbcProductRepository.class);

  private final DataSource dataSource;

  public JdbcProductRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public Product save(Product product) {
    String sql =
        "INSERT INTO product (guild_id, name, description, reward_type, reward_amount,"
            + " currency_price, fiat_price_twd, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?,"
            + " ?, ?, ?) RETURNING id";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, product.guildId());
      stmt.setString(2, product.name());
      stmt.setString(3, product.description());
      stmt.setString(4, product.rewardType() != null ? product.rewardType().name() : null);
      if (product.rewardAmount() != null) {
        stmt.setLong(5, product.rewardAmount());
      } else {
        stmt.setNull(5, Types.BIGINT);
      }
      if (product.currencyPrice() != null) {
        stmt.setLong(6, product.currencyPrice());
      } else {
        stmt.setNull(6, Types.BIGINT);
      }
      if (product.fiatPriceTwd() != null) {
        stmt.setLong(7, product.fiatPriceTwd());
      } else {
        stmt.setNull(7, Types.BIGINT);
      }
      stmt.setTimestamp(8, Timestamp.from(product.createdAt()));
      stmt.setTimestamp(9, Timestamp.from(product.updatedAt()));

      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          long id = rs.getLong("id");
          Product saved =
              new Product(
                  id,
                  product.guildId(),
                  product.name(),
                  product.description(),
                  product.rewardType(),
                  product.rewardAmount(),
                  product.currencyPrice(),
                  product.fiatPriceTwd(),
                  product.createdAt(),
                  product.updatedAt());
          LOG.debug(
              "Saved product: id={}, guildId={}, name={}", id, product.guildId(), product.name());
          return saved;
        } else {
          throw new RepositoryException("Failed to get generated ID for product");
        }
      }
    } catch (SQLException e) {
      LOG.error(
          "Failed to save product for guildId={}, name={}", product.guildId(), product.name(), e);
      throw new RepositoryException("Failed to save product", e);
    }
  }

  @Override
  public Product update(Product product) {
    if (product.id() == null) {
      throw new IllegalArgumentException("Cannot update product without ID");
    }

    String sql =
        "UPDATE product SET name = ?, description = ?, reward_type = ?, reward_amount = ?,"
            + " currency_price = ?, fiat_price_twd = ?, updated_at = ? WHERE id = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setString(1, product.name());
      stmt.setString(2, product.description());
      stmt.setString(3, product.rewardType() != null ? product.rewardType().name() : null);
      if (product.rewardAmount() != null) {
        stmt.setLong(4, product.rewardAmount());
      } else {
        stmt.setNull(4, Types.BIGINT);
      }
      if (product.currencyPrice() != null) {
        stmt.setLong(5, product.currencyPrice());
      } else {
        stmt.setNull(5, Types.BIGINT);
      }
      if (product.fiatPriceTwd() != null) {
        stmt.setLong(6, product.fiatPriceTwd());
      } else {
        stmt.setNull(6, Types.BIGINT);
      }
      stmt.setTimestamp(7, Timestamp.from(product.updatedAt()));
      stmt.setLong(8, product.id());

      int affected = stmt.executeUpdate();
      if (affected == 0) {
        throw new RepositoryException("Product not found with id: " + product.id());
      }

      LOG.debug("Updated product: id={}, name={}", product.id(), product.name());
      return product;

    } catch (SQLException e) {
      LOG.error("Failed to update product id={}", product.id(), e);
      throw new RepositoryException("Failed to update product", e);
    }
  }

  @Override
  public Optional<Product> findById(long id) {
    String sql =
        "SELECT id, guild_id, name, description, reward_type, reward_amount, currency_price,"
            + " fiat_price_twd, created_at, updated_at FROM product WHERE id = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, id);

      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return Optional.of(mapRow(rs));
        }
        return Optional.empty();
      }
    } catch (SQLException e) {
      LOG.error("Failed to find product by id={}", id, e);
      throw new RepositoryException("Failed to find product", e);
    }
  }

  @Override
  public Optional<Product> findByGuildIdAndName(long guildId, String name) {
    String sql =
        "SELECT id, guild_id, name, description, reward_type, reward_amount, currency_price,"
            + " fiat_price_twd, created_at, updated_at FROM product WHERE guild_id = ? AND name ="
            + " ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, guildId);
      stmt.setString(2, name);

      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return Optional.of(mapRow(rs));
        }
        return Optional.empty();
      }
    } catch (SQLException e) {
      LOG.error("Failed to find product by guildId={}, name={}", guildId, name, e);
      throw new RepositoryException("Failed to find product", e);
    }
  }

  @Override
  public List<Product> findByGuildId(long guildId) {
    String sql =
        "SELECT id, guild_id, name, description, reward_type, reward_amount, currency_price,"
            + " fiat_price_twd, created_at, updated_at FROM product WHERE guild_id = ? ORDER BY"
            + " created_at DESC";

    List<Product> products = new ArrayList<>();

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, guildId);

      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          products.add(mapRow(rs));
        }
      }

      LOG.debug("Found {} products for guildId={}", products.size(), guildId);
      return products;

    } catch (SQLException e) {
      LOG.error("Failed to find products for guildId={}", guildId, e);
      throw new RepositoryException("Failed to find products", e);
    }
  }

  @Override
  public List<Product> findByGuildIdPaginated(long guildId, int page, int size) {
    String sql =
        "SELECT id, guild_id, name, description, reward_type, reward_amount, currency_price,"
            + " fiat_price_twd, created_at, updated_at FROM product WHERE guild_id = ? ORDER BY"
            + " created_at DESC LIMIT ? OFFSET ?";

    List<Product> products = new ArrayList<>();

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, guildId);
      stmt.setInt(2, size);
      stmt.setInt(3, page * size);

      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          products.add(mapRow(rs));
        }
      }

      LOG.debug(
          "Found {} products for guildId={}, page={}, size={}",
          products.size(),
          guildId,
          page,
          size);
      return products;

    } catch (SQLException e) {
      LOG.error("Failed to find products paginated for guildId={}", guildId, e);
      throw new RepositoryException("Failed to find products", e);
    }
  }

  @Override
  public long countByGuildId(long guildId) {
    String sql = "SELECT COUNT(*) FROM product WHERE guild_id = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, guildId);

      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return rs.getLong(1);
        }
        return 0;
      }
    } catch (SQLException e) {
      LOG.error("Failed to count products for guildId={}", guildId, e);
      throw new RepositoryException("Failed to count products", e);
    }
  }

  @Override
  public boolean deleteById(long id) {
    String sql = "DELETE FROM product WHERE id = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, id);

      int affected = stmt.executeUpdate();
      if (affected > 0) {
        LOG.info("Deleted product id={}", id);
        return true;
      }
      return false;

    } catch (SQLException e) {
      LOG.error("Failed to delete product id={}", id, e);
      throw new RepositoryException("Failed to delete product", e);
    }
  }

  @Override
  public boolean existsByGuildIdAndName(long guildId, String name) {
    String sql = "SELECT 1 FROM product WHERE guild_id = ? AND name = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, guildId);
      stmt.setString(2, name);

      try (ResultSet rs = stmt.executeQuery()) {
        return rs.next();
      }
    } catch (SQLException e) {
      LOG.error("Failed to check product existence for guildId={}, name={}", guildId, name, e);
      throw new RepositoryException("Failed to check product existence", e);
    }
  }

  @Override
  public boolean existsByGuildIdAndNameExcludingId(long guildId, String name, long excludeId) {
    String sql = "SELECT 1 FROM product WHERE guild_id = ? AND name = ? AND id != ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, guildId);
      stmt.setString(2, name);
      stmt.setLong(3, excludeId);

      try (ResultSet rs = stmt.executeQuery()) {
        return rs.next();
      }
    } catch (SQLException e) {
      LOG.error(
          "Failed to check product existence for guildId={}, name={}, excludeId={}",
          guildId,
          name,
          excludeId,
          e);
      throw new RepositoryException("Failed to check product existence", e);
    }
  }

  private Product mapRow(ResultSet rs) throws SQLException {
    String rewardTypeStr = rs.getString("reward_type");
    Product.RewardType rewardType =
        rewardTypeStr != null ? Product.RewardType.valueOf(rewardTypeStr) : null;

    long rewardAmountValue = rs.getLong("reward_amount");
    Long rewardAmount = rs.wasNull() ? null : rewardAmountValue;

    long currencyPriceValue = rs.getLong("currency_price");
    Long currencyPrice = rs.wasNull() ? null : currencyPriceValue;
    long fiatPriceTwdValue = rs.getLong("fiat_price_twd");
    Long fiatPriceTwd = rs.wasNull() ? null : fiatPriceTwdValue;

    return new Product(
        rs.getLong("id"),
        rs.getLong("guild_id"),
        rs.getString("name"),
        rs.getString("description"),
        rewardType,
        rewardAmount,
        currencyPrice,
        fiatPriceTwd,
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }

  /**
   * Finds all products with a currency price set for the given guild. Only products where
   * currency_price is not null and greater than 0 are returned.
   *
   * @param guildId the guild ID
   * @return list of products available for currency purchase
   */
  public List<Product> findByGuildIdWithCurrencyPrice(long guildId) {
    String sql =
        "SELECT id, guild_id, name, description, reward_type, reward_amount, currency_price,"
            + " fiat_price_twd, created_at, updated_at FROM product WHERE guild_id = ? AND"
            + " currency_price IS NOT NULL AND currency_price > 0 ORDER BY name ASC";

    List<Product> products = new ArrayList<>();

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, guildId);

      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          products.add(mapRow(rs));
        }
      }

      LOG.debug("Found {} products with currency price for guildId={}", products.size(), guildId);
      return products;

    } catch (SQLException e) {
      LOG.error("Failed to find products with currency price for guildId={}", guildId, e);
      throw new RepositoryException("Failed to find products with currency price", e);
    }
  }

  /**
   * Finds fiat-only products for the given guild.
   *
   * @param guildId the guild ID
   * @return list of products that can only be ordered by fiat payment
   */
  public List<Product> findFiatOnlyByGuildId(long guildId) {
    String sql =
        "SELECT id, guild_id, name, description, reward_type, reward_amount, currency_price,"
            + " fiat_price_twd, created_at, updated_at FROM product WHERE guild_id = ? AND"
            + " fiat_price_twd IS NOT NULL AND fiat_price_twd > 0 AND (currency_price IS NULL OR"
            + " currency_price <= 0) ORDER BY name ASC";

    List<Product> products = new ArrayList<>();

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, guildId);

      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          products.add(mapRow(rs));
        }
      }

      LOG.debug("Found {} fiat-only products for guildId={}", products.size(), guildId);
      return products;

    } catch (SQLException e) {
      LOG.error("Failed to find fiat-only products for guildId={}", guildId, e);
      throw new RepositoryException("Failed to find fiat-only products", e);
    }
  }
}
