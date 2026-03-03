package ltdjms.discord.redemption.persistence;

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
import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import ltdjms.discord.currency.persistence.RepositoryException;

@DisplayName("JdbcRedemptionCodeRepository 單元測試")
class JdbcRedemptionCodeRepositoryTest {

  private static final long TEST_CODE_ID = 1L;
  private static final long TEST_USER_ID = 987654321098765432L;
  private static final long TEST_PRODUCT_ID = 42L;

  private DataSource dataSource;
  private Connection connection;
  private JdbcRedemptionCodeRepository repository;

  @BeforeEach
  void setUp() throws SQLException {
    dataSource = mock(DataSource.class);
    connection = mock(Connection.class);
    when(dataSource.getConnection()).thenReturn(connection);
    repository = new JdbcRedemptionCodeRepository(dataSource);
  }

  @Nested
  @DisplayName("markAsRedeemedIfAvailable")
  class MarkAsRedeemedIfAvailableTests {

    @Test
    @DisplayName("當更新一筆資料時應返回 true")
    void shouldReturnTrueWhenOneRowUpdated() throws SQLException {
      PreparedStatement stmt = mock(PreparedStatement.class);
      Instant redeemedAt = Instant.parse("2026-02-01T00:00:00Z");

      when(connection.prepareStatement(anyString())).thenReturn(stmt);
      when(stmt.executeUpdate()).thenReturn(1);

      boolean result = repository.markAsRedeemedIfAvailable(TEST_CODE_ID, TEST_USER_ID, redeemedAt);

      assertThat(result).isTrue();
      verify(stmt).setLong(1, TEST_USER_ID);
      verify(stmt).setTimestamp(2, Timestamp.from(redeemedAt));
      verify(stmt).setLong(3, TEST_CODE_ID);
      verify(stmt).setTimestamp(4, Timestamp.from(redeemedAt));
    }

    @Test
    @DisplayName("當沒有資料更新時應返回 false")
    void shouldReturnFalseWhenNoRowsUpdated() throws SQLException {
      PreparedStatement stmt = mock(PreparedStatement.class);
      Instant redeemedAt = Instant.parse("2026-02-01T00:00:00Z");

      when(connection.prepareStatement(anyString())).thenReturn(stmt);
      when(stmt.executeUpdate()).thenReturn(0);

      boolean result = repository.markAsRedeemedIfAvailable(TEST_CODE_ID, TEST_USER_ID, redeemedAt);

      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("當 redeemedAt 為 null 時應使用當前時間")
    void shouldUseCurrentTimeWhenRedeemedAtIsNull() throws SQLException {
      PreparedStatement stmt = mock(PreparedStatement.class);

      when(connection.prepareStatement(anyString())).thenReturn(stmt);
      when(stmt.executeUpdate()).thenReturn(1);

      Instant before = Instant.now();
      boolean result = repository.markAsRedeemedIfAvailable(TEST_CODE_ID, TEST_USER_ID, null);
      Instant after = Instant.now();

      ArgumentCaptor<Timestamp> redeemedAtCaptor = ArgumentCaptor.forClass(Timestamp.class);
      ArgumentCaptor<Timestamp> expirationCheckCaptor = ArgumentCaptor.forClass(Timestamp.class);
      verify(stmt).setTimestamp(org.mockito.ArgumentMatchers.eq(2), redeemedAtCaptor.capture());
      verify(stmt)
          .setTimestamp(org.mockito.ArgumentMatchers.eq(4), expirationCheckCaptor.capture());

      assertThat(result).isTrue();
      assertThat(redeemedAtCaptor.getValue().toInstant()).isBetween(before, after);
      assertThat(expirationCheckCaptor.getValue().toInstant()).isBetween(before, after);
    }

    @Test
    @DisplayName("應使用包含等號的到期邊界條件（expires_at >= now）")
    void shouldUseInclusiveExpirationBoundaryInSql() throws SQLException {
      PreparedStatement stmt = mock(PreparedStatement.class);
      ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);

      when(connection.prepareStatement(sqlCaptor.capture())).thenReturn(stmt);
      when(stmt.executeUpdate()).thenReturn(1);

      repository.markAsRedeemedIfAvailable(
          TEST_CODE_ID, TEST_USER_ID, Instant.parse("2026-02-01T00:00:00Z"));

      assertThat(sqlCaptor.getValue()).contains("expires_at >= ?");
    }

    @Test
    @DisplayName("當 JDBC 發生例外時應拋出 RepositoryException")
    void shouldThrowRepositoryExceptionWhenSqlFails() throws SQLException {
      when(connection.prepareStatement(anyString())).thenThrow(new SQLException("db error"));

      assertThatThrownBy(
              () ->
                  repository.markAsRedeemedIfAvailable(
                      TEST_CODE_ID, TEST_USER_ID, Instant.parse("2026-02-01T00:00:00Z")))
          .isInstanceOf(RepositoryException.class)
          .hasMessageContaining("Failed to atomically redeem code");
    }
  }

  @Nested
  @DisplayName("invalidated code queries")
  class InvalidatedCodeQueryTests {

    @Test
    @DisplayName("invalidateByProductId 不應清空 product_id，避免後續查詢失去產品邊界")
    void invalidateByProductIdShouldNotNullOutProductId() throws SQLException {
      PreparedStatement stmt = mock(PreparedStatement.class);
      ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);

      when(connection.prepareStatement(sqlCaptor.capture())).thenReturn(stmt);
      when(stmt.executeUpdate()).thenReturn(1);

      int affected = repository.invalidateByProductId(TEST_PRODUCT_ID);

      assertThat(affected).isEqualTo(1);
      assertThat(sqlCaptor.getValue()).contains("SET invalidated_at = NOW()");
      assertThat(sqlCaptor.getValue()).doesNotContain("product_id = NULL");
      verify(stmt).setLong(1, TEST_PRODUCT_ID);
    }

    @Test
    @DisplayName("findInvalidatedByProductId 應只查詢指定 product_id 的失效碼")
    void findInvalidatedByProductIdShouldScopeByProductId() throws SQLException {
      PreparedStatement stmt = mock(PreparedStatement.class);
      ResultSet rs = mock(ResultSet.class);
      ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);

      when(connection.prepareStatement(sqlCaptor.capture())).thenReturn(stmt);
      when(stmt.executeQuery()).thenReturn(rs);
      when(rs.next()).thenReturn(false);

      repository.findInvalidatedByProductId(TEST_PRODUCT_ID);

      assertThat(sqlCaptor.getValue())
          .contains("WHERE product_id = ? AND invalidated_at IS NOT NULL");
      verify(stmt).setLong(1, TEST_PRODUCT_ID);
    }
  }
}
