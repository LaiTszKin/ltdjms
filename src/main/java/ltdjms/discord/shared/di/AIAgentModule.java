package ltdjms.discord.shared.di;

import javax.inject.Singleton;
import javax.sql.DataSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import dagger.Module;
import dagger.Provides;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import ltdjms.discord.aiagent.commands.AgentCompletionListener;
import ltdjms.discord.aiagent.commands.ToolExecutionListener;
import ltdjms.discord.aiagent.persistence.AIAgentChannelConfigRepository;
import ltdjms.discord.aiagent.persistence.ConversationMessageRepository;
import ltdjms.discord.aiagent.persistence.ConversationRepository;
import ltdjms.discord.aiagent.persistence.JdbcAIAgentChannelConfigRepository;
import ltdjms.discord.aiagent.persistence.JdbcConversationMessageRepository;
import ltdjms.discord.aiagent.persistence.JdbcConversationRepository;
import ltdjms.discord.aiagent.persistence.ToolExecutionLogRepository;
import ltdjms.discord.aiagent.services.AIAgentChannelConfigService;
import ltdjms.discord.aiagent.services.AgentConfigCacheInvalidationListener;
import ltdjms.discord.aiagent.services.DefaultAIAgentChannelConfigService;
import ltdjms.discord.aiagent.services.DiscordThreadHistoryProvider;
import ltdjms.discord.aiagent.services.InMemoryToolCallHistory;
import ltdjms.discord.aiagent.services.SimplifiedChatMemoryProvider;
import ltdjms.discord.aiagent.services.TokenEstimator;
import ltdjms.discord.aiagent.services.ToolExecutionInterceptor;
import ltdjms.discord.aiagent.services.tools.LangChain4jCreateCategoryTool;
import ltdjms.discord.aiagent.services.tools.LangChain4jCreateChannelTool;
import ltdjms.discord.aiagent.services.tools.LangChain4jListCategoriesTool;
import ltdjms.discord.aiagent.services.tools.LangChain4jListChannelsTool;
import ltdjms.discord.aiagent.services.tools.LangChain4jListRolesTool;
import ltdjms.discord.aichat.domain.AIServiceConfig;
import ltdjms.discord.aichat.services.AIChatService;
import ltdjms.discord.aichat.services.LangChain4jAIChatService;
import ltdjms.discord.aichat.services.PromptLoader;
import ltdjms.discord.shared.EnvironmentConfig;
import ltdjms.discord.shared.cache.CacheService;
import ltdjms.discord.shared.events.DomainEventPublisher;

/**
 * Dagger 模組，提供 AI Agent Tools 相關的依賴注入。
 *
 * <p>此模組負責綁定：
 *
 * <ul>
 *   <li>AI Agent 頻道配置 Repository 與 Service
 *   <li>工具執行日誌 Repository（用於審計）
 *   <li>LangChain4J 工具（CreateChannelTool、CreateCategoryTool、ListChannelsTool）
 *   <li>LangChain4J ChatMemoryProvider（整合 Redis + PostgreSQL）
 *   <li>LangChain4J AIChatService
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
   * 提供 LangChain4J 創建頻道工具。
   *
   * @return LangChain4jCreateChannelTool 實例
   */
  @Provides
  @Singleton
  public LangChain4jCreateChannelTool provideLangChain4jCreateChannelTool() {
    return new LangChain4jCreateChannelTool();
  }

  /**
   * 提供 LangChain4J 創建類別工具。
   *
   * @return LangChain4jCreateCategoryTool 實例
   */
  @Provides
  @Singleton
  public LangChain4jCreateCategoryTool provideLangChain4jCreateCategoryTool() {
    return new LangChain4jCreateCategoryTool();
  }

  /**
   * 提供 LangChain4J 列出頻道工具。
   *
   * @return LangChain4jListChannelsTool 實例
   */
  @Provides
  @Singleton
  public LangChain4jListChannelsTool provideLangChain4jListChannelsTool() {
    return new LangChain4jListChannelsTool();
  }

  /**
   * 提供 LangChain4J 列出角色工具。
   *
   * @return LangChain4jListRolesTool 實例
   */
  @Provides
  @Singleton
  public LangChain4jListRolesTool provideLangChain4jListRolesTool() {
    return new LangChain4jListRolesTool();
  }

  /**
   * 提供 LangChain4J 列出類別工具。
   *
   * @return LangChain4jListCategoriesTool 實例
   */
  @Provides
  @Singleton
  public LangChain4jListCategoriesTool provideLangChain4jListCategoriesTool() {
    return new LangChain4jListCategoriesTool();
  }

  /**
   * 提供 Token 估算器。
   *
   * @return Token 估算器（使用 GPT-4o 的 128K context window）
   */
  @Provides
  @Singleton
  public TokenEstimator provideTokenEstimator() {
    return new TokenEstimator();
  }

  /**
   * 提供會話 Repository。
   *
   * @param dataSource 資料來源
   * @return JDBC 實作的會話 Repository
   */
  @Provides
  @Singleton
  public ConversationRepository provideConversationRepository(DataSource dataSource) {
    return new JdbcConversationRepository(dataSource);
  }

  /**
   * 提供會話訊息 Repository。
   *
   * @param dataSource 資料來源
   * @param objectMapper JSON 序列化器
   * @param tokenEstimator Token 估算器
   * @return JDBC 實作的會話訊息 Repository
   */
  @Provides
  @Singleton
  public ConversationMessageRepository provideConversationMessageRepository(
      DataSource dataSource, ObjectMapper objectMapper, TokenEstimator tokenEstimator) {
    return new JdbcConversationMessageRepository(dataSource, objectMapper, tokenEstimator);
  }

  /**
   * 提供用於序列化的 ObjectMapper。
   *
   * <p>配置為適合 Redis 和 PostgreSQL JSONB 欄位的序列化。
   *
   * @return ObjectMapper 實例
   */
  @Provides
  @Singleton
  public ObjectMapper provideObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.registerModule(new Jdk8Module());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    return mapper;
  }

  /**
   * 提供 Agent 完成監聽器。
   *
   * @return Agent 完成監聽器
   */
  @Provides
  @Singleton
  public AgentCompletionListener provideAgentCompletionListener() {
    return new AgentCompletionListener();
  }

  /**
   * 提供工具執行事件監聽器。
   *
   * @return ToolExecutionListener 實例
   */
  @Provides
  @Singleton
  public ToolExecutionListener provideToolExecutionListener() {
    return new ToolExecutionListener();
  }

  /**
   * 提供 LangChain4J 串流聊天語言模型。
   *
   * <p>使用 AIServiceConfig 配置創建 OpenAI 兼容的串流模型。
   *
   * @param config AI 服務配置
   * @return StreamingChatModel 實例
   */
  @Provides
  @Singleton
  public StreamingChatModel provideStreamingChatLanguageModel(AIServiceConfig config) {
    boolean enableThinking =
        config.model() != null && config.model().toLowerCase().contains("deepseek");

    return OpenAiStreamingChatModel.builder()
        .baseUrl(config.baseUrl())
        .apiKey(config.apiKey())
        .modelName(config.model())
        // LangChain4J 1.7.1 支援 reasoning 模式
        .temperature(config.temperature())
        // DeepSeek reasoning 需要回傳 reasoning_content 以便後續工具迭代
        .returnThinking(enableThinking)
        .timeout(java.time.Duration.ofSeconds(config.timeoutSeconds()))
        .build();
  }

  /**
   * 提供 InMemoryToolCallHistory。
   *
   * @return InMemoryToolCallHistory 實例
   */
  @Provides
  @Singleton
  public InMemoryToolCallHistory provideInMemoryToolCallHistory() {
    return new InMemoryToolCallHistory();
  }

  /**
   * 提供 DiscordThreadHistoryProvider。
   *
   * <p>JDA 實例會在使用時從 JDAProvider 延遲獲取，避免在 Dagger 初始化時就要求 JDA 實例存在。
   *
   * @param tokenEstimator Token 估算器
   * @return DiscordThreadHistoryProvider 實例
   */
  @Provides
  @Singleton
  public DiscordThreadHistoryProvider provideDiscordThreadHistoryProvider(
      TokenEstimator tokenEstimator) {
    return new DiscordThreadHistoryProvider(100, tokenEstimator);
  }

  /**
   * 提供 LangChain4J ChatMemoryProvider（簡化版）。
   *
   * <p>使用 SimplifiedChatMemoryProvider 整合 Discord Thread 歷史與記憶體中的工具調用歷史。 botUserId 會在使用時從
   * JDAProvider 延遲獲取，避免在 Dagger 初始化時就要求 JDA 實例存在。
   *
   * @param threadHistoryProvider Discord Thread 歷史提供者
   * @param toolCallHistory 記憶體中的工具調用歷史
   * @return ChatMemoryProvider 實例
   */
  @Provides
  @Singleton
  public ChatMemoryProvider provideChatMemoryProvider(
      DiscordThreadHistoryProvider threadHistoryProvider, InMemoryToolCallHistory toolCallHistory) {
    return new SimplifiedChatMemoryProvider(threadHistoryProvider, toolCallHistory);
  }

  /**
   * 提供 PromptLoader。
   *
   * @param envConfig 環境配置
   * @return PromptLoader 實例
   */
  @Provides
  @Singleton
  public PromptLoader providePromptLoader(EnvironmentConfig envConfig) {
    return new ltdjms.discord.aichat.services.DefaultPromptLoader(envConfig);
  }

  /**
   * 提供 AIChatService (LangChain4J 實作)。
   *
   * @param config AI 服務配置
   * @param promptLoader 提示詞載入器
   * @param eventPublisher 事件發布器
   * @param streamingChatModel 串流聊天語言模型
   * @param chatMemoryProvider 會話記憶提供者（簡化版）
   * @param createChannelTool 創建頻道工具
   * @param createCategoryTool 創建類別工具
   * @param listChannelsTool 列出頻道工具
   * @param listCategoriesTool 列出類別工具
   * @return AIChatService 實例
   */
  @Provides
  @Singleton
  public AIChatService provideAIChatService(
      AIServiceConfig config,
      PromptLoader promptLoader,
      DomainEventPublisher eventPublisher,
      StreamingChatModel streamingChatModel,
      ChatMemoryProvider chatMemoryProvider,
      ToolExecutionInterceptor toolExecutionInterceptor,
      InMemoryToolCallHistory toolCallHistory,
      LangChain4jCreateChannelTool createChannelTool,
      LangChain4jCreateCategoryTool createCategoryTool,
      LangChain4jListChannelsTool listChannelsTool,
      LangChain4jListCategoriesTool listCategoriesTool,
      LangChain4jListRolesTool listRolesTool) {
    return new LangChain4jAIChatService(
        config,
        promptLoader,
        eventPublisher,
        streamingChatModel,
        chatMemoryProvider,
        toolExecutionInterceptor,
        toolCallHistory,
        createChannelTool,
        createCategoryTool,
        listChannelsTool,
        listCategoriesTool,
        listRolesTool);
  }

  /**
   * 提供 ToolExecutionInterceptor。
   *
   * @param logRepository 工具執行日誌 Repository
   * @param objectMapper JSON 序列化器
   * @param eventPublisher 領域事件發布器
   * @return ToolExecutionInterceptor 實例
   */
  @Provides
  @Singleton
  public ToolExecutionInterceptor provideToolExecutionInterceptor(
      ToolExecutionLogRepository logRepository,
      ObjectMapper objectMapper,
      DomainEventPublisher eventPublisher) {
    return new ToolExecutionInterceptor(logRepository, objectMapper, eventPublisher);
  }
}
