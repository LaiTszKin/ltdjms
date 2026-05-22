# Tasks: Economy Business Invariant PBT

- Date: 2026-05-22
- Feature: Economy Business Invariant PBT

## **Task 1: 餘額轉帳 PBT — 總量守恆**

Purpose: 用 fast-check 隨機產生 guild + 多個用戶 + 多筆轉帳操作，驗證 `sum(balances)` 守恆
Requirements: R1.1–R1.4, R2.1–R2.3, R3.1–R3.2
Scope: `packages/economy/src/__tests__/balance-transfer.pbt.test.ts`
Out of scope: Dice game、game token、currency config 測試

- T1.1 [ ] **`packages/economy/src/__tests__/balance-transfer.pbt.test.ts`** — 匯入 test-infra 的 `createTestContainer`、`seedGuild`、`seedUserAccount`、`guildId`、`userId`、`positiveAmount` arbitrary
  - Verify: test file import 無報錯

- T1.2 [ ] **balance-transfer.pbt.test.ts** — 實作 `fc.assert(fc.property(...))` 驗證：任意 guildId、任意 N 個 userId、任意初始餘額、任意多次轉帳後，`sum(balances)` 守恆
  - Verify: `vitest run` 通過 100 次隨機輸入

- T1.3 [ ] **balance-transfer.pbt.test.ts** — 實作透支場景 PBT：sender 餘額 < 轉帳金額時回傳 DomainError，所有餘額不變
  - Verify: 所有隨機輸入下 receiver 餘額不增加、sender 餘額不減少

- T1.4 [ ] **balance-transfer.pbt.test.ts** — 實作自我轉帳場景 PBT：senderId === receiverId 時回傳 DomainError
  - Verify: 所有隨機輸入下操作回傳錯誤

## **Task 2: DiceGame1 PBT — 賠率正確**

Purpose: 驗證 DiceGame1 賠付金額 = betAmount × multiplier
Requirements: R4.1–R4.3
Scope: `packages/economy/src/__tests__/dice-game-1.pbt.test.ts`
Out of scope: DiceGame2、管理員 config 設定

- T2.1 [ ] **`packages/economy/src/__tests__/dice-game-1.pbt.test.ts`** — 實作 PBT：任意 guildId、任意 userId、任意 betAmount、任意 dice config (multiplier, tokenCost)，驗證 payout = betAmount × multiplier
  - Verify: `vitest run` 通過 100 次隨機輸入

- T2.2 [ ] **dice-game-1.pbt.test.ts** — 驗證遊戲後餘額變化 = -tokenCost + payout
  - Verify: 所有 run 的餘額計算正確

- T2.3 [ ] **dice-game-1.pbt.test.ts** — 驗證 token 不足時回傳 DomainError
  - Verify: tokenBalance < tokenCost 時操作失敗且餘額不變

## **Task 3: DiceGame2 PBT — 賠率正確**

Purpose: 驗證 DiceGame2 所有組合的賠率對應正確
Requirements: R5.1–R5.3
Scope: `packages/economy/src/__tests__/dice-game-2.pbt.test.ts`
Out of scope: DiceGame1、管理員 config 設定

- T3.1 [ ] **`packages/economy/src/__tests__/dice-game-2.pbt.test.ts`** — 實作 PBT：任意 guildId、任意 userId、任意 betAmount、任意 dice2 config (多個 multiplier)，驗證 payout = betAmount × 對應組合 multiplier
  - Verify: `vitest run` 通過 100 次隨機輸入

- T3.2 [ ] **dice-game-2.pbt.test.ts** — 驗證所有可能結果組合的賠率均正確
  - Verify: 覆蓋所有 combo payout

## **Task 4: 遊戲代幣 PBT — 數量正確**

Purpose: 驗證 GameToken add/remove/set 操作數量正確且不可為負
Requirements: R6.1–R6.4
Scope: `packages/economy/src/__tests__/game-token.pbt.test.ts`
Out of scope: Balance transfer、Dice game

- T4.1 [ ] **`packages/economy/src/__tests__/game-token.pbt.test.ts`** — 實作 PBT：任意 guildId、任意 userId、任意初始 token、任意 add/remove/set，驗證操作後數量正確
  - Verify: `vitest run` 通過 100 次隨機輸入

- T4.2 [ ] **game-token.pbt.test.ts** — 驗證 token 不可為負（remove > balance 時回傳 DomainError）
  - Verify: 超扣場景全部回傳錯誤且原始數量不變

## **Task 5: Currency Config PBT — 值正確**

Purpose: 驗證 Currency Config 查詢回傳正確設定值
Requirements: R7.1–R7.2
Scope: `packages/economy/src/__tests__/currency-config.pbt.test.ts`
Out of scope: Balance transfer、Dice game

- T5.1 [ ] **`packages/economy/src/__tests__/currency-config.pbt.test.ts`** — 實作 PBT：任意 guildId、任意貨幣名稱、任意符號，seed guild config 後查詢，驗證回傳值一致
  - Verify: `vitest run` 通過
