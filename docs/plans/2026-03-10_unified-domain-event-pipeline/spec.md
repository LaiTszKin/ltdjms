# Spec: Unified Domain Event Pipeline

- Date: 2026-03-10
- Feature: Unified Domain Event Pipeline
- Owner: Codex

## Goal
以單一、由 DI 組裝的事件管道取代啟動時手動註冊監聽器，讓所有需要監聽 `DomainEvent` 的模組都能透過同一條管道接收事件。

## Scope
- In scope: 將現有 `DomainEventPublisher` 改為由 Dagger 統一收集監聽器、移除 `DiscordCurrencyBot` 中的手動 `register(...)`、把現有事件監聽模組接到統一管道、補上事件管道 wiring 與回歸測試。
- Out of scope: 更改事件型別結構、引入非同步事件匯流排、調整事件 payload、改寫各模組既有業務邏輯。

## Functional Behaviors (BDD)

### Requirement 1: 啟動時自動組裝所有事件監聽器
**GIVEN** 系統有多個需要監聽 `DomainEvent` 的模組
**AND** 這些模組由 Dagger 管理生命週期
**WHEN** 應用程式建立統一事件管道
**THEN** 所有已宣告加入事件管道的監聽器都應自動註冊到同一個 `DomainEventPublisher`
**AND** 啟動流程不應再依賴 `DiscordCurrencyBot` 逐一手動呼叫 `register(...)`

**Requirements**:
- [x] R1.1 `DomainEventPublisher` 必須能以建構子或 DI 注入方式接收監聽器集合，並在建立時完成內部註冊。
- [x] R1.2 `DiscordCurrencyBot` 啟動流程不得再包含任何針對具體監聽器的手動註冊邏輯。
- [x] R1.3 現有已投入運作的監聽器（使用者面板、管理面板、快取失效、AI Agent 相關監聽器）都必須透過統一事件管道接線。

### Requirement 2: 模組透過統一事件管道直接監聽事件
**GIVEN** 任一模組實作了 `DomainEvent` 監聽能力
**AND** 該模組被宣告加入事件管道
**WHEN** 任一服務發佈事件
**THEN** 該事件應經由統一事件管道分發給所有相容監聽器
**AND** 單一監聽器失敗不得阻止其他監聽器接收同一事件

**Requirements**:
- [x] R2.1 監聽器加入事件管道的方式必須可由模組本身宣告，而不是集中在 bot 啟動類別硬編碼。
- [x] R2.2 `DomainEventPublisher.publish(...)` 必須維持同步分發與逐一隔離錯誤的既有行為。
- [x] R2.3 事件管道對沒有監聽器的情況必須安全，且不應因空集合而無法建立。

### Requirement 3: 重構後的事件基礎設施可被驗證且易於擴充
**GIVEN** 後續可能新增新的事件監聽模組
**AND** 本次變更屬於跨模組架構重構
**WHEN** 開發者新增另一個監聽器
**THEN** 他應只需在對應模組完成事件管道宣告即可加入分發流程
**AND** 測試必須能驗證統一事件管道的組裝與分發不會退化

**Requirements**:
- [x] R3.1 至少要有一組單元/回歸測試驗證 `DomainEventPublisher` 能把事件送到所有注入的監聽器。
- [x] R3.2 至少要有一組 wiring 測試或等價驗證，確保事件監聽器集合能由 DI 組裝並驅動分發。
- [x] R3.3 新增監聽器所需修改點必須侷限在事件模組宣告與監聽器本身，不需再修改 bot 啟動碼。

## Error and Edge Cases
- [x] 同一事件由多個監聽器處理時，單一監聽器拋例外不應中斷其他監聽器。
- [x] 事件管道在監聽器集合為空時仍可建立並安全發佈事件。
- [x] 模組漏接到事件管道時，測試必須能暴露 wiring 缺口，避免靜默失效。
- [x] 已存在但未被註冊的監聽器不應在重構中繼續被遺漏。
- [x] 啟動流程重構後不得改變既有事件發佈端 API（`publish(...)`）與同步語意。

## Clarification Questions
None

## References
- Official docs:
  - [Dagger multibindings guide](https://dagger.dev/dev-guide/multibindings.html)
  - [Dagger `@Binds` API](https://dagger.dev/api/latest/dagger/Binds.html)
  - [Dagger `@IntoSet` API](https://dagger.dev/api/latest/dagger/multibindings/IntoSet.html)
- Related code files:
  - `src/main/java/ltdjms/discord/shared/events/DomainEventPublisher.java`
  - `src/main/java/ltdjms/discord/currency/bot/DiscordCurrencyBot.java`
  - `src/main/java/ltdjms/discord/shared/di/EventModule.java`
  - `src/main/java/ltdjms/discord/shared/di/CommandHandlerModule.java`
  - `src/main/java/ltdjms/discord/shared/di/CacheModule.java`
  - `src/main/java/ltdjms/discord/shared/di/AIAgentModule.java`
