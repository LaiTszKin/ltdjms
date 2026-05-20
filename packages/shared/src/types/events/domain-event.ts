/**
 * Base interface for all domain events.
 * All events must have a guildId to identify the server context.
 * Matches the Java DomainEvent sealed interface pattern.
 */

export interface DomainEvent {
  readonly guildId: number;
}

// ---- Currency Events ----

export interface BalanceChangedEvent extends DomainEvent {
  readonly guildId: number;
  readonly userId: number;
  readonly newBalance: number;
}

export interface GameTokenChangedEvent extends DomainEvent {
  readonly guildId: number;
  readonly userId: number;
  readonly newTokens: number;
}

export interface CurrencyConfigChangedEvent extends DomainEvent {
  readonly guildId: number;
  readonly currencyName: string;
  readonly currencyIcon: string;
}

// ---- Dice Game Events ----

export enum GameType {
  DICE_GAME_1 = 'DICE_GAME_1',
  DICE_GAME_2 = 'DICE_GAME_2',
}

export interface DiceGameConfigChangedEvent extends DomainEvent {
  readonly guildId: number;
  readonly gameType: GameType;
}

// ---- Product / Redemption Events ----

export enum OperationType {
  CREATED = 'CREATED',
  UPDATED = 'UPDATED',
  DELETED = 'DELETED',
}

export interface ProductChangedEvent extends DomainEvent {
  readonly guildId: number;
  readonly productId: number;
  readonly operationType: OperationType;
}

export interface RedemptionCodesGeneratedEvent extends DomainEvent {
  readonly guildId: number;
  readonly productId: number;
  readonly count: number;
}

/** Transaction type placeholder — will be replaced by the actual domain type. */
// TODO: fill fields once the corresponding module is ported
export interface ProductRedemptionTransaction {
  readonly id: string;
  readonly userId: number;
  readonly productId: number;
  readonly timestamp: Date;
}

export interface ProductRedemptionCompletedEvent extends DomainEvent {
  readonly guildId: number;
  readonly userId: number;
  readonly transaction: ProductRedemptionTransaction;
  readonly timestamp: Date;
}

// ---- AI / Agent Events ----

export interface AIMessageEvent extends DomainEvent {
  readonly guildId: number;
  readonly channelId: string;
  readonly threadId: number | null;
  readonly userId: string;
  readonly userMessage: string;
  readonly aiResponse: string;
  readonly timestamp: Date;
  readonly messageId: number;
}

export interface AIAgentChannelConfigChangedEvent extends DomainEvent {
  readonly guildId: number;
  readonly channelId: number;
  readonly agentEnabled: boolean;
  readonly changedAt: Date;
}

/** Conversation message placeholder — will be replaced by the actual domain type. */
// TODO: fill fields once the corresponding module is ported
export interface ConversationMessage {
  readonly role: string;
  readonly content: string;
}

export interface AgentCompletedEvent extends DomainEvent {
  readonly guildId: number;
  readonly channelId: string;
  readonly userId: string;
  readonly conversationId: string;
  readonly finalResponse: string;
  readonly fullHistory: ConversationMessage[];
  readonly timestamp: Date;
}

export interface AgentFailedEvent extends DomainEvent {
  readonly guildId: number;
  readonly channelId: string;
  readonly userId: string;
  readonly conversationId: string;
  readonly reason: string;
  readonly timestamp: Date;
}

export interface LangChain4jToolExecutionStartedEvent extends DomainEvent {
  readonly guildId: number;
  readonly channelId: number;
  readonly userId: number;
  readonly toolName: string;
  readonly timestamp: Date;
}

export interface LangChain4jToolExecutedEvent extends DomainEvent {
  readonly guildId: number;
  readonly channelId: number;
  readonly userId: number;
  readonly toolName: string;
  readonly result: string;
  readonly success: boolean;
  readonly timestamp: Date;
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
  | LangChain4jToolExecutionStartedEvent
  | LangChain4jToolExecutedEvent;
