/**
 * Base interface for all domain events.
 * All events must have a guildId to identify the server context.
 * Matches the Java DomainEvent sealed interface pattern.
 */

export interface DomainEvent {
  readonly guildId: string;
  /** Discriminant for event type identification. */
  readonly eventType: string;
}

// ---- Currency Events ----

export interface BalanceChangedEvent extends DomainEvent {
  readonly eventType: 'balance_changed';
  readonly guildId: string;
  readonly userId: number;
  readonly newBalance: number;
}

export interface GameTokenChangedEvent extends DomainEvent {
  readonly eventType: 'game_token_changed';
  readonly guildId: string;
  readonly userId: number;
  readonly newTokens: number;
}

export interface CurrencyConfigChangedEvent extends DomainEvent {
  readonly eventType: 'currency_config_changed';
  readonly guildId: string;
  readonly currencyName: string;
  readonly currencyIcon: string;
}

// ---- Dice Game Events ----

export enum GameType {
  DICE_GAME_1 = 'DICE_GAME_1',
  DICE_GAME_2 = 'DICE_GAME_2',
}

export interface DiceGameConfigChangedEvent extends DomainEvent {
  readonly eventType: 'dice_game_config_changed';
  readonly guildId: string;
  readonly gameType: GameType;
  /** Previous configuration values before the change. */
  readonly oldConfig?: Record<string, unknown>;
  /** New configuration values after the change. */
  readonly newConfig?: Record<string, unknown>;
}

// ---- Product / Redemption Events ----

export enum OperationType {
  CREATED = 'CREATED',
  UPDATED = 'UPDATED',
  DELETED = 'DELETED',
}

export interface ProductChangedEvent extends DomainEvent {
  readonly eventType: 'product_changed';
  readonly guildId: string;
  readonly productId: number;
  readonly operationType: OperationType;
}

export interface RedemptionCodesGeneratedEvent extends DomainEvent {
  readonly eventType: 'redemption_codes_generated';
  readonly guildId: string;
  readonly productId: number;
  readonly count: number;
}

export interface ProductRedemptionTransaction {
  readonly id: string;
  readonly userId: number;
  readonly productId: number;
  readonly timestamp: Date;
}

export interface ProductRedemptionCompletedEvent extends DomainEvent {
  readonly eventType: 'product_redemption_completed';
  readonly guildId: string;
  readonly userId: number;
  readonly transaction: ProductRedemptionTransaction;
  readonly timestamp: Date;
}

// ---- AI / Agent Events ----

export interface AIMessageEvent extends DomainEvent {
  readonly eventType: 'ai_message';
  readonly guildId: string;
  readonly channelId: string;
  readonly threadId: number | null;
  readonly userId: string;
  readonly userMessage: string;
  readonly aiResponse: string;
  readonly timestamp: Date;
  readonly messageId: number;
}

export interface AIAgentChannelConfigChangedEvent extends DomainEvent {
  readonly eventType: 'ai_agent_channel_config_changed';
  readonly guildId: string;
  readonly channelId: number;
  readonly agentEnabled: boolean;
  readonly changedAt: Date;
}

export interface ConversationMessage {
  readonly role: string;
  readonly content: string;
}

export interface AgentCompletedEvent extends DomainEvent {
  readonly eventType: 'agent_completed';
  readonly guildId: string;
  readonly channelId: string;
  readonly userId: string;
  readonly conversationId: string;
  readonly finalResponse: string;
  readonly fullHistory: ConversationMessage[];
  readonly timestamp: Date;
}

export interface AgentFailedEvent extends DomainEvent {
  readonly eventType: 'agent_failed';
  readonly guildId: string;
  readonly channelId: string;
  readonly userId: string;
  readonly conversationId: string;
  readonly reason: string;
  readonly timestamp: Date;
}

export interface ToolExecutionStartedEvent extends DomainEvent {
  readonly eventType: 'tool_execution_started';
  readonly guildId: string;
  readonly channelId: number;
  readonly userId: number;
  readonly toolName: string;
  readonly timestamp: Date;
}

export interface ToolExecutedEvent extends DomainEvent {
  readonly eventType: 'tool_executed';
  readonly guildId: string;
  readonly channelId: number;
  readonly userId: number;
  readonly toolName: string;
  readonly result: string;
  readonly success: boolean;
  readonly timestamp: Date;
}

// ---- Dispatch Events ----

export interface DispatchAfterSalesConfigChangedEvent extends DomainEvent {
  readonly eventType: 'dispatch_after_sales_config_changed';
  readonly guildId: string;
}

export interface EscortPricingChangedEvent extends DomainEvent {
  readonly eventType: 'escort_pricing_changed';
  readonly guildId: string;
  readonly optionCode: string;
  readonly newPrice: number;
}

export interface EscortCatalogChangedEvent extends DomainEvent {
  readonly eventType: 'escort_catalog_changed';
  readonly guildId: string;
  readonly entryCode: string;
  readonly operationType: OperationType;
}

// ---- Union type for any domain event ----

export type AnyDomainEvent =
  | BalanceChangedEvent
  | GameTokenChangedEvent
  | CurrencyConfigChangedEvent
  | DiceGameConfigChangedEvent
  | ProductChangedEvent
  | RedemptionCodesGeneratedEvent
  | ProductRedemptionCompletedEvent
  | AIMessageEvent
  | AIAgentChannelConfigChangedEvent
  | AgentCompletedEvent
  | AgentFailedEvent
  | ToolExecutionStartedEvent
  | ToolExecutedEvent
  | DispatchAfterSalesConfigChangedEvent
  | EscortPricingChangedEvent
  | EscortCatalogChangedEvent;
