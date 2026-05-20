# Tasks: Shop and Payment

- Date: 2026-05-20
- Feature: Shop and Payment

## **Task 1: ECPay Crypto 工具模組（最高優先級）**

Purpose: 實作 ECPay AES/CBC 加解密、CheckMacValue 簽章演算法、URL 編碼替代規則，所有輸出必須逐 byte 與 Java 一致。
Requirements: R4, R5, R9
Scope: `packages/shop/src/crypto/`
Out of scope: 任何 ECPay HTTP 呼叫、config 讀取、domain model

- T1.1 [ ] **`packages/shop/src/crypto/ecpay-aes.ts`** — 實作 `encryptData(plainJson: string, hashKey: string, hashIv: string): string`
  - AES/CBC/PKCS5Padding（在 Node.js 對應 `aes-128-cbc`，因 Java `SecretKeySpec` 根據 key bytes 長度自動選擇 AES-128/192/256）
  - 步驟：`encodeURIComponent(plainJson)` → `crypto.createCipheriv('aes-128-cbc', Buffer.from(hashKey, 'utf8'), Buffer.from(hashIv, 'utf8'))` → `Buffer.concat([cipher.update(urlEncoded, 'utf8'), cipher.final()]).toString('base64')`
  - Verify: 與 Java `EcpayCvsPaymentService.encryptData()` 使用相同輸入比對輸出（逐 byte）
- T1.2 [ ] **`packages/shop/src/crypto/ecpay-aes.ts`** — 實作 `decryptData(encryptedBase64: string, hashKey: string, hashIv: string): string`
  - 步驟：`Buffer.from(encryptedBase64, 'base64')` → `crypto.createDecipheriv('aes-128-cbc', Buffer.from(hashKey, 'utf8'), Buffer.from(hashIv, 'utf8'))` → `Buffer.concat([decipher.update(decoded), decipher.final()]).toString('utf8')` → `decodeURIComponent(decrypted)`
  - 確認 `decipher.setAutoPadding(true)`（Node.js 預設 PKCS7 = PKCS5 相容）
  - Verify: 與 Java `FiatPaymentCallbackService.decryptData()` 使用相同輸入比對輸出（逐 byte）
- T1.3 [ ] **`packages/shop/src/crypto/ecpay-checkmac.ts`** — 實作 `buildCheckMacValue(params: Record<string, string>, hashKey: string, hashIv: string): string`
  - 步驟：
    1. 建立字串 `HashKey={hashKey}&{sortedNonEmptyParams}&HashIV={hashIv}`
    2. `encodeURIComponent(whole)` 並 `.toLowerCase()`
    3. ECPay 替代規則（依序）：`%2d→-`, `%5f→_`, `%2e→.`, `%21→!`, `%2a→*`, `%28→(`, `%29→)`, `%20→+`, `%7e→~`
    4. `crypto.createHash('sha256').update(encoded).digest('hex').toUpperCase()`
  - Verify: 與 Java `EcpayTradeQueryService.buildCheckMacValue()` 使用相同參數比對輸出（逐 byte）
- T1.4 [ ] **`packages/shop/src/crypto/__tests__/ecpay-crypto.crosscheck.test.ts`** — 撰寫逐 byte 比對測試
  - 使用 Java 原版的已知輸入/輸出 pair（從 Java 測試擷取 fixture：`FiatPaymentCallbackServiceTest`、`EcpayTradeQueryServiceTest`）
  - 測試空參數、特殊字元中文參數、邊界長度
  - 測試 URL 編碼替代規則的每一條
  - 測試 AES roundtrip（encrypt 後 decrypt 回原值）
  - Verify: `pnpm --filter @ltdjms/shop test` 全部通過

## **Task 2: Drizzle Schema 定義**

Purpose: 定義 `fiat_order`、`redemption_code`、`product_redemption_transaction` 三個 table 的 Drizzle schema，完全對齊現有 Flyway migration SQL。
Requirements: R4, R7, R8, R10, R11
Scope: `packages/shop/src/persistence/schema.ts`
Out of scope: Repository 實作、domain model validation

- T2.1 [ ] **`packages/shop/src/persistence/schema.ts`** — 定義 `fiatOrder` pgTable
  - 36 個 columns：`id` (serial PK)、`guild_id` (bigint, not null)、`buyer_user_id` (bigint, not null)、`product_id` (bigint, not null)、`product_name` (varchar 100, not null)、`fulfillment_reward_type` (varchar 16, nullable)、`fulfillment_reward_amount` (bigint, nullable)、`fulfillment_auto_create_escort_order` (boolean, default false)、`fulfillment_escort_option_code` (varchar 120, nullable)、`order_number` (varchar 32, not null, unique)、`payment_no` (varchar 32, not null)、`amount_twd` (bigint, not null)、`status` (varchar 32, not null)、`trade_status` (varchar 32, nullable)、`payment_message` (varchar 512, nullable)、`paid_at` (timestamp, nullable)、`expire_at` (timestamp, not null)、`expired_at` (timestamp, nullable)、`terminal_reason` (varchar 128, nullable)、`buyer_notified_at` (timestamp, nullable)、`reward_granted_at` (timestamp, nullable)、`fulfilled_at` (timestamp, nullable)、`admin_notified_at` (timestamp, nullable)、`last_callback_payload` (text, nullable)、`fulfillment_processing_at` (timestamp, nullable)、`admin_notification_processing_at` (timestamp, nullable)、`reconciliation_processing_at` (timestamp, nullable)、`reconciliation_attempt_count` (int, default 0)、`reconciliation_next_attempt_at` (timestamp, nullable)、`created_at` (timestamp, not null)、`updated_at` (timestamp, not null)
  - Indexes：`order_number` (unique)、composite `(status, fulfilled_at, fulfillment_processing_at)`、composite `(status, paid_at, reconciliation_processing_at, expire_at)`、composite `(status, paid_at, reconciliation_processing_at, reconciliation_next_attempt_at)`
  - Verify: `pnpm drizzle-kit generate` 無錯誤，diff Drizzle schema vs 現有 Flyway migration SQL（V021-V026）→ 所有 column/type/constraint 一致
- T2.2 [ ] **`packages/shop/src/persistence/schema.ts`** — 定義 `redemptionCode` pgTable
  - Columns：`id` (serial PK)、`code` (varchar 32, not null, unique)、`product_id` (bigint, nullable)、`guild_id` (bigint, not null)、`expires_at` (timestamp, nullable)、`redeemed_by` (bigint, nullable)、`redeemed_at` (timestamp, nullable)、`created_at` (timestamp, not null, default now)、`invalidated_at` (timestamp, nullable)、`quantity` (int, not null, default 1)
  - Indexes：`code` (unique)、`product_id`
  - Verify: diff vs Flyway V006 SQL → 一致
- T2.3 [ ] **`packages/shop/src/persistence/schema.ts`** — 定義 `productRedemptionTransaction` pgTable
  - Columns：`id` (serial PK)、`guild_id` (bigint, not null)、`user_id` (bigint, not null)、`product_id` (bigint, not null)、`product_name` (varchar 100, not null)、`redemption_code_id` (bigint, not null)、`code` (varchar 32, not null)、`rewarded_amount` (bigint, nullable)、`created_at` (timestamp, not null, default now)
  - Indexes：`redemption_code_id`
  - Verify: 與 Java 對應 migration SQL 一致

## **Task 3: FiatOrder 領域模型 + Repository Interface**

Purpose: 定義 `FiatOrder` 型別、zod schema validation（compact constructor 等價）、`FiatOrderRepository` interface（16 方法）。
Requirements: R4, R5, R7, R8
Scope: `packages/shop/src/domain/`
Out of scope: Repository 實作（Task 4）

- T3.1 [ ] **`packages/shop/src/domain/fiat-order.ts`** — 定義 `FiatOrder` type、`FiatOrderStatus` enum、zod schema
  - `FiatOrderStatus` enum：`'PENDING_PAYMENT' | 'PAID' | 'EXPIRED'`
  - `FiatOrderSchema` zod object：36 個欄位，包含所有 cross-field validation
    - productName：必填、非空白、≤100 字元
    - orderNumber：必填、非空白、≤32 字元
    - paymentNo：必填、非空白、≤32 字元
    - amountTwd：> 0
    - fulfillmentRewardType 和 fulfillmentRewardAmount 同為 null 或同為 non-null
    - fulfillmentRewardAmount != null 時必須 > 0
    - fulfillmentAutoCreateEscortOrder=true 時 escortOptionCode 必填
    - fulfillmentAutoCreateEscortOrder=false 時 escortOptionCode 不可有值
    - status=PENDING_PAYMENT 時 paidAt 必須為 null
    - status=PAID 時 paidAt 必須有值
    - status=EXPIRED 時 expiredAt + terminalReason 必須有值，paidAt 必須為 null
    - status != EXPIRED 時 expiredAt 必須為 null
  - Helper functions：
    - `createPending(guildId, buyerUserId, productId, productName, fulfillmentRewardType, fulfillmentRewardAmount, fulfillmentAutoCreateEscortOrder, fulfillmentEscortOptionCode, orderNumber, paymentNo, amountTwd, expireAt): FiatOrder`
    - `isPaid()`, `isExpired()`, `isTerminal()`, `isFulfilled()`, `isBuyerNotified()`, `isRewardGranted()`, `isAdminNotified()`
    - `hasFulfillmentReward()`, `shouldAutoCreateEscortOrder()`, `toFulfillmentProduct()`
  - Verify: 單元測試所有 validation 規則（比對 Java `FiatOrderTest.java` 的所有測試案例）
- T3.2 [ ] **`packages/shop/src/domain/fiat-order-repository.ts`** — 定義 `FiatOrderRepository` interface
  - 16 個方法簽名，回傳型別使用 `Promise<FiatOrder | null>`（對應 Java Optional）
  - 完整方法清單：`save`, `findByOrderNumber`, `updateCallbackStatus`, `markPaidIfPending`, `markBuyerNotifiedIfNeeded`, `markRewardGrantedIfNeeded`, `markFulfilledIfNeeded`, `markAdminNotifiedIfNeeded`, `findOrdersPendingExpiry`, `findOrdersPendingPostPayment`, `findOrdersPendingReconciliation`, `markExpiredIfPending`, `claimFulfillmentProcessing`, `releaseFulfillmentProcessing`, `claimAdminNotificationProcessing`, `releaseAdminNotificationProcessing`, `claimReconciliationProcessing`, `releaseReconciliationProcessing`, `markReconciliationAttempted`
  - Verify: TypeScript 型別檢查通過、interface 方法數量與 Java `FiatOrderRepository.java` 一致

## **Task 4: DrizzleFiatOrderRepository 實作**

Purpose: 實作 `FiatOrderRepository` interface 的全部 16 個方法，使用 Drizzle ORM 的 conditional UPDATE + `returning()` 模式。
Requirements: R4, R5, R7, R8
Scope: `packages/shop/src/persistence/drizzle-fiat-order-repository.ts`
Out of scope: 其他 repository、service 邏輯

- T4.1 [ ] **`packages/shop/src/persistence/drizzle-fiat-order-repository.ts`** — 實作基礎 CRUD 方法
  - `save()`: `db.insert(fiatOrder).values(row).returning()` 取得 generated id
  - `findByOrderNumber()`: `db.select().from(fiatOrder).where(eq(fiatOrder.orderNumber, orderNumber)).limit(1)`
  - `updateCallbackStatus()`: `db.update(fiatOrder).set({...}).where(eq(fiatOrder.orderNumber, orderNumber)).returning()`
  - Verify: 整合測試寫入後讀出比對所有 36 個欄位
- T4.2 [ ] **`packages/shop/src/persistence/drizzle-fiat-order-repository.ts`** — 實作 idempotent status update 方法（conditional UPDATE `WHERE col IS NULL`）
  - `markPaidIfPending`: `UPDATE ... SET status='PAID', trade_status=?, payment_message=?, paid_at=?, last_callback_payload=?, reconciliation_processing_at=NULL, updated_at=NOW()` WHERE `order_number=? AND status='PENDING_PAYMENT' RETURNING *`
  - `markBuyerNotifiedIfNeeded`: WHERE `order_number=? AND buyer_notified_at IS NULL`
  - `markRewardGrantedIfNeeded`: WHERE `order_number=? AND reward_granted_at IS NULL`
  - `markFulfilledIfNeeded`: WHERE `order_number=? AND fulfilled_at IS NULL`（同時 SET `fulfillment_processing_at=NULL`）
  - `markAdminNotifiedIfNeeded`: WHERE `order_number=? AND admin_notified_at IS NULL`（同時 SET `admin_notification_processing_at=NULL`）
  - `markExpiredIfPending`: WHERE `order_number=? AND status='PENDING_PAYMENT' AND paid_at IS NULL AND expired_at IS NULL AND COALESCE(expire_at, created_at + INTERVAL '7 days') <= ?`
  - Verify: 每個方法單獨測試 conditional UPDATE 行為（條件不匹配時回傳 null / rowsAffected=0）
- T4.3 [ ] **`packages/shop/src/persistence/drizzle-fiat-order-repository.ts`** — 實作 claim/release 方法（`processing_at` lightweight row-level lock）
  - `claimFulfillmentProcessing`: `UPDATE ... SET fulfillment_processing_at=? WHERE order_number=? AND fulfilled_at IS NULL AND fulfillment_processing_at IS NULL`，回傳 `rowsAffected > 0`
  - `releaseFulfillmentProcessing`: `UPDATE ... SET fulfillment_processing_at=NULL WHERE order_number=? AND fulfilled_at IS NULL`
  - `claimAdminNotificationProcessing`: `UPDATE ... SET admin_notification_processing_at=? WHERE order_number=? AND admin_notified_at IS NULL AND admin_notification_processing_at IS NULL`
  - `releaseAdminNotificationProcessing`: `UPDATE ... SET admin_notification_processing_at=NULL WHERE order_number=? AND admin_notified_at IS NULL`
  - `claimReconciliationProcessing`: `UPDATE ... SET reconciliation_processing_at=? WHERE order_number=? AND status='PENDING_PAYMENT' AND paid_at IS NULL AND reconciliation_processing_at IS NULL AND COALESCE(expire_at, created_at + INTERVAL '7 days') > ?`（加上 expire_at 檢查避免 claim 已過期訂單）
  - `releaseReconciliationProcessing`: `UPDATE ... SET reconciliation_processing_at=NULL WHERE order_number=? AND paid_at IS NULL`
  - `markReconciliationAttempted`: `UPDATE ... SET reconciliation_processing_at=NULL, reconciliation_attempt_count=?, reconciliation_next_attempt_at=? WHERE order_number=? AND paid_at IS NULL RETURNING *`
  - Verify: 並發 claim 競爭整合測試（兩個同時 claim，只有一個成功）
- T4.4 [ ] **`packages/shop/src/persistence/drizzle-fiat-order-repository.ts`** — 實作查詢方法
  - `findOrdersPendingPostPayment(limit)`: WHERE `status='PAID' AND fulfilled_at IS NULL AND fulfillment_processing_at IS NULL ORDER BY paid_at ASC NULLS LAST, created_at ASC LIMIT ?`
  - `findOrdersPendingExpiry(notAfter, limit)`: WHERE `status='PENDING_PAYMENT' AND paid_at IS NULL AND reconciliation_processing_at IS NULL AND COALESCE(expire_at, created_at + INTERVAL '7 days') <= ?`
  - `findOrdersPendingReconciliation(notBefore, createdAfter, limit)`: WHERE `status='PENDING_PAYMENT' AND paid_at IS NULL AND created_at >= ? AND reconciliation_processing_at IS NULL AND (reconciliation_next_attempt_at IS NULL OR reconciliation_next_attempt_at <= ?) AND COALESCE(expire_at, created_at + INTERVAL '7 days') > ?`
  - Verify: 整合測試驗證查詢條件、排序、limit

## **Task 5: ECPay API 整合服務**

Purpose: 實作 `EcpayCvsPaymentService` 和 `EcpayTradeQueryService`，整合 ECPay crypto 工具、HTTP client (fetch)、Config。
Requirements: R4, R9
Scope: `packages/shop/src/services/ecpay-cvs-payment.service.ts`, `packages/shop/src/services/ecpay-trade-query.service.ts`
Out of scope: Callback 處理、post-payment 履約、排程

- T5.1 [ ] **`packages/shop/src/services/ecpay-cvs-payment.service.ts`** — 實作 `EcpayCvsPaymentService`
  - MerchantTradeNo 生成器：格式 `FD{yyMMddHHmmssSSS}{3-digit-sequence}`，module-level atomic counter + 時鐘回溯保護
  - 建立 Data payload JSON（`MerchantID`, `ChoosePayment: "CVS"`, `OrderInfo: {MerchantTradeDate, MerchantTradeNo, TotalAmount, ReturnURL, TradeDesc, ItemName}`, `CVSInfo: {ExpireDate, CVSCode: "CVS"}`）
  - `encryptData(payloadJson, hashKey, hashIv)` → 加密 Data
  - HTTP POST JSON 到 GenPaymentCode endpoint（stage/prod URL，15 秒 timeout via `AbortSignal.timeout(15000)`）
  - 解密回應 Data → `decryptData(responseData, hashKey, hashIv)` → JSON 解析
  - TransCode=1 且 RtnCode=1 才算成功
  - 提取 orderNumber (`OrderInfo.MerchantTradeNo`)、paymentNo (`CVSInfo.PaymentNo`)、expireDate (`CVSInfo.ExpireDate`)、paymentUrl (`CVSInfo.PaymentURL`)
  - CVS 過期分鐘數 clamp 到 [1, 43200]
  - expireAt 解析：優先使用 ECPay 回應的 ExpireDate（`yyyy/MM/dd HH:mm:ss`，Asia/Taipei 時區），fallback = requestAt + expireMinutes
  - 官方 stage 金鑰保護（MerchantID=3002607 在 `stageMode=false` 時拒絕）
  - TransCode 錯誤訊息含 "decrypt fail" 偵測
  - Verify: Mock HTTP 回應測試所有 TransCode/RtnCode 組合
- T5.2 [ ] **`packages/shop/src/services/ecpay-trade-query.service.ts`** — 實作 `EcpayTradeQueryService`
  - Stage endpoint: `https://payment-stage.ecpay.com.tw/Cashier/QueryTradeInfo/V5`
  - Prod endpoint: `https://payment.ecpay.com.tw/Cashier/QueryTradeInfo/V5`
  - 建立參數 map：`MerchantID`, `MerchantTradeNo`, `TimeStamp` (= epoch seconds)
  - `buildCheckMacValue(params, hashKey, hashIv)` → CheckMacValue
  - HTTP POST `application/x-www-form-urlencoded`（15 秒 timeout）
  - 解析回應：parse form body → 提取 TradeStatus、TradeNo、TradeAmt、RtnMsg/TradeMsg/PaymentType
  - 回傳 `QueryTradeResult(orderNumber, paid, tradeStatus, tradeNo, tradeAmount, message)`
  - paid = `TradeStatus === "1"`
  - Verify: Mock HTTP 回應測試各種 trade status 組合、HTTP 非 200、timeout、InterruptedException

## **Task 6: Express HTTP Server（ECPay Callback）**

Purpose: 實作 Express HTTP 伺服器接收 ECPay callback。
Requirements: R6
Scope: `packages/shop/src/web/ecpay-callback-server.ts`
Out of scope: Callback 業務邏輯（由 `FiatPaymentCallbackService` 處理）

- T6.1 [ ] **`packages/shop/src/web/ecpay-callback-server.ts`** — 實作 `EcpayCallbackHttpServer` 類別
  - `start()`:
    - 若無有效的 return URL（`ECPAY_RETURN_URL` 或 `APP_PUBLIC_BASE_URL`），記錄 info log 並 skip
    - 建立 Express app
    - 設定 middleware：`express.json({ limit: '64kb', type: ['application/json', 'text/plain'] })`、`express.urlencoded({ extended: true, limit: '64kb' })`
    - 註冊 callback route（POST `callbackPath`）：讀取 `req.body`、`req.headers['content-type']` → 調用 `callbackService.handleCallback(requestBody, contentType)` → 回傳對應 HTTP status 和 body
    - 註冊 landing page route（GET/HEAD `/` 和 `/index.html`）：回傳靜態 HTML
    - callback path 不可與 `/` 或 `/index.html` 衝突（start 時檢查並拋錯）
    - `app.listen(port, host)` → 儲存 `http.Server` reference
  - `stop()`: `server.close()` → `server = null`
  - Config-driven：Host 預設 `127.0.0.1`、Port 預設 `8085`、Callback path 預設 `/ecpay/callback`
  - Stage mode 保護：綁定非 localhost（`0.0.0.0` 或公開 IP）且 stageMode=true 時拋出 `IllegalStateException`
  - Callback route 僅接受 POST（非 POST 回 405）
  - Landing page route 僅接受 GET/HEAD（非 GET/HEAD 回 405）
  - Body 超過 64KB 時 Express 自動回 HTTP 413
  - 8 worker threads 改成依賴 Express 內建非同步處理
  - Verify: 啟動伺服器、發送測試 POST 請求驗證 routing、body limit、stage mode 保護

## **Task 7: FiatPaymentCallbackService**

Purpose: 實作 ECPay callback 解密、驗證、idempotent PAID 轉換。
Requirements: R5
Scope: `packages/shop/src/services/fiat-payment-callback.service.ts`
Out of scope: HTTP server（已由 Task 6 處理）

- T7.1 [ ] **`packages/shop/src/services/fiat-payment-callback.service.ts`** — 實作 `FiatPaymentCallbackService`
  - `handleCallback(requestBody: string, contentType: string): CallbackResult`
  - 步驟：
    1. requestBody null/blank → HTTP 400
    2. `sanitizePayload(requestBody)`: 截斷到 4000 字元
    3. `parseCallbackNode(requestBody, contentType)`:
       - contentType 含 `application/json` 或 body 以 `{` 開頭 → JSON parse
       - 否則 → parse form body（`&` 分割、`=` 分割、URL decode 各 key/value）
       - 提取 `Data` 欄位
       - `decryptData(encryptedData, hashKey, hashIv)` → JSON parse
    4. `extractOrderNumber(callbackNode)`: 優先頂層 `.MerchantTradeNo`，fallback 到 `.OrderInfo.MerchantTradeNo`
    5. orderNumber 空白 → HTTP 400
    6. `extractTradeStatus(callbackNode)`: 優先頂層 `.TradeStatus`，fallback 到 `.OrderInfo.TradeStatus`
    7. `extractPaymentMessage(callbackNode)`: 優先 `.RtnMsg`，fallback `.TradeMsg`
    8. `fiatOrderRepository.findByOrderNumber(orderNumber)` → 不存在 → HTTP 200（靜默處理）
    9. `isPaidStatus(tradeStatus)` = `tradeStatus === "1"`
    10. 未付款 (`!paid`): `updateCallbackStatus` 僅記錄狀態 → HTTP 200
    11. 已付款 (`paid`) + `isValidPaidCallback`（驗證 MerchantID 匹配 + TradeAmt 匹配）:
        - `fiatOrderRepository.markPaidIfPending(orderNumber, tradeStatus, paymentMessage, callbackPayload, now)`
        - markPaidIfPending 回傳 null → 訂單已過期或已重複處理 → HTTP 200（靜默）
        - markPaidIfPending 成功 → log info → HTTP 200
    12. 已付款但驗證失敗：`updateCallbackStatus` 記錄狀態 → HTTP 200（不回傳錯誤給 ECPay）
    13. `InvalidCallbackPayloadException` → HTTP 400
    14. 其他 Exception → HTTP 500
  - Helper functions（全部 private）:
    - `parseCallbackNode`, `parseFormBody`, `isJson`, `parseDecryptedData`
    - `extractOrderNumber`, `extractTradeStatus`, `extractPaymentMessage`, `extractMerchantId`, `extractTradeAmount`
    - `isValidPaidCallback(callbackNode, order, orderNumber)`: 驗證 MerchantID（若 config 有設定）和 TradeAmt 匹配
    - `isPaidStatus`, `sanitizePayload`, `textOrNull`, `parsePositiveLong`
  - `CallbackResult` type: `{ httpStatus: number, responseBody: string }`，`CallbackResult.ok()` = `{200, "1|OK"}`，`CallbackResult.fail(status)` = `{status, "0|FAIL"}`
  - Verify: Mock `FiatOrderRepository` + `Config` + crypto → 測試所有分支（paid/unpaid/expired/duplicate/not-found/decrypt-fail/merchant-mismatch/amount-mismatch）

## **Task 8: FiatOrderService（法幣訂單建立）**

Purpose: 實作法幣訂單建立流程。
Requirements: R4
Scope: `packages/shop/src/services/fiat-order.service.ts`
Out of scope: 貨幣購買（CurrencyPurchaseService）、通知、履約

- T8.1 [ ] **`packages/shop/src/services/fiat-order.service.ts`** — 實作 `FiatOrderService`
  - `createFiatOnlyOrder(guildId, userId, productId): Result<FiatOrderResult, DomainError>`
  - 驗證商品存在且屬於該 guild
  - 驗證 `product.hasFiatPriceTwd()` 和 `product.isFiatOnly()`
  - 驗證 `product.id !== null`
  - 呼叫 `ecpayCvsPaymentService.generateCvsPaymentCode(product.fiatPriceTwd, product.name, tradeDesc)`
  - 建立 `FiatOrder.createPending(guildId, userId, product.id, product.name, product.rewardType, product.rewardAmount, product.autoCreateEscortOrder, product.escortOptionCode, paymentCode.orderNumber, paymentCode.paymentNo, product.fiatPriceTwd, paymentCode.expireAt)`
  - `fiatOrderRepository.save(order)` → 取得 persisted order
  - `order.toFulfillmentProduct()` → fulfillment snapshot
  - 回傳 `FiatOrderResult(product, orderNumber, paymentNo, expireDate, paymentUrl, fulfillmentWarning)`
  - `FiatOrderResult.formatDirectMessage()`: zh-TW 格式的 DM 訊息
  - Verify: Mock EcpayCvsPaymentService + FiatOrderRepository → 測試成功路徑、商品不存在、商品非法幣限定、ECPay 取號失敗、persist 失敗

## **Task 9: FiatOrderPostPaymentWorker**

Purpose: 實作非同步付款後履約處理（idempotent 多步驟 pipeline，claim/release）。
Requirements: R7
Scope: `packages/shop/src/services/fiat-order-post-payment-worker.ts`
Out of scope: 排程觸發（Task 12）、對帳（Task 10）

- T9.1 [ ] **`packages/shop/src/services/fiat-order-post-payment-worker.ts`** — 實作 `FiatOrderPostPaymentWorker`
  - `processPendingOrders()`:
    - `fiatOrderRepository.findOrdersPendingPostPayment(DEFAULT_BATCH_SIZE=20)`
    - 對每筆 order 調用 `processSingleOrder(order)`
  - `processSingleOrder(order)`:
    1. **Claim**: `claimFulfillmentProcessing(orderNumber, now)` → false 則 return（跳過）
    2. **Buyer Notification** (idempotent): `!order.isBuyerNotified()` → `buyerNotificationService.notifyPaymentSucceeded(order)` → `markBuyerNotifiedIfNeeded(orderNumber, now)`
    3. **Escort Handoff** (conditional): `order.shouldAutoCreateEscortOrder() && !order.isAdminNotified()` →
       - `escortDispatchHandoffService.handoffFromFiatPayment(guildId, buyerUserId, fulfillmentProduct, orderNumber)` → 失敗 throw
       - `claimAdminNotificationProcessing(orderNumber, adminClaimTime)` → false 則 throw（已被他人處理）
       - 成功後：`escortOrderBuyerNotificationService.notifyEscortOrderCreated(dispatchOrder)` + `adminNotificationService.notifyAdminsOrderCreated(guildId, buyerUserId, dispatchOrder)` + `markAdminNotifiedIfNeeded(orderNumber, adminClaimTime)`
       - admin 通知失敗 → `releaseAdminNotificationProcessing(orderNumber)` + throw
    4. **Reward Grant** (idempotent): `order.hasFulfillmentReward() && !order.isRewardGranted()` →
       - `productRewardService.grantReward(request)` → 失敗 throw
       - `markRewardGrantedIfNeeded(orderNumber, now)`
    5. **Fulfill**: `markFulfilledIfNeeded(orderNumber, now)`（同時清空 `fulfillment_processing_at=NULL`）
    6. **Error handling**: 任何步驟拋出例外 → `releaseFulfillmentProcessing(orderNumber)` + warn log（不傳播例外）
  - Verify: Mock 所有依賴，測試正常流程、各步驟 idempotent（重跑不重複執行）、claim 競爭（跳過）、中途失敗 release lock、admin notification claim 成功後通知失敗 release

## **Task 10: FiatPaymentReconciliationService**

Purpose: 實作對帳排程（逾期標記 + 查單補單 + 指數退避）。
Requirements: R8
Scope: `packages/shop/src/services/fiat-payment-reconciliation.service.ts`
Out of scope: 排程觸發（Task 12）

- T10.1 [ ] **`packages/shop/src/services/fiat-payment-reconciliation.service.ts`** — 實作 `FiatPaymentReconciliationService`
  - `reconcilePendingOrders()`:
    1. `expirePendingOrders(now)`:
       - `fiatOrderRepository.findOrdersPendingExpiry(now, DEFAULT_BATCH_SIZE=20)`
       - 每筆 `fiatOrderRepository.markExpiredIfPending(orderNumber, now, 'EXPIRED')`
    2. `fiatOrderRepository.findOrdersPendingReconciliation(now, now - 7days, DEFAULT_BATCH_SIZE=20)`
    3. 每筆調用 `reconcileSingleOrder(order, now)`
  - `reconcileSingleOrder(order, now)`:
    1. `claimReconciliationProcessing(orderNumber, now)` → false 則 return
    2. `ecpayTradeQueryService.queryTrade(orderNumber)`
    3. query 失敗 → `scheduleRetry(order, now)`（先 release lock）
    4. trade.paid = true → `markPaidIfPending(...)` .isEmpty → `releaseReconciliationProcessing`
    5. trade.paid = false →
       - `now >= order.expireAt` → `markExpiredIfPending(orderNumber, now, 'EXPIRED')`
       - 未過期 → `scheduleRetry(order, now)`
    6. 最外層 catch → `releaseReconciliationProcessing(orderNumber)` + warn log
  - `scheduleRetry(order, now)`:
    - nextAttempt = order.reconciliationAttemptCount + 1
    - nextAttemptAt = now + min(300, 30 × nextAttempt) 秒（指數退避）
    - `markReconciliationAttempted(orderNumber, nextAttempt, nextAttemptAt)`
  - `buildSyntheticPayload(trade)`: JSON `{source: "ECPAY_QUERY_TRADE_INFO", orderNumber, tradeStatus, tradeNo?, tradeAmt?, message?}`
  - `RECONCILIATION_WINDOW = Duration.ofDays(7)` → 7 天前的訂單不再對帳
  - Verify: Mock EcpayTradeQueryService + FiatOrderRepository → 測試 paid/unpaid 未過期/unpaid 已過期/查單失敗/claim 競爭/各種分支、指數退避計算

## **Task 11: ShopService + ShopView（商店瀏覽與搜尋）**

Purpose: 實作商店分頁瀏覽、商品搜尋、UI 組件建構。
Requirements: R1, R2
Scope: `packages/shop/src/services/shop.service.ts`, `packages/shop/src/view/shop-view.ts`
Out of scope: Discord interaction handler（屬於 administration spec）

- T11.1 [ ] **`packages/shop/src/services/shop.service.ts`** — 實作 `ShopService`
  - `getShopPage(guildId, page)`:
    - `totalCount = productRepository.countByGuildId(guildId)`
    - `totalPages = Math.ceil(totalCount / pageSize)`
    - `validPage = Math.max(0, Math.min(page, totalPages - 1))`
    - `products = productRepository.findByGuildIdPaginated(guildId, validPage, pageSize)`
    - 回傳 `ShopPage(products, validPage + 1, totalPages)`
  - `searchProducts(guildId, keyword, page)`:
    - keyword blank → 回傳 `ShopPage([], 1, 0)`
    - `totalCount = productRepository.countByGuildIdAndNameContaining(guildId, keyword)`
    - 同上分頁邏輯
    - `products = productRepository.findByGuildIdAndNameContaining(guildId, keyword, validPage, pageSize)`
  - `getProductCount(guildId)`, `hasProducts(guildId)`
  - `ShopPage` type: `{ products: Product[], currentPage: number, totalPages: number }`
    - `isEmpty()`, `hasPreviousPage()`, `hasNextPage()`, `formatPageIndicator()`
  - Verify: Mock ProductRepository → 測試分頁計算、搜尋空白關鍵字、page 超出範圍 clamp
- T11.2 [ ] **`packages/shop/src/view/shop-view.ts`** — 實作 `ShopView` 靜態方法
  - `buildEmptyShopEmbed()`: embed 顏色 0x5865F2，title "🏪 商店"，description "目前沒有可購買的商品"
  - `buildShopEmbed(products, currentPage, totalPages, guildId)`: 遍歷 products 渲染格式（編號、名稱、貨幣價格、法幣價格、描述、獎勵），footer 顯示頁碼
  - `buildShopComponents(currentPage, totalPages)`: 翻頁按鈕（`shop_prev_{page}`, `shop_next_{page}`）
  - `buildShopComponents(currentPage, totalPages, hasProducts)`: 翻頁 + 購買按鈕（`shop_buy`, SUCCESS）+ 搜尋按鈕（`shop_search`, SECONDARY）
  - `buildBuyMenu(allProducts)`: select menu（customId `shop_buy_select`），自動 split 為每組 ≤25 選項
  - `buildSearchResultComponents(currentPage, totalPages, keyword, products)`: select menu（`shop_search_buy_select`）+ 翻頁按鈕（`shop_sprev_{encodedKw}_{page}` / `shop_snext_{encodedKw}_{page}`）+ 返回商店按鈕（`shop_back`）
  - `buildSearchModal()`: Modal customId `shop_search_modal`，text input（keyword, minLength=1, maxLength=100）
  - `buildPaymentMethodChoiceEmbed(product)` + `buildPaymentMethodChoiceComponents(product)`: 雙支付方式選擇 UI
  - `buildPurchaseConfirmEmbed(product, userBalance)`: 確認購買（商品名、價格、餘額、購買後餘額、若不足顯示警告 0xED4245）
  - `encodeKeyword(keyword)`: Base64 無 padding 編碼（`Buffer.from(keyword, 'utf8').toString('base64').replace(/=+$/, '')`）
  - `decodeKeyword(encoded)`: Base64 解碼（`Buffer.from(encoded, 'base64').toString('utf8')`）
  - `getPageSize()`: 回傳 `PAGE_SIZE = 5`
  - Verify: 檢查所有 customId 格式、embed color、zh-TW 文字、Base64 roundtrip

## **Task 12: FiatOrderProcessingScheduler（排程服務）**

Purpose: 實作定時排程觸發 post-payment worker 和 reconciliation。
Requirements: R7, R8
Scope: `packages/shop/src/services/fiat-order-processing-scheduler.ts`
Out of scope: Worker 邏輯（Task 9）、對帳邏輯（Task 10）

- T12.1 [ ] **`packages/shop/src/services/fiat-order-processing-scheduler.ts`** — 實作 `FiatOrderProcessingScheduler`
  - `start()`:
    - 若已啟動（intervals 已存在）則 return（idempotent）
    - `postPaymentInterval = setInterval(() => { try { postPaymentWorker.processPendingOrders() } catch (e) { log.warn(...) } }, 10000)`（初始延遲 2 秒可用第一次 setTimeout 再 setInterval）
    - `reconciliationInterval = setInterval(() => { try { reconciliationService.reconcilePendingOrders() } catch (e) { log.warn(...) } }, 60000)`（初始延遲 5 秒）
    - 記錄 info log "Started fiat order processing scheduler"
  - `stop()`:
    - `clearInterval(postPaymentInterval)`, `clearInterval(reconciliationInterval)`
    - intervals = null
    - 記錄 info log "Stopped fiat order processing scheduler"
  - 每個 tick 包裝 try-catch（例外不傳播，僅 log warn）
  - Verify: 使用 `vi.useFakeTimers()` 測試排程間隔、啟動/停止、例外不傳播

## **Task 13: CurrencyPurchaseService（貨幣購買）**

Purpose: 實作貨幣購買流程（扣款 → 獎勵 → 自動退款）。
Requirements: R3
Scope: `packages/shop/src/services/currency-purchase.service.ts`
Out of scope: Discord interaction handler

- T13.1 [ ] **`packages/shop/src/services/currency-purchase.service.ts`** — 實作 `CurrencyPurchaseService`
  - `purchaseProduct(guildId, userId, productId): Result<PurchaseResult, DomainError>`
  - 步驟：
    1. `productService.getProduct(productId)` → 不存在或 guild 不匹配 → `DomainError.invalidInput("找不到該商品")`
    2. `!product.hasCurrencyPrice()` → `DomainError.invalidInput("此商品不可用貨幣購買")`
    3. `balanceService.tryGetBalance(guildId, userId)` → 失敗則回傳 error
    4. `currentBalance < price` → `DomainError.invalidInput("餘額不足。需要: X 貨幣，目前餘額: Y 貨幣")`
    5. `balanceAdjustmentService.tryAdjustBalance(guildId, userId, -price)` → 失敗則 `DomainError.persistenceFailure("扣除貨幣失敗")`
    6. `transactionService.recordTransaction(guildId, userId, -price, newBalance, PRODUCT_PURCHASE, "購買商品: {name}")`
    7. 若 `product.hasReward()` → `productRewardService.grantReward(request)`
       - 成功 → 更新 finalBalance（若為 CURRENCY reward）
       - 失敗 → `refundPurchaseAfterRewardFailure(...)` → `tryAdjustBalance(guildId, userId, +price)` + `recordTransaction(PRODUCT_PURCHASE_REFUND)` → 退款成功則回傳 `unexpectedFailure("商品獎勵發放失敗，已自動退款")`；退款失敗則回傳 `persistenceFailure("商品獎勵發放失敗，且自動退款失敗")`
    8. 成功 → `PurchaseResult(product, currentBalance, finalBalance, price, rewardMessage)`
  - `PurchaseResult` type 含 `formatSuccessMessage()`: zh-TW 成功訊息（✅ 購買成功 + 商品、價格、購買前後餘額、獎勵）
  - Verify: Mock BalanceService/BalanceAdjustmentService/ProductRewardService/ProductService → 測試所有分支

## **Task 14: 兌換碼領域模型 + Repository Interface + Generator**

Purpose: 定義 `RedemptionCode` 型別、zod schema、`RedemptionCodeRepository` interface、`RedemptionCodeGenerator`。
Requirements: R10
Scope: `packages/shop/src/domain/redemption-code.ts`, `packages/shop/src/domain/redemption-code-repository.ts`, `packages/shop/src/services/redemption-code-generator.ts`
Out of scope: RedemptionService 業務邏輯（Task 15）

- T14.1 [ ] **`packages/shop/src/domain/redemption-code.ts`** — 定義 `RedemptionCode` type + zod schema
  - 10 個欄位：id, code, productId, guildId, expiresAt, redeemedBy, redeemedAt, createdAt, invalidatedAt, quantity
  - 驗證：code 必填、非空白、≤32 字元；redeemedBy 和 redeemedAt 必須同為 null 或同為 non-null；quantity ∈ [1, 1000]
  - `CODE_LENGTH = 16`、`CODE_CHARACTERS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"`
  - Helper: `create()`, `withRedeemed(userId)`, `withInvalidated()`, `isRedeemed()`, `isExpired()`, `isValid()`, `isInvalidated()`, `belongsToGuild(guildId)`, `getMaskedCode()`
  - `withRedeemed`: 若已 redeemed 則拋錯
  - `withInvalidated`: 若已 invalidated 則拋錯
  - Verify: 單元測試所有 helper 方法和 validation
- T14.2 [ ] **`packages/shop/src/domain/redemption-code-repository.ts`** — 定義 `RedemptionCodeRepository` interface
  - `saveAll(codes: RedemptionCode[]): Promise<RedemptionCode[]>`
  - `findByCode(code: string): Promise<RedemptionCode | null>`
  - `findByProductId(productId: number, limit: number, offset: number): Promise<RedemptionCode[]>`
  - `countByProductId(productId: number): Promise<number>`
  - `existsByCode(code: string): Promise<boolean>`
  - `markAsRedeemedIfAvailable(id: number, userId: number, redeemedAt: Date): Promise<boolean>`
  - `clearRedeemedIfMatches(id: number, userId: number, redeemedAt: Date): Promise<boolean>`
  - `getStatsByProductId(productId: number): Promise<CodeStats>` — `CodeStats = { total: number, redeemed: number, available: number }`
  - `getCodePage(productId, page, pageSize): Promise<CodePage>`
  - Verify: TypeScript 型別檢查通過
- T14.3 [ ] **`packages/shop/src/services/redemption-code-generator.ts`** — 實作 `RedemptionCodeGenerator`
  - `generate()`: 使用 `crypto.randomInt(CHARACTERS.length)` 選取 16 個字元，組成大寫字串
  - `isValidFormat(code)`: static 方法 — 長度=16、所有字元都在 CHARACTERS 中
  - Verify: 測試生成代碼長度、字元範圍、大量生成（1000+）無重複

## **Task 15: RedemptionRepository + RedemptionService**

Purpose: 實作兌換碼 persistence 和業務邏輯（generate + redeem）。
Requirements: R10, R11
Scope: `packages/shop/src/persistence/drizzle-redemption-code-repository.ts`, `packages/shop/src/services/redemption.service.ts`
Out of scope: 兌換碼生成器（Task 14.3）

- T15.1 [ ] **`packages/shop/src/persistence/drizzle-redemption-code-repository.ts`** — 實作 `RedemptionCodeRepository`
  - `saveAll(codes)`: batch insert returning
  - `findByCode(code)`: `WHERE code = UPPER(TRIM(?))`
  - `markAsRedeemedIfAvailable(id, userId, redeemedAt)`: `UPDATE ... SET redeemed_by=?, redeemed_at=? WHERE id=? AND redeemed_by IS NULL` → rowsAffected > 0
  - `clearRedeemedIfMatches(id, userId, redeemedAt)`: `UPDATE ... SET redeemed_by=NULL, redeemed_at=NULL WHERE id=? AND redeemed_by=? AND redeemed_at=?` → rowsAffected > 0
  - `getStatsByProductId`: `SELECT COUNT(*) as total, COUNT(redeemed_by) as redeemed, COUNT(*) FILTER (WHERE redeemed_by IS NULL AND (expires_at IS NULL OR expires_at > NOW()) AND invalidated_at IS NULL) as available FROM redemption_code WHERE product_id = ?`
  - Verify: 整合測試驗證 markAsRedeemed 的競爭保護、clearRedeemedIfMatches 條件匹配
- T15.2 [ ] **`packages/shop/src/services/redemption.service.ts`** — 實作 `RedemptionService`
  - `generateCodes(productId, count, expiresAt, quantity=1)`:
    - 驗證 count ∈ [1, 100]、quantity ∈ [1, 1000]、expiresAt 在未來（若有）
    - 查詢商品存在
    - 生成 N 個唯一碼（`generateUniqueCode()` 嘗試最多 10 次去重 → `existsByCode`）
    - `codeRepository.saveAll(codes)`
    - 發布 `RedemptionCodesGeneratedEvent`
  - `redeemCode(codeStr, guildId, userId)`:
    - code 轉大寫 trim
    - 查詢代碼 → 不存在 → "兌換碼無效"
    - `!code.belongsToGuild(guildId)` → "兌換碼無效"
    - `code.isInvalidated()` → "此兌換碼已失效"
    - `code.isRedeemed()` → "此兌換碼已被使用"
    - `code.isExpired()` → "此兌換碼已過期"
    - `code.productId == null` → "此兌換碼已失效"
    - 查詢商品 → 不存在 → "商品資料異常"
    - `calculateTotalRewardAmount(product, code)`: `rewardAmount × quantity`（overflow 檢測 → MultiplierExact 等價）
    - `code.withRedeemed(userId)` → `markAsRedeemedIfAvailable(id, userId, redeemedAt)` → false → "此兌換碼已被使用或不可用"
    - 發放獎勵 → `productRewardService.grantReward(request)` → 失敗 → `rollbackRedeemedCodeAfterRewardFailure(...)` → `clearRedeemedIfMatches` → 成功則 `unexpectedFailure("商品獎勵發放失敗，兌換已取消")`；失敗則 `persistenceFailure("商品獎勵發放失敗，且兌換碼回復失敗")`
    - 記錄 `ProductRedemptionTransaction`
    - 發布 `ProductRedemptionCompletedEvent`
    - 回傳 `RedemptionResult`
  - `findByCode()`, `getCodePage(productId, page, pageSize)`, `getCodeStats(productId)`
  - `CodePage` type: `{ codes, currentPage, totalPages, totalCount, pageSize }`
  - `RedemptionResult` type: `{ code: RedemptionCode, product: Product, rewardedAmount: number | null }` + `formatSuccessMessage()`
  - `MAX_BATCH_SIZE = 100`
  - Verify: Mock repository + ProductRewardService → 測試所有驗證、獎勵發放、rollback 路徑

## **Task 16: 通知服務**

Purpose: 實作三個通知服務（買家付款成功、買家護航建立、管理員新訂單）。
Requirements: R12
Scope: `packages/shop/src/services/` (三個檔案)
Out of scope: 通知觸發邏輯（已含在 Worker 和其他 service 中）

- T16.1 [ ] **`packages/shop/src/services/fiat-order-buyer-notification.service.ts`** — 實作 `FiatOrderBuyerNotificationService`
  - `notifyPaymentSucceeded(order: FiatOrder)`: null guard → 透過 `DiscordRuntimeGateway` 發送 DM（付款成功訊息，zh-TW 格式）
  - `buildPaymentSucceededMessage(order)`: "✅ 付款成功！" + 商品名、訂單編號、超商代碼、金額
  - 全部包裝 try-catch（fire-and-forget，失敗僅 log warn）
  - Verify: Mock DiscordRuntimeGateway → 驗證訊息格式、null guard
- T16.2 [ ] **`packages/shop/src/services/escort-order-buyer-notification.service.ts`** — 實作 `EscortOrderBuyerNotificationService`
  - `notifyEscortOrderCreated(order: EscortDispatchOrder)`: null guard → 跳過 bot 自身 → DM 通知（護航訂單建立訊息）
  - `buildEscortOrderCreatedMessage(order)`: "🛡️ 護航訂單已建立" + 商品名、護航編號、付款方式
  - `resolveSelfUserId()`: 從 `DiscordRuntimeGateway` 取得 bot 自身 ID
  - Verify: Mock DiscordRuntimeGateway → 驗證 bot self skip、訊息格式
- T16.3 [ ] **`packages/shop/src/services/shop-admin-notification.service.ts`** — 實作 `ShopAdminNotificationService`
  - `notifyAdminsOrderCreated(guildId, buyerUserId, product, orderType, orderReference)`: 遍歷 guild 內所有 ADMINISTRATOR 成員 + guild owner → 發送 DM（best-effort，排除 bot 自身、去重）
  - `notifyAdminsOrderCreated(guildId, buyerUserId, dispatchOrder: EscortDispatchOrder)`: 同上
  - `buildAdminOrderNotification(guild, buyerUserId, product, orderType, orderReference)`: "📩 有新訂單發起" + 伺服器名、買家 mention、商品名、訂單類型、訂單編號
  - `buildAdminOrderNotification(guild, buyerUserId, dispatchOrder)`: "📩 有新護航工作交接" + 來源類型、來源參考、護航選項、dispatch 編號
  - `isAdmin(member, guild)`: 檢查 `ADMINISTRATOR` permission 或 guild owner
  - `sendAdminNotification(adminUser, message)`: DM fire-and-forget
  - `resolveSelfUserId(guildId)`: bot 自身 ID 偵測
  - Verify: Mock DiscordRuntimeGateway → 驗證通知對象選擇（admin+owner）、去重、bot 自身過濾

## **Task 17: DI 註冊與模組入口**

Purpose: 將所有 service、repository、worker 註冊到 DI container。
Requirements: 所有 R
Scope: `packages/shop/src/di/`, `packages/shop/src/index.ts`
Out of scope: 其他 package 的 DI

- T17.1 [ ] **`packages/shop/src/index.ts`** — 模組入口
  - Export 所有 public interface/type（`FiatOrder`, `FiatOrderRepository`, `FiatOrderService`, `RedemptionService`, `ShopService`, `CurrencyPurchaseService` 等）
  - Export DI 註冊函數
- T17.2 [ ] **`packages/shop/src/di/shop-module.ts`** — DI 註冊
  - `DrizzleFiatOrderRepository` → bind as `FiatOrderRepository` (singleton)
  - `DrizzleRedemptionCodeRepository` → bind as `RedemptionCodeRepository` (singleton)
  - 所有 service：constructor injection、singleton
  - `FiatOrderProcessingScheduler`：lifecycle start/stop hooks
  - `EcpayCallbackHttpServer`：lifecycle start/stop hooks
  - Verify: DI container 啟動成功、所有依賴解析無誤
