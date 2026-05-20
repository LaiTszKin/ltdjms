# Checklist: Shared Infrastructure

- Date: 2026-05-20
- Feature: Shared Infrastructure

## Clarification & Approval Gate

- [ ] Clarification responses recorded (N/A — 所有需求基於現有 Java 程式碼)
- [ ] Explicit approval obtained (date/ref: [to be filled])

## Behavior-to-Test Checklist

- [ ] CL-01: `Result.ok(value)` 回傳 `isOk() === true`，`getValue()` 回傳原始值 — R1.1 → UT-Result-01～03 — Result: `NOT RUN`
- [ ] CL-02: `Result.err(error)` 回傳 `isErr() === true`，`getError()` 回傳原始錯誤 — R1.1 → UT-Result-04～05 — Result: `NOT RUN`
- [ ] CL-03: `Result.map(fn)` 對 Ok 值轉換，對 Err 保持不變 — R1.3 → UT-Result-06～07 — Result: `NOT RUN`
- [ ] CL-04: `Result.flatMap(fn)` 支援鏈式呼叫，短路在第一次 Err — R1.3 → UT-Result-08～09 — Result: `NOT RUN`
- [ ] CL-05: `DomainError.invalidInput(msg)` 回傳 Category=INVALID_INPUT、message 匹配 — R2.2 → UT-DomainError-01 — Result: `NOT RUN`
- [ ] CL-06: `EnvironmentConfig.parse()` 在缺少 DISCORD_BOT_TOKEN 時拋出彙總錯誤 — R3.1 → UT-Config-01 — Result: `NOT RUN`
- [ ] CL-07: `.env` 載入支援 `#` 註解、引號值、含 `=` 的值 — R3.1 → UT-Config-02 — Result: `NOT RUN`
- [ ] CL-08: Config 優先級: `process.env` > `.env` > Zod defaults — R3.1 → UT-Config-03 — Result: `NOT RUN`
- [ ] CL-09: Database pool 建立失敗時重試 3 次，最終拋出 `SchemaMigrationException` — R4.3 → IT-DB-01 — Result: `NOT RUN`
- [ ] CL-10: Migration runner 對已同步的 DB 為 no-op — R4.3 → IT-DB-02 — Result: `NOT RUN`
- [ ] CL-11: `CacheService.put(key, val, ttl)` + `get(key)` 正確存取 — R5.1 → IT-Cache-01 — Result: `NOT RUN`
- [ ] CL-12: CacheService 在 Redis 不可用時優雅降級（get 回傳 null、put 不拋例外） — R5.1 → IT-Cache-02 — Result: `NOT RUN`
- [ ] CL-13: DomainEventPublisher.publish(event) 呼叫所有已註冊 listener — R6.1 → UT-Event-01 — Result: `NOT RUN`
- [ ] CL-14: Listener 拋出例外時不影響其他 listener — R6.2 → UT-Event-02 — Result: `NOT RUN`
- [ ] CL-15: DiscordEmbedBuilder 強制 title ≤256、description ≤4096、fields ≤25 — R8.3 → UT-Discord-01 — Result: `NOT RUN`
- [ ] CL-16: EmbedBuilder.buildPaginated() 將超長內容分割為多個 embed — R8.3 → UT-Discord-02 — Result: `NOT RUN`
- [ ] CL-17: SelectMenuUtil.splitSelectMenus(30 items) → 2 個選單 — R8.6 → UT-Discord-03 — Result: `NOT RUN`
- [ ] CL-18: tsyringe container.resolve() 回傳 singleton — R9.2 → UT-DI-01 — Result: `NOT RUN`
- [ ] CL-19: main() 啟動序列: config → logger → db → redis → events → discord → ready — R3, R4, R8, R9 → IT-Main-01 — Result: `NOT RUN`

## Hardening Checklist

- [ ] Unit drift checks: Result 組合子邏輯與 Java `Result.java` 行為比較
- [ ] Property-based coverage: `EmbedView` to discord.js `EmbedBuilder` 轉換對隨機輸入不丟失資料
- [ ] External services mocked: Redis 不可用時的降級測試（使用 ioredis-mock 或 fake）
- [ ] Adversarial cases: 超大 Config 值（超長 token、負數 port）、SQL injection 嘗試
- [ ] Authorization, idempotency, concurrency risks: EventPublisher publish 期間的 listener 註冊/取消註冊安全
- [ ] Assertions verify outcomes: EmbedBuilder 截斷時驗證實際截斷後的字串長度，不僅驗證「無錯誤」
- [ ] Fixtures reproducible: Config test 使用固定 `.env` fixture 檔案

## E2E / Integration Decisions

- [ ] Database connection + migration: Integration test（對接真實 PostgreSQL Testcontainers 或 docker-compose） — Reason: 驗證 Drizzle schema 與 Java Flyway schema 的一致性
- [ ] Redis cache: Integration test（對接真實 Redis） — Reason: 驗證 TTL、序列化、降級行為
- [ ] Discord interaction abstraction: N/A (E2E 需完整 bot，留待 administration spec)
- [ ] main() startup: Integration test（啟動完整 DI 容器和 discord.js client） — Reason: 驗證啟動序列無錯誤

## Execution Summary

- [ ] Unit: `NOT RUN`
- [ ] Regression: `N/A`（新專案，無回歸）
- [ ] Property-based: `NOT RUN`
- [ ] Integration: `NOT RUN`
- [ ] E2E: `N/A`
- [ ] Mock scenarios: `NOT RUN`
- [ ] Adversarial: `NOT RUN`

## Completion Records

- [ ] types/ (Result + DomainError + DomainEvent types): pending
- [ ] infra/config/: pending
- [ ] infra/logger/: pending
- [ ] infra/database/: pending
- [ ] infra/cache/: pending
- [ ] infra/events/: pending
- [ ] discord/: pending
- [ ] di/: pending
- [ ] main.ts: pending
