# Tasks: Admin Panel PBT

- Date: 2026-05-22
- Feature: Admin Panel PBT

## **Task 1: 貨幣經濟參數設定 PBT**

Purpose: 驗證 CurrencyConfig 變更後即時生效、不影響既有餘額
Requirements: R1.1–R1.4
Scope: `packages/admin/src/__tests__/currency-config.pbt.test.ts`
Out of scope: 其他 admin 功能、Discord UI 渲染

- T1.1 [ ] **`packages/admin/src/__tests__/currency-config.pbt.test.ts`** — 匯入 test-infra + seed factory；設定 DI container with admin + economy modules
  - Verify: import 無報錯，DI resolve CurrencyManagementFacade

- T1.2 [ ] **currency-config.pbt.test.ts** — 實作 PBT：任意 guildId、任意新貨幣名稱、任意新符號，透過 CurrencyManagementFacade 更新後查詢驗證值正確
  - Verify: `vitest run` 通過 100 次隨機輸入

- T1.3 [ ] **currency-config.pbt.test.ts** — 驗證設定變更不影響既有用戶餘額
  - Verify: 變更前後所有用戶餘額不變

## **Task 2: 商品管理 PBT**

Purpose: 驗證商品上架／下架／價格更新操作的正確性
Requirements: R2.1–R2.3, R3.1–R3.3
Scope: `packages/admin/src/__tests__/product-management.pbt.test.ts`
Out of scope: 兌換碼、購買流程（由 shop-pbt 負責）

- T2.1 [ ] **`packages/admin/src/__tests__/product-management.pbt.test.ts`** — 實作 PBT：任意 guildId、任意商品，透過 ProductManagementFacade 下架後驗證狀態變更、上架後恢復
  - Verify: `vitest run` 通過

- T2.2 [ ] **product-management.pbt.test.ts** — 驗證價格更新：新訂單反映新價格、既有 pending 訂單保持原價格
  - Verify: DB 中既有訂單價格不變、新訂單使用新價格

- T2.3 [ ] **product-management.pbt.test.ts** — 驗證已下架商品不可被購買（購買請求回傳 DomainError）
  - Verify: 下架商品購買失敗

## **Task 3: 兌換碼批量生成 PBT**

Purpose: 驗證批量生成兌換碼的唯一性、格式正確
Requirements: R4.1–R4.4
Scope: `packages/admin/src/__tests__/redemption-code-gen.pbt.test.ts`
Out of scope: 兌換碼兌換（由 shop-pbt 負責）

- T3.1 [ ] **`packages/admin/src/__tests__/redemption-code-gen.pbt.test.ts`** — 實作 PBT：任意 guildId、任意 quantity（1-100），驗證生成數量正確、所有 code 唯一
  - Verify: `vitest run` 通過

- T3.2 [ ] **redemption-code-gen.pbt.test.ts** — 驗證 code 格式符合規範
  - Verify: 所有 code 匹配 prefix + random suffix 格式

- T3.3 [ ] **redemption-code-gen.pbt.test.ts** — 驗證生成的 code 全部狀態為 available（可被兌換）
  - Verify: DB 中所有 code 狀態為 available

## **Task 4: 遊戲設定 PBT — Dice Config**

Purpose: 驗證 Dice 參數變更後新遊戲生效、既有記錄不受影響
Requirements: R5.1–R5.4
Scope: `packages/admin/src/__tests__/game-config.pbt.test.ts`
Out of scope: 遊戲代幣管理、AI 設定

- T4.1 [ ] **`packages/admin/src/__tests__/game-config.pbt.test.ts`** — 實作 PBT：任意 guildId、任意 multiplier、任意 tokenCost，透過 GameConfigManagementFacade 更新後查詢驗證
  - Verify: `vitest run` 通過

- T4.2 [ ] **game-config.pbt.test.ts** — 驗證無效 multiplier（負數、0）被拒絕
  - Verify: 無效值回傳 DomainError

- T4.3 [ ] **game-config.pbt.test.ts** — 驗證變更後新遊戲使用新參數、既有遊戲記錄不受影響
  - Verify: 既有 dice transaction record 的 multiplier 不變

## **Task 5: 遊戲代幣管理 PBT**

Purpose: 驗證代幣批量調整的正確性
Requirements: R6.1–R6.3
Scope: `packages/admin/src/__tests__/game-token-management.pbt.test.ts`
Out of scope: Economy 模組的 token 操作（已在 economy-pbt）

- T5.1 [ ] **`packages/admin/src/__tests__/game-token-management.pbt.test.ts`** — 實作 PBT：任意 guildId、任意多個用戶、任意 token adjust 值，透過 GameTokenManagementFacade 調整後驗證
  - Verify: `vitest run` 通過

- T5.2 [ ] **game-token-management.pbt.test.ts** — 驗證減少代幣時不可為負
  - Verify: 超扣回傳 DomainError

## **Task 6: 護航派單選項管理 PBT**

Purpose: 驗證護航選項目錄的 CRUD 操作
Requirements: R7.1–R7.3
Scope: `packages/admin/src/__tests__/dispatch-config.pbt.test.ts`
Out of scope: 護航訂單建立（由 dispatch module 測試）

- T6.1 [ ] **`packages/admin/src/__tests__/dispatch-config.pbt.test.ts`** — 實作 PBT：任意 guildId、任意選項，透過 DispatchManagementFacade 新增／刪除／更新選項
  - Verify: `vitest run` 通過

- T6.2 [ ] **dispatch-config.pbt.test.ts** — 驗證選項價格更新後新建訂單反映新價格
  - Verify: DB 中新訂單使用新價格

## **Task 7: AI 頻道設定 PBT**

Purpose: 驗證 AI 白名單頻道的新增與移除
Requirements: R8.1–R8.3
Scope: `packages/admin/src/__tests__/ai-channel-config.pbt.test.ts`
Out of scope: AI 實際回應測試

- T7.1 [ ] **`packages/admin/src/__tests__/ai-channel-config.pbt.test.ts`** — 實作 PBT：任意 guildId、任意頻道 ID，透過 AIConfigManagementFacade 新增／移除白名單頻道
  - Verify: `vitest run` 通過

- T7.2 [ ] **ai-channel-config.pbt.test.ts** — 驗證新增後頻道在白名單中、移除後不在白名單中
  - Verify: DB 查詢確認 channel restriction 狀態正確
