# Tasks: Unified Domain Event Pipeline

- Date: 2026-03-10
- Feature: Unified Domain Event Pipeline

## **Task 1: 設計統一事件管道 DI 組裝方式**

對應 `R1.1`、`R2.1`、`R2.3`，核心目標是讓事件監聽器由 Dagger 模組分散宣告、集中注入到同一個 `DomainEventPublisher`。

- 1. [x] 盤點所有現有 `Consumer<DomainEvent>` 監聽器並確認應納入事件管道的範圍
  - 1.1 [x] 確認 `UserPanelUpdateListener`、`AdminPanelUpdateListener`、`CacheInvalidationListener`、`AgentConfigCacheInvalidationListener`、`AgentCompletionListener`、`ToolExecutionListener` 的接線方式
  - 1.2 [x] 檢查是否有既有監聽器目前未註冊卻應納入統一管道
- 2. [x] 設計 Dagger multibinding 或等價方案，讓各模組能自行宣告事件監聽器貢獻
  - 2.1 [x] 調整 `EventModule` 與相關 module/provider 以建立 `Set<Consumer<DomainEvent>>`
  - 2.2 [x] 確保空集合或未來擴充情境仍可安全建立 publisher

## **Task 2: 實作啟動流程與監聽器接線重構**

對應 `R1.2`、`R1.3`、`R2.2`、`R3.3`，核心目標是把事件接線責任從 `DiscordCurrencyBot` 移到 DI 與事件基礎設施。

- 2. [x] 調整 `DomainEventPublisher` 使其支援由建構子/DI 初始化監聽器集合
  - 2.1 [x] 保留 `publish(...)` 的同步分發與例外隔離語意
  - 2.2 [x] 視需要保留向後相容的手動註冊能力，避免測試或局部使用情境破壞
- 3. [x] 移除 `DiscordCurrencyBot` 中對具體監聽器的手動 `register(...)`
  - 3.1 [x] 清理 `AppComponent` 僅為手動註冊而暴露的 listener accessor（若已不再需要）
  - 3.2 [x] 讓各 listener 只在自身 module 宣告加入事件管道，不再依賴 bot 啟動碼

## **Task 3: 補強回歸與 wiring 測試**

對應 `R3.1`、`R3.2` 與錯誤/邊界案例，核心目標是用測試鎖住事件管道重構的行為。

- 3. [x] 新增或更新 `DomainEventPublisher` 單元測試
  - 3.1 [x] 驗證多個監聽器皆會收到事件
  - 3.2 [x] 驗證單一監聽器拋錯時其他監聽器仍會被呼叫
- 4. [x] 新增 DI wiring 測試或等價回歸測試
  - 4.1 [x] 驗證 module 宣告的 listener 集合會被自動組裝進 publisher
  - 4.2 [x] 驗證 bot 啟動碼不再需要手動註冊即可維持事件分發

## Notes
- 任務順序應先完成事件管道組裝設計，再做實作接線，最後補測試與驗證。
- 本次屬架構重構，不涉及新的業務規則；property-based 測試預期可標記 `N/A`，但需在 `checklist.md` 寫明理由。
- 若發現未接線的既有監聽器，應優先判斷是否屬於本次統一事件管道範圍並以最小修改納入。
