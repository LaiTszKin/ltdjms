package ltdjms.discord.aiagent.unit.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import ltdjms.discord.aiagent.domain.AIAgentChannelConfig;
import ltdjms.discord.aiagent.persistence.AIAgentChannelConfigRepository;
import ltdjms.discord.aiagent.persistence.JdbcAIAgentChannelConfigRepository;

/**
 * 測試 {@link JdbcAIAgentChannelConfigRepository} 的 JDBC 操作。
 *
 * <p>測試範圍：
 *
 * <ul>
 *   <li>T023: JdbcAIAgentChannelConfigRepository 單元測試
 * </ul>
 */
@DisplayName("T023: JdbcAIAgentChannelConfigRepository 單元測試")
class JdbcAIAgentChannelConfigRepositoryTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_CHANNEL_ID = 111111111111111111L;
  private static final long TEST_ID = 1L;
  private static final LocalDateTime TEST_TIME = LocalDateTime.of(2025, 12, 29, 10, 0);

  private DataSource dataSource;
  private Connection connection;
  private AIAgentChannelConfigRepository repository;

  @BeforeEach
  void setUp() throws SQLException {
    dataSource = mock(DataSource.class);
    connection = mock(Connection.class);
    when(dataSource.getConnection()).thenReturn(connection);
    repository = new JdbcAIAgentChannelConfigRepository(dataSource);
  }

  @Nested
  @DisplayName("save - 儲存或更新配置")
  class SaveTests {

    @Test
    @DisplayName("應成功儲存新配置")
    void shouldSaveNewConfig() throws SQLException {
      PreparedStatement stmt = mock(PreparedStatement.class);
      ResultSet rs = mock(ResultSet.class);
      when(connection.prepareStatement(anyString())).thenReturn(stmt);
      when(stmt.executeQuery()).thenReturn(rs);
      when(rs.next()).thenReturn(true);
      when(rs.getLong("id")).thenReturn(TEST_ID);
      when(rs.getLong("guild_id")).thenReturn(TEST_GUILD_ID);
      when(rs.getLong("channel_id")).thenReturn(TEST_CHANNEL_ID);
      when(rs.getBoolean("agent_enabled")).thenReturn(true);
      when(rs.getTimestamp("created_at")).thenReturn(java.sql.Timestamp.valueOf(TEST_TIME));
      when(rs.getTimestamp("updated_at")).thenReturn(java.sql.Timestamp.valueOf(TEST_TIME));

      AIAgentChannelConfig config = AIAgentChannelConfig.create(TEST_GUILD_ID, TEST_CHANNEL_ID);

      var result = repository.save(config);

      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().id()).isEqualTo(TEST_ID);
      assertThat(result.getValue().agentEnabled()).isTrue();
      verify(stmt).setLong(1, TEST_GUILD_ID);
      verify(stmt).setLong(2, TEST_CHANNEL_ID);
      verify(stmt).setBoolean(3, true);
    }

    @Test
    @DisplayName("應成功更新現有配置（Upsert）")
    void shouldUpdateExistingConfig() throws SQLException {
      PreparedStatement stmt = mock(PreparedStatement.class);
      ResultSet rs = mock(ResultSet.class);
      when(connection.prepareStatement(anyString())).thenReturn(stmt);
      when(stmt.executeQuery()).thenReturn(rs);
      when(rs.next()).thenReturn(true);
      when(rs.getLong("id")).thenReturn(TEST_ID);
      when(rs.getLong("guild_id")).thenReturn(TEST_GUILD_ID);
      when(rs.getLong("channel_id")).thenReturn(TEST_CHANNEL_ID);
      when(rs.getBoolean("agent_enabled")).thenReturn(false);
      when(rs.getTimestamp("created_at")).thenReturn(java.sql.Timestamp.valueOf(TEST_TIME));
      when(rs.getTimestamp("updated_at")).thenReturn(java.sql.Timestamp.valueOf(TEST_TIME));

      AIAgentChannelConfig config =
          new AIAgentChannelConfig(
              TEST_ID, TEST_GUILD_ID, TEST_CHANNEL_ID, false, TEST_TIME, TEST_TIME);

      var result = repository.save(config);

      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().agentEnabled()).isFalse();
    }

    @Test
    @DisplayName("當資料庫錯誤時，應返回錯誤")
    void shouldReturnErrorWhenDatabaseFails() throws SQLException {
      when(connection.prepareStatement(anyString())).thenThrow(new SQLException("DB error"));

      AIAgentChannelConfig config = AIAgentChannelConfig.create(TEST_GUILD_ID, TEST_CHANNEL_ID);

      var result = repository.save(config);

      assertThat(result.isErr()).isTrue();
      assertThat(result.getError()).isInstanceOf(SQLException.class);
    }
  }

  @Nested
  @DisplayName("findByChannelId - 根據頻道 ID 查找配置")
  class FindByChannelIdTests {

    @Test
    @DisplayName("應成功找到現有配置")
    void shouldFindExistingConfig() throws SQLException {
      PreparedStatement stmt = mock(PreparedStatement.class);
      ResultSet rs = mock(ResultSet.class);
      when(connection.prepareStatement(anyString())).thenReturn(stmt);
      when(stmt.executeQuery()).thenReturn(rs);
      when(rs.next()).thenReturn(true);
      when(rs.getLong("id")).thenReturn(TEST_ID);
      when(rs.getLong("guild_id")).thenReturn(TEST_GUILD_ID);
      when(rs.getLong("channel_id")).thenReturn(TEST_CHANNEL_ID);
      when(rs.getBoolean("agent_enabled")).thenReturn(true);
      when(rs.getTimestamp("created_at")).thenReturn(java.sql.Timestamp.valueOf(TEST_TIME));
      when(rs.getTimestamp("updated_at")).thenReturn(java.sql.Timestamp.valueOf(TEST_TIME));

      var result = repository.findByChannelId(TEST_CHANNEL_ID);

      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue()).isPresent();
      assertThat(result.getValue().get().channelId()).isEqualTo(TEST_CHANNEL_ID);
      assertThat(result.getValue().get().agentEnabled()).isTrue();
    }

    @Test
    @DisplayName("當配置不存在時，應返回 empty")
    void shouldReturnEmptyWhenNotFound() throws SQLException {
      PreparedStatement stmt = mock(PreparedStatement.class);
      ResultSet rs = mock(ResultSet.class);
      when(connection.prepareStatement(anyString())).thenReturn(stmt);
      when(stmt.executeQuery()).thenReturn(rs);
      when(rs.next()).thenReturn(false);

      var result = repository.findByChannelId(TEST_CHANNEL_ID);

      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue()).isEmpty();
    }

    @Test
    @DisplayName("當資料庫錯誤時，應返回錯誤")
    void shouldReturnErrorWhenDatabaseFails() throws SQLException {
      when(connection.prepareStatement(anyString())).thenThrow(new SQLException("DB error"));

      var result = repository.findByChannelId(TEST_CHANNEL_ID);

      assertThat(result.isErr()).isTrue();
      assertThat(result.getError()).isInstanceOf(SQLException.class);
    }
  }

  @Nested
  @DisplayName("findEnabledByGuildId - 查找伺服器中啟用的頻道")
  class FindEnabledByGuildIdTests {

    @Test
    @DisplayName("應成功找到所有啟用的頻道配置")
    void shouldFindAllEnabledChannels() throws SQLException {
      PreparedStatement stmt = mock(PreparedStatement.class);
      ResultSet rs = mock(ResultSet.class);
      when(connection.prepareStatement(anyString())).thenReturn(stmt);
      when(stmt.executeQuery()).thenReturn(rs);
      when(rs.next()).thenReturn(true, true, false);
      when(rs.getLong("id")).thenReturn(1L, 2L);
      when(rs.getLong("guild_id")).thenReturn(TEST_GUILD_ID, TEST_GUILD_ID);
      when(rs.getLong("channel_id")).thenReturn(TEST_CHANNEL_ID, 222222222222222222L);
      when(rs.getBoolean("agent_enabled")).thenReturn(true, true);
      when(rs.getTimestamp("created_at"))
          .thenReturn(java.sql.Timestamp.valueOf(TEST_TIME), java.sql.Timestamp.valueOf(TEST_TIME));
      when(rs.getTimestamp("updated_at"))
          .thenReturn(java.sql.Timestamp.valueOf(TEST_TIME), java.sql.Timestamp.valueOf(TEST_TIME));

      var result = repository.findEnabledByGuildId(TEST_GUILD_ID);

      assertThat(result.isOk()).isTrue();
      List<AIAgentChannelConfig> configs = result.getValue();
      assertThat(configs).hasSize(2);
      assertThat(configs.get(0).channelId()).isEqualTo(TEST_CHANNEL_ID);
      assertThat(configs.get(1).channelId()).isEqualTo(222222222222222222L);
    }

    @Test
    @DisplayName("當無啟用頻道時，應返回空列表")
    void shouldReturnEmptyListWhenNoEnabledChannels() throws SQLException {
      PreparedStatement stmt = mock(PreparedStatement.class);
      ResultSet rs = mock(ResultSet.class);
      when(connection.prepareStatement(anyString())).thenReturn(stmt);
      when(stmt.executeQuery()).thenReturn(rs);
      when(rs.next()).thenReturn(false);

      var result = repository.findEnabledByGuildId(TEST_GUILD_ID);

      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue()).isEmpty();
    }

    @Test
    @DisplayName("當資料庫錯誤時，應返回錯誤")
    void shouldReturnErrorWhenDatabaseFails() throws SQLException {
      when(connection.prepareStatement(anyString())).thenThrow(new SQLException("DB error"));

      var result = repository.findEnabledByGuildId(TEST_GUILD_ID);

      assertThat(result.isErr()).isTrue();
      assertThat(result.getError()).isInstanceOf(SQLException.class);
    }
  }

  @Nested
  @DisplayName("deleteByChannelId - 刪除頻道配置")
  class DeleteByChannelIdTests {

    @Test
    @DisplayName("應成功刪除配置")
    void shouldDeleteConfig() throws SQLException {
      PreparedStatement stmt = mock(PreparedStatement.class);
      when(connection.prepareStatement(anyString())).thenReturn(stmt);
      when(stmt.executeUpdate()).thenReturn(1);

      var result = repository.deleteByChannelId(TEST_CHANNEL_ID);

      assertThat(result.isOk()).isTrue();
      verify(stmt).setLong(1, TEST_CHANNEL_ID);
    }

    @Test
    @DisplayName("當配置不存在時，應仍返回成功")
    void shouldReturnSuccessWhenConfigNotExists() throws SQLException {
      PreparedStatement stmt = mock(PreparedStatement.class);
      when(connection.prepareStatement(anyString())).thenReturn(stmt);
      when(stmt.executeUpdate()).thenReturn(0);

      var result = repository.deleteByChannelId(TEST_CHANNEL_ID);

      assertThat(result.isOk()).isTrue();
    }

    @Test
    @DisplayName("當資料庫錯誤時，應返回錯誤")
    void shouldReturnErrorWhenDatabaseFails() throws SQLException {
      when(connection.prepareStatement(anyString())).thenThrow(new SQLException("DB error"));

      var result = repository.deleteByChannelId(TEST_CHANNEL_ID);

      assertThat(result.isErr()).isTrue();
      assertThat(result.getError()).isInstanceOf(SQLException.class);
    }
  }
}
