package ltdjms.discord.product.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.currency.persistence.RepositoryException;
import ltdjms.discord.product.domain.EscortOptionCatalog;
import ltdjms.discord.product.domain.EscortOptionCatalogRepository;

/** JDBC-based implementation of {@link EscortOptionCatalogRepository}. */
public class JdbcEscortOptionCatalogRepository implements EscortOptionCatalogRepository {

  private static final Logger LOG =
      LoggerFactory.getLogger(JdbcEscortOptionCatalogRepository.class);

  private static final String SELECT_COLUMNS =
      "id, code, type, level, map_scope, target, price_twd, created_at, updated_at";

  private final DataSource dataSource;

  public JdbcEscortOptionCatalogRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public List<EscortOptionCatalog> findAll() {
    String sql = "SELECT " + SELECT_COLUMNS + " FROM escort_option_catalog ORDER BY id";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql);
        ResultSet rs = stmt.executeQuery()) {
      List<EscortOptionCatalog> results = new ArrayList<>();
      while (rs.next()) {
        results.add(mapRow(rs));
      }
      return results;
    } catch (SQLException e) {
      throw new RepositoryException("Failed to find all escort option catalogs", e);
    }
  }

  @Override
  public Optional<EscortOptionCatalog> findByCode(String code) {
    String sql = "SELECT " + SELECT_COLUMNS + " FROM escort_option_catalog WHERE code = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, code);
      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return Optional.of(mapRow(rs));
        }
      }
      return Optional.empty();
    } catch (SQLException e) {
      throw new RepositoryException("Failed to find escort option catalog by code: " + code, e);
    }
  }

  @Override
  public EscortOptionCatalog save(EscortOptionCatalog catalog) {
    String sql =
        "INSERT INTO escort_option_catalog (code, type, level, map_scope, target, price_twd,"
            + " created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW()) RETURNING id,"
            + " created_at, updated_at";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, catalog.code());
      stmt.setString(2, catalog.type());
      stmt.setString(3, catalog.level());
      stmt.setString(4, catalog.mapScope());
      stmt.setString(5, catalog.target());
      stmt.setLong(6, catalog.priceTwd());
      try (ResultSet rs = stmt.executeQuery()) {
        if (!rs.next()) {
          throw new RepositoryException("Failed to get generated id for escort option catalog");
        }
        return new EscortOptionCatalog(
            rs.getLong("id"),
            catalog.code(),
            catalog.type(),
            catalog.level(),
            catalog.mapScope(),
            catalog.target(),
            catalog.priceTwd(),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());
      }
    } catch (SQLException e) {
      throw new RepositoryException("Failed to save escort option catalog: " + catalog.code(), e);
    }
  }

  @Override
  public EscortOptionCatalog update(EscortOptionCatalog catalog) {
    String sql =
        "UPDATE escort_option_catalog SET code = ?, type = ?, level = ?, map_scope = ?, target = ?,"
            + " price_twd = ?, updated_at = NOW() WHERE code = ? RETURNING id, created_at,"
            + " updated_at";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, catalog.code());
      stmt.setString(2, catalog.type());
      stmt.setString(3, catalog.level());
      stmt.setString(4, catalog.mapScope());
      stmt.setString(5, catalog.target());
      stmt.setLong(6, catalog.priceTwd());
      stmt.setString(7, catalog.code());
      try (ResultSet rs = stmt.executeQuery()) {
        if (!rs.next()) {
          throw new RepositoryException(
              "Escort option catalog not found for update: " + catalog.code());
        }
        return new EscortOptionCatalog(
            rs.getLong("id"),
            catalog.code(),
            catalog.type(),
            catalog.level(),
            catalog.mapScope(),
            catalog.target(),
            catalog.priceTwd(),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());
      }
    } catch (SQLException e) {
      throw new RepositoryException("Failed to update escort option catalog: " + catalog.code(), e);
    }
  }

  @Override
  public boolean deleteByCode(String code) {
    String sql = "DELETE FROM escort_option_catalog WHERE code = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, code);
      return stmt.executeUpdate() > 0;
    } catch (SQLException e) {
      throw new RepositoryException("Failed to delete escort option catalog: " + code, e);
    }
  }

  @Override
  public boolean existsByCode(String code) {
    String sql = "SELECT 1 FROM escort_option_catalog WHERE code = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, code);
      try (ResultSet rs = stmt.executeQuery()) {
        return rs.next();
      }
    } catch (SQLException e) {
      throw new RepositoryException(
          "Failed to check existence of escort option catalog: " + code, e);
    }
  }

  @Override
  public long count() {
    String sql = "SELECT COUNT(*) FROM escort_option_catalog";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql);
        ResultSet rs = stmt.executeQuery()) {
      if (rs.next()) {
        return rs.getLong(1);
      }
      return 0;
    } catch (SQLException e) {
      throw new RepositoryException("Failed to count escort option catalogs", e);
    }
  }

  private EscortOptionCatalog mapRow(ResultSet rs) throws SQLException {
    Timestamp createdAt = rs.getTimestamp("created_at");
    Timestamp updatedAt = rs.getTimestamp("updated_at");
    return new EscortOptionCatalog(
        rs.getLong("id"),
        rs.getString("code"),
        rs.getString("type"),
        rs.getString("level"),
        rs.getString("map_scope"),
        rs.getString("target"),
        rs.getLong("price_twd"),
        createdAt != null ? createdAt.toInstant() : null,
        updatedAt != null ? updatedAt.toInstant() : null);
  }
}
