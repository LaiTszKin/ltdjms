# Contract: Shop and Payment

- Date: 2026-05-20
- Feature: Shop and Payment
- Change Name: shop-payment

> **Purpose:** **High-level external-dependency context for `tasks.md`**: cite-backed facts, limits, failures, security—so integrations are not hallucinated. **Not** a runnable checklist; **`tasks.md` executes** wiring (files, calls, mocks, tests). Internal coupling intent stays in **`design.md`** (`INT-###`).
>
> **Anti-duplication:** Do not enumerate per-file edits, checkbox steps, or copy task ordering. **`EXT-###`** are **constraints / anchors** that task rows may cite.
>
> **Undocumented gaps:** **`TBD`** + clarification—never invent payloads, endpoints, or semantics.

## Scope

- **External deps in this doc:** 2（ECPay 金流 API + Express.js HTTP framework）

## Dependencies

### ECPay 全方位金流 API（CVS 繳費、交易查詢、回呼通知）

#### Evidence

| Primary docs URL(s)             | Sections / anchors used |
| ------------------------------- | ----------------------- |
| https://developers.ecpay.com.tw/ | CVS 超商代碼繳費（GenPaymentCode）、交易查詢（QueryTradeInfo V5）、Server 端回呼（ReturnURL）、加密機制（AES/CBC/PKCS5Padding）、檢查碼（CheckMacValue） |

**Version revision assumed:** Not fixed — ECPay API 版本以 endpoint path 中的版本號標示（`/1.0.0/`、`/V5`）。實作以 Java 原版使用的端點為準。

#### Facts we rely on (must be citeable)

| Fact / capability needed | Doc location |
| ------------------------ | ------------ |
| CVS 繳費代碼產生 API endpoint（stage: `https://ecpayment-stage.ecpay.com.tw/1.0.0/Cashier/GenPaymentCode`, prod: `https://ecpayment.ecpay.com.tw/1.0.0/Cashier/GenPaymentCode`） | ECPay API 文件 — CVS 章節 |
| 交易查詢 API endpoint（stage: `https://payment-stage.ecpay.com.tw/Cashier/QueryTradeInfo/V5`, prod: `https://payment.ecpay.com.tw/Cashier/QueryTradeInfo/V5`） | ECPay API 文件 — 交易查詢章節 |
| Request body 加密格式：JSON `{MerchantID, RqHeader: {Timestamp}, Data: "<AES-encrypted base64>"}` | ECPay API 文件 — 通用規格 |
| AES 加密規格：`AES/CBC/PKCS5Padding`，key = HashKey（UTF-8 bytes），iv = HashIV（UTF-8 bytes） | ECPay API 文件 — 資料加密章節 |
| AES 加密/解密前需對 plaintext/ciphertext 做 URL encoding/decoding 處理 | ECPay API 文件 — 通用規格（Java 原版實作確認） |
| Server 端回呼（ReturnURL）payload 格式：`{MerchantID, RqHeader, Data: "<AES-encrypted base64>"}` 或 form-urlencoded `Data=...` | ECPay API 文件 — 回呼機制章節 |
| CheckMacValue 計算流程：`HashKey={key}&{sorted params}&HashIV={iv}` → URL encode → 特定字元替代 → SHA-256 → 大寫 hex | ECPay API 文件 — 檢查碼章節（Java 原版實作確認替代規則） |
| TradeStatus `"1"` = 已付款 | ECPay API 文件 — 交易狀態碼 |
| 查單 API 使用 `application/x-www-form-urlencoded` POST | ECPay API 文件 — 交易查詢章節 |

#### Limits & failures (coding obligations)

| Category                         | Doc fact | Meaning while executing **`tasks.md`** |
| -------- | --------- | ---------------------------------------- |
| 請求超時 | 無官方規定，Java 原版設為 15 秒 | `fetch()` 使用 `AbortSignal.timeout(15000)` |
| HTTP 非 200 | ECPay API 回傳 HTTP 200 以外的狀態碼 | 回傳 `DomainError.unexpectedFailure`，由 caller 決定重試策略 |
| TransCode != 1 | ECPay GenPaymentCode API 層級錯誤（如解密失敗、金鑰錯誤） | 回傳 `DomainError.unexpectedFailure`，若訊息含 "decrypt fail" 提示環境/金鑰不匹配 |
| RtnCode != 1 | ECPay 業務層級錯誤（如重複 MerchantTradeNo） | 回傳 `DomainError.unexpectedFailure`，不重試 |
| CheckMacValue 不匹配 | 查單 API 回應中的 CheckMacValue 與本地計算不符 | Java 原版不驗證回應的 CheckMacValue（僅計算請求的），TBD 是否加入 |
| Server 回呼可能重送 | ECPay 在未收到 "1|OK" 時會重送 callback | `markPaidIfPending` conditional UPDATE 保證 idempotent；未付款狀態僅 updateCallbackStatus 不觸發轉換 |
| 官方 Stage 金鑰 | MerchantID=3002607, HashKey=pwFHCqoQZGmho4w6, HashIV=EkRm7iFT261dpevs | 用於 stage 測試；`ECPAY_STAGE_MODE=false` 時若仍使用此金鑰則拒絕請求 |
| CVS 過期時間 | ECPay 接受 1~43200 分鐘（30 天） | 輸入 clamp 到此範圍 |

#### Security & secrets (policy level)

| Concern           | Constraint |
| ----------------- | ---------- |
| Auth / scopes     | ECPay 使用 MerchantID + HashKey + HashIV 進行 API 認證。不支援 OAuth 或 Bearer token。 |
| Secret keys (names)| `ECPAY_HASH_KEY`（32 字元）、`ECPAY_HASH_IV`（16 字元）、`ECPAY_MERCHANT_ID`（7-10 位數字）。透過 `@ltdjms/shared` 的 `Config` 注入。 |
| Callback security | ECPay callback 必須驗證解密後的 MerchantID 與設定值一致、TradeAmt 與訂單 amountTwd 一致。不依賴 IP whitelist。 |
| Stage 模式保護 | `ECPAY_STAGE_MODE=true` 時 callback server 強制綁定 localhost（禁止公開位址），防止 stage 環境接收外部請求。 |

#### Integration anchors (`EXT-###`)

**Grain:** Boundary truth + obligations—**fewer anchors than typical task rows**. Multiple checkboxes often satisfy one anchor.

| ID        | What we integrate at this boundary *(doc-named surface)* | Non‑negotiables (handling, retries, idempotency *per doc*) | Forbidden assumptions |
| --------- | ---------------------------------------------------------- | ----------------------------------------------------------- | --------------------- |
| `EXT-001` | **ECPay GenPaymentCode API** — `POST {base}/1.0.0/Cashier/GenPaymentCode`，JSON body `{MerchantID, RqHeader: {Timestamp}, Data}` | 1. Request Data 必須是 `AES/CBC/PKCS5Padding` 加密的 URL-encoded JSON（key=HashKey, iv=HashIV）。2. 加密前先 `encodeURIComponent(plainJson)`。3. 回應 Data 解密後 `decodeURIComponent`。4. TransCode=1 且 RtnCode=1 才算成功。5. 官方 stage 金鑰在 prod 模式下拒絕使用。6. cvsexpireMinutes clamp 到 [1, 43200]。 | 不可假設 ECPay 一定回傳 ExpireDate（fallback 用 requestAt + expireMinutes）。不可假設 ECPay 回應 encoding 與請求相同。 |
| `EXT-002` | **ECPay QueryTradeInfo API** — `POST {base}/Cashier/QueryTradeInfo/V5`，form-urlencoded body `MerchantID, MerchantTradeNo, TimeStamp, CheckMacValue` | 1. CheckMacValue 必須依循 ECPay 特定 URL 編碼規則（見下方演算法）。2. TradeStatus="1" 才視為已付款。3. TimeStamp 為 epoch seconds。4. 15 秒 timeout。 | 不可假設 ECPay 回應一定包含 TradeNo 或 TradeAmt。不可假設查單一定能找到訂單（新訂單可能尚未同步）。 |
| `EXT-003` | **ECPay CheckMacValue 演算法** — SHA-256 簽章用於 QueryTradeInfo 請求驗證 | **完整演算法（必須逐 byte 與 Java 一致）**：<br>1. 建立 `HashKey={key}&{param1}={val1}&{param2}={val2}&...&HashIV={iv}` 字串（params 按 key 字母序排列，僅包含非空值）。<br>2. `encodeURIComponent()` 整串，轉小寫。<br>3. **ECPay 特有替代規則**（在 URL encode 之後）：`%2d` → `-`、`%5f` → `_`、`%2e` → `.`、`%21` → `!`、`%2a` → `*`、`%28` → `(`、`%29` → `)`、`%20` → `+`、`%7e` → `~`。<br>4. 對替代後的字串做 SHA-256 hash。<br>5. 輸出大寫 hex 字串。 | 不可使用標準 URL encoding 直接做 hash（必須先做 ECPay 特有替代）。不可遺漏任何一個替代規則。不可改變步驟順序（URL encode → 轉小寫 → 替代 → hash → 大寫 hex）。 |
| `EXT-004` | **ECPay Server 端回呼 (ReturnURL)** — ECPay 對 callback URL 發送 POST，body 含加密 Data | 1. Body 格式為 JSON 或 form-urlencoded（由 Content-Type 判斷）。2. Data 欄位為 AES-encrypted base64。3. 解密後為 URL-encoded query string → 需 `decodeURIComponent`。4. 回呼必須回傳 `1|OK`（成功）或 `0|FAIL`（失敗）。5. 不回傳錯誤給 ECPay 做為防禦（訂單不存在、重複 callback 皆回 200）。6. MerchantID 和 TradeAmt 驗證失敗時不標記 PAID，但仍回傳 200。 | 不可假設 callback body 一定是 JSON（ECPay 可能送 form-urlencoded）。不可假設 callback 中的 MerchantTradeNo 一定在頂層（可能在 OrderInfo 巢狀結構中）。不可假設 callback payload 長度一定小於 4000。 |

**Doc-level ordering constraint (if any):** `EXT-003` (CheckMacValue) → `EXT-002` (QueryTradeInfo 依賴 CheckMacValue)；`EXT-001` (GenPaymentCode) 與 `EXT-004` (Callback) 依賴 AES 加解密共用邏輯。

#### Trace hooks (no task parroting)

- Spec IDs covered: R4 (Fiat Order + ECPay GenPaymentCode)、R5 (Callback)、R6 (HTTP Server)、R8 (Reconciliation)、R9 (Trade Query)
- Related **`design.md`** module keys / `INT-###`: ecpay-crypto (`INT-305`, `INT-306`, `INT-311`), ecpay-client (`INT-304`, `INT-310`), fiat-callback (`INT-307`), web-server
- **Unknown / `TBD`:** `None` — 所有 ECPay API 行為已從 Java 原版程式碼完整確認。

---

### Express.js HTTP Framework

#### Evidence

| Primary docs URL(s)             | Sections / anchors used |
| ------------------------------- | ----------------------- |
| https://expressjs.com/en/4x/api.html | `express()`, `express.json()`, `express.urlencoded()`, `app.post()`, `app.get()`, `app.listen()` |

**Version revision assumed:** Express 4.x（`package.json` 中以 `^4.18.0` 鎖定）

#### Facts we rely on (must be citeable)

| Fact / capability needed | Doc location |
| ------------------------ | ------------ |
| HTTP POST route with JSON and URL-encoded body parsing | `express.json()`, `express.urlencoded()` middleware |
| Static file serving for landing page | `express.static()` or manual `res.sendFile()` |
| Body size limiting | `express.json({ limit: '64kb' })`, `express.urlencoded({ limit: '64kb' })` |
| Server start/stop for lifecycle management | `app.listen(port, host)` returns `http.Server`；`server.close()` |

#### Limits & failures (coding obligations)

| Category                         | Doc fact | Meaning while executing **`tasks.md`** |
| -------- | --------- | ---------------------------------------- |
| Body size limit | Express 預設 `json` limit 為 100kb，`urlencoded` limit 為 100kb | 明確設定為 64KB（與 Java `MAX_CALLBACK_BODY_BYTES = 64 * 1024` 一致） |
| Payload too large | Express 回傳 HTTP 413 | 對齊 Java 的 `PayloadTooLargeException` → HTTP 413 + "Payload Too Large" |
| Port already in use | `app.listen()` 拋出 `EADDRINUSE` error | 包裝為 `IllegalStateException`（與 Java 一致） |

#### Security & secrets (policy level)

| Concern           | Constraint |
| ----------------- | ---------- |
| Auth / scopes    | 無 — 此伺服器僅接收 ECPay callback，不驗證 client auth（安全性依賴 AES 解密 + MerchantID/amount 驗證） |
| Secret keys (names)| 無直接使用 — 加密金鑰由 `FiatPaymentCallbackService` 從 `Config` 讀取 |

#### Integration anchors (`EXT-###`)

| ID        | What we integrate at this boundary *(doc-named surface)* | Non‑negotiables (handling, retries, idempotency *per doc*) | Forbidden assumptions |
| --------- | ---------------------------------------------------------- | ----------------------------------------------------------- | --------------------- |
| `EXT-005` | **Express HTTP Server** — `app.post(callbackPath, handler)`, `app.get('/', landingHandler)`, `app.listen(port, host)` | 1. callback 路徑僅接受 POST。2. 根路徑僅接受 GET/HEAD。3. 64KB body limit。4. Host/Port 從 Config 讀取（預設 127.0.0.1:8085）。5. callback 路徑不可與根路徑 `/` 衝突。6. Stage mode 強制 localhost 綁定。 | 不可假設 server 永遠在 8085 port 啟動（應從 config 讀取）。不可假設 callback path 永遠是 `/ecpay/callback`（應從 config 讀取）。 |

#### Trace hooks (no task parroting)

- Spec IDs covered: R6 (HTTP Server)
- Related **`design.md`** module keys / `INT-###`: web-server
- **Unknown / `TBD`:** `None`
