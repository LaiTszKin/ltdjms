# Spec: Economy Business Invariant PBT

- Date: 2026-05-22
- Feature: Economy Business Invariant PBT
- Owner: [To be filled]

## Goal

透過 Integration PBT 驗證 Economy 模組所有用戶可操作功能的業務不變量（餘額守恆、賠率正確、冪等交易），一次走完整 handler→service→repository→real DB 管線。

## Scope

### In Scope
- 餘額轉帳（Balance Transfer）：`sum(balances)` 守恆、不可為負、不重複入帳
- 骰子遊戲 1（DiceGame1）：賠率計算正確（`bet × multiplier === payout`）、餘額同步更新
- 骰子遊戲 2（DiceGame2）：賠率計算正確（`bet × multiplier === payout`）、餘額同步更新
- 遊戲代幣管理（GameToken）：代幣 add/remove/spend 數量正確、不可為負
- Currency Config 查詢：貨幣名稱、符號、匯率正確回傳

### Out of Scope
- 管理員設定貨幣參數的 admin panel 流程（由 admin-pbt 負責）
- Discord interaction 層的 UI 渲染驗證
- 效能測試（PBT 框架已量測 response time，但不做專門的效能調校）
- 與 Shop/Dispatch 模組的跨模組互動

## Functional Behaviors (BDD)

### Requirement 1: 餘額轉帳 — 總量守恆
**GIVEN** Guild A 中有三個用戶 U1（1000 金幣）、U2（500 金幣）、U3（200 金幣）
**AND** 初始總餘額 = 1700 金幣
**WHEN** U1 轉帳任意正整數金額 X 給 U2（X ≤ U1 餘額）
**THEN** 系統總餘額保持 1700 不變
**AND** U1 餘額 = 1000 - X
**AND** U2 餘額 = 500 + X
**AND** U3 餘額不變 = 200

**Requirements**:
- [ ] R1.1 任意合法轉帳後 `sum(allBalances)` 守恆
- [ ] R1.2 轉帳後 sender 餘額精確扣減
- [ ] R1.3 轉帳後 receiver 餘額精確增加
- [ ] R1.4 無關第三方餘額不受影響

### Requirement 2: 餘額轉帳 — 不可透支
**GIVEN** U1 有 100 金幣
**WHEN** U1 嘗試轉帳 101 金幣給 U2
**THEN** 操作回傳錯誤（餘額不足）
**AND** U1 餘額保持 100
**AND** U2 餘額不變

**Requirements**:
- [ ] R2.1 轉帳金額超過餘額時回傳 DomainError
- [ ] R2.2 失敗轉帳不產生任何 transaction record
- [ ] R2.3 失敗轉帳後所有用戶餘額不變

### Requirement 3: 餘額轉帳 — 不可轉給自己
**GIVEN** U1 有 500 金幣
**WHEN** U1 嘗試轉帳給自己
**THEN** 操作回傳錯誤
**AND** U1 餘額保持不變

**Requirements**:
- [ ] R3.1 sender === receiver 時回傳 DomainError
- [ ] R3.2 不產生 transaction record

### Requirement 4: DiceGame1 — 賠率正確
**GIVEN** Guild A 中 U1 有 10000 金幣
**AND** DiceGame1 設定賠率 multiplier 與 token cost
**WHEN** U1 下注 betAmount，結果為任意點數
**THEN** 賠付金額 = betAmount × multiplier
**AND** U1 最終餘額 = 初始餘額 - tokenCost + payout
**AND** tokenCost 正確扣減（若有）

**Requirements**:
- [ ] R4.1 任意合法下注後賠付金額 = betAmount × multiplier（精確計算）
- [ ] R4.2 所有用戶總餘額變化僅來自遊戲 reward（非憑空產生）
- [ ] R4.3 下注後 game token 扣減正確

### Requirement 5: DiceGame2 — 賠率正確
**GIVEN** Guild A 中 U1 有 10000 金幣
**AND** DiceGame2 設定賠率表（不同組合對應不同 multiplier）
**WHEN** U1 下注 betAmount，結果為任意組合
**THEN** 賠付金額 = betAmount × 對應 multiplier
**AND** U1 最終餘額 = 初始餘額 - tokenCost + payout

**Requirements**:
- [ ] R5.1 所有合法組合的賠率均正確對應
- [ ] R5.2 賠付金額 = betAmount × multiplier
- [ ] R5.3 下注後 game token 扣減正確

### Requirement 6: 遊戲代幣 — 數量正確
**GIVEN** Guild A 中 U1 有 50 tokens
**WHEN** 管理員為 U1 調整 tokens（add/remove/set）
**THEN** 操作後 token 數量精確反映變更
**AND** 所有用戶總 token 數量守恆（add/remove 操作）
**AND** token 數量不可為負

**Requirements**:
- [ ] R6.1 token add 後數量 = 初始 + amount
- [ ] R6.2 token remove 後數量 = 初始 - amount
- [ ] R6.3 token set 後數量 = 設定值
- [ ] R6.4 token 不可扣至負數，超扣回傳 DomainError

### Requirement 7: Currency Config — 正確回傳
**GIVEN** Guild A 設定貨幣名稱為「金幣」、符號為「🪙」
**WHEN** 查詢 Guild A 的 Currency Config
**THEN** 回傳貨幣名稱「金幣」、符號「🪙」
**AND** 未設定貨幣的 Guild B 回傳預設值

**Requirements**:
- [ ] R7.1 Currency Config 查詢回傳正確設定值
- [ ] R7.2 未設定 guild 回傳預設值、不拋錯

## Error and Edge Cases
- [ ] DiceGame 在 guild 未設定 currency config 時的行為
- [ ] DiceGame 在 guild 未設定 dice config 時的行為
- [ ] 用戶餘額為 0 時進行轉帳操作
- [ ] 大量並行轉帳的資料一致性（透過 PBT 隨機順序模擬）
- [ ] 金額為 0 或負數的邊界輸入
- [ ] 超大金額（接近 JS Number.MAX_SAFE_INTEGER）的計算精度

## Clarification Questions
None

## References
- Project docs:
  - `docs/features/guild-economy.md` — 貨幣經濟功能說明
  - `docs/principles/testing-patterns.md` — 測試模式
- Related code files:
  - `packages/economy/src/currency/services/balance-service.ts` — 餘額查詢
  - `packages/economy/src/currency/services/balance-adjustment-service.ts` — 餘額調整（轉帳）
  - `packages/economy/src/currency/services/currency-tx-service.ts` — 交易記錄
  - `packages/economy/src/dice/services/dice-game-1-service.ts` — 骰子遊戲 1
  - `packages/economy/src/dice/services/dice-game-2-service.ts` — 骰子遊戲 2
  - `packages/economy/src/dice/services/game-reward-service.ts` — 遊戲獎勵發放
  - `packages/economy/src/token/services/game-token-service.ts` — 遊戲代幣
  - `packages/economy/src/currency/services/currency-config-service.ts` — 貨幣設定
  - `packages/economy/src/__tests__/balance-service.test.ts` — 現有單元測試
  - `packages/economy/src/__tests__/dice-game-1.test.ts` — 現有單元測試
  - `packages/economy/src/__tests__/dice-game-2.test.ts` — 現有單元測試
