# Tasks: ECPay Payment E2E Tests

- Date: 2026-05-22
- Feature: ECPay Payment E2E Tests

## **Task 1: CVS 超商繳費取號 E2E**

Purpose: 實際呼叫 ECPay Stage API 取號，驗證 paymentNo 格式、CheckMacValue、冪等性
Requirements: R1.1–R1.4
Scope: `packages/shop/src/__tests__/ecpay-cvs-e2e.test.ts`
Out of scope: 回撥處理、對帳、信用卡付款

- T1.1 [ ] **`packages/shop/src/__tests__/ecpay-cvs-e2e.test.ts`** — 匯入 test-infra 的 testcontainer + seed factory；設定 `RUN_ECPAY_E2E` 環境變數閘門
  - Verify: 未設 `RUN_ECPAY_E2E` 時 test skip（非 fail）

- T1.2 [ ] **ecpay-cvs-e2e.test.ts** — 實作取號測試：seed fiat order → 呼叫 `EcpayCvsPaymentService.createPaymentForm()` → 驗證 HTTP 200 → 驗證 paymentNo 非空
  - Verify: `RUN_ECPAY_E2E=true vitest run` 通過

- T1.3 [ ] **ecpay-cvs-e2e.test.ts** — 驗證同訂單取號兩次回傳相同 paymentNo（ECPay 端冪等）
  - Verify: 兩次 paymentNo 相同

- T1.4 [ ] **ecpay-cvs-e2e.test.ts** — 實作網路逾時重試：模擬 timeout → 驗證 retry 最多 3 次後 skip
  - Verify: timeout retry 行為正確

## **Task 2: ECPay 回撥處理 E2E — SimulatePaid=1**

Purpose: 手動建構回撥 payload → AES 加密 → POST callback server → 驗證 DB 狀態更新
Requirements: R2.1–R2.5
Scope: `packages/shop/src/__tests__/ecpay-callback-e2e.test.ts`
Out of scope: 真實 ECPay Server 回撥（非模擬）、對帳排程

- T2.1 [ ] **`packages/shop/src/__tests__/ecpay-callback-e2e.test.ts`** — 實作回撥測試：seed fiat order (PENDING_PAYMENT) → 建構 SimulatePaid=1 payload → AES 加密 → POST callback server → 驗證 HTTP 200 + "1|OK"
  - Verify: `RUN_ECPAY_E2E=true vitest run` 通過

- T2.2 [ ] **ecpay-callback-e2e.test.ts** — 驗證 DB 中訂單狀態從 PENDING_PAYMENT 變更為 PAID、paidAt 已設定
  - Verify: DB 查詢確認狀態轉移

- T2.3 [ ] **ecpay-callback-e2e.test.ts** — 驗證重複回撥冪等（同一訂單第二次 callback 不回傳錯誤）
  - Verify: 第二次 callback 仍回傳 200 + "1|OK"

- T2.4 [ ] **ecpay-callback-e2e.test.ts** — 驗證不存在的訂單回撥仍回傳 HTTP 200（ECPay 協議要求）
  - Verify: 不存在 orderNumber 的 callback 回傳 200

## **Task 3: CheckMacValue 驗證失敗 E2E**

Purpose: 驗證簽章錯誤時 callback server 回傳 400
Requirements: R3.1–R3.3
Scope: `packages/shop/src/__tests__/ecpay-callback-e2e.test.ts`
Out of scope: AES 加解密（已有 crosscheck test）

- T3.1 [ ] **ecpay-callback-e2e.test.ts** — 建構 CheckMacValue 被篡改的 payload → POST callback server → 驗證 HTTP 400 + "0|FAIL"
  - Verify: 400 回應、DB 狀態不變

- T3.2 [ ] **ecpay-callback-e2e.test.ts** — 驗證缺少必要欄位的 payload 回傳 400
  - Verify: 缺少 MerchantTradeNo 等欄位時回傳 400

## **Task 4: 對帳排程 E2E**

Purpose: 驗證 reconciliation 正確查詢並更新訂單狀態
Requirements: R4.1–R4.4
Scope: `packages/shop/src/__tests__/ecpay-reconciliation-e2e.test.ts`
Out of scope: 排程定時觸發（僅測試單次 reconciliation）

- T4.1 [ ] **`packages/shop/src/__tests__/ecpay-reconciliation-e2e.test.ts`** — seed 多筆 PENDING_PAYMENT 訂單（含已逾期、即將逾期）→ 觸發 reconciliation → 驗證 TradeQuery API 呼叫成功
  - Verify: `RUN_ECPAY_E2E=true vitest run` 通過

- T4.2 [ ] **ecpay-reconciliation-e2e.test.ts** — 驗證已付款但本地未更新的訂單被同步為 PAID
  - Verify: 訂單狀態正確同步

- T4.3 [ ] **ecpay-reconciliation-e2e.test.ts** — 驗證已逾期的未付款訂單被標記為 EXPIRED
  - Verify: 逾期訂單狀態為 EXPIRED

- T4.4 [ ] **ecpay-reconciliation-e2e.test.ts** — 驗證對帳次數遞增、達上限後停止
  - Verify: reconciliationAttemptCount 正確遞增

## **Task 5: AES 加解密交叉驗證**

Purpose: 確保 TS 版 AES 加解密與 ECPay Server 完全相容
Requirements: R5.1–R5.3
Scope: `packages/shop/src/__tests__/ecpay-crypto-e2e.test.ts`

- T5.1 [ ] **`packages/shop/src/__tests__/ecpay-crypto-e2e.test.ts`** — 實作 round-trip：加密 JSON → 透過 ECPay API 送 → 收到回應解密 → 驗證格式正確
  - Verify: 加解密 round-trip 成功

- T5.2 [ ] **ecpay-crypto-e2e.test.ts** — 驗證 URL 編碼與 Java `URLEncoder.encode()` 行為一致（比對 golden data）
  - Verify: URL 編碼輸出與 Java 版 golden data 一致
