import type { DomainEvent } from '@ltdjms/shared';

export interface BalanceChangedEvent extends DomainEvent {
  readonly eventType: 'balance_changed';
  readonly guildId: string;
  readonly userId: string;
  readonly newBalance: number;
}

export interface CurrencyConfigChangedEvent extends DomainEvent {
  readonly eventType: 'currency_config_changed';
  readonly guildId: string;
  readonly currencyName: string;
  readonly currencyIcon: string;
}
