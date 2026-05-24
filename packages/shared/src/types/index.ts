export { type Result, Ok, Err, Unit, ok, okVoid, err, isOk, isErr } from './result.js';

export { DomainErrorCategory, DomainError } from './domain-error.js';

export { type DomainEvent, GameType } from './events/index.js';
export type {
  AgentCompletedEvent,
  AgentFailedEvent,
  LangChain4jToolExecutionStartedEvent,
  LangChain4jToolExecutedEvent,
} from './events/index.js';

export { OperationType } from './operation-type.js';
