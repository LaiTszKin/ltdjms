# Design: ECPay Payment E2E Tests

- Date: 2026-05-22
- Feature: ECPay Payment E2E Tests
- Change Name: ecpay-e2e

## Traceability

|                             |                                                                              |
| --------------------------- | ---------------------------------------------------------------------------- |
| Requirement IDs             | R1.1–R1.4, R2.1–R2.5, R3.1–R3.3, R4.1–R4.4, R5.1–R5.3                       |
| In-scope modules (≤3)       | `packages/shop/src/services/` (ecpay-cvs, fiat-payment-callback, reconciliation), `packages/shop/src/crypto/` |
| External systems touched    | ECPay Stage API (CVS payment, TradeQuery)                                    |
| Batch coordination          | `../coordination.md`                                                        |

## Target vs baseline

|                       | Baseline (today) | Target (after this change) |
| --------------------- | ---------------- | --------------------------- |
| Structure / ownership | 僅有單元測試（mock ECPay API）；無真實 API 整合測試 | 5 個 E2E test files，使用真實 ECPay Stage API + MerchantID 2000132 |

## Boundaries

- Entry surface(s): vitest → ECPay services → real HTTPS calls to ECPay Stage API
- Trust boundary crossed: Network boundary（HTTP 呼叫外部 ECPay Stage 伺服器，測試商戶無敏感資料）
- Outside → inside (one line): `PBT test` → `EcpayCvsPaymentService.createPaymentForm()` → HTTPS POST to `https://payment-stage.ecpay.com.tw/Cashier/AioCheckOut/V5`

## Modules (nouns only)

| Module key | Responsibility (one sentence) | Owned artifacts (types, tables, queues) |
| ---------- | ---------------------------- | ---------------------------------------- |
| `cvsE2e` | 實際呼叫 ECPay Stage API 取號 | `ecpay-cvs-e2e.test.ts` |
| `callbackE2e` | 模擬回撥 payload → POST callback server → 驗證 DB | `ecpay-callback-e2e.test.ts` |
| `reconciliationE2e` | 觸發對帳排程 → 驗證 TradeQuery + 狀態同步 | `ecpay-reconciliation-e2e.test.ts` |
| `cryptoE2e` | 加解密 round-trip 與 golden data 交叉驗證 | `ecpay-crypto-e2e.test.ts` |

---

## Interaction anchors (`INT-###`)

| ID        | Intent | Caller → Callee | Coupling kind | Information crossing | Failure expectation |
| --------- | ------ | --------------- | ------------- | -------------------- | ------------------- |
| `INT-001` | 所有 E2E 測試需要在 testcontainer PostgreSQL 中準備 seed data | `E2E test` → `test-infra` → `shop DI` | sync import/call | DB pool + seed factory → ECPay service instances | test-infra 不可用時 skip |
| `INT-002` | CVS E2E 呼叫 EcpayCvsPaymentService（內含 HTTP POST 到 ECPay） | `E2E test` → `EcpayCvsPaymentService` → ECPay Stage API | HTTPS POST | 取號請求 → ECPay 回傳 paymentNo | 網路逾時最多 3 次 retry 後 skip |
| `INT-003` | Callback E2E 直接 POST callback server + 驗證 DB state | `E2E test` → callback server HTTP → `FiatPaymentCallbackService` → DB | HTTP POST (local) | 加密 payload → DB 訂單狀態更新 | CheckMacValue 錯誤時 400 |
| `INT-004` | Reconciliation E2E 呼叫 TradeQuery → ECPay Stage API | `E2E test` → `FiatPaymentReconciliationService` → ECPay TradeQuery API | HTTPS POST | 查詢請求 → ECPay 回傳交易狀態 | API 不可用時 skip |

**Ordering / concurrency (design-level):** 五個 E2E test files 可獨立執行。CVS 取號 → Callback 回撥有隱含順序（需先取號才能模擬回撥），但 callback test 可手動建構 orderNumber 而不依賴取號結果。

## Requirement linkage (coarse ordering)

### R1 (CVS 取號) → R2-R3 (回撥) → R4 (對帳) + R5 (加解密)
- Anchor order hint: `INT-002` → `INT-003` → `INT-004`
- Narrative glue:
  - CVS 取號是最基礎的外部 API 整合，先確認加密/簽章正確
  - 回撥測試依賴相同的 AES 加解密模組
  - 對帳和加解密可與回撥並行開發

## Data & persistence (design-level)

| Resource                      | Typical readers/writers | Consistency expectation |
| ----------------------------- | ----------------------- | ----------------------- |
| `fiat_order` table            | E2E tests (seed) + ECPay services (read/write) | 每 test 前 reset |
| ECPay Stage API state         | EcpayCvsPaymentService (write via POST), EcpayTradeQueryService (read via POST) | 外部系統，不可控 |

## Invariants (system-level)

| Invariant | What breaks it architecturally | Symptoms if violated |
| --------- | ------------------------ | -------------------- |
| CheckMacValue 正確性 | AES key/IV 設定錯誤、SHA-256 實現錯誤 | ECPay API 回傳簽章錯誤 |
| 回撥冪等性 | FiatPaymentCallbackService 未檢查已 PAID 狀態 | 重複回撥造成重複 fulfillment |

## Tradeoffs inherited by implementation

| Decision | Rejected alternative | Locks in (for **`tasks.md`**) |
| -------- | -------------------- | ---------------------------- |
| 使用真實 ECPay Stage API | 使用 mock HTTP server | 需 `RUN_ECPAY_E2E=true` 環境變數閘門 |
| SimulatePaid=1 不加入檢查 | 加入檢查邏輯 | SimulatePaid flag 僅在 spec 記錄，不實作 |

## Batch-only

ECPay E2E 依賴 test-infra 的 testcontainer PostgreSQL + seed factory，以及 shop module 的 DI 配置。
