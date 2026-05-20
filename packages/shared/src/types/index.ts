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
  type BalanceChangedEvent,
  type GameTokenChangedEvent,
  type CurrencyConfigChangedEvent,
  GameType,
  type DiceGameConfigChangedEvent,
  OperationType,
  type ProductChangedEvent,
  type RedemptionCodesGeneratedEvent,
  type ProductRedemptionTransaction,
  type ProductRedemptionCompletedEvent,
  type AIMessageEvent,
  type DispatchAfterSalesConfigChangedEvent,
  type EscortPricingChangedEvent,
  type EscortCatalogChangedEvent,
  type AIAgentChannelConfigChangedEvent,
  type ConversationMessage,
  type AgentCompletedEvent,
  type AgentFailedEvent,
  type ToolExecutionStartedEvent,
  type ToolExecutedEvent,
  type AnyDomainEvent,
} from './events/domain-event.js';
