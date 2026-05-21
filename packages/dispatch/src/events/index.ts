import type { DomainEvent } from '@ltdjms/shared';
import { OperationType } from '@ltdjms/shop';

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
