package ltdjms.discord.aiagent.services;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.aiagent.domain.AIAgentChannelConfig;
import ltdjms.discord.aiagent.persistence.AIAgentChannelConfigRepository;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.Unit;
import ltdjms.discord.shared.cache.CacheService;
import ltdjms.discord.shared.di.JDAProvider;
import ltdjms.discord.shared.events.AIAgentChannelConfigChangedEvent;
import ltdjms.discord.shared.events.DomainEventPublisher;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;

/**
 * 預設實作的 AI Agent 頻道配置服務。
 *
 * <p>管理頻道的 AI Agent 模式啟用狀態，並使用 Redis 快取提升效能。
 */
public class DefaultAIAgentChannelConfigService implements AIAgentChannelConfigService {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DefaultAIAgentChannelConfigService.class);

  private final AIAgentChannelConfigRepository repository;
  private final CacheService cacheService;
  private final DomainEventPublisher eventPublisher;

  /** 快取鍵格式 */
  private static final String CACHE_KEY_FORMAT = "ai:agent:config:%d:%d";

  /** 快取 TTL：1 小時 */
  private static final long CACHE_TTL_SECONDS = 3600;

  /**
   * 建立服務。
   *
   * @param repository 配置 Repository
   * @param cacheService 快取服務
   * @param eventPublisher 事件發布器
   */
  public DefaultAIAgentChannelConfigService(
      AIAgentChannelConfigRepository repository,
      CacheService cacheService,
      DomainEventPublisher eventPublisher) {
    this.repository = repository;
    this.cacheService = cacheService;
    this.eventPublisher = eventPublisher;
  }

  @Override
  public boolean isAgentEnabled(long guildId, long channelId) {
    // 解析實際配置頻道 ID（討論串會繼承父頻道配置）
    long effectiveChannelId = resolveEffectiveChannelId(guildId, channelId);
    if (effectiveChannelId == -1) {
      // 解析失敗，預設停用
      return false;
    }

    // 嘗試從快取讀取
    String cacheKey = String.format(CACHE_KEY_FORMAT, guildId, effectiveChannelId);
    Optional<Boolean> cached = cacheService.get(cacheKey, Boolean.class);

    if (cached.isPresent()) {
      return cached.get();
    }

    // 從資料庫讀取
    Result<Optional<AIAgentChannelConfig>, Exception> result =
        repository.findByChannelId(effectiveChannelId);

    if (result.isErr() || result.getValue().isEmpty()) {
      // 未找到配置，預設停用
      return false;
    }

    boolean enabled = result.getValue().get().agentEnabled();

    // 寫入快取
    cacheService.put(cacheKey, enabled, (int) CACHE_TTL_SECONDS);

    return enabled;
  }

  /**
   * 解析實際配置頻道 ID。
   *
   * <p>如果頻道是討論串，則返回其父頻道 ID；否則返回原頻道 ID。
   *
   * @param guildId Discord 伺服器 ID
   * @param channelId Discord 頻道 ID
   * @return 實際配置頻道 ID，如果解析失敗則返回 -1
   */
  private long resolveEffectiveChannelId(long guildId, long channelId) {
    try {
      Guild guild = JDAProvider.getJda().getGuildById(guildId);
      if (guild == null) {
        LOGGER.debug("Guild {} 不存在", guildId);
        return -1;
      }

      GuildChannel channel = guild.getGuildChannelById(channelId);
      if (channel == null) {
        LOGGER.debug("頻道 {} 在 Guild {} 中不存在", channelId, guildId);
        return -1;
      }

      // 如果是討論串，返回父頻道 ID
      ChannelType type = channel.getType();
      if (type == ChannelType.GUILD_PUBLIC_THREAD
          || type == ChannelType.GUILD_PRIVATE_THREAD
          || type == ChannelType.GUILD_NEWS_THREAD) {
        ThreadChannel threadChannel = (ThreadChannel) channel;
        long parentId = threadChannel.getParentChannel().getIdLong();
        LOGGER.debug("頻道 {} 是討論串，使用父頻道 {}", channelId, parentId);
        return parentId;
      }

      // 一般頻道，直接返回
      return channelId;

    } catch (Exception e) {
      LOGGER.error("解析頻道 {} 的有效配置 ID 時發生錯誤", channelId, e);
      return -1;
    }
  }

  @Override
  public Result<Unit, DomainError> setAgentEnabled(long guildId, long channelId, boolean enabled) {
    // 查找現有配置
    Result<Optional<AIAgentChannelConfig>, Exception> findResult =
        repository.findByChannelId(channelId);

    AIAgentChannelConfig config;
    if (findResult.isErr()) {
      return Result.err(DomainError.persistenceFailure("查詢配置失敗", findResult.getError()));
    }

    if (findResult.getValue().isEmpty()) {
      // 建立新配置
      config = AIAgentChannelConfig.create(guildId, channelId).withAgentEnabled(enabled);
    } else {
      // 更新現有配置
      config = findResult.getValue().get().withAgentEnabled(enabled);
    }

    // 儲存配置
    Result<AIAgentChannelConfig, Exception> saveResult = repository.save(config);
    if (saveResult.isErr()) {
      return Result.err(DomainError.persistenceFailure("儲存配置失敗", saveResult.getError()));
    }

    // 更新快取
    String cacheKey = String.format(CACHE_KEY_FORMAT, guildId, channelId);
    cacheService.put(cacheKey, enabled, (int) CACHE_TTL_SECONDS);

    // 發布事件
    eventPublisher.publish(AIAgentChannelConfigChangedEvent.of(guildId, channelId, enabled));

    return Result.okVoid();
  }

  @Override
  public Result<Boolean, DomainError> toggleAgentMode(long guildId, long channelId) {
    // 查找現有配置
    Result<Optional<AIAgentChannelConfig>, Exception> findResult =
        repository.findByChannelId(channelId);

    if (findResult.isErr()) {
      return Result.err(DomainError.persistenceFailure("查詢配置失敗", findResult.getError()));
    }

    AIAgentChannelConfig config;
    if (findResult.getValue().isEmpty()) {
      // 建立新配置（預設啟用，所以切換為停用）
      config = AIAgentChannelConfig.create(guildId, channelId).toggleAgentMode();
    } else {
      // 切換現有配置
      config = findResult.getValue().get().toggleAgentMode();
    }

    // 儲存配置
    Result<AIAgentChannelConfig, Exception> saveResult = repository.save(config);
    if (saveResult.isErr()) {
      return Result.err(DomainError.persistenceFailure("儲存配置失敗", saveResult.getError()));
    }

    boolean newEnabled = saveResult.getValue().agentEnabled();

    // 更新快取
    String cacheKey = String.format(CACHE_KEY_FORMAT, guildId, channelId);
    cacheService.put(cacheKey, newEnabled, (int) CACHE_TTL_SECONDS);

    // 發布事件
    eventPublisher.publish(AIAgentChannelConfigChangedEvent.of(guildId, channelId, newEnabled));

    return Result.ok(newEnabled);
  }

  @Override
  public Result<List<Long>, DomainError> getEnabledChannels(long guildId) {
    Result<List<AIAgentChannelConfig>, Exception> result = repository.findEnabledByGuildId(guildId);

    if (result.isErr()) {
      return Result.err(DomainError.persistenceFailure("查詢啟用頻道失敗", result.getError()));
    }

    List<Long> channelIds =
        result.getValue().stream().map(AIAgentChannelConfig::channelId).toList();

    return Result.ok(channelIds);
  }

  @Override
  public Result<Unit, DomainError> removeChannel(long guildId, long channelId) {
    // 刪除配置
    Result<Unit, Exception> deleteResult = repository.deleteByChannelId(channelId);

    if (deleteResult.isErr()) {
      return Result.err(DomainError.persistenceFailure("刪除配置失敗", deleteResult.getError()));
    }

    // 清除快取
    String cacheKey = String.format(CACHE_KEY_FORMAT, guildId, channelId);
    cacheService.invalidate(cacheKey);

    return Result.okVoid();
  }
}
