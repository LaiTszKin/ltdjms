# Checklist: Unified Domain Event Pipeline

- Date: 2026-03-10
- Feature: Unified Domain Event Pipeline

## Usage Notes
- 此檢查表已依本次架構重構調整，聚焦事件管道 wiring 與分發保證。
- 若項目不適用，保留 `N/A` 並寫明具體原因。
- 本次主要風險在於 listener 遺漏接線、分發退化與例外隔離失效，而非業務規則改變。

## Clarification & Approval Gate (required when clarification replies exist)
- [x] User clarification responses are recorded (N/A - 本次需求已由 GIVEN/WHEN/THEN 明確描述，無額外澄清回覆)。
- [x] Affected specs are reviewed/updated (`spec.md` / `tasks.md` / `checklist.md`; N/A - 目前為首次建立規格文件)。
- [x] Explicit user approval on updated specs is obtained (date/conversation reference: 2026-03-10 user reply "同意").

## Behavior-to-Test Checklist

- [x] CL-01 事件管道建立後會把事件送到所有已宣告的監聽器
  - Requirement mapping: `R1.1`, `R3.1`
  - Actual test case IDs: `UT-DEP-01`
  - Test level: `Unit`
  - Risk class: `regression`
  - Property/matrix focus: `listener set matrix`
  - External dependency strategy: `none`
  - Oracle/assertion focus: `side effects`, `emitted event`
  - Test result: `PASS`
  - Notes (optional): `DomainEventPublisherTest.shouldDispatchEventToAllConstructorInjectedListeners` 已驗證多監聽器分發。

- [x] CL-02 單一監聽器失敗時，其他監聽器仍可收到同一事件
  - Requirement mapping: `R2.2`
  - Actual test case IDs: `UT-DEP-02`
  - Test level: `Unit`
  - Risk class: `external failure`
  - Property/matrix focus: `failure isolation`
  - External dependency strategy: `none`
  - Oracle/assertion focus: `side effects`, `no cascade failure`
  - Test result: `PASS`
  - Notes (optional): `DomainEventPublisherTest.shouldContinueDispatchingWhenOneListenerThrows` 已鎖住錯誤隔離行為。

- [x] CL-03 DI 組裝可自動收集 listener，且不再需要 bot 啟動碼手動註冊
  - Requirement mapping: `R1.2`, `R1.3`, `R3.2`, `R3.3`
  - Actual test case IDs: `IT-DEP-01`
  - Test level: `Integration`
  - Risk class: `data integrity`
  - Property/matrix focus: `DI wiring matrix`
  - External dependency strategy: `mocked service states`
  - Oracle/assertion focus: `side effects`, `emitted event`
  - Test result: `PASS`
  - Notes (optional): `DomainEventPublisherDaggerWiringTest.shouldAssembleListenersIntoUnifiedEventPipeline` 驗證 Dagger multibinding 會自動組裝 listener。

- [x] CL-04 事件管道在沒有 listener 或 listener 遺漏時能安全暴露問題
  - Requirement mapping: `R2.3`
  - Actual test case IDs: `UT-DEP-03`
  - Test level: `Unit`
  - Risk class: `boundary`
  - Property/matrix focus: `empty set`
  - External dependency strategy: `none`
  - Oracle/assertion focus: `no exception`, `explicit wiring coverage`
  - Test result: `PASS`
  - Notes (optional): `DomainEventPublisherTest.shouldAllowPublishingWithNoListeners` 與 `DomainEventPublisherDaggerWiringTest.shouldBuildPublisherWhenNoListenersAreBound` 皆已驗證空集合情境。

## Required Hardening Records
- [x] Regression tests are added/updated for bug-prone or high-risk behavior, or `N/A` is recorded with a concrete reason.
- [x] Property-based coverage is added/updated for changed business logic, or `N/A` is recorded with a concrete reason.
  - N/A reason: 本次僅重構 DI 與事件基礎設施，未新增可用 property-based 驗證的業務規則。
- [x] External services in the business logic chain are mocked/faked for scenario testing, or `N/A` is recorded with a concrete reason.
  - N/A reason: 事件管道測試不依賴外部服務。
- [x] Adversarial/penetration-style cases are added/updated for abuse paths and edge combinations, or `N/A` is recorded with a concrete reason.
  - Coverage note: 以 throwing listener 驗證故障隔離，覆蓋高風險異常路徑。
- [x] Authorization, invalid transition, replay/idempotency, and concurrency risks are evaluated; uncovered items are marked `N/A` with concrete reasons.
  - N/A reason: 本次不涉及權限、狀態轉移、重播或併發語義改變。
- [x] Assertions verify business outcomes and side effects/no-side-effects, not only "returns 200" or "does not throw".
- [x] Test fixtures are reproducible (fixed seed/clock/fixtures) or `N/A` is recorded with a concrete reason.

## E2E Decision Record
- [ ] Build E2E (case: `N/A`; reason: `N/A`).
- [x] Do not build E2E; cover with integration tests instead (alternative case: `IT-DEP-01`; reason: `本次為 DI/event infra 重構，關鍵風險在組裝與分發，不在 Discord 端互動流程`).
- [ ] No additional E2E/integration hardening required (reason: `N/A`).

## Execution Summary (fill with actual results)
- [x] Unit tests: `PASS`
- [x] Regression tests: `PASS`
- [x] Property-based tests: `N/A`
- [x] Integration tests: `PASS`
- [x] E2E tests: `N/A`
- [x] External service mock scenarios: `N/A`
- [x] Adversarial/penetration-style cases: `PASS`

## Completion Rule
- [x] Agent has updated checkboxes, test outcomes, and necessary notes based on real execution (including added/removed items).
