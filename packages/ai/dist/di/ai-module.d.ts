/**
 * AI Module DI Tokens.
 */
export declare const AI_TOKENS: {
    AIServiceConfig: symbol;
    PromptLoader: symbol;
    AIChannelRestrictionRepository: symbol;
    AIChannelRestrictionService: symbol;
    AIAgentChannelConfigRepository: symbol;
    AIAgentChannelConfigService: symbol;
    AIChatService: symbol;
    LangChainAIChatService: symbol;
    AIChatMentionRoutingDecision: symbol;
    AIChatMentionListener: symbol;
    AgentServiceFactory: symbol;
    InMemoryToolCallHistory: symbol;
    DiscordThreadHistoryProvider: symbol;
    SimplifiedChatMemoryProvider: symbol;
    TokenEstimator: symbol;
    CommonMarkValidator: symbol;
    RegexBasedAutoFixer: symbol;
    DiscordMarkdownSanitizer: symbol;
    DiscordMarkdownPaginator: symbol;
    ToolCallerAuthorizationGuard: symbol;
    PermissionParser: symbol;
    CreateChannelTool: symbol;
    CreateCategoryTool: symbol;
    CreateRoleTool: symbol;
    ListChannelsTool: symbol;
    ListCategoriesTool: symbol;
    ListRolesTool: symbol;
    GetChannelPermissionsTool: symbol;
    GetCategoryPermissionsTool: symbol;
    GetRolePermissionsTool: symbol;
    ModifyChannelPermissionsTool: symbol;
    ModifyCategoryPermissionsTool: symbol;
    ModifyRolePermissionsTool: symbol;
    SendMessagesTool: symbol;
    SearchMessagesTool: symbol;
    ManageMessageTool: symbol;
    MoveChannelTool: symbol;
    DeleteDiscordResourceTool: symbol;
};
/**
 * Initializes the AI module in the tsyringe DI container.
 */
export declare function initializeAIModule(): void;
