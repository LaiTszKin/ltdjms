# Layers and Boundaries

## Module Map

The system is organized into horizontal layers (shared infrastructure) and vertical modules (business domains).

### Shared Infrastructure Layer

| Module | Responsibility |
|--------|---------------|
| `shared/` | Environment config, database, cache, event system, DI, result type |
| `discord/` | Discord API abstraction (domain interfaces, JDA implementations, adapters, mocks) |

### Business Domain Modules

| Module | Responsibility |
|--------|---------------|
| `currency/` | Guild currency config, member balances, transactions |
| `gametoken/` | Game token accounts, dice games, token transactions |
| `panel/` | User panel, admin panel, session management |
| `product/` | Product definitions, reward types, escort option catalog |
| `redemption/` | Redemption code generation, validation, and claim |
| `shop/` | Product browsing, currency purchase, fiat payment, post-payment processing |
| `dispatch/` | Escort dispatch orders, lifecycle state machine, after-sales |
| `membership/` | Global membership tiers, spend ledger, settlement, token grants, join tracking |
| `aichat/` | AI chat, channel allowlists, streaming, prompt loading |
| `aiagent/` | Agent channel config, tool execution, conversation memory, audit |
| `markdown/` | AI response Markdown validation, auto-fix, segmentation |

## Internal Module Structure

Each business module follows a consistent four-package layout:

- **`domain/`** — Core data models (records), repository interfaces, domain invariants
- **`persistence/`** — JDBC/jOOQ repository implementations, SQL queries
- **`services/`** — Business logic orchestration, cross-module coordination
- **`commands/`** — Discord event coordination, input validation, response assembly

Handlers in `commands/` act as thin Discord coordinators. Complex view assembly is delegated to factory/builder/helper classes. Business logic lives in `services/`.

## Data Flow Direction

### Slash Command / Interaction Flow

```
JDA Event
  -> ListenerAdapter (SlashCommandListener / ButtonHandler)
    -> CommandHandler (validates input, delegates to service)
      -> Service (orchestrates, uses Result<T, DomainError>)
        -> Repository (persistence)
        -> DomainEventPublisher (side effects)
      -> Response (embed, components)
```

### Fiat Payment Flow

```
Shop UI
  -> FiatOrderService.createFiatOnlyOrder()
    -> EcpayCvsPaymentService (ECPay API call)
    -> JdbcFiatOrderRepository.save()

ECPay Callback (HTTP)
  -> EcpayCallbackHttpServer
    -> FiatPaymentCallbackService.handleCallback()
      -> JdbcFiatOrderRepository.markPaidIfPending()

Scheduler (every 10s)
  -> FiatOrderPostPaymentWorker.processPendingOrders()
    -> Claim -> notify buyer -> record membership spend (snapshot) -> create escort -> grant reward -> mark fulfilled -> Release

Scheduler (hourly)
  -> MembershipSettlementScheduler
    -> Lease-guarded tick: retry spend/grant -> settle due members -> retry grants

Scheduler (every 60s)
  -> FiatPaymentReconciliationService
    -> Expire unpaid, query ECPay for missing callbacks
```

### AI Mention Flow

```
MessageReceivedEvent
  -> AIChatMentionListener
    -> AIChatMentionRoutingDecision.decide()
      -> AGENT_ROUTE | AI_CHAT_ROUTE | DENY
    -> Streaming AI response
      -> MarkdownValidatingAIChatService (decorator)
        -> DiscordMarkdownStreamProcessor (segment -> sanitize -> validate -> fix -> paginate)
```

## Boundary Rules

- Services communicate across module boundaries through injected service interfaces, not through commands/handlers
- Side effects (cache invalidation, panel updates, agent sync) propagate via `DomainEventPublisher`, not inline in service methods
- The `dispatch/` handoff service (`EscortDispatchHandoffService`) is the only boundary crosser for shop-to-dispatch integration
- The `product/` reward service (`ProductRewardService`) bridges product definitions to currency/token accounts
- Shop records membership spend via `PaidEscortOrderSnapshot` and `MembershipSpendRecorder`; membership does not depend on shop domain types
- The `markdown/` module is a decorator around `aichat/` — it knows nothing about Discord or business domains

## Runtime Entry Points

Production behavior is wired from `DiscordCurrencyBot` (`currency/bot/DiscordCurrencyBot.java`).

### Registered Slash Commands (7)

Defined in `SlashCommandListener.buildAllCommandDefinitions()` and dispatched in `onSlashCommandInteraction`: `currency-config`, `dice-game-1`, `dice-game-2`, `user-panel`, `admin-panel`, `shop`, `dispatch-panel`. Legacy handlers (`/balance`, `/adjust-balance`, `/game-token-adjust`, dice config commands) remain in the repo but are not registered; their UX lives in `/user-panel` and `/admin-panel` (`SlashCommandListener.java:104-106`, `AppComponent.java:54-56`).

### JDA Listeners (8)

`buildEventListeners()` registers: slash commands, user/admin panel buttons, admin product panel, dispatch panel interactions, shop buttons/selects, AI mention listener, guild member join listener (`DiscordCurrencyBot.java:193-212`).

### Background Workers and HTTP

- `EcpayCallbackHttpServer` — ECPay ReturnURL POST (`shop/services/EcpayCallbackHttpServer.java`)
- `FiatOrderProcessingScheduler` — post-payment fulfillment (10s) and payment reconciliation (60s)
- `MembershipSettlementScheduler` — hourly settlement, spend retry, token grants

### Non-Slash AI Entry

AI chat and agent flows are triggered by **bot mentions** in allowed or agent-enabled channels, not slash commands (`aichat/commands/AIChatMentionListener.java`).

## Code Evidence Index

| Topic | Primary source |
| --- | --- |
| Module map | `AppComponent.java:60-76`, package roots under `src/main/java/ltdjms/discord/` |
| Slash commands | `SlashCommandListener.java` |
| Listener wiring | `DiscordCurrencyBot.java:193-212` |
| Shop → dispatch handoff | `shop/commands/ShopSelectMenuHandler.java`, `dispatch/services/EscortDispatchHandoffService.java` |
| Fiat fulfillment | `shop/services/FiatOrderPostPaymentWorker.java` |
| Membership settlement | `membership/services/MembershipSettlementScheduler.java` |
