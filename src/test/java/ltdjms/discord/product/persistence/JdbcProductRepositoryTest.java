package ltdjms.discord.product.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import ltdjms.discord.currency.persistence.RepositoryException;
import ltdjms.discord.product.domain.Product;

@DisplayName("JdbcProductRepository 測試")
class JdbcProductRepositoryTest {

  private static final long TEST_GUILD_ID = 123456789L;

  private DataSource dataSource;
  private Connection connection;
  private JdbcProductRepository repository;

  @BeforeEach
  void setUp() throws SQLException {
    dataSource = mock(DataSource.class);
    connection = mock(Connection.class);
    when(dataSource.getConnection()).thenReturn(connection);
    repository = new JdbcProductRepository(dataSource);
  }

  @Nested
  @DisplayName("findFiatOnlyByGuildId")
  class FindFiatOnlyByGuildIdTests {

    @Test
    @DisplayName("應使用正確 SQL 條件查詢限定法幣商品，並保留排序")
    void shouldQueryFiatOnlyProductsWithExpectedSqlFiltersAndOrdering() throws SQLException {
      PreparedStatement stmt = mock(PreparedStatement.class);
      ResultSet rs = mock(ResultSet.class);
      ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);

      when(connection.prepareStatement(sqlCaptor.capture())).thenReturn(stmt);
      when(stmt.executeQuery()).thenReturn(rs);
      when(rs.next()).thenReturn(true, true, false);

      when(rs.getString("reward_type")).thenReturn((String) null, (String) null);
      when(rs.getLong("reward_amount")).thenReturn(0L, 0L);
      when(rs.getLong("currency_price")).thenReturn(0L, 0L);
      when(rs.getLong("fiat_price_twd")).thenReturn(500L, 700L);
      when(rs.wasNull()).thenReturn(true, true, false, true, false, false);

      when(rs.getLong("id")).thenReturn(1L, 2L);
      when(rs.getLong("guild_id")).thenReturn(TEST_GUILD_ID, TEST_GUILD_ID);
      when(rs.getString("name")).thenReturn("Alpha", "Bravo");
      when(rs.getString("description")).thenReturn("desc-a", "desc-b");
      Timestamp row1Time = Timestamp.from(Instant.parse("2026-02-25T00:00:00Z"));
      Timestamp row2Time = Timestamp.from(Instant.parse("2026-02-26T00:00:00Z"));
      when(rs.getTimestamp("created_at")).thenReturn(row1Time, row2Time);
      when(rs.getTimestamp("updated_at")).thenReturn(row1Time, row2Time);

      List<Product> result = repository.findFiatOnlyByGuildId(TEST_GUILD_ID);

      verify(stmt).setLong(1, TEST_GUILD_ID);
      assertThat(sqlCaptor.getValue())
          .contains("fiat_price_twd IS NOT NULL")
          .contains("fiat_price_twd > 0")
          .contains("(currency_price IS NULL OR")
          .contains("currency_price <= 0)")
          .contains("ORDER BY name ASC");

      assertThat(result).hasSize(2);
      assertThat(result.get(0).name()).isEqualTo("Alpha");
      assertThat(result.get(1).name()).isEqualTo("Bravo");
      assertThat(result.get(0).currencyPrice()).isNull();
      assertThat(result.get(1).currencyPrice()).isZero();
      assertThat(result).allMatch(Product::isFiatOnly);
    }

    @Test
    @DisplayName("查無資料時應回傳空集合")
    void shouldReturnEmptyListWhenNoRowsFound() throws SQLException {
      PreparedStatement stmt = mock(PreparedStatement.class);
      ResultSet rs = mock(ResultSet.class);

      when(connection.prepareStatement(anyString())).thenReturn(stmt);
      when(stmt.executeQuery()).thenReturn(rs);
      when(rs.next()).thenReturn(false);

      List<Product> result = repository.findFiatOnlyByGuildId(TEST_GUILD_ID);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("資料庫例外時應拋出 RepositoryException")
    void shouldThrowRepositoryExceptionWhenSqlFails() throws SQLException {
      when(connection.prepareStatement(anyString())).thenThrow(new SQLException("db error"));

      assertThatThrownBy(() -> repository.findFiatOnlyByGuildId(TEST_GUILD_ID))
          .isInstanceOf(RepositoryException.class)
          .hasMessageContaining("Failed to find fiat-only products");
    }
  }
}
