package ltdjms.discord.aiagent.unit.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import ltdjms.discord.aiagent.domain.AIAgentChannelConfig;
import ltdjms.discord.aiagent.persistence.AIAgentChannelConfigRepository;
import ltdjms.discord.aiagent.services.AIAgentChannelConfigService;
import ltdjms.discord.aiagent.services.DefaultAIAgentChannelConfigService;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.Unit;
import ltdjms.discord.shared.cache.CacheService;
import ltdjms.discord.shared.di.JDAProvider;
import ltdjms.discord.shared.events.AIAgentChannelConfigChangedEvent;
import ltdjms.discord.shared.events.DomainEventPublisher;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;

/**
 * 測試 {@link DefaultAIAgentChannelConfigService} 的服務邏輯。
 *
 * <p>測試範圍：
 *
 * <ul>
 *   <li>T024: AIAgentChannelConfigService 單元測試
 * </ul>
 */
@DisplayName("T024: AIAgentChannelConfigService 單元測試")
class AIAgentChannelConfigServiceTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_CHANNEL_ID = 111111111111111111L;

  private AIAgentChannelConfigRepository repository;
  private CacheService cacheService;
  private DomainEventPublisher eventPublisher;
  private AIAgentChannelConfigService service;
  private JDA mockJda;
  private Guild mockGuild;
  private GuildChannel mockChannel;

  @BeforeEach
  void setUp() {
    repository = mock(AIAgentChannelConfigRepository.class);
    cacheService = mock(CacheService.class);
    eventPublisher = mock(DomainEventPublisher.class);

    // 設置 JDA mock
    mockJda = mock(JDA.class);
    mockGuild = mock(Guild.class);
    mockChannel = mock(TextChannel.class);

    when(mockJda.getGuildById(TEST_GUILD_ID)).thenReturn(mockGuild);
    when(mockGuild.getGuildChannelById(TEST_CHANNEL_ID)).thenReturn(mockChannel);
    when(mockChannel.getType()).thenReturn(ChannelType.TEXT);
    when(mockChannel.getIdLong()).thenReturn(TEST_CHANNEL_ID);

    JDAProvider.setJda(mockJda);

    service = new DefaultAIAgentChannelConfigService(repository, cacheService, eventPublisher);
  }

  @AfterEach
  void tearDown() {
    JDAProvider.clear();
  }

  @Nested
  @DisplayName("isAgentEnabled - 檢查頻道是否啟用 AI Agent")
  class IsAgentEnabledTests {

    @Test
    @DisplayName("當頻道已啟用 Agent 時，應返回 true")
    void shouldReturnTrueWhenEnabled() {
      AIAgentChannelConfig config = AIAgentChannelConfig.create(TEST_GUILD_ID, TEST_CHANNEL_ID);
      when(cacheService.get(any(), eq(Boolean.class))).thenReturn(Optional.empty());
      when(repository.findByChannelId(TEST_CHANNEL_ID)).thenReturn(Result.ok(Optional.of(config)));

      boolean enabled = service.isAgentEnabled(TEST_GUILD_ID, TEST_CHANNEL_ID);

      assertThat(enabled).isTrue();
      verify(cacheService).put(any(), eq(true), anyInt());
    }

    @Test
    @DisplayName("當頻道配置不存在時，應返回 false")
    void shouldReturnFalseWhenNotConfigured() {
      when(cacheService.get(any(), eq(Boolean.class))).thenReturn(Optional.empty());
      when(repository.findByChannelId(TEST_CHANNEL_ID)).thenReturn(Result.ok(Optional.empty()));

      boolean enabled = service.isAgentEnabled(TEST_GUILD_ID, TEST_CHANNEL_ID);

      assertThat(enabled).isFalse();
    }

    @Test
    @DisplayName("當從快取讀取時，應直接返回快取值")
    void shouldReturnCachedValue() {
      when(cacheService.get(any(), eq(Boolean.class))).thenReturn(Optional.of(true));

      boolean enabled = service.isAgentEnabled(TEST_GUILD_ID, TEST_CHANNEL_ID);

      assertThat(enabled).isTrue();
      verify(repository, never()).findByChannelId(TEST_CHANNEL_ID);
    }

    @Test
    @DisplayName("當資料庫查詢失敗時，應返回 false")
    void shouldReturnFalseWhenDatabaseFails() {
      when(cacheService.get(any(), eq(Boolean.class))).thenReturn(Optional.empty());
      when(repository.findByChannelId(TEST_CHANNEL_ID))
          .thenReturn(Result.err(new Exception("DB error")));

      boolean enabled = service.isAgentEnabled(TEST_GUILD_ID, TEST_CHANNEL_ID);

      assertThat(enabled).isFalse();
    }
  }

  @Nested
  @DisplayName("setAgentEnabled - 設定 Agent 模式狀態")
  class SetAgentEnabledTests {

    @Test
    @DisplayName("應成功啟用頻道的 Agent 模式")
    void shouldEnableAgentMode() {
      AIAgentChannelConfig config = AIAgentChannelConfig.create(TEST_GUILD_ID, TEST_CHANNEL_ID);
      when(repository.findByChannelId(TEST_CHANNEL_ID)).thenReturn(Result.ok(Optional.empty()));
      when(repository.save(any())).thenReturn(Result.ok(config.withAgentEnabled(true)));

      Result<Unit, DomainError> result =
          service.setAgentEnabled(TEST_GUILD_ID, TEST_CHANNEL_ID, true);

      assertThat(result.isOk()).isTrue();
      verify(cacheService).put(any(), eq(true), anyInt());
      verify(eventPublisher).publish(any(AIAgentChannelConfigChangedEvent.class));
    }

    @Test
    @DisplayName("應成功停用頻道的 Agent 模式")
    void shouldDisableAgentMode() {
      AIAgentChannelConfig config = AIAgentChannelConfig.create(TEST_GUILD_ID, TEST_CHANNEL_ID);
      when(repository.findByChannelId(TEST_CHANNEL_ID)).thenReturn(Result.ok(Optional.of(config)));
      when(repository.save(any())).thenReturn(Result.ok(config.withAgentEnabled(false)));

      Result<Unit, DomainError> result =
          service.setAgentEnabled(TEST_GUILD_ID, TEST_CHANNEL_ID, false);

      assertThat(result.isOk()).isTrue();
      verify(cacheService).put(any(), eq(false), anyInt());
      verify(eventPublisher).publish(any(AIAgentChannelConfigChangedEvent.class));
    }

    @Test
    @DisplayName("當資料庫查詢失敗時，應返回錯誤")
    void shouldReturnErrorWhenQueryFails() {
      when(repository.findByChannelId(TEST_CHANNEL_ID))
          .thenReturn(Result.err(new Exception("DB error")));

      Result<Unit, DomainError> result =
          service.setAgentEnabled(TEST_GUILD_ID, TEST_CHANNEL_ID, true);

      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().category()).isEqualTo(DomainError.Category.PERSISTENCE_FAILURE);
    }

    @Test
    @DisplayName("當儲存失敗時，應返回錯誤")
    void shouldReturnErrorWhenSaveFails() {
      AIAgentChannelConfig config = AIAgentChannelConfig.create(TEST_GUILD_ID, TEST_CHANNEL_ID);
      when(repository.findByChannelId(TEST_CHANNEL_ID)).thenReturn(Result.ok(Optional.empty()));
      when(repository.save(any())).thenReturn(Result.err(new Exception("Save error")));

      Result<Unit, DomainError> result =
          service.setAgentEnabled(TEST_GUILD_ID, TEST_CHANNEL_ID, true);

      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().category()).isEqualTo(DomainError.Category.PERSISTENCE_FAILURE);
    }
  }

  @Nested
  @DisplayName("toggleAgentMode - 切換 Agent 模式狀態")
  class ToggleAgentModeTests {

    @Test
    @DisplayName("應成功從啟用切換為停用")
    void shouldToggleFromEnabledToDisabled() {
      AIAgentChannelConfig config = AIAgentChannelConfig.create(TEST_GUILD_ID, TEST_CHANNEL_ID);
      when(repository.findByChannelId(TEST_CHANNEL_ID)).thenReturn(Result.ok(Optional.of(config)));
      when(repository.save(any())).thenReturn(Result.ok(config.toggleAgentMode()));

      Result<Boolean, DomainError> result = service.toggleAgentMode(TEST_GUILD_ID, TEST_CHANNEL_ID);

      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue()).isFalse();
      verify(eventPublisher).publish(any(AIAgentChannelConfigChangedEvent.class));
    }

    @Test
    @DisplayName("應成功從停用切換為啟用")
    void shouldToggleFromDisabledToEnabled() {
      AIAgentChannelConfig config =
          AIAgentChannelConfig.create(TEST_GUILD_ID, TEST_CHANNEL_ID).withAgentEnabled(false);
      when(repository.findByChannelId(TEST_CHANNEL_ID)).thenReturn(Result.ok(Optional.of(config)));
      when(repository.save(any())).thenReturn(Result.ok(config.toggleAgentMode()));

      Result<Boolean, DomainError> result = service.toggleAgentMode(TEST_GUILD_ID, TEST_CHANNEL_ID);

      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue()).isTrue();
    }

    @Test
    @DisplayName("當配置不存在時，應建立新配置並切換")
    void shouldCreateNewConfigWhenNotExists() {
      AIAgentChannelConfig newConfig =
          AIAgentChannelConfig.create(TEST_GUILD_ID, TEST_CHANNEL_ID).toggleAgentMode();
      when(repository.findByChannelId(TEST_CHANNEL_ID)).thenReturn(Result.ok(Optional.empty()));
      when(repository.save(any())).thenReturn(Result.ok(newConfig));

      Result<Boolean, DomainError> result = service.toggleAgentMode(TEST_GUILD_ID, TEST_CHANNEL_ID);

      assertThat(result.isOk()).isTrue();
    }
  }

  @Nested
  @DisplayName("getEnabledChannels - 獲取啟用的頻道列表")
  class GetEnabledChannelsTests {

    @Test
    @DisplayName("應成功返回所有啟用的頻道 ID")
    void shouldReturnEnabledChannelIds() {
      AIAgentChannelConfig config1 = AIAgentChannelConfig.create(TEST_GUILD_ID, TEST_CHANNEL_ID);
      AIAgentChannelConfig config2 =
          AIAgentChannelConfig.create(TEST_GUILD_ID, 222222222222222222L);
      when(repository.findEnabledByGuildId(TEST_GUILD_ID))
          .thenReturn(Result.ok(List.of(config1, config2)));

      Result<List<Long>, DomainError> result = service.getEnabledChannels(TEST_GUILD_ID);

      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue()).containsExactly(TEST_CHANNEL_ID, 222222222222222222L);
    }

    @Test
    @DisplayName("當無啟用頻道時，應返回空列表")
    void shouldReturnEmptyListWhenNoEnabledChannels() {
      when(repository.findEnabledByGuildId(TEST_GUILD_ID)).thenReturn(Result.ok(List.of()));

      Result<List<Long>, DomainError> result = service.getEnabledChannels(TEST_GUILD_ID);

      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue()).isEmpty();
    }

    @Test
    @DisplayName("當查詢失敗時，應返回錯誤")
    void shouldReturnErrorWhenQueryFails() {
      when(repository.findEnabledByGuildId(TEST_GUILD_ID))
          .thenReturn(Result.err(new Exception("DB error")));

      Result<List<Long>, DomainError> result = service.getEnabledChannels(TEST_GUILD_ID);

      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().category()).isEqualTo(DomainError.Category.PERSISTENCE_FAILURE);
    }
  }

  @Nested
  @DisplayName("removeChannel - 移除頻道配置")
  class RemoveChannelTests {

    @Test
    @DisplayName("應成功移除頻道配置")
    void shouldRemoveChannel() {
      when(repository.deleteByChannelId(TEST_CHANNEL_ID)).thenReturn(Result.okVoid());

      Result<Unit, DomainError> result = service.removeChannel(TEST_GUILD_ID, TEST_CHANNEL_ID);

      assertThat(result.isOk()).isTrue();
      verify(cacheService).invalidate(any());
    }

    @Test
    @DisplayName("當刪除失敗時，應返回錯誤")
    void shouldReturnErrorWhenDeleteFails() {
      when(repository.deleteByChannelId(TEST_CHANNEL_ID))
          .thenReturn(Result.err(new Exception("Delete error")));

      Result<Unit, DomainError> result = service.removeChannel(TEST_GUILD_ID, TEST_CHANNEL_ID);

      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().category()).isEqualTo(DomainError.Category.PERSISTENCE_FAILURE);
    }
  }
}
