# Event-Driven Patterns

## Domain Events

Events are simple Java records implementing the sealed `DomainEvent` interface, which requires a `long guildId()` accessor.

**15 event types** (see `shared/events/`):
- `BalanceChangedEvent`, `GameTokenChangedEvent` — balance/token changes
- `CurrencyConfigChangedEvent`, `DiceGameConfigChangedEvent` — config changes
- `ProductChangedEvent` — product CRUD (with `OperationType` enum)
- `RedemptionCodesGeneratedEvent`, `ProductRedemptionCompletedEvent` — redemption
- `MembershipTierChangedEvent` — global membership tier updates after settlement or admin adjustment
- `MembershipPeriodSpendChangedEvent` — period spend ledger changes (e.g. admin `ADMIN_ADJUST`)
- `AIMessageEvent` — AI interaction record
- `AgentCompletedEvent`, `AgentFailedEvent` — agent session outcomes
- `AIAgentChannelConfigChangedEvent` — agent config changes
- `LangChain4jToolExecutionStartedEvent`, `LangChain4jToolExecutedEvent` — tool audit

## Publisher Pattern

`DomainEventPublisher` holds a `CopyOnWriteArrayList<Consumer<DomainEvent>>`. Listeners are registered:
1. Via constructor injection: Dagger `@IntoSet` multibindings collect all `Consumer<DomainEvent>` implementations
2. Via `register()`: manual registration for dynamic listeners

**Evidence**: See `DomainEventPublisher.java`, `EventModule.java`

## Synchronous Dispatch

Events are dispatched synchronously:
- All listeners run in the publisher's thread
- Per-listener failure isolation: exceptions are caught and logged per listener
- No ordering guarantee beyond insertion order

This means service methods that call `publish()` block until all listeners complete. Long-running listeners (AI processing, complex panel updates) can delay the caller.

## Key Listeners

| Listener | Events Consumed | Side Effect |
|----------|----------------|-------------|
| `CacheInvalidationListener` | BalanceChangedEvent, GameTokenChangedEvent | Invalidates Redis cache keys |
| `UserPanelUpdateListener` | BalanceChangedEvent, GameTokenChangedEvent, CurrencyConfigChangedEvent, MembershipTierChangedEvent, MembershipPeriodSpendChangedEvent | Refreshes open user panels |
| `AdminPanelUpdateListener` | CurrencyConfigChangedEvent, DiceGameConfigChangedEvent, ProductChangedEvent, RedemptionCodesGeneratedEvent | Refreshes open admin panels |
| `ProductRedemptionUpdateListener` | ProductRedemptionCompletedEvent | Refreshes admin product panels |
| `AgentConfigCacheInvalidationListener` | AIAgentChannelConfigChangedEvent | Invalidates agent cache keys |
| `AgentCompletionListener` | AgentCompletedEvent, AgentFailedEvent | Sends agent result messages |
| `ToolExecutionListener` | LangChain4jToolExecutionStartedEvent | Sends tool execution notifications |
| `ToolExecutionInterceptor` | (direct) LangChain4jToolExecutedEvent | Records audit log |
