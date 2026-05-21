export {
  type Result,
  Ok,
  Err,
  Unit,
  ok,
  okVoid,
  err,
  isOk,
  isErr,
} from './result.js';

export {
  DomainErrorCategory,
  DomainError,
} from './domain-error.js';

export {
  type DomainEvent,
  GameType,
  type BalanceChangedEvent,
  type GameTokenChangedEvent,
  type CurrencyConfigChangedEvent,
  type DiceGameConfigChangedEvent,
  type ProductChangedEvent,
  type RedemptionCodesGeneratedEvent,
  type ProductRedemptionTransaction,
  type ProductRedemptionCompletedEvent,
  type AIAgentChannelConfigChangedEvent,
  type AgentFailedEvent,
  type AIChannelConfigChangedEvent,
  type DispatchAfterSalesConfigChangedEvent,
  type EscortPricingChangedEvent,
  type EscortCatalogChangedEvent,
} from './events/index.js';

export { OperationType } from './operation-type.js';
