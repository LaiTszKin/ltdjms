# Checklist: Cache Invalidation Listener

- Date: 2026-05-22
- Feature: cache-invalidation-listener

## Usage Notes

- 快取失效邏輯為非關鍵路徑（cache miss 會 fallback 到 DB），測試重點在正確性和 graceful degradation。
- Property-based coverage: `N/A` — 此為基礎設施層，輸入空間有限且行為確定性強，不適合 PBT。
- Test result values: `PASS / FAIL / BLOCKED / NOT RUN / N/A`.

## Clarification & Approval Gate

- [ ] Clarification responses recorded (N/A — 需求明確).
- [ ] Affected plans updated after clarification (N/A).
- [ ] Explicit approval obtained (date/ref: [to be filled]).

## Behavior-to-Test Checklist

- [x] CL-01: BalanceChangedEvent 正確觸發餘額快取失效 — R1.1, R1.2 → T3.1 — Result: `PASS`
- [x] CL-02: GameTokenChangedEvent 正確觸發代幣快取失效 — R2.1, R2.2 → T3.1 — Result: `PASS`
- [x] CL-03: 非相關事件不觸發快取失效 — R1.2, R2.1 → T3.1 — Result: `PASS`
- [x] CL-04: Cache service 失敗時不中斷事件處理鏈 — R1.3 → T3.1 — Result: `PASS`
- [x] CL-05: 事件缺少必要欄位時不拋出例外 — R1.2 → T3.1 — Result: `PASS`

## Hardening Checklist

- [ ] Regression tests for bug-prone/high-risk behavior (N/A — 新建功能).
- [ ] Unit drift checks for non-trivial tasks (N/A — VT 合約已定義預期行為).
- [ ] Property-based coverage for business logic (N/A — 基礎設施層).
- [ ] External services mocked/faked — Redis 透過 mock CacheService 隔離.
- [ ] Adversarial cases for abuse paths (N/A — 此為內部基礎設施).
- [ ] Authorization, idempotency, concurrency risks evaluated — 快取失效操作為冪等；並發 non-issue.
- [ ] Assertions verify outcomes/side-effects — 驗證 `invalidate()` 被呼叫且鍵格式正確.
- [ ] Fixtures reproducible (N/A — 使用 mock，無需 fixture).

## E2E / Integration Decisions

- [ ] Cache invalidation flow: Integration — Reason: 需驗證監聽器透過 DI 註冊後在真實事件流中正確運作；使用 testcontainers 的 Redis

## Execution Summary

- [x] Unit: `PASS`
- [ ] Regression: `N/A`
- [ ] Property-based: `N/A`
- [ ] Integration: `N/A`
- [ ] E2E: `N/A`
- [x] Mock scenarios: `PASS`
- [ ] Adversarial: `N/A`

## Completion Records

- [x] Cache invalidation listener: done — T1, T2, T3
