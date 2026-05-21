import { type OperationType } from '../operation-type.js';
import type { DomainEvent } from './domain-event.js';
export type { DomainEvent } from './domain-event.js';

/**
 * Game type discriminant for dice game configuration.
 */
export enum GameType {
  DICE_GAME_1 = 'DICE_GAME_1',
  DICE_GAME_2 = 'DICE_GAME_2',
}

// ---- Economy events ----

export interface BalanceChangedEvent extends DomainEvent {
  readonly eventType: 'balance_changed';
  readonly guildId: string;
  readonly userId: string;
  readonly newBalance: number;
}

export interface GameTokenChangedEvent extends DomainEvent {
  readonly eventType: 'game_token_changed';
  readonly guildId: string;
  readonly userId: string;
  readonly newTokens: number;
}

export interface CurrencyConfigChangedEvent extends DomainEvent {
  readonly eventType: 'currency_config_changed';
  readonly guildId: string;
  readonly currencyName: string;
  readonly currencyIcon: string;
}

export interface DiceGameConfigChangedEvent extends DomainEvent {
  readonly eventType: 'dice_game_config_changed';
  readonly guildId: string;
  readonly gameType: GameType;
  readonly oldConfig?: Record<string, unknown>;
  readonly newConfig?: Record<string, unknown>;
}

// ---- Shop events ----

export interface ProductChangedEvent extends DomainEvent {
  readonly eventType: 'product_changed';
  readonly guildId: string;
  readonly productId: number;
  readonly operationType: OperationType;
}

export interface ProductRedemptionTransaction {
  readonly id: string;
  readonly userId: string;
  readonly productId: number;
  readonly timestamp: Date;
}

export interface RedemptionCodesGeneratedEvent extends DomainEvent {
  readonly eventType: 'redemption_codes_generated';
  readonly guildId: string;
  readonly productId: number;
  readonly count: number;
}

export interface ProductRedemptionCompletedEvent extends DomainEvent {
  readonly eventType: 'product_redemption_completed';
  readonly guildId: string;
  readonly userId: string;
  readonly transaction: ProductRedemptionTransaction;
  readonly timestamp: Date;
}

// ---- AI events ----

export interface AIAgentChannelConfigChangedEvent extends DomainEvent {
  readonly eventType: 'ai_agent_channel_config_changed';
  readonly guildId: string;
  readonly channelId: number;
  readonly agentEnabled: boolean;
  readonly changedAt: Date;
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

export interface AIChannelConfigChangedEvent extends DomainEvent {
  readonly eventType: 'ai_channel_config_changed';
  readonly guildId: string;
  readonly changeType: 'channel_added' | 'channel_removed' | 'category_added' | 'category_removed';
  readonly targetId: string;
}

// ---- Dispatch events ----

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
