# Design: Shop and Payment

- Date: 2026-05-20
- Feature: Shop and Payment
- Change Name: shop-payment

## Traceability

|                             |                                                                              |
| --------------------------- | ---------------------------------------------------------------------------- |
| Requirement IDs             | R1 (Shop), R2 (Currency Purchase), R3 (FiatOrder), R4 (ECPay CVS), R5 (Callback), R6 (HTTP Server), R7 (PostPayment Worker), R8 (Reconciliation), R9 (Trade Query), R10 (Redemption Generate), R11 (Redemption Redeem), R12 (Notifications) |
| In-scope modules            | `packages/shop/src/domain/`, `packages/shop/src/persistence/`, `packages/shop/src/services/` |
| External systems touched    | ECPay 金流 API (GenPaymentCode, QueryTradeInfo, Callback ReturnURL) |
| Batch coordination          | `../coordination.md` |

## Target vs baseline

|                       | Baseline (Java) | Target (TypeScript) |
| --------------------- | --------------- | ------------------- |
| ECPay HTTP Client | `java.net.http.HttpClient` | `fetch` / `undici` |
| ECPay Crypto | `javax.crypto.Cipher` ("AES/CBC/PKCS5Padding") | `crypto.createDecipheriv` ("aes-128-cbc") |
| Callback HTTP Server | `com.sun.net.httpserver.HttpServer` (8 threads) | Express |
| Scheduler | `ScheduledExecutorService` (2 thread pool) | `setInterval` |
| FiatOrder model | Java `record` (36 fields) | TypeScript `type` + Zod validation |
| MerchantTradeNo gen | `synchronized` counter | Atomic counter or mutex |

## Boundaries

- Entry surface(s): Discord slash commands + button/select-menu interactions (via `@ltdjms/shared` Discord abstraction), HTTP callback (Express)
- Trust boundary crossed: ECPay external API (AES encryption keys, MerchantID)
- Outside → inside: `Discord User` → `Shop Interaction` → `CurrencyPurchaseService` / `FiatOrderService` / `RedemptionService`; `ECPay Server` → `Express callback route` → `FiatPaymentCallbackService` → `FiatOrderRepository`

## Modules

| Module key | Responsibility | Owned artifacts |
| ---------- | -------------- | --------------- |
| `shop-domain` | FiatOrder、RedemptionCode 領域模型 | Drizzle schema (fiat_order, redemption_code, product_redemption_transaction) |
| `shop-persistence` | Repository 實作（FiatOrderRepository, RedemptionCodeRepository） | Drizzle query functions |
| `ecpay-crypto` | ECPay AES 解密、CheckMacValue、URL 編碼 | Pure functions（無 side effect） |
| `ecpay-api` | ECPay API 呼叫（GenPaymentCode, QueryTradeInfo） | HTTP client |
| `callback-server` | Express HTTP server for ECPay callbacks | Express app |
| `payment-services` | 回調處理、履約 worker、對帳、排程 | FiatPaymentCallbackService, FiatOrderPostPaymentWorker, FiatPaymentReconciliationService |
| `shop-services` | 商店瀏覽、貨幣購買、兌換碼、通知 | ShopService, CurrencyPurchaseService, RedemptionService, notification services |

## Invariants

| Invariant | What breaks it | Symptoms if violated |
| --------- | -------------- | -------------------- |
| ECPay AES 解密輸出必須逐 byte 與 Java 一致 | 不同 padding 或 IV 處理 | 回調解密失敗、付款無法確認 |
| CheckMacValue 計算必須逐 byte 與 Java 一致 | URL 編碼怪癖再現錯誤 | ECPay 查單 API 拒絕請求 |
| markPaidIfPending 的 WHERE 條件不可變更 | 放寬條件 | 重複標記、狀態不一致 |
| Claim/release 的 atomicity 不可破損 | 移除 WHERE processing_at IS NULL guard | 同一訂單被多 worker 同時處理 |

## Tradeoffs

| Decision | Rejected alternative | Locks in |
| -------- | -------------------- | -------- |
| Express for callback server | Fastify (更輕但多一個依賴) | Express middleware pattern |
| `setInterval` for scheduler | node-cron (需額外依賴) | 簡單的 fixed-delay 排程 |
| Node.js `crypto` for ECPay | 第三方 AES library | Node.js 內建模組、無需額外套件 |
