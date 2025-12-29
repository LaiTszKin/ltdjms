package ltdjms.discord.shared.di;

import javax.inject.Singleton;
import javax.sql.DataSource;

import dagger.Module;
import dagger.Provides;
import ltdjms.discord.aiagent.commands.ToolCallListener;
import ltdjms.discord.aiagent.domain.AIAgentTools;
import ltdjms.discord.aiagent.persistence.AIAgentChannelConfigRepository;
import ltdjms.discord.aiagent.persistence.JdbcAIAgentChannelConfigRepository;
import ltdjms.discord.aiagent.persistence.ToolExecutionLogRepository;
import ltdjms.discord.aiagent.services.AIAgentChannelConfigService;
import ltdjms.discord.aiagent.services.AgentConfigCacheInvalidationListener;
import ltdjms.discord.aiagent.services.DefaultAIAgentChannelConfigService;
import ltdjms.discord.aiagent.services.DefaultToolExecutor;
import ltdjms.discord.aiagent.services.DefaultToolRegistry;
import ltdjms.discord.aiagent.services.ToolExecutor;
import ltdjms.discord.aiagent.services.ToolRegistry;
import ltdjms.discord.aiagent.services.tools.CreateCategoryTool;
import ltdjms.discord.aiagent.services.tools.CreateChannelTool;
import ltdjms.discord.shared.cache.CacheService;
import ltdjms.discord.shared.events.DomainEventPublisher;

/**
 * Dagger 模組，提供 AI Agent Tools 相關的依賴注入。
 *
 * <p>此模組負責綁定：
 *
 * <ul>
 *   <li>AI Agent 頻道配置 Repository 與 Service
 *   <li>工具執行日誌 Repository
 *   <li>工具註冊中心與執行器
 *   <li>內建工具（CreateChannelTool、CreateCategoryTool）
 * </ul>
 */
@Module
public class AIAgentModule {

  /**
   * 提供 AI Agent 頻道配置 Repository。
   *
   * @param dataSource 資料來源
   * @return JDBC 實作的 Repository
   */
  @Provides
  @Singleton
  public AIAgentChannelConfigRepository provideAIAgentChannelConfigRepository(
      DataSource dataSource) {
    return new JdbcAIAgentChannelConfigRepository(dataSource);
  }

  /**
   * 提供 AI Agent 頻道配置服務。
   *
   * @param repository 配置 Repository
   * @param cacheService 快取服務
   * @param eventPublisher 事件發布器
   * @return 預設實作的服務
   */
  @Provides
  @Singleton
  public AIAgentChannelConfigService provideAIAgentChannelConfigService(
      AIAgentChannelConfigRepository repository,
      CacheService cacheService,
      DomainEventPublisher eventPublisher) {
    return new DefaultAIAgentChannelConfigService(repository, cacheService, eventPublisher);
  }

  /**
   * 提供工具執行日誌 Repository。
   *
   * @param dataSource 資料來源
   * @return JDBC 實作的 Repository
   */
  @Provides
  @Singleton
  public ToolExecutionLogRepository provideToolExecutionLogRepository(DataSource dataSource) {
    return new ltdjms.discord.aiagent.persistence.JdbcToolExecutionLogRepository(dataSource);
  }

  /**
   * 提供 AI Agent 配置快取失效監聽器。
   *
   * @param cacheService 快取服務
   * @return 監聽器
   */
  @Provides
  @Singleton
  public AgentConfigCacheInvalidationListener provideAgentConfigCacheInvalidationListener(
      CacheService cacheService) {
    return new AgentConfigCacheInvalidationListener(cacheService);
  }

  /**
   * 提供工具註冊中心。
   *
   * <p>註冊所有內建工具到註冊中心。
   *
   * @param createChannelTool 創建頻道工具
   * @param createCategoryTool 創建類別工具
   * @return 工具註冊中心
   */
  @Provides
  @Singleton
  public ToolRegistry provideToolRegistry(
      CreateChannelTool createChannelTool, CreateCategoryTool createCategoryTool) {
    DefaultToolRegistry registry = new DefaultToolRegistry();
    // 註冊內建工具定義和實例
    registry.register(AIAgentTools.CREATE_CHANNEL);
    registry.registerToolInstance(createChannelTool);
    registry.register(AIAgentTools.CREATE_CATEGORY);
    registry.registerToolInstance(createCategoryTool);
    return registry;
  }

  /**
   * 提供創建頻道工具。
   *
   * @return CreateChannelTool 實例
   */
  @Provides
  @Singleton
  public CreateChannelTool provideCreateChannelTool() {
    return new CreateChannelTool();
  }

  /**
   * 提供創建類別工具。
   *
   * @return CreateCategoryTool 實例
   */
  @Provides
  @Singleton
  public CreateCategoryTool provideCreateCategoryTool() {
    return new CreateCategoryTool();
  }

  /**
   * 提供工具執行器。
   *
   * <p>JDA 實例將在工具執行時從 {@link JDAProvider} 延遲獲取。
   *
   * @param registry 工具註冊中心
   * @param logRepository 工具執行日誌 Repository
   * @return 工具執行器
   */
  @Provides
  @Singleton
  public ToolExecutor provideToolExecutor(
      ToolRegistry registry, ToolExecutionLogRepository logRepository) {
    // JDA 將在 DiscordCurrencyBot 中設置到 JDAProvider
    return new DefaultToolExecutor(registry, logRepository);
  }

  /**
   * 提供工具調用監聽器。
   *
   * @param configService AI Agent 配置服務
   * @param toolExecutor 工具執行器
   * @return 工具調用監聽器
   */
  @Provides
  @Singleton
  public ToolCallListener provideToolCallListener(
      AIAgentChannelConfigService configService, ToolExecutor toolExecutor) {
    return new ToolCallListener(configService, toolExecutor);
  }
}
