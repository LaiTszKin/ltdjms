# Contract: ECPay Payment E2E Tests

- Date: 2026-05-22
- Feature: ECPay Payment E2E Tests
- Change Name: ecpay-e2e

## Scope

- **External deps in this doc:** 1
- ECPay Stage API — 綠界測試環境，用於 CVS 取號、交易查詢

## Dependencies

### ECPay Stage API

#### Evidence

| Primary docs URL(s)             | Sections / anchors used |
| ------------------------------- | ----------------------- |
| https://developers.ecpay.com.tw/ | CVS 超商繳費 API、CheckMacValue 驗證、AES 加解密 |
| https://www.ecpay.com.tw/Content/files/ecpay_cvs.pdf | API endpoint、參數格式、回傳值 |

**Version revision assumed:** Stage API (無版本號，Stage 環境隨正式環境更新)

#### Facts we rely on (must be citeable)

| Fact / capability needed | Doc location |
| ------------------------ | ------------ |
| CVS 取號 endpoint: `https://payment-stage.ecpay.com.tw/Cashier/AioCheckOut/V5` | ecpay_cvs.pdf § 應用端介接規格 |
| TradeQuery endpoint: `https://payment-stage.ecpay.com.tw/Cashier/QueryTradeInfo/V5` | 綠界開發者文件 — 交易查詢 |
| CheckMacValue = SHA-256(HashKey + params + HashIV) | ecpay_cvs.pdf § 檢查碼機制 |
| AES-256-CBC 加密，padding: PKCS7 | 綠界開發者文件 — 資料加密 |
| SimulatePaid=1 標記模擬付款（測試環境專用） | 綠界測試後台文件 |

#### Limits & failures (coding obligations)

| Category                         | Doc fact | Meaning while executing **`tasks.md`** |
| -------- | --------- | ---------------------------------------- |
| 請求逾時 | Stage API SLA 無保證 | 最多 retry 3 次，每次 timeout 10s；3 次失敗則 skip |
| 維護時段 | ECPay 公告維護時段 | 若連線失敗不是 timeout 而是 connection refused，skip 並記錄 |
| 測試商戶限制 | 2000132 為測試特店，僅能在 Stage 使用 | 不可在 production 環境執行 E2E 測試 |

#### Security & secrets (policy level)

| Concern           | Constraint |
| ----------------- | ---------- |
| MerchantID / HashKey / HashIV | `2000132` / `ejCk326UnaZWKisg` / `q9jcZX8Ib9LM8wYk`（測試憑證，非機密） |
| 不可使用正式商戶憑證 | 透過 `ECPAY_STAGE_MODE=true` 環境變數控制 |

#### Integration anchors (`EXT-###`)

| ID        | What we integrate at this boundary | Non‑negotiables | Forbidden assumptions |
| --------- | ---------------------------------------------------------- | ----------------------------------------------------------- | --------------------- |
| `EXT-001` | `POST https://payment-stage.ecpay.com.tw/Cashier/AioCheckOut/V5` (CVS 取號) | 必須附正確 CheckMacValue；回傳 HTML form 需 parse | 不可假設 paymentNo 長度固定 |
| `EXT-002` | `POST https://payment-stage.ecpay.com.tw/Cashier/QueryTradeInfo/V5` (交易查詢) | 必須附正確 CheckMacValue；回傳 key=value 格式 | 不可假設所有交易都可查到 |
| `EXT-003` | Callback server `POST /ecpay/callback` (本機) | 需 AES 解密 payload → 驗證 CheckMacValue → 回傳 "1|OK" 或 "0|FAIL" | 不可假設 callback 順序 |

**Doc-level ordering constraint:** `EXT-001` (取號) 應先於 `EXT-003` (回撥) 驗證，以確保加解密模組正確。

#### Trace hooks (no task parroting)

- Spec IDs covered: R1.1–R1.4, R2.1–R2.5, R3.1–R3.3, R4.1–R4.4, R5.1–R5.3
- Related **`design.md`** module keys: cvsE2e, callbackE2e, reconciliationE2e, cryptoE2e
- **Unknown / `TBD`:** None
