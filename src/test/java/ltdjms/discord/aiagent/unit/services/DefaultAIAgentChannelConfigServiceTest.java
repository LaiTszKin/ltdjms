package ltdjms.discord.aiagent.unit.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import ltdjms.discord.aiagent.domain.AIAgentChannelConfig;
import ltdjms.discord.aiagent.persistence.AIAgentChannelConfigRepository;
import ltdjms.discord.aiagent.services.DefaultAIAgentChannelConfigService;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.cache.CacheService;
import ltdjms.discord.shared.di.JDAProvider;
import ltdjms.discord.shared.events.DomainEventPublisher;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.unions.IThreadContainerUnion;

/**
 * {@link DefaultAIAgentChannelConfigService} 的單元測試。
 *
 * <p>測試重點：
 *
 * <ul>
 *   <li>討論串繼承父頻道配置功能
 *   <li>頻道類型檢測邏輯
 *   <li>錯誤處理場景
 * </ul>
 */
class DefaultAIAgentChannelConfigServiceTest {

  private static final long GUILD_ID = 123456789L;
  private static final long CHANNEL_ID = 987654321L;
  private static final long PARENT_CHANNEL_ID = 111111111L;
  private static final long THREAD_CHANNEL_ID = 222222222L;

  @Mock private AIAgentChannelConfigRepository repository;
  @Mock private CacheService cacheService;
  @Mock private DomainEventPublisher eventPublisher;
  @Mock private JDA jda;
  @Mock private Guild guild;
  @Mock private IThreadContainerUnion parentChannelUnion;
  @Mock private TextChannel parentChannel;
  @Mock private ThreadChannel threadChannel;

  private DefaultAIAgentChannelConfigService service;
  private AutoCloseable mocks;

  @BeforeEach
  void setUp() {
    mocks = MockitoAnnotations.openMocks(this);

    // 設置 JDAProvider
    JDAProvider.setJda(jda);

    // 建立服務
    service = new DefaultAIAgentChannelConfigService(repository, cacheService, eventPublisher);

    // 設置預設的 mock 行為
    when(jda.getGuildById(GUILD_ID)).thenReturn(guild);
    when(guild.getGuildChannelById(anyLong()))
        .thenAnswer(
            invocation -> {
              long channelId = invocation.getArgument(0);
              if (channelId == CHANNEL_ID) {
                return parentChannel;
              } else if (channelId == THREAD_CHANNEL_ID) {
                return threadChannel;
              }
              return null;
            });

    when(parentChannel.getType()).thenReturn(ChannelType.TEXT);
    when(parentChannel.getIdLong()).thenReturn(PARENT_CHANNEL_ID);
    when(parentChannelUnion.getIdLong()).thenReturn(PARENT_CHANNEL_ID);
    when(threadChannel.getType()).thenReturn(ChannelType.GUILD_PUBLIC_THREAD);
    when(threadChannel.getParentChannel()).thenReturn(parentChannelUnion);
    when(threadChannel.getIdLong()).thenReturn(THREAD_CHANNEL_ID);

    // 預設快取未命中
    when(cacheService.get(any(), eq(Boolean.class))).thenReturn(Optional.empty());
  }

  @AfterEach
  void tearDown() throws Exception {
    mocks.close();
    JDAProvider.clear();
  }

  @Nested
  class 討論串繼承功能 {

    @Test
    void isAgentEnabled_當頻道是文字頻道_返回正確配置() {
      // Arrange
      AIAgentChannelConfig config =
          AIAgentChannelConfig.create(GUILD_ID, CHANNEL_ID).withAgentEnabled(true);
      when(repository.findByChannelId(CHANNEL_ID)).thenReturn(Result.ok(Optional.of(config)));

      // Act
      boolean result = service.isAgentEnabled(GUILD_ID, CHANNEL_ID);

      // Assert
      assertTrue(result);
      verify(repository).findByChannelId(CHANNEL_ID);
      verify(cacheService)
          .put(
              eq(String.format("ai:agent:config:%d:%d", GUILD_ID, CHANNEL_ID)), eq(true), anyInt());
    }

    @Test
    void isAgentEnabled_當頻道是討論串且父頻道啟用_返回true() {
      // Arrange
      AIAgentChannelConfig parentConfig =
          AIAgentChannelConfig.create(GUILD_ID, PARENT_CHANNEL_ID).withAgentEnabled(true);
      when(repository.findByChannelId(PARENT_CHANNEL_ID))
          .thenReturn(Result.ok(Optional.of(parentConfig)));

      // Act
      boolean result = service.isAgentEnabled(GUILD_ID, THREAD_CHANNEL_ID);

      // Assert
      assertTrue(result);
      // 應該使用父頻道 ID 查詢，而非討論串 ID
      verify(repository).findByChannelId(PARENT_CHANNEL_ID);
      verify(repository, never()).findByChannelId(THREAD_CHANNEL_ID);
      // 快取應該使用父頻道 ID
      verify(cacheService)
          .put(
              eq(String.format("ai:agent:config:%d:%d", GUILD_ID, PARENT_CHANNEL_ID)),
              eq(true),
              anyInt());
    }

    @Test
    void isAgentEnabled_當頻道是討論串且父頻道停用_返回false() {
      // Arrange
      AIAgentChannelConfig parentConfig =
          AIAgentChannelConfig.create(GUILD_ID, PARENT_CHANNEL_ID).withAgentEnabled(false);
      when(repository.findByChannelId(PARENT_CHANNEL_ID))
          .thenReturn(Result.ok(Optional.of(parentConfig)));

      // Act
      boolean result = service.isAgentEnabled(GUILD_ID, THREAD_CHANNEL_ID);

      // Assert
      assertFalse(result);
      verify(repository).findByChannelId(PARENT_CHANNEL_ID);
    }

    @Test
    void isAgentEnabled_當頻道是討論串且父頻道無配置_返回false() {
      // Arrange
      when(repository.findByChannelId(PARENT_CHANNEL_ID)).thenReturn(Result.ok(Optional.empty()));

      // Act
      boolean result = service.isAgentEnabled(GUILD_ID, THREAD_CHANNEL_ID);

      // Assert
      assertFalse(result);
      verify(repository).findByChannelId(PARENT_CHANNEL_ID);
      // 不應該寫入快取
      verify(cacheService, never()).put(any(), anyBoolean(), anyInt());
    }
  }

  @Nested
  class 錯誤處理 {

    @Test
    void isAgentEnabled_當Guild不存在_返回false() {
      // Arrange
      when(jda.getGuildById(GUILD_ID)).thenReturn(null);

      // Act
      boolean result = service.isAgentEnabled(GUILD_ID, CHANNEL_ID);

      // Assert
      assertFalse(result);
      // 不應該查詢 repository 或寫入快取
      verify(repository, never()).findByChannelId(anyLong());
      verify(cacheService, never()).put(any(), anyBoolean(), anyInt());
    }

    @Test
    void isAgentEnabled_當頻道不存在_返回false() {
      // Arrange
      long nonExistentChannelId = 999999999L;
      when(guild.getGuildChannelById(nonExistentChannelId)).thenReturn(null);

      // Act
      boolean result = service.isAgentEnabled(GUILD_ID, nonExistentChannelId);

      // Assert
      assertFalse(result);
      // 不應該查詢 repository 或寫入快取
      verify(repository, never()).findByChannelId(anyLong());
      verify(cacheService, never()).put(any(), anyBoolean(), anyInt());
    }

    @Test
    void isAgentEnabled_當資料庫查詢失敗_返回false() {
      // Arrange
      when(repository.findByChannelId(CHANNEL_ID))
          .thenReturn(Result.err(new Exception("Database error")));

      // Act
      boolean result = service.isAgentEnabled(GUILD_ID, CHANNEL_ID);

      // Assert
      assertFalse(result);
      verify(cacheService, never()).put(any(), anyBoolean(), anyInt());
    }
  }

  @Nested
  class 快取功能 {

    @Test
    void isAgentEnabled_當快取命中_直接返回快取值() {
      // Arrange
      when(cacheService.get(
              eq(String.format("ai:agent:config:%d:%d", GUILD_ID, CHANNEL_ID)), eq(Boolean.class)))
          .thenReturn(Optional.of(true));

      // Act
      boolean result = service.isAgentEnabled(GUILD_ID, CHANNEL_ID);

      // Assert
      assertTrue(result);
      // 不應該查詢 repository
      verify(repository, never()).findByChannelId(anyLong());
    }

    @Test
    void isAgentEnabled_當討論串快取命中_直接返回快取值() {
      // Arrange
      String cacheKey = String.format("ai:agent:config:%d:%d", GUILD_ID, PARENT_CHANNEL_ID);
      when(cacheService.get(eq(cacheKey), eq(Boolean.class))).thenReturn(Optional.of(true));

      // Act
      boolean result = service.isAgentEnabled(GUILD_ID, THREAD_CHANNEL_ID);

      // Assert
      assertTrue(result);
      // 不應該查詢 repository
      verify(repository, never()).findByChannelId(anyLong());
    }
  }
}
