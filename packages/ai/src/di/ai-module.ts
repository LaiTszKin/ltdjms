import { ChatOpenAI } from '@langchain/openai';
import { container, TOKENS, type TokenMap } from '@ltdjms/shared';
import type {
  EnvironmentConfig,
  CacheService,
  DomainEventPublisher,
  DiscordRuntimeGateway,
} from '@ltdjms/shared';
import { drizzle } from 'drizzle-orm/node-postgres';
import { type Pool } from 'pg';

import { AIServiceConfig } from '../config/ai-service-config.js';
import { DefaultPromptLoader, type PromptLoader } from '../prompts/prompt-loader.js';

// Routing Services
import { AIChatMentionRoutingDecision } from '../services/routing/routing-decision.js';
import {
  DefaultAIChannelRestrictionService,
  type AIChannelRestrictionService,
  InMemoryAIChannelRestrictionRepository,
  type AIChannelRestrictionRepository,
} from '../services/routing/channel-restriction-service.js';
import {
  DefaultAIAgentChannelConfigService,
  type AIAgentChannelConfigService,
  InMemoryAIAgentChannelConfigRepository,
  type AIAgentChannelConfigRepository,
} from '../services/routing/agent-config-service.js';

// DB-backed persistence
import { DrizzleAIChannelRestrictionRepository } from '../persistence/drizzle-channel-restriction-repository.js';
import { DrizzleAIAgentChannelConfigRepository } from '../persistence/drizzle-agent-config-repository.js';

// AI Chat Service
import { type AIChatService } from '../services/ai-chat-service.js';
import { LangChainAIChatService } from '../services/LangChainAIChatService.js';
import { MarkdownValidatingAIChatService } from '../markdown/services/MarkdownValidatingAIChatService.js';
import { AgentConfigCacheInvalidationListener } from '../services/routing/agent-config-cache-invalidation-listener.js';

// Tools
import { ToolCallerAuthorizationGuard } from '../tools/ToolCallerAuthorizationGuard.js';
import { PermissionParser } from '../tools/PermissionParser.js';
import { CreateChannelTool } from '../tools/CreateChannelTool.js';
import { CreateCategoryTool } from '../tools/CreateCategoryTool.js';
import { CreateRoleTool } from '../tools/CreateRoleTool.js';
import { ListChannelsTool } from '../tools/ListChannelsTool.js';
import { ListCategoriesTool } from '../tools/ListCategoriesTool.js';
import { ListRolesTool } from '../tools/ListRolesTool.js';
import { GetChannelPermissionsTool } from '../tools/GetChannelPermissionsTool.js';
import { GetCategoryPermissionsTool } from '../tools/GetCategoryPermissionsTool.js';
import { GetRolePermissionsTool } from '../tools/GetRolePermissionsTool.js';
import { ModifyChannelPermissionsTool } from '../tools/ModifyChannelPermissionsTool.js';
import { ModifyCategoryPermissionsTool } from '../tools/ModifyCategoryPermissionsTool.js';
import { ModifyRolePermissionsTool } from '../tools/ModifyRolePermissionsTool.js';
import { SendMessagesTool } from '../tools/SendMessagesTool.js';
import { SearchMessagesTool } from '../tools/SearchMessagesTool.js';
import { ManageMessageTool } from '../tools/ManageMessageTool.js';
import { MoveChannelTool } from '../tools/MoveChannelTool.js';
import { DeleteDiscordResourceTool } from '../tools/DeleteDiscordResourceTool.js';

// Memory
import { InMemoryToolCallHistory } from '../services/memory/tool-call-history.js';
import { DiscordThreadHistoryProvider } from '../services/memory/chat-memory-provider.js';
import { SimplifiedChatMemoryProvider } from '../services/memory/chat-memory-provider.js';
import { ToolExecutionInterceptor } from '../services/ToolExecutionInterceptor.js';
import {
  DrizzleToolExecutionLogRepository,
  InMemoryToolExecutionLogRepository,
  type ToolExecutionLogRepository,
} from '../persistence/drizzle-tool-execution-log-repository.js';
import { createLangGraphCheckpointProvider } from '../services/memory/langgraph-checkpoint-provider.js';
import { ToolExecutionListener } from '../listeners/tool-execution-listener.js';
import { AgentCompletionListener } from '../listeners/agent-completion-listener.js';

// Markdown
import { CommonMarkValidator } from '../markdown/validation/CommonMarkValidator.js';
import { RegexBasedAutoFixer } from '../markdown/autofix/RegexBasedAutoFixer.js';
import { DiscordMarkdownSanitizer } from '../markdown/services/DiscordMarkdownSanitizer.js';
import { DiscordMarkdownPaginator } from '../markdown/services/DiscordMarkdownPaginator.js';

// Commands
import { AIChatMentionListener } from '../commands/ai-chat-mention-listener.js';

/**
 * AI Module DI Tokens.
 */
export const AI_TOKENS = {
  AIServiceConfig: Symbol('AIServiceConfig'),
  ChatOpenAI: Symbol('ChatOpenAI'),
  PromptLoader: Symbol('PromptLoader'),
  AIChannelRestrictionRepository: Symbol('AIChannelRestrictionRepository'),
  AIChannelRestrictionService: Symbol('AIChannelRestrictionService'),
  AIAgentChannelConfigRepository: Symbol('AIAgentChannelConfigRepository'),
  AIAgentChannelConfigService: Symbol('AIAgentChannelConfigService'),
  AIChatService: Symbol('AIChatService'),
  LangChainAIChatService: Symbol('LangChainAIChatService'),
  AIChatMentionRoutingDecision: Symbol('AIChatMentionRoutingDecision'),
  AIChatMentionListener: Symbol('AIChatMentionListener'),
  InMemoryToolCallHistory: Symbol('InMemoryToolCallHistory'),
  DiscordThreadHistoryProvider: Symbol('DiscordThreadHistoryProvider'),
  SimplifiedChatMemoryProvider: Symbol('SimplifiedChatMemoryProvider'),
  CommonMarkValidator: Symbol('CommonMarkValidator'),
  RegexBasedAutoFixer: Symbol('RegexBasedAutoFixer'),
  DiscordMarkdownSanitizer: Symbol('DiscordMarkdownSanitizer'),
  DiscordMarkdownPaginator: Symbol('DiscordMarkdownPaginator'),
  ToolCallerAuthorizationGuard: Symbol('ToolCallerAuthorizationGuard'),
  PermissionParser: Symbol('PermissionParser'),

  // Tools
  CreateChannelTool: Symbol('CreateChannelTool'),
  CreateCategoryTool: Symbol('CreateCategoryTool'),
  CreateRoleTool: Symbol('CreateRoleTool'),
  ListChannelsTool: Symbol('ListChannelsTool'),
  ListCategoriesTool: Symbol('ListCategoriesTool'),
  ListRolesTool: Symbol('ListRolesTool'),
  GetChannelPermissionsTool: Symbol('GetChannelPermissionsTool'),
  GetCategoryPermissionsTool: Symbol('GetCategoryPermissionsTool'),
  GetRolePermissionsTool: Symbol('GetRolePermissionsTool'),
  ModifyChannelPermissionsTool: Symbol('ModifyChannelPermissionsTool'),
  ModifyCategoryPermissionsTool: Symbol('ModifyCategoryPermissionsTool'),
  ModifyRolePermissionsTool: Symbol('ModifyRolePermissionsTool'),
  SendMessagesTool: Symbol('SendMessagesTool'),
  SearchMessagesTool: Symbol('SearchMessagesTool'),
  ManageMessageTool: Symbol('ManageMessageTool'),
  MoveChannelTool: Symbol('MoveChannelTool'),
  DeleteDiscordResourceTool: Symbol('DeleteDiscordResourceTool'),
  ToolExecutionLogRepository: Symbol('ToolExecutionLogRepository'),
  ToolExecutionInterceptor: Symbol('ToolExecutionInterceptor'),
  LangGraphCheckpointProvider: Symbol('LangGraphCheckpointProvider'),
  ToolExecutionListener: Symbol('ToolExecutionListener'),
  AgentCompletionListener: Symbol('AgentCompletionListener'),
};

/**
 * Initializes the AI module in the tsyringe DI container.
 */
export async function initializeAIModule(): Promise<void> {
  const envConfig = container.resolve<EnvironmentConfig>(TOKENS.EnvironmentConfig);
  const cacheService = container.resolve<CacheService>(TOKENS.CacheService);
  const eventPublisher = container.resolve<DomainEventPublisher>(TOKENS.DomainEventPublisher);
  const runtimeGateway = container.resolve<DiscordRuntimeGateway>(TOKENS.DiscordRuntimeGateway);

  // ===== Config =====
  const aiConfig = AIServiceConfig.from(envConfig);
  container.registerInstance(AI_TOKENS.AIServiceConfig, aiConfig);

  // ===== Prompt Loader =====
  const promptLoader = new DefaultPromptLoader(
    envConfig.getPromptsDirPath(),
    envConfig.getPromptMaxSizeBytes(),
  );
  container.registerInstance<PromptLoader>(AI_TOKENS.PromptLoader, promptLoader);

  // ===== Channel Restriction =====
  const rawPool = container.resolve<Pool>(TOKENS.DatabasePool);
  const db = drizzle(rawPool);
  const restrictionRepo = db
    ? new DrizzleAIChannelRestrictionRepository(db)
    : new InMemoryAIChannelRestrictionRepository();
  container.registerInstance<AIChannelRestrictionRepository>(
    AI_TOKENS.AIChannelRestrictionRepository,
    restrictionRepo,
  );

  const restrictionService = new DefaultAIChannelRestrictionService(restrictionRepo);
  container.registerInstance<AIChannelRestrictionService>(
    AI_TOKENS.AIChannelRestrictionService,
    restrictionService,
  );

  // ===== Agent Config =====
  const agentConfigRepo = db
    ? new DrizzleAIAgentChannelConfigRepository(db)
    : new InMemoryAIAgentChannelConfigRepository();
  container.registerInstance<AIAgentChannelConfigRepository>(
    AI_TOKENS.AIAgentChannelConfigRepository,
    agentConfigRepo,
  );

  const agentConfigService = new DefaultAIAgentChannelConfigService(
    agentConfigRepo,
    cacheService,
    eventPublisher,
    runtimeGateway,
  );
  container.registerInstance<AIAgentChannelConfigService>(
    AI_TOKENS.AIAgentChannelConfigService,
    agentConfigService,
  );

  // ===== Routing Decision =====
  const routingDecision = new AIChatMentionRoutingDecision(agentConfigService, restrictionService);
  container.registerInstance(AI_TOKENS.AIChatMentionRoutingDecision, routingDecision);

  // ===== Tools =====
  const logger = container.resolve<TokenMap['Logger']>(TOKENS.Logger);
  const authGuard = new ToolCallerAuthorizationGuard(logger);
  container.registerInstance(AI_TOKENS.ToolCallerAuthorizationGuard, authGuard);

  const permissionParser = new PermissionParser();
  container.registerInstance(AI_TOKENS.PermissionParser, permissionParser);

  // Register all 17 tools
  container.registerInstance(
    AI_TOKENS.CreateChannelTool,
    new CreateChannelTool(authGuard, permissionParser),
  );
  container.registerInstance(
    AI_TOKENS.CreateCategoryTool,
    new CreateCategoryTool(authGuard, permissionParser),
  );
  container.registerInstance(AI_TOKENS.CreateRoleTool, new CreateRoleTool(authGuard));
  container.registerInstance(AI_TOKENS.ListChannelsTool, new ListChannelsTool(authGuard));
  container.registerInstance(AI_TOKENS.ListCategoriesTool, new ListCategoriesTool(authGuard));
  container.registerInstance(AI_TOKENS.ListRolesTool, new ListRolesTool(authGuard));
  container.registerInstance(
    AI_TOKENS.GetChannelPermissionsTool,
    new GetChannelPermissionsTool(authGuard),
  );
  container.registerInstance(
    AI_TOKENS.GetCategoryPermissionsTool,
    new GetCategoryPermissionsTool(authGuard),
  );
  container.registerInstance(
    AI_TOKENS.GetRolePermissionsTool,
    new GetRolePermissionsTool(authGuard),
  );
  container.registerInstance(
    AI_TOKENS.ModifyChannelPermissionsTool,
    new ModifyChannelPermissionsTool(authGuard, permissionParser),
  );
  container.registerInstance(
    AI_TOKENS.ModifyCategoryPermissionsTool,
    new ModifyCategoryPermissionsTool(authGuard, permissionParser),
  );
  container.registerInstance(
    AI_TOKENS.ModifyRolePermissionsTool,
    new ModifyRolePermissionsTool(authGuard),
  );
  container.registerInstance(AI_TOKENS.SendMessagesTool, new SendMessagesTool(authGuard));
  container.registerInstance(AI_TOKENS.SearchMessagesTool, new SearchMessagesTool(authGuard));
  container.registerInstance(AI_TOKENS.ManageMessageTool, new ManageMessageTool(authGuard));
  container.registerInstance(AI_TOKENS.MoveChannelTool, new MoveChannelTool(authGuard));
  container.registerInstance(
    AI_TOKENS.DeleteDiscordResourceTool,
    new DeleteDiscordResourceTool(authGuard),
  );

  // Build tool map for agent tool execution (P0-7)
  const allTools = [
    container.resolve(AI_TOKENS.CreateChannelTool),
    container.resolve(AI_TOKENS.CreateCategoryTool),
    container.resolve(AI_TOKENS.CreateRoleTool),
    container.resolve(AI_TOKENS.ListChannelsTool),
    container.resolve(AI_TOKENS.ListCategoriesTool),
    container.resolve(AI_TOKENS.ListRolesTool),
    container.resolve(AI_TOKENS.GetChannelPermissionsTool),
    container.resolve(AI_TOKENS.GetCategoryPermissionsTool),
    container.resolve(AI_TOKENS.GetRolePermissionsTool),
    container.resolve(AI_TOKENS.ModifyChannelPermissionsTool),
    container.resolve(AI_TOKENS.ModifyCategoryPermissionsTool),
    container.resolve(AI_TOKENS.ModifyRolePermissionsTool),
    container.resolve(AI_TOKENS.SendMessagesTool),
    container.resolve(AI_TOKENS.SearchMessagesTool),
    container.resolve(AI_TOKENS.ManageMessageTool),
    container.resolve(AI_TOKENS.MoveChannelTool),
    container.resolve(AI_TOKENS.DeleteDiscordResourceTool),
  ];
  const toolMap = new Map<
    string,
    {
      name: string;
      description: string;
      schema: import('zod').ZodType<unknown>;
      execute: (
        params: Record<string, unknown>,
        guild: import('discord.js').Guild,
      ) => Promise<string>;
    }
  >();
  for (const tool of allTools) {
    const registeredTool = tool as {
      name: string;
      description: string;
      schema: import('zod').ZodType<unknown>;
      execute: (
        params: Record<string, unknown>,
        guild: import('discord.js').Guild,
      ) => Promise<string>;
    };
    toolMap.set(registeredTool.name, registeredTool);
  }

  // ===== Tool execution audit =====
  const toolLogRepo: ToolExecutionLogRepository = db
    ? new DrizzleToolExecutionLogRepository(db)
    : new InMemoryToolExecutionLogRepository();
  container.registerInstance(AI_TOKENS.ToolExecutionLogRepository, toolLogRepo);

  const toolExecutionInterceptor = new ToolExecutionInterceptor(
    toolLogRepo,
    eventPublisher,
    logger,
  );
  container.registerInstance(AI_TOKENS.ToolExecutionInterceptor, toolExecutionInterceptor);

  const toolCallHistory = new InMemoryToolCallHistory();
  container.registerInstance(AI_TOKENS.InMemoryToolCallHistory, toolCallHistory);

  // ===== LangGraph checkpoint (Postgres + optional Redis) =====
  let checkpointProvider: Awaited<ReturnType<typeof createLangGraphCheckpointProvider>> = null;
  try {
    checkpointProvider = await createLangGraphCheckpointProvider(rawPool, envConfig.getRedisUri());
    if (checkpointProvider) {
      container.registerInstance(AI_TOKENS.LangGraphCheckpointProvider, checkpointProvider);
    }
  } catch (error) {
    logger.warn(
      { err: error instanceof Error ? error.message : String(error) },
      'LangGraph checkpoint init failed — agent memory uses Discord thread history only',
    );
  }

  // ===== Agent event listeners =====
  const toolExecutionListener = new ToolExecutionListener(runtimeGateway, logger);
  const agentCompletionListener = new AgentCompletionListener(runtimeGateway, logger);
  container.registerInstance(AI_TOKENS.ToolExecutionListener, toolExecutionListener);
  container.registerInstance(AI_TOKENS.AgentCompletionListener, agentCompletionListener);
  eventPublisher.register((event) => toolExecutionListener.accept(event));
  // AgentCompletionListener stays in DI for unit tests; mention listener owns Discord UX (P1-5/P1-6).

  // ===== Shared ChatOpenAI Singleton =====
  // Single shared instance to avoid multiple HTTP agents/connection pools (P1-30, P2-11)
  const sharedChatModel = new ChatOpenAI({
    configuration: {
      baseURL: aiConfig.baseUrl,
      apiKey: aiConfig.apiKey,
    },
    modelName: aiConfig.model,
    temperature: aiConfig.temperature,
    timeout: aiConfig.timeoutSeconds * 1000,
    streaming: true,
  });
  container.registerInstance(AI_TOKENS.ChatOpenAI, sharedChatModel);

  // ===== Memory (created before AI Chat Service so it can be injected) =====

  const threadHistoryProvider = new DiscordThreadHistoryProvider(runtimeGateway);
  container.registerInstance(AI_TOKENS.DiscordThreadHistoryProvider, threadHistoryProvider);

  const memoryProvider = new SimplifiedChatMemoryProvider(
    threadHistoryProvider,
    toolCallHistory,
    runtimeGateway,
  );
  container.registerInstance(AI_TOKENS.SimplifiedChatMemoryProvider, memoryProvider);

  const langChainService = new LangChainAIChatService(
    aiConfig,
    promptLoader,
    sharedChatModel,
    toolMap,
    toolExecutionInterceptor,
    toolCallHistory,
    runtimeGateway,
    eventPublisher,
    memoryProvider,
    checkpointProvider ?? undefined,
  );
  container.registerInstance(AI_TOKENS.LangChainAIChatService, langChainService);

  // ===== Markdown Pipeline =====
  // Register Markdown services before they are resolved by MarkdownValidatingAIChatService (P1-1)
  container.registerInstance(AI_TOKENS.CommonMarkValidator, new CommonMarkValidator());
  container.registerInstance(AI_TOKENS.RegexBasedAutoFixer, new RegexBasedAutoFixer());
  container.registerInstance(AI_TOKENS.DiscordMarkdownSanitizer, new DiscordMarkdownSanitizer());
  container.registerInstance(AI_TOKENS.DiscordMarkdownPaginator, new DiscordMarkdownPaginator());

  // Wrap with Markdown validation decorator if enabled
  let aiChatService: AIChatService;
  if (aiConfig.enableMarkdownValidation) {
    aiChatService = new MarkdownValidatingAIChatService(
      langChainService,
      container.resolve(AI_TOKENS.DiscordMarkdownSanitizer),
      container.resolve(AI_TOKENS.RegexBasedAutoFixer),
      container.resolve(AI_TOKENS.CommonMarkValidator),
      container.resolve(AI_TOKENS.DiscordMarkdownPaginator),
    );
  } else {
    aiChatService = langChainService;
  }
  container.registerInstance<AIChatService>(AI_TOKENS.AIChatService, aiChatService);

  // ===== Agent Config Cache Invalidation Listener =====
  // Subscribes to AIAgentChannelConfigChangedEvent and invalidates cache entries
  new AgentConfigCacheInvalidationListener(cacheService, eventPublisher);

  // ===== AIChatMentionListener =====
  const listener = new AIChatMentionListener(
    routingDecision,
    aiChatService,
    runtimeGateway.selfUserId(),
    aiConfig.showReasoning,
    aiConfig.enableMarkdownValidation,
    aiConfig.streamingBypassValidation,
  );
  container.registerInstance(AI_TOKENS.AIChatMentionListener, listener);
}
