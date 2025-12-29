package ltdjms.discord.aiagent.services;

import java.util.List;
import java.util.Optional;

import ltdjms.discord.aiagent.domain.AIAgentChannelConfig;
import ltdjms.discord.aiagent.persistence.AIAgentChannelConfigRepository;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.Unit;
import ltdjms.discord.shared.cache.CacheService;
import ltdjms.discord.shared.events.AIAgentChannelConfigChangedEvent;
import ltdjms.discord.shared.events.DomainEventPublisher;

/**
 * 預設實作的 AI Agent 頻道配置服務。
 *
 * <p>管理頻道的 AI Agent 模式啟用狀態，並使用 Redis 快取提升效能。
 */
public class DefaultAIAgentChannelConfigService implements AIAgentChannelConfigService {

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
    // 嘗試從快取讀取
    String cacheKey = String.format(CACHE_KEY_FORMAT, guildId, channelId);
    Optional<Boolean> cached = cacheService.get(cacheKey, Boolean.class);

    if (cached.isPresent()) {
      return cached.get();
    }

    // 從資料庫讀取
    Result<Optional<AIAgentChannelConfig>, Exception> result =
        repository.findByChannelId(channelId);

    if (result.isErr() || result.getValue().isEmpty()) {
      // 未找到配置，預設停用
      return false;
    }

    boolean enabled = result.getValue().get().agentEnabled();

    // 寫入快取
    cacheService.put(cacheKey, enabled, (int) CACHE_TTL_SECONDS);

    return enabled;
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
