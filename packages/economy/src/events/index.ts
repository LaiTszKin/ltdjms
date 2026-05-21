import type { DomainEvent } from '@ltdjms/shared';

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

export enum GameType {
  DICE_GAME_1 = 'DICE_GAME_1',
  DICE_GAME_2 = 'DICE_GAME_2',
}

export interface DiceGameConfigChangedEvent extends DomainEvent {
  readonly eventType: 'dice_game_config_changed';
  readonly guildId: string;
  readonly gameType: GameType;
  readonly oldConfig?: Record<string, unknown>;
  readonly newConfig?: Record<string, unknown>;
}
