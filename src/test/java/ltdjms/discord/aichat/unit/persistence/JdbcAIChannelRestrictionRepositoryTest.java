package ltdjms.discord.aichat.unit.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import ltdjms.discord.aichat.domain.AllowedChannel;
import ltdjms.discord.aichat.persistence.AIChannelRestrictionRepository;
import ltdjms.discord.aichat.persistence.JdbcAIChannelRestrictionRepository;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.Unit;

/** 測試 {@link JdbcAIChannelRestrictionRepository}。 */
@DisplayName("JdbcAIChannelRestrictionRepository 測試")
class JdbcAIChannelRestrictionRepositoryTest {

  private DataSource dataSource;
  private Connection connection;
  private AIChannelRestrictionRepository repository;

  @BeforeEach
  void setUp() throws SQLException {
    dataSource = mock(DataSource.class);
    connection = mock(Connection.class);
    when(dataSource.getConnection()).thenReturn(connection);
    repository = new JdbcAIChannelRestrictionRepository(dataSource);
  }

  @Nested
  @DisplayName("findByGuildId")
  class FindByGuildId {

    @Test
    @DisplayName("應成功返回伺服器的允許頻道清單")
    void shouldReturnAllowedChannels() throws SQLException {
      PreparedStatement stmt = mock(PreparedStatement.class);
      ResultSet rs = mock(ResultSet.class);
      when(connection.prepareStatement(anyString())).thenReturn(stmt);
      when(stmt.executeQuery()).thenReturn(rs);
      when(rs.next()).thenReturn(true, true, false);
      when(rs.getLong("channel_id")).thenReturn(1001L, 1002L);
      when(rs.getString("channel_name")).thenReturn("general", "ai-chat");

      Result<Set<AllowedChannel>, DomainError> result = repository.findByGuildId(123L);

      assertTrue(result.isOk());
      Set<AllowedChannel> channels = result.getValue();
      assertEquals(2, channels.size());
      assertTrue(channels.stream().anyMatch(c -> c.channelId() == 1001L));
      assertTrue(channels.stream().anyMatch(c -> c.channelId() == 1002L));
    }

    @Test
    @DisplayName("當無允許頻道時，應返回空集合")
    void shouldReturnEmptySetWhenNoChannels() throws SQLException {
      PreparedStatement stmt = mock(PreparedStatement.class);
      ResultSet rs = mock(ResultSet.class);
      when(connection.prepareStatement(anyString())).thenReturn(stmt);
      when(stmt.executeQuery()).thenReturn(rs);
      when(rs.next()).thenReturn(false);

      Result<Set<AllowedChannel>, DomainError> result = repository.findByGuildId(123L);

      assertTrue(result.isOk());
      assertTrue(result.getValue().isEmpty());
    }

    @Test
    @DisplayName("當資料庫錯誤時，應返回 PERSISTENCE_FAILURE 錯誤")
    void shouldReturnErrorWhenDatabaseFails() throws SQLException {
      when(connection.prepareStatement(anyString())).thenThrow(new SQLException("DB error"));

      Result<Set<AllowedChannel>, DomainError> result = repository.findByGuildId(123L);

      assertTrue(result.isErr());
      assertEquals(DomainError.Category.PERSISTENCE_FAILURE, result.getError().category());
    }
  }

  @Nested
  @DisplayName("addChannel")
  class AddChannel {

    @Test
    @DisplayName("應成功新增允許頻道")
    void shouldAddChannel() throws SQLException {
      PreparedStatement stmt = mock(PreparedStatement.class);
      when(connection.prepareStatement(anyString())).thenReturn(stmt);
      when(stmt.executeUpdate()).thenReturn(1);

      AllowedChannel channel = new AllowedChannel(1001L, "general");
      Result<AllowedChannel, DomainError> result = repository.addChannel(123L, channel);

      assertTrue(result.isOk());
      assertEquals(channel, result.getValue());
      verify(stmt).setLong(1, 123L);
      verify(stmt).setLong(2, 1001L);
      verify(stmt).setString(3, "general");
    }

    @Test
    @DisplayName("當頻道已存在時，應返回 DUPLICATE_CHANNEL 錯誤")
    void shouldReturnErrorWhenDuplicate() throws SQLException {
      PreparedStatement stmt = mock(PreparedStatement.class);
      when(connection.prepareStatement(anyString())).thenReturn(stmt);
      when(stmt.executeUpdate()).thenThrow(new SQLException("Duplicate key"));

      AllowedChannel channel = new AllowedChannel(1001L, "general");
      Result<AllowedChannel, DomainError> result = repository.addChannel(123L, channel);

      assertTrue(result.isErr());
      assertEquals(DomainError.Category.DUPLICATE_CHANNEL, result.getError().category());
    }
  }

  @Nested
  @DisplayName("removeChannel")
  class RemoveChannel {

    @Test
    @DisplayName("應成功移除允許頻道")
    void shouldRemoveChannel() throws SQLException {
      PreparedStatement stmt = mock(PreparedStatement.class);
      when(connection.prepareStatement(anyString())).thenReturn(stmt);
      when(stmt.executeUpdate()).thenReturn(1);

      Result<Unit, DomainError> result = repository.removeChannel(123L, 1001L);

      assertTrue(result.isOk());
      verify(stmt).setLong(1, 123L);
      verify(stmt).setLong(2, 1001L);
    }

    @Test
    @DisplayName("當頻道不存在時，應返回 CHANNEL_NOT_FOUND 錯誤")
    void shouldReturnErrorWhenNotFound() throws SQLException {
      PreparedStatement stmt = mock(PreparedStatement.class);
      when(connection.prepareStatement(anyString())).thenReturn(stmt);
      when(stmt.executeUpdate()).thenReturn(0);

      Result<Unit, DomainError> result = repository.removeChannel(123L, 1001L);

      assertTrue(result.isErr());
      assertEquals(DomainError.Category.CHANNEL_NOT_FOUND, result.getError().category());
    }
  }
}
