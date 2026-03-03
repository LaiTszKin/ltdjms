package ltdjms.discord.redemption.persistence;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.currency.persistence.RepositoryException;
import ltdjms.discord.redemption.domain.RedemptionCode;
import ltdjms.discord.redemption.domain.RedemptionCodeRepository;

/** JDBC-based implementation of RedemptionCodeRepository. */
public class JdbcRedemptionCodeRepository implements RedemptionCodeRepository {

  private static final Logger LOG = LoggerFactory.getLogger(JdbcRedemptionCodeRepository.class);

  private final DataSource dataSource;

  public JdbcRedemptionCodeRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public RedemptionCode save(RedemptionCode code) {
    String sql =
        "INSERT INTO redemption_code (code, product_id, guild_id, expires_at, redeemed_by,"
            + " redeemed_at, created_at, quantity) VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setString(1, code.code());
      stmt.setLong(2, code.productId());
      stmt.setLong(3, code.guildId());
      setNullableTimestamp(stmt, 4, code.expiresAt());
      setNullableLong(stmt, 5, code.redeemedBy());
      setNullableTimestamp(stmt, 6, code.redeemedAt());
      stmt.setTimestamp(7, Timestamp.from(code.createdAt()));
      stmt.setInt(8, code.quantity());

      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          long id = rs.getLong("id");
          RedemptionCode saved =
              new RedemptionCode(
                  id,
                  code.code(),
                  code.productId(),
                  code.guildId(),
                  code.expiresAt(),
                  code.redeemedBy(),
                  code.redeemedAt(),
                  code.createdAt(),
                  code.invalidatedAt(),
                  code.quantity());
          LOG.debug("Saved redemption code: id={}, productId={}", id, code.productId());
          return saved;
        } else {
          throw new RepositoryException("Failed to get generated ID for redemption code");
        }
      }
    } catch (SQLException e) {
      LOG.error("Failed to save redemption code for productId={}", code.productId(), e);
      throw new RepositoryException("Failed to save redemption code", e);
    }
  }

  @Override
  public List<RedemptionCode> saveAll(List<RedemptionCode> codes) {
    if (codes.isEmpty()) {
      return List.of();
    }

    String sql =
        "INSERT INTO redemption_code (code, product_id, guild_id, expires_at, redeemed_by,"
            + " redeemed_at, created_at, quantity) VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";

    List<RedemptionCode> savedCodes = new ArrayList<>(codes.size());

    try (Connection conn = dataSource.getConnection()) {
      conn.setAutoCommit(false);
      try (PreparedStatement stmt = conn.prepareStatement(sql)) {
        for (RedemptionCode code : codes) {
          stmt.setString(1, code.code());
          stmt.setLong(2, code.productId());
          stmt.setLong(3, code.guildId());
          setNullableTimestamp(stmt, 4, code.expiresAt());
          setNullableLong(stmt, 5, code.redeemedBy());
          setNullableTimestamp(stmt, 6, code.redeemedAt());
          stmt.setTimestamp(7, Timestamp.from(code.createdAt()));
          stmt.setInt(8, code.quantity());

          try (ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
              long id = rs.getLong("id");
              savedCodes.add(
                  new RedemptionCode(
                      id,
                      code.code(),
                      code.productId(),
                      code.guildId(),
                      code.expiresAt(),
                      code.redeemedBy(),
                      code.redeemedAt(),
                      code.createdAt(),
                      code.invalidatedAt(),
                      code.quantity()));
            }
          }
        }
        conn.commit();
        LOG.info("Saved {} redemption codes", savedCodes.size());
        return savedCodes;
      } catch (SQLException e) {
        conn.rollback();
        throw e;
      } finally {
        conn.setAutoCommit(true);
      }
    } catch (SQLException e) {
      LOG.error("Failed to save batch of {} redemption codes", codes.size(), e);
      throw new RepositoryException("Failed to save redemption codes", e);
    }
  }

  @Override
  public RedemptionCode update(RedemptionCode code) {
    if (code.id() == null) {
      throw new IllegalArgumentException("Cannot update code without ID");
    }

    String sql =
        "UPDATE redemption_code SET " + "redeemed_by = ?, redeemed_at = ? " + "WHERE id = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      setNullableLong(stmt, 1, code.redeemedBy());
      setNullableTimestamp(stmt, 2, code.redeemedAt());
      stmt.setLong(3, code.id());

      int affected = stmt.executeUpdate();
      if (affected == 0) {
        throw new RepositoryException("Redemption code not found with id: " + code.id());
      }

      LOG.debug("Updated redemption code: id={}, redeemedBy={}", code.id(), code.redeemedBy());
      return code;

    } catch (SQLException e) {
      LOG.error("Failed to update redemption code id={}", code.id(), e);
      throw new RepositoryException("Failed to update redemption code", e);
    }
  }

  @Override
  public boolean markAsRedeemedIfAvailable(long codeId, long userId, Instant redeemedAt) {
    String sql =
        "UPDATE redemption_code SET redeemed_by = ?, redeemed_at = ? "
            + "WHERE id = ? AND redeemed_by IS NULL AND invalidated_at IS NULL "
            + "AND (expires_at IS NULL OR expires_at >= ?)";

    Instant redeemTime = redeemedAt != null ? redeemedAt : Instant.now();

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, userId);
      stmt.setTimestamp(2, Timestamp.from(redeemTime));
      stmt.setLong(3, codeId);
      stmt.setTimestamp(4, Timestamp.from(redeemTime));

      int affected = stmt.executeUpdate();
      if (affected == 1) {
        LOG.debug("Atomically redeemed code: id={}, userId={}", codeId, userId);
        return true;
      }

      LOG.debug("Atomic redeem skipped (already unavailable): id={}, userId={}", codeId, userId);
      return false;

    } catch (SQLException e) {
      LOG.error("Failed to atomically redeem code id={}, userId={}", codeId, userId, e);
      throw new RepositoryException("Failed to atomically redeem code", e);
    }
  }

  @Override
  public Optional<RedemptionCode> findByCode(String code) {
    String sql =
        "SELECT id, code, product_id, guild_id, expires_at, redeemed_by, redeemed_at, created_at,"
            + " invalidated_at, quantity FROM redemption_code WHERE code = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setString(1, code.toUpperCase());

      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return Optional.of(mapRow(rs));
        }
        return Optional.empty();
      }
    } catch (SQLException e) {
      LOG.error("Failed to find redemption code by code={}", code, e);
      throw new RepositoryException("Failed to find redemption code", e);
    }
  }

  @Override
  public Optional<RedemptionCode> findById(long id) {
    String sql =
        "SELECT id, code, product_id, guild_id, expires_at, redeemed_by, redeemed_at, created_at,"
            + " invalidated_at, quantity FROM redemption_code WHERE id = ?";

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
      LOG.error("Failed to find redemption code by id={}", id, e);
      throw new RepositoryException("Failed to find redemption code", e);
    }
  }

  @Override
  public boolean existsByCode(String code) {
    String sql = "SELECT 1 FROM redemption_code WHERE code = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setString(1, code.toUpperCase());

      try (ResultSet rs = stmt.executeQuery()) {
        return rs.next();
      }
    } catch (SQLException e) {
      LOG.error("Failed to check redemption code existence for code={}", code, e);
      throw new RepositoryException("Failed to check redemption code existence", e);
    }
  }

  @Override
  public List<RedemptionCode> findByProductId(long productId, int limit, int offset) {
    String sql =
        "SELECT id, code, product_id, guild_id, expires_at, redeemed_by, redeemed_at, created_at,"
            + " invalidated_at, quantity FROM redemption_code WHERE product_id = ? ORDER BY"
            + " created_at DESC LIMIT ? OFFSET ?";

    List<RedemptionCode> codes = new ArrayList<>();

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, productId);
      stmt.setInt(2, limit);
      stmt.setInt(3, offset);

      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          codes.add(mapRow(rs));
        }
      }

      LOG.debug("Found {} redemption codes for productId={}", codes.size(), productId);
      return codes;

    } catch (SQLException e) {
      LOG.error("Failed to find redemption codes for productId={}", productId, e);
      throw new RepositoryException("Failed to find redemption codes", e);
    }
  }

  @Override
  public long countByProductId(long productId) {
    String sql = "SELECT COUNT(*) FROM redemption_code WHERE product_id = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, productId);

      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return rs.getLong(1);
        }
        return 0;
      }
    } catch (SQLException e) {
      LOG.error("Failed to count redemption codes for productId={}", productId, e);
      throw new RepositoryException("Failed to count redemption codes", e);
    }
  }

  @Override
  public long countRedeemedByProductId(long productId) {
    String sql =
        "SELECT COUNT(*) FROM redemption_code WHERE product_id = ? AND redeemed_by IS NOT NULL";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, productId);

      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return rs.getLong(1);
        }
        return 0;
      }
    } catch (SQLException e) {
      LOG.error("Failed to count redeemed codes for productId={}", productId, e);
      throw new RepositoryException("Failed to count redeemed codes", e);
    }
  }

  @Override
  public long countUnusedByProductId(long productId) {
    String sql =
        "SELECT COUNT(*) FROM redemption_code WHERE product_id = ? AND redeemed_by IS NULL";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, productId);

      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return rs.getLong(1);
        }
        return 0;
      }
    } catch (SQLException e) {
      LOG.error("Failed to count unused codes for productId={}", productId, e);
      throw new RepositoryException("Failed to count unused codes", e);
    }
  }

  @Override
  public int deleteUnusedByProductId(long productId) {
    String sql = "DELETE FROM redemption_code WHERE product_id = ? AND redeemed_by IS NULL";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, productId);

      int affected = stmt.executeUpdate();
      if (affected > 0) {
        LOG.info("Deleted {} unused redemption codes for productId={}", affected, productId);
      }
      return affected;

    } catch (SQLException e) {
      LOG.error("Failed to delete unused codes for productId={}", productId, e);
      throw new RepositoryException("Failed to delete unused codes", e);
    }
  }

  @Override
  public CodeStats getStatsByProductId(long productId) {
    String sql =
        "SELECT COUNT(*) as total, COUNT(CASE WHEN redeemed_by IS NOT NULL THEN 1 END) as redeemed,"
            + " COUNT(CASE WHEN redeemed_by IS NULL THEN 1 END) as unused, COUNT(CASE WHEN"
            + " redeemed_by IS NULL AND expires_at IS NOT NULL AND expires_at < NOW() THEN 1 END)"
            + " as expired FROM redemption_code WHERE product_id = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, productId);

      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return new CodeStats(
              rs.getLong("total"),
              rs.getLong("redeemed"),
              rs.getLong("unused"),
              rs.getLong("expired"));
        }
        return CodeStats.zero();
      }
    } catch (SQLException e) {
      LOG.error("Failed to get stats for productId={}", productId, e);
      throw new RepositoryException("Failed to get code stats", e);
    }
  }

  private RedemptionCode mapRow(ResultSet rs) throws SQLException {
    Long productId = rs.getLong("product_id");
    if (rs.wasNull()) {
      productId = null;
    }

    Long redeemedBy = rs.getLong("redeemed_by");
    if (rs.wasNull()) {
      redeemedBy = null;
    }

    Timestamp expiresAtTs = rs.getTimestamp("expires_at");
    Instant expiresAt = expiresAtTs != null ? expiresAtTs.toInstant() : null;

    Timestamp redeemedAtTs = rs.getTimestamp("redeemed_at");
    Instant redeemedAt = redeemedAtTs != null ? redeemedAtTs.toInstant() : null;

    Timestamp invalidatedAtTs = rs.getTimestamp("invalidated_at");
    Instant invalidatedAt = invalidatedAtTs != null ? invalidatedAtTs.toInstant() : null;

    return new RedemptionCode(
        rs.getLong("id"),
        rs.getString("code"),
        productId,
        rs.getLong("guild_id"),
        expiresAt,
        redeemedBy,
        redeemedAt,
        rs.getTimestamp("created_at").toInstant(),
        invalidatedAt,
        rs.getInt("quantity"));
  }

  private void setNullableTimestamp(PreparedStatement stmt, int index, Instant value)
      throws SQLException {
    if (value != null) {
      stmt.setTimestamp(index, Timestamp.from(value));
    } else {
      stmt.setNull(index, Types.TIMESTAMP);
    }
  }

  private void setNullableLong(PreparedStatement stmt, int index, Long value) throws SQLException {
    if (value != null) {
      stmt.setLong(index, value);
    } else {
      stmt.setNull(index, Types.BIGINT);
    }
  }

  @Override
  public int invalidateByProductId(long productId) {
    String sql =
        "UPDATE redemption_code "
            + "SET invalidated_at = NOW() "
            + "WHERE product_id = ? AND invalidated_at IS NULL";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, productId);

      int affected = stmt.executeUpdate();
      if (affected > 0) {
        LOG.info("Invalidated {} redemption codes for productId={}", affected, productId);
      }
      return affected;

    } catch (SQLException e) {
      LOG.error("Failed to invalidate codes for productId={}", productId, e);
      throw new RepositoryException("Failed to invalidate codes", e);
    }
  }

  @Override
  public List<RedemptionCode> findInvalidatedByProductId(long productId) {
    String sql =
        "SELECT id, code, product_id, guild_id, expires_at, redeemed_by, redeemed_at, created_at,"
            + " invalidated_at, quantity FROM redemption_code WHERE product_id = ? AND"
            + " invalidated_at IS NOT NULL"
            + " ORDER BY invalidated_at DESC";

    List<RedemptionCode> codes = new ArrayList<>();

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, productId);

      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          codes.add(mapRow(rs));
        }
      }

      return codes;

    } catch (SQLException e) {
      LOG.error("Failed to find invalidated codes", e);
      throw new RepositoryException("Failed to find invalidated codes", e);
    }
  }
}
