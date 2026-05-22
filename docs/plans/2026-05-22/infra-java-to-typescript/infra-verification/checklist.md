# Checklist: Infrastructure Verification Suite

- Date: 2026-05-22
- Feature: infra-verification

## Usage Notes

- 此驗證套件的目標是確認 TS 基建行為與 Java 一致，非測試新功能。
- 每個測試必須能獨立執行且可重複。
- 需要 testcontainers (PostgreSQL + Redis) 來執行整合測試。
- Test result values: `PASS / FAIL / BLOCKED / NOT RUN / N/A`.

## Clarification & Approval Gate

- [ ] Clarification responses recorded (N/A — 需求明確).
- [ ] Affected plans updated after clarification (N/A).
- [ ] Explicit approval obtained (date/ref: [to be filled]).

## Behavior-to-Test Checklist

- [ ] CL-01: CacheService 合約 — R1.1 → T1.1 — Result: `NOT RUN`
- [ ] CL-02: RedisCacheService CRUD + graceful degradation — R1.2, R1.3 → T1.2 — Result: `NOT RUN`
- [ ] CL-03: CacheKeyGenerator 鍵格式與 Java 一致 — R1.1 → T1.3 — Result: `NOT RUN`
- [ ] CL-04: EnvironmentConfig 優先級順序 — R2.1, R2.2 → T2.1 — Result: `NOT RUN`
- [ ] CL-05: 必要設定缺失時拋出 — R2.3, R2.4 → T2.1 — Result: `NOT RUN`
- [ ] CL-06: EnvLoader 正確解析 .env — R2.x → T2.2 — Result: `NOT RUN`
- [ ] CL-07: 連線池重試邏輯 — R3.1 → T3.1 — Result: `NOT RUN`
- [ ] CL-08: Migration runner baseline + incremental — R3.2 → T3.2 — Result: `NOT RUN`
- [ ] CL-09: Migration 失敗錯誤處理 — R3.3 → T3.2 — Result: `NOT RUN`
- [ ] CL-10: EventPublisher 同步分發 + 隔離 — R4.1, R4.2 → T4.1 — Result: `NOT RUN`
- [ ] CL-11: 所有事件類型 eventType 值與 Java 一致 — R4.3 → T4.2 — Result: `NOT RUN`
- [ ] CL-12: Result map/flatMap/mapError 行為 — R5.1-R5.4 → T5.1 — Result: `NOT RUN`
- [ ] CL-13: DomainError category + factory — R5.5 → T5.2 — Result: `NOT RUN`
- [ ] CL-14: Snowflake ID 正確性 — 工具層 → T6.1 — Result: `NOT RUN`
- [ ] CL-15: 全基建 smoke test — 跨組件 → T7.1 — Result: `NOT RUN`

## Hardening Checklist

- [ ] Regression tests — 驗證測試本身應納入 CI (`make verify` 或獨立 script).
- [ ] Unit drift checks — 部分測試需手動比對 Java 原始碼確認行為一致性.
- [ ] Property-based coverage (N/A — 基礎設施層).
- [ ] External services mocked/faked — 單元測試使用 mock；整合測試使用 testcontainers.
- [ ] Adversarial cases — 包含：Redis 不可用、DB 連線失敗、Migration SQL 語法錯誤.
- [ ] Authorization, idempotency, concurrency risks — Migration runner 的冪等性已在測試範圍.
- [ ] Assertions verify outcomes/side-effects — 每個測試驗證具體行為結果.
- [ ] Fixtures reproducible — 整合測試使用 Docker Compose / testcontainers 確保環境一致.

## E2E / Integration Decisions

- [ ] Redis cache flow: Testcontainers — Reason: 需要真實 Redis 驗證 TTL、連線失敗等行為
- [ ] PostgreSQL migration: Testcontainers — Reason: 需要真實 PostgreSQL 驗證 migration 行為
- [ ] Discord abstraction: Mock — Reason: 不依賴 Discord API

## Execution Summary

- [ ] Unit: `NOT RUN`
- [ ] Regression: `N/A`
- [ ] Property-based: `N/A`
- [ ] Integration: `NOT RUN`
- [ ] E2E: `N/A`
- [ ] Mock scenarios: `NOT RUN`
- [ ] Adversarial: `NOT RUN`

## Completion Records

- [ ] Cache layer verification: pending — Remaining: T1.1-T1.3
- [ ] Config layer verification: pending — Remaining: T2.1-T2.2
- [ ] Database layer verification: pending — Remaining: T3.1-T3.2
- [ ] Event layer verification: pending — Remaining: T4.1-T4.2
- [ ] Type layer verification: pending — Remaining: T5.1-T5.2
- [ ] Utility layer verification: pending — Remaining: T6.1-T6.2
- [ ] Smoke test: pending — Remaining: T7.1
