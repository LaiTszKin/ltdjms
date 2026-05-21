import { OperationType, type DomainEvent } from '@ltdjms/shared';
export { OperationType };

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
