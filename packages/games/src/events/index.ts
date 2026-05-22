import type { DomainEvent } from '@ltdjms/shared';

export interface GameTokenChangedEvent extends DomainEvent {
  readonly eventType: 'game_token_changed';
  readonly guildId: string;
  readonly userId: string;
  readonly newTokens: number;
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
