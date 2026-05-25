# Infrastructure

## Dependency Injection (Dagger 2)

The object graph is assembled at compile time via Dagger 2 annotations.

**Root component**: `AppComponent` aggregates 16 Dagger modules covering repositories, services, command handlers, AI/markdown wiring, and event listeners (`AppComponent.java:60-76`).

**Module structure**:
- Database, cache, and Discord modules are foundational — most services depend on them
- Repository modules provide data access interfaces
- Service modules wire business logic
- `EventModule` uses `@Multibinds` to collect event listeners via `@IntoSet`
- `AIAgentModule` is the largest module (~40 `@Provides` methods), providing all LangChain4j tools

**Construction**: `AppComponentFactory.create(EnvironmentConfig)` manually provides `DatabaseModule` and `CacheModule` at construction time (they need the config object).

## Event System

**Publisher**: `DomainEventPublisher` holds a `CopyOnWriteArrayList<Consumer<DomainEvent>>`.

**Dispatch**: Synchronous — each listener is called in order within the same thread. One listener's failure does not block others; exceptions are caught and logged.

**Listener registration**: Dagger `@IntoSet` multibindings collect all `Consumer<DomainEvent>` implementations. `EventModule` declares the multibound set and `DomainEventPublisher` receives it via constructor injection.

**14 event types** covering balance changes, game token changes, config changes, product changes, redemption completions, membership tier changes, AI messages, agent completions, and tool executions (`shared/events/*Event.java`).

## Cache Layer

**Interface**: `CacheService` with `get`, `put`, `invalidate` — designed to never throw.

**Implementations**:
- `RedisCacheService` — Lettuce-backed, JSON serialization via Jackson
- `NoOpCacheService` — singleton no-op for testing

**Key format**: `cache:{entity_type}:{guildId}:{userId}`

**Invalidation**: `CacheInvalidationListener` subscribes to `BalanceChangedEvent` and `GameTokenChangedEvent` and invalidates the corresponding cache keys.

## Database

**Connection pool**: HikariCP configured via `EnvironmentConfig` (pool size, timeouts, lifetime).

**Migrations**: Flyway with `baselineOnMigrate=true`. Schema migration runs at startup before JDA initialization. Migration failure blocks bot startup.

**Query layer**: jOOQ DSLContext for type-safe SQL. Some older repositories use raw JDBC.

**Persistence patterns**:
- Atomic conditional updates with `WHERE ... AND ... IS NULL RETURNING` for idempotent state transitions
- Claim/release columns (`processing_at`) for lightweight row-level locking in background workers
- Return-generated-keys for insert-then-read patterns

## Discord Abstraction Layer

**Domain interfaces** (in `discord/domain/`):
- `DiscordRuntimeGateway` — injected entry point for live Discord API access
- `DiscordInteraction` — unified reply interface (slash commands, buttons, modals)
- `DiscordContext` — event context extraction (guild, user, options)
- `DiscordEmbedBuilder` — fluent builder with Discord length-limit enforcement
- `DiscordSessionManager` — generic session management with TTL

**JDA implementations** (in `discord/services/`):
- Bridge JDA event objects to domain interfaces
- `JdaDiscordRuntimeGateway` uses `AtomicReference<JDA>` for thread-safe lazy publication

**Adapters** (in `discord/adapter/`):
- Stateless conversion utilities from JDA event types to domain interfaces

**Mocks** (in `discord/mock/`):
- `MockDiscordInteraction`, `MockDiscordContext`, `MockDiscordEmbedBuilder`
- Located in main source tree for use by all test modules

## Session Management

**Interface**: `DiscordSessionManager<K>` parameterized by `SessionType` enum.

**TTL**: 900 seconds (15 minutes), matching Discord's InteractionHook lifetime.

**Storage**: `ConcurrentHashMap<String, Session<K>>` with auto-expiry on read.

**Usage**: Each interactive panel (user panel, admin panel) has its own session manager instance to persist `InteractionHook` across pagination and multi-step workflows.
