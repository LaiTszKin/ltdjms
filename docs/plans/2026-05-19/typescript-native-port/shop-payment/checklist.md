# Checklist: Shop and Payment

- Date: 2026-05-20
- Feature: Shop and Payment

## Usage Notes

- Add/remove items based on actual scope; keep only applicable items.
- Use `$test-case-strategy` for test level selection, oracle design, and drift-check planning.
- Property-based coverage required for business-logic changes unless `N/A` with reason.
- Test result values: `PASS / FAIL / BLOCKED / NOT RUN / N/A`.

## Clarification & Approval Gate

- [ ] Clarification responses recorded (or `N/A` if none).
- [ ] Affected plans updated after clarification (or `N/A` + reason).
- [ ] Explicit approval obtained (date/ref: [to be filled]).

## Behavior-to-Test Checklist

### 商店瀏覽與搜尋 (R1, R2)

- [ ] CL-01: 商店分頁瀏覽 — 第 1 頁顯示前 5 個商品、翻頁按鈕正確 — R1.x → UT-Shop-01 — Result: `NOT RUN`
- [ ] CL-02: 空商店顯示「目前沒有可購買的商品」、無購買/搜尋按鈕 — R1.1 → UT-Shop-02 — Result: `NOT RUN`
- [ ] CL-03: page 超出範圍時 clamp 到有效範圍（如 totalPages=1 時請求 page=5）— R1.3 → UT-Shop-03 — Result: `NOT RUN`
- [ ] CL-04: 關鍵字搜尋回傳匹配結果、翻頁按鈕含編碼關鍵字 — R2.3, R2.4 → UT-Shop-04 — Result: `NOT RUN`
- [ ] CL-05: 空白關鍵字搜尋回傳空結果 — R2.2 → UT-Shop-05 — Result: `NOT RUN`
- [ ] CL-06: 搜尋 Modal customId 為 `shop_search_modal`、input 欄位驗證 — R2.5 → UT-Shop-06 — Result: `NOT RUN`
- [ ] CL-07: 購買 select menu auto-split 為每組 ≤25 選項 — R1.6 → UT-Shop-07 — Result: `NOT RUN`

### 貨幣購買 (R3)

- [ ] CL-08: 貨幣購買成功 — 扣款後餘額正確、交易記錄寫入（Source=PRODUCT_PURCHASE）— R3.1 → UT-CPurchase-01 — Result: `NOT RUN`
- [ ] CL-09: 餘額不足時回傳錯誤且不扣款 — R3.4 → UT-CPurchase-02 — Result: `NOT RUN`
- [ ] CL-10: 商品不存在或 guild 不匹配時回傳「找不到該商品」— R3.2 → UT-CPurchase-03 — Result: `NOT RUN`
- [ ] CL-11: 商品無貨幣價格時回傳「此商品不可用貨幣購買」— R3.3 → UT-CPurchase-04 — Result: `NOT RUN`
- [ ] CL-12: 獎勵發放失敗 → 自動退款成功 → 回傳「商品獎勵發放失敗，已自動退款」+ 退款交易記錄（Source=PRODUCT_PURCHASE_REFUND）— R3.6 → UT-CPurchase-05 — Result: `NOT RUN`
- [ ] CL-13: 獎勵發放失敗 + 退款也失敗 → 回傳「商品獎勵發放失敗，且自動退款失敗」— R3.7 → UT-CPurchase-06 — Result: `NOT RUN`
- [ ] CL-14: 成功訊息格式 zh-TW（商品名、價格、購買前後餘額）— R3.8 → UT-CPurchase-07 — Result: `NOT RUN`

### FiatOrder 領域模型 (R4)

- [ ] CL-15: `createPending()` 建立 PENDING_PAYMENT 狀態的 FiatOrder（所有欄位正確）— R4.1 → UT-FiatOrder-01 — Result: `NOT RUN`
- [ ] CL-16: compact constructor 驗證：productName 空白/超長拒絕 — R4.1 → UT-FiatOrder-02 — Result: `NOT RUN`
- [ ] CL-17: compact constructor 驗證：orderNumber 空白/超長拒絕 — R4.1 → UT-FiatOrder-03 — Result: `NOT RUN`
- [ ] CL-18: compact constructor 驗證：fulfillmentRewardType/Amount 成對約束（其一為 null 另一不為 null 拒絕）— R4.1 → UT-FiatOrder-04 — Result: `NOT RUN`
- [ ] CL-19: compact constructor 驗證：fulfillmentAutoCreateEscortOrder + escortOptionCode 約束（true 時必填、false 時不可有值）— R4.1 → UT-FiatOrder-05 — Result: `NOT RUN`
- [ ] CL-20: compact constructor 驗證：status-paidiAt-expiredAt-terminalReason 一致性（PAID 需 paidAt、EXPIRED 需 expiredAt+terminalReason 且 paidAt 為 null、非 EXPIRED 時 expiredAt 為 null）— R4.1 → UT-FiatOrder-06 — Result: `NOT RUN`

### ECPay Crypto（最高優先級逐 byte 比對）(R4, R5, R9)

- [ ] CL-21: **[CRITICAL]** ECPay AES 加密輸出逐 byte 與 Java 一致（使用相同 plaintext + key + iv，比對 base64 輸出）— R4.4 → UT-Crypto-Golden-01 — Result: `NOT RUN`
- [ ] CL-22: **[CRITICAL]** ECPay AES 解密輸出逐 byte 與 Java 一致（使用相同 base64 ciphertext + key + iv，比對解密後字串）— R5.3 → UT-Crypto-Golden-02 — Result: `NOT RUN`
- [ ] CL-23: **[CRITICAL]** ECPay CheckMacValue 逐 byte 與 Java 一致（使用相同 params + key + iv，比對 hex 輸出）— R9.1 → UT-Crypto-Golden-03 — Result: `NOT RUN`
- [ ] CL-24: **[CRITICAL]** CheckMacValue URL 編碼替代規則每一條獨立驗證（`%2d→-`, `%5f→_`, `%2e→.`, `%21→!`, `%2a→*`, `%28→(`, `%29→)`, `%20→+`, `%7e→~`）— R9.1 → UT-Crypto-Golden-04 — Result: `NOT RUN`
- [ ] CL-25: AES roundtrip — encrypt 後 decrypt 回原值（使用隨機 plaintext + random key/iv）— R4.4, R5.3 → UT-Crypto-PBT-01 (property-based) — Result: `NOT RUN`
- [ ] CL-26: 解密失敗（無效 base64、錯誤 key/iv）時拋出對應錯誤 — R5.10 → UT-Crypto-Error-01 — Result: `NOT RUN`

### ECPay API 整合 (R4, R9)

- [ ] CL-27: MerchantTradeNo 格式 `FD{yyMMddHHmmssSSS}{3-digit-sequence}`，同毫秒內序列號遞增 — R4.2 → UT-Ecpay-01 — Result: `NOT RUN`
- [ ] CL-28: GenPaymentCode 成功流程（Mock HTTP → TransCode=1, RtnCode=1）— R4.3-R4.8 → UT-Ecpay-02 — Result: `NOT RUN`
- [ ] CL-29: GenPaymentCode TransCode!=1 回傳錯誤（含 "decrypt fail" 提示）— R4.7 → UT-Ecpay-03 — Result: `NOT RUN`
- [ ] CL-30: GenPaymentCode 官方 stage 金鑰在 prod 模式下拒絕 — R4.9 → UT-Ecpay-04 — Result: `NOT RUN`
- [ ] CL-31: GenPaymentCode expireAt fallback（ECPay 無 ExpireDate 時用 requestAt + expireMinutes）— R4.11 → UT-Ecpay-05 — Result: `NOT RUN`
- [ ] CL-32: QueryTradeInfo 成功流程（Mock HTTP → 解析 TradeStatus/TradeNo/TradeAmt）— R9.1-R9.5 → UT-Ecpay-06 — Result: `NOT RUN`
- [ ] CL-33: QueryTradeInfo HTTP 非 200 時回傳錯誤 — R9.3 → UT-Ecpay-07 — Result: `NOT RUN`
- [ ] CL-34: QueryTradeInfo timeout（15 秒）時回傳錯誤 — R9.4 → UT-Ecpay-08 — Result: `NOT RUN`

### Callback HTTP Server + Callback 處理 (R5, R6)

- [ ] CL-35: Express server 啟動在指定 host:port，callback route 和 landing page route 正確 — R6.1-R6.4 → UT-CallbackServer-01 — Result: `NOT RUN`
- [ ] CL-36: Callback path 與 `/` 衝突時阻止啟動 — R6.5 → UT-CallbackServer-02 — Result: `NOT RUN`
- [ ] CL-37: Stage mode + 非 localhost 綁定時拋出 IllegalStateException — R6.7 → UT-CallbackServer-03 — Result: `NOT RUN`
- [ ] CL-38: 64KB body limit — 超過時 HTTP 413 — R6.6 → UT-CallbackServer-04 — Result: `NOT RUN`
- [ ] CL-39: Callback 處理 — 付款成功（TradeStatus=1，MerchantID+TradeAmt 匹配）→ markPaidIfPending 成功 — R5.1-R5.7 → UT-Callback-01 — Result: `NOT RUN`
- [ ] CL-40: Callback 處理 — 未付款（TradeStatus!=1）→ 僅 updateCallbackStatus，不轉換狀態 — R5.1 → UT-Callback-02 — Result: `NOT RUN`
- [ ] CL-41: Callback 處理 — 訂單不存在 → HTTP 200（不回傳錯誤）— R5.5 → UT-Callback-03 — Result: `NOT RUN`
- [ ] CL-42: Callback 處理 — 重複付款 callback（已 PAID）→ 回傳空 → HTTP 200 — R5.8 → UT-Callback-04 — Result: `NOT RUN`
- [ ] CL-43: Callback 處理 — 逾期後 callback → markPaidIfPending 失敗（status 非 PENDING_PAYMENT）→ HTTP 200 — R5.7 → UT-Callback-05 — Result: `NOT RUN`
- [ ] CL-44: Callback 處理 — MerchantID 不匹配 → 拒絕標記 PAID，但仍回 200 — R5.6 → UT-Callback-06 — Result: `NOT RUN`
- [ ] CL-45: Callback 處理 — TradeAmt 不匹配 → 拒絕標記 PAID，但仍回 200 — R5.6 → UT-Callback-07 — Result: `NOT RUN`
- [ ] CL-46: Callback 處理 — 解密失敗（InvalidCallbackPayloadException）→ HTTP 400 — R5.10 → UT-Callback-08 — Result: `NOT RUN`
- [ ] CL-47: Callback 處理 — 內部錯誤（Exception）→ HTTP 500 — R5.11 → UT-Callback-09 — Result: `NOT RUN`
- [ ] CL-48: Callback payload 超過 4000 字元時截斷 — R5.9 → UT-Callback-10 — Result: `NOT RUN`

### Post-Payment Worker (R7)

- [ ] CL-49: 正常履約流程 — notify buyer → escort handoff → admin notify → grant reward → mark fulfilled — R7.1-R7.7 → UT-Worker-01 — Result: `NOT RUN`
- [ ] CL-50: Idempotent — 重跑相同訂單時，每一步被 `WHERE col IS NULL` 跳過 — R7.3 → UT-Worker-02 — Result: `NOT RUN`
- [ ] CL-51: Claim 競爭 — 兩個 worker 同時 claim，只有一個成功（另一個跳過）— R7.2 → UT-Worker-03 — Result: `NOT RUN`
- [ ] CL-52: 護航交接失敗 → throw → releaseFulfillmentProcessing — R7.5 → UT-Worker-04 — Result: `NOT RUN`
- [ ] CL-53: 獎勵發放失敗 → throw → releaseFulfillmentProcessing — R7.6 → UT-Worker-05 — Result: `NOT RUN`
- [ ] CL-54: Admin notification claim 成功後通知失敗 → releaseAdminNotificationProcessing + throw → releaseFulfillmentProcessing — R7.4 → UT-Worker-06 — Result: `NOT RUN`
- [ ] CL-55: 無護航選項時跳過 Step 2 — R7.1 → UT-Worker-07 — Result: `NOT RUN`
- [ ] CL-56: 無獎勵時跳過 Step 3 — R7.1 → UT-Worker-08 — Result: `NOT RUN`
- [ ] CL-57: 查詢排序 `paid_at ASC NULLS LAST, created_at ASC` — R7.8 → UT-Worker-09 — Result: `NOT RUN`

### 對帳 (R8)

- [ ] CL-58: 逾期標記 — expire_at 已過的 PENDING_PAYMENT 訂單 → markExpiredIfPending 成功 — R8.1-R8.2 → UT-Recon-01 — Result: `NOT RUN`
- [ ] CL-59: 對帳查單補單 — paid=true → markPaidIfPending → 觸發 post-payment worker — R8.3 → UT-Recon-02 — Result: `NOT RUN`
- [ ] CL-60: 對帳查單補單 — paid=false 且未過期 → scheduleRetry 指數退避 — R8.4 → UT-Recon-03 — Result: `NOT RUN`
- [ ] CL-61: 對帳查單補單 — paid=false 且已過期 → markExpiredIfPending — R8.3 → UT-Recon-04 — Result: `NOT RUN`
- [ ] CL-62: 查單 API 失敗 → scheduleRetry（指數退避）— R8.4 → UT-Recon-05 — Result: `NOT RUN`
- [ ] CL-63: 指數退避計算 — 30s × attempt, capped 300s（attempt=1 → 30s, attempt=5 → 150s, attempt=15 → 300s）— R8.4 → UT-Recon-06 — Result: `NOT RUN`
- [ ] CL-64: Claim 競爭 — 兩個 reconciliation 同時 claim → 只有一個成功 — R8.3 → UT-Recon-07 — Result: `NOT RUN`
- [ ] CL-65: 已過期但 expire_at > now 的訂單不進入對帳查單（reconciliation claim condition 包含 `COALESCE(expire_at, ...) > now`）— R8.3 → UT-Recon-08 — Result: `NOT RUN`
- [ ] CL-66: syntheticPayload JSON 格式正確 — R8.6 → UT-Recon-09 — Result: `NOT RUN`

### 法幣訂單建立 (R4)

- [ ] CL-67: 法幣訂單建立成功（完整流程：驗證商品 → ECPay 取號 → persist → 回傳 FiatOrderResult）— R4.1-R4.12 → UT-FiatOrderSvc-01 — Result: `NOT RUN`
- [ ] CL-68: 商品非法幣限定 → 回傳錯誤 — R4.1 → UT-FiatOrderSvc-02 — Result: `NOT RUN`
- [ ] CL-69: ECPay 取號失敗 → 回傳錯誤 — R4.7 → UT-FiatOrderSvc-03 — Result: `NOT RUN`
- [ ] CL-70: Persist 失敗 → 回傳錯誤 — R4.12 → UT-FiatOrderSvc-04 — Result: `NOT RUN`

### 兌換碼 (R10, R11)

- [ ] CL-71: 生成兌換碼成功（count=5, quantity=3）— 5 個 16 字元代碼、quantity=3 — R10.1-R10.4 → UT-Redemption-01 — Result: `NOT RUN`
- [ ] CL-72: 生成數量超出 MAX_BATCH_SIZE(100) 拒絕 — R10.2 → UT-Redemption-02 — Result: `NOT RUN`
- [ ] CL-73: 數量 ≤0、quantity ≤0 / >1000 拒絕 — R10.2-R10.3 → UT-Redemption-03 — Result: `NOT RUN`
- [ ] CL-74: expiresAt 在過去 → 拒絕 — R10.4 → UT-Redemption-04 — Result: `NOT RUN`
- [ ] CL-75: 商品不存在 → 拒絕 — R10.5 → UT-Redemption-05 — Result: `NOT RUN`
- [ ] CL-76: 生成重複代碼 → 重試最多 10 次 → 10 次後拋錯 — R10.6 → UT-Redemption-06 — Result: `NOT RUN`
- [ ] CL-77: 兌換成功（完整流程：驗證 → markAsRedeemed → 發獎勵 → 記錄 transaction → 發布事件）— R11.1-R11.10 → UT-Redemption-07 — Result: `NOT RUN`
- [ ] CL-78: 代碼不存在/guild 不匹配 → 「兌換碼無效」— R11.2 → UT-Redemption-08 — Result: `NOT RUN`
- [ ] CL-79: 代碼已失效（invalidated）→ 「此兌換碼已失效」— R11.3 → UT-Redemption-09 — Result: `NOT RUN`
- [ ] CL-80: 代碼已使用 → 「此兌換碼已被使用」— R11.4 → UT-Redemption-10 — Result: `NOT RUN`
- [ ] CL-81: 代碼已過期 → 「此兌換碼已過期」— R11.5 → UT-Redemption-11 — Result: `NOT RUN`
- [ ] CL-82: 並發兌換競爭 → markAsRedeemedIfAvailable 只一人成功 — R11.6-R11.7 → UT-Redemption-12 — Result: `NOT RUN`
- [ ] CL-83: 獎勵發放失敗 → rollback redeem 成功 → 「商品獎勵發放失敗，兌換已取消」— R11.9 → UT-Redemption-13 — Result: `NOT RUN`
- [ ] CL-84: 獎勵發放失敗 + rollback 失敗 → 「商品獎勵發放失敗，且兌換碼回復失敗」— R11.9 → UT-Redemption-14 — Result: `NOT RUN`
- [ ] CL-85: 總獎勵計算 overflow（rewardAmount × quantity 超出 Number.MAX_SAFE_INTEGER）→ 拒絕 — R11.8 → UT-Redemption-15 — Result: `NOT RUN`
- [ ] CL-86: `getMaskedCode()` 正確遮蔽（前 4 + **** + 後 4）— R14.1 → UT-Redemption-16 — Result: `NOT RUN`

### 通知服務 (R12)

- [ ] CL-87: 買家付款成功 DM 通知 — 訊息格式正確 — R12.1 → UT-Notify-01 — Result: `NOT RUN`
- [ ] CL-88: 買家護航建立 DM 通知 — 跳過 bot 自身 — R12.2 → UT-Notify-02 — Result: `NOT RUN`
- [ ] CL-89: 管理員新訂單通知 — 發送給所有 ADMINISTRATOR + guild owner、排除 bot、去重 — R12.3-R12.4 → UT-Notify-03 — Result: `NOT RUN`

### Repository idempotency (R5, R7, R8)

- [ ] CL-90: `markPaidIfPending` — 第二次調用時回傳 null（status 已非 PENDING_PAYMENT）— R5.6 → IT-Repo-01 — Result: `NOT RUN`
- [ ] CL-91: `markBuyerNotifiedIfNeeded` — 第二次調用時回傳 null（buyer_notified_at 已非 NULL）— R7.3 → IT-Repo-02 — Result: `NOT RUN`
- [ ] CL-92: `claimFulfillmentProcessing` — 第二次 claim 回傳 false — R7.2 → IT-Repo-03 — Result: `NOT RUN`
- [ ] CL-93: 每個 `mark*IfNeeded` 方法在條件不匹配時回傳 null — R7.3 → IT-Repo-04 — Result: `NOT RUN`

## Hardening Checklist

- [ ] **[CRITICAL]** ECPay crypto golden-data cross-check tests（TS vs Java 逐 byte 比對所有 crypto 函數）
- [ ] Property-based test: AES roundtrip（random plaintext × 100 iterations → encrypt → decrypt → 與原文一致）
- [ ] Property-based test: FiatOrder zod schema validation（random valid/invalid field combinations）
- [ ] Concurrency test: dual claim attempts on same order → only one succeeds
- [ ] Concurrency test: dual markAsRedeemedIfAvailable on same code → only one succeeds
- [ ] FiatOrder validation adversarial: negative amounts, max-length strings, null fields, status inconsistency
- [ ] RedemptionCode validation adversarial: empty code, code > 32 chars, quantity=0, quantity=1001
- [ ] External services mocked/faked: ECPay HTTP、DiscordRuntimeGateway、ProductRepository、BalanceService 全部 mock
- [ ] Fixtures reproducible: ECPay crypto golden data 來自 Java 測試的已知輸入/輸出 pair
- [ ] Assertions verify outcomes/side-effects, not just "returns 200": 檢查資料庫狀態變更、事件發布、交易記錄寫入

## E2E / Integration Decisions

- [ ] ECPay Stage E2E: 在 ECPay stage 環境發起真實 CVS 繳費 → 等待 callback → 驗證完整履約流程 — Reason: 確保與 ECPay 真實 API 相容（最高風險整合點）
- [ ] Post-payment worker 整合測試: 對接真實 PostgreSQL → 驗證完整 claim → notify → handoff → reward → fulfill pipeline
- [ ] Reconciliation 整合測試: 對接真實 PostgreSQL + mock ECPay → 驗證對帳排程的查詢/補單/退避邏輯

## Execution Summary

- [ ] Unit: `NOT RUN`
- [ ] Integration: `NOT RUN`
- [ ] E2E (ECPay stage env): `NOT RUN`
- [ ] Property-based: `NOT RUN`
- [ ] Mock scenarios: `NOT RUN`
- [ ] Adversarial: `NOT RUN`
- [ ] Concurrency: `NOT RUN`

## Completion Records

- [ ] Task 1 (ECPay Crypto): `NOT STARTED` — Remaining: None
- [ ] Task 2 (Drizzle Schema): `NOT STARTED` — Remaining: None
- [ ] Task 3 (FiatOrder Domain): `NOT STARTED` — Remaining: None
- [ ] Task 4 (FiatOrderRepository): `NOT STARTED` — Remaining: None
- [ ] Task 5 (ECPay API Services): `NOT STARTED` — Remaining: None
- [ ] Task 6 (Express HTTP Server): `NOT STARTED` — Remaining: None
- [ ] Task 7 (FiatPaymentCallbackService): `NOT STARTED` — Remaining: None
- [ ] Task 8 (FiatOrderService): `NOT STARTED` — Remaining: None
- [ ] Task 9 (PostPaymentWorker): `NOT STARTED` — Remaining: None
- [ ] Task 10 (ReconciliationService): `NOT STARTED` — Remaining: None
- [ ] Task 11 (ShopService + ShopView): `NOT STARTED` — Remaining: None
- [ ] Task 12 (Scheduler): `NOT STARTED` — Remaining: None
- [ ] Task 13 (CurrencyPurchaseService): `NOT STARTED` — Remaining: None
- [ ] Task 14 (Redemption Domain): `NOT STARTED` — Remaining: None
- [ ] Task 15 (RedemptionRepository + Service): `NOT STARTED` — Remaining: None
- [ ] Task 16 (Notification Services): `NOT STARTED` — Remaining: None
- [ ] Task 17 (DI Registration): `NOT STARTED` — Remaining: None
