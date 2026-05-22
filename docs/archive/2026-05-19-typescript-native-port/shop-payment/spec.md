# Spec: Shop and Payment

- Date: 2026-05-20
- Feature: Shop and Payment
- Owner: [To be filled]

## Goal

將 Java 商店系統（商品瀏覽、貨幣購買、ECPay 法幣付款、兌換碼）、付款回呼處理、履約 worker 與對帳排程完整移植到 TypeScript，確保所有付款狀態機、ECPay 加密/解密/CheckMacValue 演算法、冪等機制與通知行為與原版完全一致。

## Scope

### In Scope

- 商店瀏覽與搜尋：`ShopService`、`ShopView`（分頁 embed、select menu 商品選擇、按鈕互動）
- 貨幣購買：`CurrencyPurchaseService`（餘額檢查 → 扣款 → 獎勵發放 → 獎勵失敗自動退款）
- 法幣付款（ECPay）：`EcpayCvsPaymentService`（產生 CVS 繳費代碼）、`FiatOrderService`（建立法幣訂單）
- ECPay callback 處理：`EcpayCallbackHttpServer`（Express HTTP 伺服器）、`FiatPaymentCallbackService`（AES 解密回呼 payload、驗證商家 ID 與金額、idempotent PAID 轉換）
- 付款後履約 worker：`FiatOrderPostPaymentWorker`（通知買家 → 護航交接 → 發放獎勵 → 標記完成，每一步 idempotent）
- 排程：`FiatOrderProcessingScheduler`（post-payment 每 10 秒、對帳每 60 秒）
- 對帳：`FiatPaymentReconciliationService`（逾期訂單標記、ECPay 查單補單、指數退避重試）
- ECPay 查單：`EcpayTradeQueryService`（SHA-256 CheckMacValue、ECPay 特有 URL 編碼規則）
- 兌換碼：`RedemptionCodeGenerator`（16 字元 SecureRandom 生成）、`RedemptionService`（生成、兌換、回滾）
- 通知：`FiatOrderBuyerNotificationService`、`EscortOrderBuyerNotificationService`、`ShopAdminNotificationService`
- 領域模型：`FiatOrder`（36 欄位 record、strict compact constructor validation）、`RedemptionCode`、`FiatOrderRepository`（16 方法 + 3 組 claim/release）

### Out of Scope

- 商品管理（CRUD Product）與管理面板（屬於 administration spec）
- Product 領域模型本身（已存在於 `@ltdjms/shared` 或 product domain，此 spec 僅 import）
- ProductRepository 的 CRUD 實作（屬於 administration spec 或 shared infra）
- 護航派單完整生命週期（屬於 escort-dispatch spec，此 spec 僅透過 `EscortDispatchHandoffService` 介面調用）
- 貨幣/代幣系統的完整實作（屬於 guild-economy spec，此 spec 僅透過 `BalanceService`、`BalanceAdjustmentService`、`GameTokenService`、`ProductRewardService` 介面調用）
- Discord slash command 註冊（屬於 administration spec）

## Functional Behaviors (BDD)

### Requirement 1: 商店商品瀏覽與分頁

**GIVEN** 一個 guild 在資料庫中有 N 個商品
**WHEN** 使用者執行商店指令（或點擊商店按鈕）
**THEN** 顯示第一頁商品列表（每頁最多 5 個商品）
**AND** 每個商品顯示編號、名稱、貨幣價格（若有）、法幣價格（若有）、描述（若有）、獎勵資訊（若有）
**AND** 若總頁數 > 1，顯示「上一頁」/「下一頁」按鈕
**AND** 若有商品，顯示「購買」按鈕和「搜尋」按鈕
**AND** 若無商品，顯示「目前沒有可購買的商品」

**Requirements**:
- [ ] R1.1 `ShopService.getShopPage(guildId, page)` 回傳 `ShopPage(products, currentPage, totalPages)`
- [ ] R1.2 分頁大小為 5（`PAGE_SIZE = 5`），currentPage 為 1-based
- [ ] R1.3 page 超出範圍時自動 clamp 到有效範圍（max(0, min(page, totalPages-1))）
- [ ] R1.4 `ShopView.buildShopEmbed()` 以 zh-TW 格式渲染 embed，顏色 0x5865F2
- [ ] R1.5 `ShopView.buildShopComponents()` 生成翻頁按鈕 ID 格式 `shop_prev_{page}` / `shop_next_{page}`
- [ ] R1.6 購買按鈕 customId 為 `shop_buy`，搜尋按鈕 customId 為 `shop_search`

### Requirement 2: 商品搜尋

**GIVEN** 使用者點擊「搜尋」按鈕
**WHEN** 系統顯示搜尋 Modal
**AND** 使用者輸入關鍵字並提交
**THEN** 查詢 `ProductRepository.countByGuildIdAndNameContaining(guildId, keyword)` 和分頁查詢
**AND** 顯示搜尋結果（與商店頁面相同格式，但含搜尋結果專用的 select menu 和翻頁按鈕）
**AND** 搜尋結果的翻頁按鈕 ID 包含 Base64 編碼的關鍵字（`shop_sprev_{encodedKw}_{page}` / `shop_snext_{encodedKw}_{page}`）
**AND** 顯示「返回商店」按鈕

**Requirements**:
- [ ] R2.1 `ShopService.searchProducts(guildId, keyword, page)` 回傳 `ShopPage`
- [ ] R2.2 空白關鍵字時回傳空結果（`ShopPage([], 1, 0)`）
- [ ] R2.3 關鍵字以 Base64（無 padding）編碼後嵌入按鈕 customId
- [ ] R2.4 搜尋結果也包含購買 select menu（customId: `shop_search_buy_select`），自動拆分為每組 ≤25 個選項
- [ ] R2.5 Modal customId 為 `shop_search_modal`，輸入欄位 minLength=1、maxLength=100

### Requirement 3: 貨幣購買流程

**GIVEN** 使用者選擇一個商品並點擊「貨幣購買」
**AND** 該商品有貨幣價格（`currencyPrice > 0`）
**WHEN** 系統執行購買
**THEN** 檢查商品存在且屬於該 guild
**AND** 檢查商品有貨幣價格
**AND** 檢查使用者餘額 ≥ 商品價格
**AND** 扣除貨幣（`BalanceAdjustmentService.tryAdjustBalance(guildId, userId, -price)`）
**AND** 記錄購買交易（Source=`PRODUCT_PURCHASE`）
**AND** 若商品有獎勵，呼叫 `ProductRewardService.grantReward()`
**AND** 若獎勵發放失敗，自動退款（`tryAdjustBalance(guildId, userId, +price)`）並記錄退款交易（Source=`PRODUCT_PURCHASE_REFUND`）
**AND** 回傳 `PurchaseResult`（product, previousBalance, newBalance, price, rewardMessage）

**Requirements**:
- [ ] R3.1 `CurrencyPurchaseService.purchaseProduct(guildId, userId, productId)` 回傳 `Result<PurchaseResult, DomainError>`
- [ ] R3.2 商品不存在或 guild 不匹配時回傳 `DomainError.invalidInput("找不到該商品")`
- [ ] R3.3 商品無貨幣價格時回傳 `DomainError.invalidInput("此商品不可用貨幣購買")`
- [ ] R3.4 餘額不足時回傳 `DomainError.invalidInput("餘額不足。需要: X 貨幣，目前餘額: Y 貨幣")`
- [ ] R3.5 扣除失敗時回傳 `DomainError.persistenceFailure("扣除貨幣失敗")`
- [ ] R3.6 獎勵發放失敗時，先執行退款再回傳 `DomainError.unexpectedFailure("商品獎勵發放失敗，已自動退款")`
- [ ] R3.7 退款失敗時回傳 `DomainError.persistenceFailure("商品獎勵發放失敗，且自動退款失敗")`
- [ ] R3.8 成功訊息格式：`✅ 購買成功！\n\n**商品：** X\n**價格：** Y 貨幣\n**購買前餘額：** A 貨幣\n**購買後餘額：** B 貨幣`

### Requirement 4: 法幣訂單建立與 ECPay 繳費代碼取得

**GIVEN** 使用者選擇一個法幣限定商品（`isFiatOnly() = true`，`fiatPriceTwd > 0`，`!hasCurrencyPrice()`）
**WHEN** 系統建立法幣訂單
**THEN** 驗證商品有法幣價格且為法幣限定
**AND** 呼叫 `EcpayCvsPaymentService.generateCvsPaymentCode(totalAmountTwd, itemName, tradeDesc)`
**AND** 生成唯一的 MerchantTradeNo（格式 `FD` + yyMMddHHmmssSSS + 3 位序號，synchronized 避免重複）
**AND** 向 ECPay GenPaymentCode API 發送加密請求（AES/CBC/PKCS5Padding 加密 Request Body JSON）
**AND** 解密 ECPay 回應取得 orderNumber、paymentNo、expireDate、paymentUrl
**AND** 建立 `FiatOrder`（Status=`PENDING_PAYMENT`，含 snapshot 的 fulfillment 資訊）
**AND** 持久化到資料庫
**AND** 回傳 DM 訊息（含訂單編號、超商代碼、金額、繳費期限、繳費說明）

**Requirements**:
- [ ] R4.1 `FiatOrderService.createFiatOnlyOrder(guildId, userId, productId)` 回傳 `Result<FiatOrderResult, DomainError>`
- [ ] R4.2 生成 MerchantTradeNo 格式 `FD{yyMMddHHmmssSSS}{3-digit-sequence}`，synchronized 保證同毫秒不重複
- [ ] R4.3 ECPay API request 以 JSON body 格式發送：`{MerchantID, RqHeader: {Timestamp}, Data: <encrypted>}`
- [ ] R4.4 `Data` 欄位內容為 AES/CBC/PKCS5Padding 加密的 URL-encoded JSON（key=HashKey, iv=HashIV）
- [ ] R4.5 ECPay stage 模式使用 `https://ecpayment-stage.ecpay.com.tw/1.0.0/Cashier/GenPaymentCode`
- [ ] R4.6 ECPay prod 模式使用 `https://ecpayment.ecpay.com.tw/1.0.0/Cashier/GenPaymentCode`
- [ ] R4.7 TransCode != 1 時回傳錯誤（含 "decrypt fail" 偵測並提示環境/金鑰不匹配）
- [ ] R4.8 RtnCode != 1 時回傳業務錯誤
- [ ] R4.9 官方 stage 金鑰（MerchantID=3002607）在 stageMode=false 時拒絕使用
- [ ] R4.10 CVS 過期分鐘數 clamp 到 [1, 43200]（1 分鐘 ~ 30 天）
- [ ] R4.11 expireAt 優先使用 ECPay 回應的 ExpireDate（Asia/Taipei 時區解析），fallback 用 requestAt + expireMinutes
- [ ] R4.12 請求超時設為 15 秒

### Requirement 5: ECPay 付款回呼處理

**GIVEN** ECPay 向 callback HTTP 伺服器發送 POST 請求
**WHEN** 系統接收到回呼
**THEN** 解析 request body（支援 JSON 和 form-urlencoded 兩種格式）
**AND** 從 body 中提取 `Data` 欄位
**AND** 以 AES/CBC/PKCS5Padding 解密（key=HashKey, iv=HashIV，Base64 decode → AES decrypt → URL decode）
**AND** 從解密後的 JSON 提取 MerchantTradeNo（支援頂層和 OrderInfo 巢狀結構）
**AND** 查詢對應的 FiatOrder
**AND** 提取 TradeStatus（`"1"` = 已付款）
**AND** 若未付款：僅更新 callback status（tradeStatus + paymentMessage + callbackPayload），不觸發狀態轉換
**AND** 若已付款且驗證通過（MerchantID 匹配 + TradeAmt 匹配）：執行 `markPaidIfPending`（conditional UPDATE `WHERE status = PENDING_PAYMENT`）
**AND** 回傳 HTTP 200 "1|OK"（成功）或 HTTP 400/500 "0|FAIL"（失敗）
**AND** payload 長度超過 4000 字元時截斷儲存

**Requirements**:
- [ ] R5.1 `FiatPaymentCallbackService.handleCallback(requestBody, contentType)` 回傳 `CallbackResult(httpStatus, responseBody)`
- [ ] R5.2 支援 `application/json` Content-Type 和 `application/x-www-form-urlencoded` 兩種格式
- [ ] R5.3 解密流程：`Base64.decode → AES/CBC/PKCS5Padding decrypt → URLDecoder.decode`
- [ ] R5.4 支援 MerchantTradeNo 的兩種 JSON 路徑：頂層 `.MerchantTradeNo` 和巢狀 `.OrderInfo.MerchantTradeNo`
- [ ] R5.5 訂單不存在時回傳 HTTP 200（不回傳錯誤，避免 ECPay 重送）
- [ ] R5.6 `markPaidIfPending` 為 conditional UPDATE：`WHERE order_number = ? AND status = 'PENDING_PAYMENT' RETURNING *`
- [ ] R5.7 已過期訂單收到付款 callback 時不回傳錯誤（記錄 log 並回傳 200）
- [ ] R5.8 重複付款 callback 時不回傳錯誤（記錄 log 並回傳 200）
- [ ] R5.9 callbackPayload 截斷到 4000 字元
- [ ] R5.10 解密失敗時回傳 HTTP 400（`InvalidCallbackPayloadException`）
- [ ] R5.11 內部錯誤時回傳 HTTP 500

### Requirement 6: Express HTTP 伺服器（ECPay Callback）

**GIVEN** 系統啟動
**WHEN** 設定了有效的 `ECPAY_RETURN_URL`（或 `APP_PUBLIC_BASE_URL`）
**THEN** 啟動 Express HTTP 伺服器監聽 ECPay callback
**AND** 綁定到設定的 host（預設 127.0.0.1）和 port（預設 8085）
**AND** callback 路徑預設為 `/ecpay/callback`
**AND** 根路徑 `/` 提供靜態 landing page（`index.html`）
**AND** POST 請求 body 大小限制 64KB
**AND** stage 模式時強制綁定 localhost（127.0.0.1 / ::1），禁止綁定公開位址

**Requirements**:
- [ ] R6.1 `EcpayCallbackHttpServer` 在 TypeScript 中以 Express 實作
- [ ] R6.2 callback 路徑僅接受 POST
- [ ] R6.3 根路徑僅接受 GET/HEAD
- [ ] R6.4 正確的 Host/Port 預設值（127.0.0.1:8085）
- [ ] R6.5 callback 路徑不可與根路徑 `/` 或 `/index.html` 衝突
- [ ] R6.6 request body 限制 64KB（`express.json({ limit: '64kb' })` / `express.urlencoded({ limit: '64kb' })`）
- [ ] R6.7 stage mode + 非 localhost 綁定時拋出 `IllegalStateException`（阻止啟動）

### Requirement 7: 付款後履約 Worker

**GIVEN** 排程每 10 秒觸發一次 `processPendingOrders()`
**WHEN** 查詢 `findOrdersPendingPostPayment(limit=20)`（`status = PAID AND fulfilled_at IS NULL AND fulfillment_processing_at IS NULL`）
**AND** 對每筆訂單執行 `processSingleOrder(order)`
**THEN** **Step 0 — Claim**：`claimFulfillmentProcessing(orderNumber, now)`（conditional UPDATE `WHERE fulfilled_at IS NULL AND fulfillment_processing_at IS NULL`），失敗則跳過
**AND** **Step 1 — 通知買家**（idempotent）：若 `!isBuyerNotified()`，呼叫 `buyerNotificationService.notifyPaymentSucceeded(order)`，然後 `markBuyerNotifiedIfNeeded(orderNumber, now)`（`WHERE buyer_notified_at IS NULL`）
**AND** **Step 2 — 護航交接**（若需要）：若 `shouldAutoCreateEscortOrder() && !isAdminNotified()`，呼叫 `escortDispatchHandoffService.handoffFromFiatPayment()`，然後 `claimAdminNotificationProcessing` → 通知買家（escort created）→ 通知管理員 → `markAdminNotifiedIfNeeded`
**AND** **Step 3 — 發放獎勵**（idempotent）：若 `hasFulfillmentReward() && !isRewardGranted()`，呼叫 `productRewardService.grantReward()`，然後 `markRewardGrantedIfNeeded(orderNumber, now)`（`WHERE reward_granted_at IS NULL`）
**AND** **Step 4 — 標記完成**（idempotent）：`markFulfilledIfNeeded(orderNumber, now)`（`WHERE fulfilled_at IS NULL`，同時清空 `fulfillment_processing_at`）
**AND** 任何步驟拋出例外時：`releaseFulfillmentProcessing(orderNumber)`，記錄 warn log，不傳播例外

**Requirements**:
- [ ] R7.1 `FiatOrderPostPaymentWorker.processPendingOrders()` 每次處理最多 20 筆
- [ ] R7.2 claim 失敗時（其他人已在處理）直接跳過該訂單
- [ ] R7.3 每一步使用 `WHERE col IS NULL` 條件保證 idempotent
- [ ] R7.4 admin notification claim 成功後若通知失敗，必須 `releaseAdminNotificationProcessing`
- [ ] R7.5 護航交接失敗時拋出 `IllegalStateException` → release → 下次重試
- [ ] R7.6 獎勵發放失敗時拋出 `IllegalStateException` → release → 下次重試
- [ ] R7.7 markFulfilled 同時清空 `fulfillment_processing_at = NULL`（釋放 lock）
- [ ] R7.8 findAll 排序：`paid_at ASC NULLS LAST, created_at ASC`

### Requirement 8: 對帳排程

**GIVEN** 排程每 60 秒觸發一次 `reconcilePendingOrders()`
**WHEN** 執行對帳
**THEN** **Phase 1 — 逾期標記**：查詢 `findOrdersPendingExpiry(now, limit=20)`，對每筆執行 `markExpiredIfPending`
**AND** **Phase 2 — 查單補單**：查詢 `findOrdersPendingReconciliation(now, 7天前, limit=20)`，對每筆執行：
  - `claimReconciliationProcessing`（conditional UPDATE `WHERE status=PENDING_PAYMENT AND paid_at IS NULL AND reconciliation_processing_at IS NULL AND expire_at > now`）
  - 呼叫 `ecpayTradeQueryService.queryTrade(orderNumber)`
  - 若 paid=true → `markPaidIfPending`（觸發 post-payment worker）
  - 若 paid=false 且未過期 → `scheduleRetry`（指數退避：30s × attempt，上限 300s）
  - 若 paid=false 且已過期 → `markExpiredIfPending`
  - 查單失敗 → `scheduleRetry`
  - 最後 release lock（`releaseReconciliationProcessing` 或 `markReconciliationAttempted` 自動清 `reconciliation_processing_at = NULL`）

**Requirements**:
- [ ] R8.1 `expirePendingOrders(now)` 查詢條件：`status=PENDING_PAYMENT AND paid_at IS NULL AND reconciliation_processing_at IS NULL AND COALESCE(expire_at, created_at + 7 days) <= now`
- [ ] R8.2 `markExpiredIfPending` 為 conditional UPDATE：`WHERE order_number = ? AND status = PENDING_PAYMENT AND paid_at IS NULL AND expired_at IS NULL AND COALESCE(expire_at, ...) <= ?`
- [ ] R8.3 對帳查詢條件：status=PENDING_PAYMENT、created_at >= 7天前、reconciliation_processing_at IS NULL、reconciliation_next_attempt_at IS NULL OR <= now、expire_at > now
- [ ] R8.4 指數退避公式：`nextAttempt = now + min(300, 30 × attempt)` 秒
- [ ] R8.5 `markReconciliationAttempted` 同時清空 `reconciliation_processing_at = NULL`（釋放 lock）
- [ ] R8.6 syntheticPayload 格式為 JSON：`{source: "ECPAY_QUERY_TRADE_INFO", orderNumber, tradeStatus, tradeNo?, tradeAmt?, message?}`

### Requirement 9: ECPay 查單 API

**GIVEN** 需要查詢某筆訂單的 ECPay 交易狀態
**WHEN** 呼叫 `EcpayTradeQueryService.queryTrade(orderNumber)`
**THEN** 建立參數 map：`MerchantID`, `MerchantTradeNo`, `TimeStamp`（epoch seconds）
**AND** 計算 CheckMacValue（演算法見 contract.md `EXT-003`）
**AND** 以 `application/x-www-form-urlencoded` POST 到 ECPay QueryTradeInfo API
**AND** 解析回應中的 `TradeStatus`（`"1"` = paid）、`TradeNo`、`TradeAmt`、`RtnMsg`/`TradeMsg`
**AND** 回傳 `QueryTradeResult(orderNumber, paid, tradeStatus, tradeNo, tradeAmount, message)`
**AND** 請求超時設為 15 秒

**Requirements**:
- [ ] R9.1 Stage endpoint: `https://payment-stage.ecpay.com.tw/Cashier/QueryTradeInfo/V5`
- [ ] R9.2 Prod endpoint: `https://payment.ecpay.com.tw/Cashier/QueryTradeInfo/V5`
- [ ] R9.3 HTTP 非 200 時回傳 `DomainError.unexpectedFailure("綠界查單失敗（HTTP {status}）")`
- [ ] R9.4 `InterruptedException`（在 TS 中為 AbortError/timeout）回傳專屬錯誤
- [ ] R9.5 orderNumber 空白時回傳 `DomainError.invalidInput("訂單編號不可為空")`

### Requirement 10: 兌換碼生成

**GIVEN** 管理員請求為某商品生成兌換碼
**WHEN** 呼叫 `RedemptionService.generateCodes(productId, count, expiresAt, quantity)`
**THEN** 驗證 count ∈ [1, 100]、quantity ∈ [1, 1000]、expiresAt 在未來
**AND** 驗證商品存在
**AND** 調用 `RedemptionCodeGenerator.generate()` 生成 16 字元代碼（字元集：ABCDEFGHJKMNPQRSTUVWXYZ23456789，排除 0/O/1/I/L）
**AND** 檢查代碼不與既有代碼重複（最多重試 10 次）
**AND** 批量持久化到資料庫
**AND** 發布 `RedemptionCodesGeneratedEvent`

**Requirements**:
- [ ] R10.1 `generateCodes(productId, count, expiresAt, quantity)` 回傳 `Result<List<RedemptionCode>, DomainError>`
- [ ] R10.2 單次最多生成 100 個（`MAX_BATCH_SIZE = 100`）
- [ ] R10.3 quantity 預設為 1，上限 1000
- [ ] R10.4 expiresAt 可為 null（無過期）
- [ ] R10.5 使用 `crypto.randomInt()`（Node.js）替代 Java `SecureRandom`
- [ ] R10.6 重複代碼時重試最多 10 次，超過拋出 `IllegalStateException`

### Requirement 11: 兌換碼兌換

**GIVEN** 使用者輸入兌換碼
**WHEN** 呼叫 `RedemptionService.redeemCode(codeStr, guildId, userId)`
**THEN** 代碼轉大寫並 trim
**AND** 驗證代碼存在、屬於該 guild、未被 redeemed、未被 invalidated、未過期
**AND** 驗證對應商品存在
**AND** 計算總獎勵金額（`rewardAmount × quantity`，使用 overflow 檢測）
**AND** `markAsRedeemedIfAvailable`（conditional UPDATE `WHERE redeemed_by IS NULL`，保證不重複兌換）
**AND** 若商品有獎勵，呼叫 `ProductRewardService.grantReward()`
**AND** 若獎勵發放失敗，執行 rollback（`clearRedeemedIfMatches`）
**AND** 記錄 `ProductRedemptionTransaction`
**AND** 發布 `ProductRedemptionCompletedEvent`
**AND** 回傳 `RedemptionResult`（含 maskedCode、product、rewardedAmount）

**Requirements**:
- [ ] R11.1 `redeemCode(codeStr, guildId, userId)` 回傳 `Result<RedemptionResult, DomainError>`
- [ ] R11.2 代碼不存在/guild 不匹配時回傳 `DomainError.invalidInput("兌換碼無效")`（不洩漏具體原因）
- [ ] R11.3 已失效（invalidated）時回傳 `DomainError.invalidInput("此兌換碼已失效")`
- [ ] R11.4 已使用時回傳 `DomainError.invalidInput("此兌換碼已被使用")`
- [ ] R11.5 已過期時回傳 `DomainError.invalidInput("此兌換碼已過期")`
- [ ] R11.6 `markAsRedeemedIfAvailable` 為 conditional UPDATE：`WHERE id = ? AND redeemed_by IS NULL`
- [ ] R11.7 markAsRedeemed 競爭失敗時回傳 `DomainError.invalidInput("此兌換碼已被使用或不可用")`
- [ ] R11.8 總獎勵計算使用 `Math.multiplyExact` 等價的 overflow 檢測
- [ ] R11.9 獎勵失敗 rollback：`clearRedeemedIfMatches(id, userId, redeemedAt)` → 成功時回傳 `unexpectedFailure("商品獎勵發放失敗，兌換已取消")`，失敗時回傳 `persistenceFailure("商品獎勵發放失敗，且兌換碼回復失敗")`
- [ ] R11.10 成功訊息格式：`你已成功兌換「{productName}」\n{description}\n\n已發放獎勵：{reward}`

### Requirement 12: 通知服務

**GIVEN** 各種需要通知的情境
**WHEN** 系統觸發通知
**THEN** **買家付款成功通知**：以 DM 發送付款成功訊息（商品名、訂單編號、超商代碼、金額）
**AND** **買家護航建立通知**：以 DM 發送護航訂單建立訊息（商品名、護航編號、付款方式）
**AND** **管理員新訂單通知**：向 guild 內所有 ADMINISTRATOR 成員 + guild owner 發送 DM（排除 bot 自身、去重）
**AND** 所有通知為 best-effort（失敗僅記錄 warn log，不拋出例外）

**Requirements**:
- [ ] R12.1 `FiatOrderBuyerNotificationService.notifyPaymentSucceeded(order)` 為 fire-and-forget
- [ ] R12.2 `EscortOrderBuyerNotificationService.notifyEscortOrderCreated(order)` 跳過 bot 自身
- [ ] R12.3 `ShopAdminNotificationService.notifyAdminsOrderCreated()` 排除 bot 自身、對重複管理員去重
- [ ] R12.4 管理員通知內容包含：伺服器名稱、買家 mention、商品名、訂單類型、訂單編號

## Error and Edge Cases

- [ ] 貨幣購買時餘額不足 → `INSUFFICIENT_BALANCE` 錯誤，不扣款
- [ ] 貨幣購買時獎勵發放失敗 → 自動退款，若退款也失敗則回傳雙重錯誤
- [ ] ECPay API 無法連線（HTTP 非 200、timeout）→ `DomainError.unexpectedFailure`
- [ ] ECPay TransCode 非 1（如 decrypt fail）→ 提示確認環境/金鑰對應
- [ ] ECPay callback payload 無法解密 → HTTP 400（`InvalidCallbackPayloadException`）
- [ ] ECPay callback 訂單不存在 → HTTP 200（靜默處理，避免 ECPay 重送）
- [ ] ECPay callback 重複（已 PAID 的訂單再次收到 callback）→ HTTP 200，markPaidIfPending 回傳 empty
- [ ] ECPay callback 在訂單逾期後才到達 → HTTP 200，markPaidIfPending 回傳 empty（status 不再是 PENDING_PAYMENT）
- [ ] post-payment worker claim 競爭 → 跳過（另一 worker instance 處理中）
- [ ] post-payment worker 中途失敗 → release lock，下次排程重試
- [ ] **Claim 方法含 5 分鐘 crash recovery timeout**：`claimFulfillmentProcessing`、`claimAdminNotificationProcessing`、`claimReconciliationProcessing` 的 WHERE 條件皆包含 `OR processing_at < now() - 5 minutes`，用於自動釋放因 worker crash 而僵死的鎖定。5 分鐘時間為 post-payment pipeline 最長執行時間（< 30 秒）的寬裕值，足以區分僵死鎖定與正常慢查詢。
- [ ] 對帳查單 API 失敗 → scheduleRetry（指數退避）
- [ ] 對帳查單時訂單已逾期 → markExpiredIfPending
- [ ] 兌換碼重複生成（10 次重試耗盡）→ `IllegalStateException`
- [ ] 兌換碼 concurrent redeem 競爭 → 只一人成功，其他人得到「已被使用」
- [ ] 兌換碼獎勵發放失敗 → rollback redeem 狀態
- [ ] MerchantTradeNo 同毫秒大量生成 → synchronized sequence 保證唯一
- [ ] ECPAY_STAGE_MODE=true 但使用官方 stage 金鑰而 stageMode=false → 拒絕並提示
- [ ] callback server 綁定公開位址但 stageMode=true → 拋出 `IllegalStateException` 阻止啟動
- [ ] FiatOrder 金額與 callback TradeAmt 不匹配 → 拒絕標記為 PAID（記錄 warn log + updateCallbackStatus）
- [ ] FiatOrder MerchantID 與 callback MerchantID 不匹配 → 拒絕標記為 PAID

## Clarification Questions

None — 所有需求已從 Java 原始碼完整理解。

## References

- Official docs:
  - ECPay 全方位金流 API 文件（GenPaymentCode、QueryTradeInfo、Callback 規範）
  - discord.js v14: EmbedBuilder, ActionRowBuilder, ButtonBuilder, StringSelectMenuBuilder, ModalBuilder
  - Node.js crypto 模組: `createDecipheriv`, `createHash`, `randomInt`
  - Express.js: `express()`, `express.json()`, `express.urlencoded()`
- Related Java files:
  - `src/main/java/ltdjms/discord/shop/domain/FiatOrder.java` (36-field record, 3-state machine)
  - `src/main/java/ltdjms/discord/shop/domain/FiatOrderRepository.java` (16 methods)
  - `src/main/java/ltdjms/discord/shop/persistence/JdbcFiatOrderRepository.java` (all SQL patterns)
  - `src/main/java/ltdjms/discord/shop/services/EcpayCvsPaymentService.java` (ECPay API integration)
  - `src/main/java/ltdjms/discord/shop/services/EcpayTradeQueryService.java` (CheckMacValue algorithm)
  - `src/main/java/ltdjms/discord/shop/services/FiatPaymentCallbackService.java` (AES decrypt callback)
  - `src/main/java/ltdjms/discord/shop/services/FiatOrderPostPaymentWorker.java` (claim/release pattern)
  - `src/main/java/ltdjms/discord/shop/services/FiatPaymentReconciliationService.java` (exponential backoff)
  - `src/main/java/ltdjms/discord/shop/services/FiatOrderProcessingScheduler.java` (10s/60s intervals)
  - `src/main/java/ltdjms/discord/shop/services/FiatOrderService.java` (order creation)
  - `src/main/java/ltdjms/discord/shop/services/CurrencyPurchaseService.java` (refund on reward failure)
  - `src/main/java/ltdjms/discord/shop/services/ShopService.java` + `ShopView.java` (browsing/search UI)
  - `src/main/java/ltdjms/discord/shop/services/EcpayCallbackHttpServer.java` (embedded HTTP server)
  - `src/main/java/ltdjms/discord/shop/services/FiatOrderBuyerNotificationService.java`
  - `src/main/java/ltdjms/discord/shop/services/EscortOrderBuyerNotificationService.java`
  - `src/main/java/ltdjms/discord/shop/services/ShopAdminNotificationService.java`
  - `src/main/java/ltdjms/discord/redemption/domain/RedemptionCode.java` (16-char code model)
  - `src/main/java/ltdjms/discord/redemption/services/RedemptionService.java` (generate + redeem)
  - `src/main/java/ltdjms/discord/redemption/services/RedemptionCodeGenerator.java` (SecureRandom generator)
  - `src/main/java/ltdjms/discord/product/domain/Product.java` (RewardType enum, factory methods)
  - `src/main/java/ltdjms/discord/product/services/ProductRewardService.java` (central reward dispatch)
