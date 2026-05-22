# Spec: Admin Panel PBT

- Date: 2026-05-22
- Feature: Admin Panel PBT
- Owner: [To be filled]

## Goal

透過 Integration PBT 驗證 Admin 管理面板所有管理功能的業務不變量（設定生效、不影響既有訂單、狀態一致性），確保管理操作走完整 facade→service→repository→real DB 管線並產生正確結果。

## Scope

### In Scope
- 貨幣經濟參數設定（CurrencyConfig）：設定後帳務行為符合新參數、不影響現有餘額
- 商品管理（ProductManagement）：上下架狀態變更、價格更新、庫存調整
- 兌換碼生成（RedemptionCodeGeneration）：格式正確、不重複、可兌換
- 遊戲設定（GameConfig / DiceConfig）：賠率、token cost 變更後新遊戲生效
- 遊戲代幣管理（GameTokenManagement）：批量調整代幣
- 護航派單規則配置（DispatchConfig）：選項目錄、價格設定
- AI 頻道設定（AIChannelConfig）：白名單頻道管理

### Out of Scope
- Discord admin panel UI 渲染（embed 格式驗證）
- Admin panel session 生命週期管理
- AI Agent 工具執行設定（已有獨立測試）
- Admin 權限驗證（Discord permission 層面，非業務邏輯）

## Functional Behaviors (BDD)

### Requirement 1: 貨幣經濟參數設定
**GIVEN** Guild A 貨幣名稱為「金幣」、符號為「🪙」
**WHEN** 管理員將貨幣名稱改為「鑽石」、符號改為「💎」
**THEN** Currency Config 查詢回傳新名稱「鑽石」、新符號「💎」
**AND** 所有用戶餘額不受影響（僅變更顯示設定）
**AND** 交易記錄顯示新貨幣名稱

**Requirements**:
- [ ] R1.1 貨幣名稱變更後立即生效
- [ ] R1.2 貨幣符號變更後立即生效
- [ ] R1.3 設定變更不影響已有餘額
- [ ] R1.4 設定變更產生 DomainEvent

### Requirement 2: 商品管理 — 上架／下架
**GIVEN** 商品 P 目前為上架狀態
**WHEN** 管理員將商品 P 下架
**THEN** 商品 P 狀態變為下架（不可購買）
**AND** 已存在但未完成的訂單不受影響
**AND** 新購買請求回傳錯誤（商品不可用）

**Requirements**:
- [ ] R2.1 商品下架後新購買請求失敗
- [ ] R2.2 商品重新上架後可正常購買
- [ ] R2.3 已存在的 pending 訂單不受上／下架影響

### Requirement 3: 商品管理 — 價格更新
**GIVEN** 商品 P 價格為 500 金幣
**WHEN** 管理員將價格改為 800 金幣
**THEN** 新訂單使用新價格 800 金幣
**AND** 已存在但未付款的訂單保持原價格 500 金幣

**Requirements**:
- [ ] R3.1 價格更新後新訂單反映新價格
- [ ] R3.2 既有 pending 訂單價格不變
- [ ] R3.3 貨幣價格與 TWD 價格可獨立更新

### Requirement 4: 兌換碼批量生成
**GIVEN** 商品 P 需要 10 個兌換碼
**WHEN** 管理員觸發批量生成（quantity=10）
**THEN** 產生 10 個唯一兌換碼
**AND** 每個 code 格式符合規範（prefix + random suffix）
**AND** 所有 code 可被不同用戶兌換
**AND** 無重複 code

**Requirements**:
- [ ] R4.1 生成數量 = 請求 quantity
- [ ] R4.2 所有 code 唯一
- [ ] R4.3 code 格式符合規範
- [ ] R4.4 生成的 code 全部可兌換（狀態為 available）

### Requirement 5: 遊戲設定 — Dice 參數
**GIVEN** DiceGame1 目前 multiplier 為 2.0、tokenCost 為 10
**WHEN** 管理員將 multiplier 改為 1.5、tokenCost 改為 20
**THEN** 新遊戲使用 multiplier=1.5、tokenCost=20
**AND** 遊戲設定變更產生 DomainEvent
**AND** 已有遊戲記錄不受影響

**Requirements**:
- [ ] R5.1 Dice multiplier 變更後新遊戲生效
- [ ] R5.2 Dice tokenCost 變更後新遊戲生效
- [ ] R5.3 已完成的遊戲記錄賠率不變
- [ ] R5.4 無效 multiplier（負數、0）被拒絕

### Requirement 6: 遊戲代幣管理
**GIVEN** Guild A 中有 U1=50 tokens, U2=30 tokens
**WHEN** 管理員為 U1 增加 20 tokens
**THEN** U1 tokens = 70
**AND** U2 tokens 不變
**AND** 操作記錄在 token transaction table

**Requirements**:
- [ ] R6.1 代幣增加後數量正確
- [ ] R6.2 代幣減少後數量正確（不可為負）
- [ ] R6.3 批量調整多個用戶代幣

### Requirement 7: 護航派單選項管理
**GIVEN** Guild A 目前有 3 個護航選項
**WHEN** 管理員新增第 4 個選項
**THEN** 選項目錄變為 4 個
**AND** 新選項可被選取建立護航訂單
**AND** 既有未完成訂單的選項不受影響

**Requirements**:
- [ ] R7.1 選項新增後目錄正確更新
- [ ] R7.2 選項刪除後不可再被選取
- [ ] R7.3 選項價格更新後新建訂單反映新價格

### Requirement 8: AI 頻道設定
**GIVEN** Guild A 有一個 AI 白名單頻道
**WHEN** 管理員新增第二個白名單頻道
**THEN** 兩個頻道均可觸發 AI 回應
**AND** 非白名單頻道不觸發 AI 回應

**Requirements**:
- [ ] R8.1 白名單頻道新增後立即生效
- [ ] R8.2 白名單頻道移除後不再觸發 AI
- [ ] R8.3 頻道設定變更不影響現有對話

## Error and Edge Cases
- [ ] 非管理員嘗試執行管理操作的權限檢查（由 facade 層攔截）
- [ ] 貨幣名稱包含非法字元（空字串、超長字串）
- [ ] 商品價格設為負數或超大數字
- [ ] 兌換碼生成 quantity=0 或超大數量（如 100000）
- [ ] Dice multiplier 為 0 或負數
- [ ] 同時兩個管理員修改同一設定的競爭條件
- [ ] 護航選項被刪除後，既有 pending 護航訂單的處理

## Clarification Questions
None

## References
- Project docs:
  - `docs/features/administration.md` — 管理面板功能說明
  - `docs/features/ai-chat-and-agent.md` — AI 頻道設定
  - `docs/features/escort-dispatch.md` — 護航派單功能
  - `docs/features/guild-economy.md` — 貨幣經濟
  - `docs/principles/event-driven-patterns.md` — 事件驅動模式
- Related code files:
  - `packages/admin/src/facades/CurrencyManagementFacade.ts`
  - `packages/admin/src/facades/ProductManagementFacade.ts`
  - `packages/admin/src/facades/GameConfigManagementFacade.ts`
  - `packages/admin/src/facades/GameTokenManagementFacade.ts`
  - `packages/admin/src/facades/DispatchManagementFacade.ts`
  - `packages/admin/src/facades/AIConfigManagementFacade.ts`
  - `packages/admin/src/panel/admin/handlers/BalanceManagementHandler.ts`
  - `packages/admin/src/panel/admin/handlers/TokenManagementHandler.ts`
  - `packages/admin/src/panel/admin/handlers/GameSettingsHandler.ts`
  - `packages/admin/src/panel/admin/product/AdminProductPanelHandler.ts`
  - `packages/admin/src/panel/admin/handlers/AIChannelConfigHandler.ts`
  - `packages/admin/src/panel/admin/handlers/EscortCatalogHandler.ts`
  - `packages/admin/src/panel/admin/handlers/EscortPricingHandler.ts`
  - `packages/admin/src/__tests__/admin-panel-update-listener.test.ts`
  - `packages/admin/src/__tests__/balance-management-handler.test.ts`
