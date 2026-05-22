# Tasks: Shop Business Invariant PBT

- Date: 2026-05-22
- Feature: Shop Business Invariant PBT

## **Task 1: 兌換碼兌換 PBT — 冪等與庫存**

Purpose: 驗證兌換碼兌換的冪等性、庫存遞減、獎勵發放
Requirements: R1.1–R1.4, R2.1–R2.3
Scope: `packages/shop/src/__tests__/redemption.pbt.test.ts`
Out of scope: 商店購買、貨幣購買、ECPay 金流

- T1.1 [ ] **`packages/shop/src/__tests__/redemption.pbt.test.ts`** — 匯入 test-infra 的 `createTestContainer`、`seedGuild`、`seedProduct`、`seedRedemptionCode`、`seedUserAccount`；設定 DI container with shop module
  - Verify: import 無報錯，DI container 可 resolve RedemptionService

- T1.2 [ ] **redemption.pbt.test.ts** — 實作 PBT：任意 guildId、任意數量兌換碼（1-10）、任意數量用戶（2-5），驗證每個 code 僅能被兌換一次、第二次兌換回傳 DomainError
  - Verify: `vitest run` 通過 100 次隨機輸入

- T1.3 [ ] **redemption.pbt.test.ts** — 驗證批量兌換碼全部被兌換後狀態為 used，所有用戶 reward 總和正確
  - Verify: DB 中所有 code 狀態為 used，reward 發放正確

- T1.4 [ ] **redemption.pbt.test.ts** — 驗證不存在兌換碼的錯誤處理
  - Verify: 兌換不存在的 code 回傳 DomainError

## **Task 2: 商店購買 PBT — 貨幣餘額正確**

Purpose: 驗證貨幣購買商品的餘額扣減、獎勵發放、交易記錄
Requirements: R3.1–R3.4
Scope: `packages/shop/src/__tests__/shop-purchase.pbt.test.ts`
Out of scope: ECPay 法幣付款

- T2.1 [ ] **`packages/shop/src/__tests__/shop-purchase.pbt.test.ts`** — 實作 PBT：任意 guildId、任意用戶（初始餘額 100-100000）、任意商品（價格 10-10000），驗證 balanceAfter = balance - cost
  - Verify: `vitest run` 通過 100 次隨機輸入

- T2.2 [ ] **shop-purchase.pbt.test.ts** — 驗證餘額不足時回傳 DomainError 且不扣款
  - Verify: 所有超額購買回傳錯誤，餘額不變

- T2.3 [ ] **shop-purchase.pbt.test.ts** — 驗證 transaction record 正確寫入（guildId、userId、amount、source）
  - Verify: DB currency_transaction record 存在且值正確

## **Task 3: 法幣訂單建立 PBT — 資料完整性**

Purpose: 驗證法幣訂單建立的 orderNumber 唯一、狀態初始化、必要欄位
Requirements: R4.1–R4.5
Scope: `packages/shop/src/__tests__/fiat-order-creation.pbt.test.ts`
Out of scope: ECPay API 呼叫（mock）、回撥處理

- T3.1 [ ] **`packages/shop/src/__tests__/fiat-order-creation.pbt.test.ts`** — 實作 PBT：任意 guildId、任意用戶、任意商品（含 TWD 價格），建立訂單後驗證 DB 欄位完整
  - Verify: `vitest run` 通過

- T3.2 [ ] **fiat-order-creation.pbt.test.ts** — 驗證連續建立多筆訂單的 orderNumber 唯一性
  - Verify: 所有 orderNumber 不重複

- T3.3 [ ] **fiat-order-creation.pbt.test.ts** — 驗證 amountTwd = 商品價格、expireAt = 建立時間 + 逾期期限
  - Verify: DB 中 amountTwd 和 expireAt 值正確

## **Task 4: 貨幣購買 PBT — 獎勵計算**

Purpose: 驗證付款後貨幣獎勵發放的正確性
Requirements: R5.1–R5.2
Scope: `packages/shop/src/__tests__/currency-purchase.pbt.test.ts`
Out of scope: ECPay 回撥、Discord 通知

- T4.1 [ ] **`packages/shop/src/__tests__/currency-purchase.pbt.test.ts`** — 實作 PBT：任意 guildId、任意用戶、任意商品 rewardAmount（0-100000），驗證獎勵發放後餘額增加正確
  - Verify: `vitest run` 通過

- T4.2 [ ] **currency-purchase.pbt.test.ts** — 驗證 rewardGrantedAt timestamp 正確設定
  - Verify: rewardGrantedAt 非 null 且在合理時間範圍內
