# Design: Economy Business Invariant PBT

- Date: 2026-05-22
- Feature: Economy Business Invariant PBT
- Change Name: economy-pbt

## Traceability

|                             |                                                                              |
| --------------------------- | ---------------------------------------------------------------------------- |
| Requirement IDs             | R1.1–R1.4, R2.1–R2.3, R3.1–R3.2, R4.1–R4.3, R5.1–R5.3, R6.1–R6.4, R7.1–R7.2 |
| In-scope modules (≤3)       | `packages/economy/src/currency/`, `packages/economy/src/dice/`, `packages/economy/src/token/` |
| External systems touched    | None (僅 testcontainers PostgreSQL，已在 test-infra 定義)                    |
| Batch coordination          | `../coordination.md`                                                        |

## Target vs baseline

|                       | Baseline (today) | Target (after this change) |
| --------------------- | ---------------- | --------------------------- |
| Structure / ownership | 單元測試使用 mock DB + mock DI；測試與實作耦合低 | 5 個 Integration PBT 檔案，每個對應一類業務不變量；全部走真實 DB |

## Boundaries

- Entry surface(s): vitest test runner → PBT test files → Economy services (BalanceService, DiceGame1Service, DiceGame2Service, GameTokenService, CurrencyConfigService)
- Trust boundary crossed: `None`
- Outside → inside (one line): `PBT test` → `createTestContainer()` + `seed*()` → `BalanceService.transfer()` / `DiceGame1Service.play()` / etc → real PostgreSQL

## Modules (nouns only)

| Module key | Responsibility (one sentence) | Owned artifacts (types, tables, queues) |
| ---------- | ---------------------------- | ---------------------------------------- |
| `balanceTransferPbt` | 產生任意 guild + 用戶 + 金額組合，驗證餘額守恆 | `balance-transfer.pbt.test.ts` |
| `diceGame1Pbt` | 產生任意下注參數，驗證 DiceGame1 賠率正確 | `dice-game-1.pbt.test.ts` |
| `diceGame2Pbt` | 產生任意下注參數，驗證 DiceGame2 組合賠率正確 | `dice-game-2.pbt.test.ts` |
| `gameTokenPbt` | 產生任意 token 操作，驗證數量正確 | `game-token.pbt.test.ts` |
| `currencyConfigPbt` | 產生任意貨幣設定，驗證查詢回傳正確 | `currency-config.pbt.test.ts` |

---

## Interaction anchors (`INT-###`)

| ID        | Intent (when this coupling matters) | Caller → Callee | Coupling kind | Information / state crossing (summary) | Failure / propagation expectation (summary) |
| --------- | ------------------------------------ | --------------- | ---------------------------------------------------------------------------------------- | -------------------------------------- | ------------------------------------------- |
| `INT-001` | 所有 PBT 測試依賴 test-infra 的 DI container + seed factory | `PBT test` → `test-infra` | sync import | `createTestContainer()`, `seedGuild()`, `seedUserAccount()` | test-infra 不可用時所有測試 skip |
| `INT-002` | 餘額轉帳 PBT 直接呼叫 BalanceAdjustmentService | `PBT test` → `BalanceAdjustmentService` | sync call | `tryAdjustBalance()` 參數 → DB 餘額變更 | 服務層錯誤時 assert 驗證 DomainError |
| `INT-003` | DiceGame PBT 呼叫 GameRewardService (內部依賴 BalanceAdjustmentService) | `PBT test` → `DiceGame1Service` / `DiceGame2Service` | sync call | 下注參數 → DB 餘額變更 + token 扣減 | 遊戲邏輯錯誤時 assert 驗證 |

**Ordering / concurrency (design-level):** 五個 PBT 檔案可獨立執行（各自 seed 獨立資料）。每個 PBT 內 `beforeAll` reset DB → seed guild/users → `afterAll` 不需手動清理（下個 test file 的 `beforeAll` reset 會處理）。

## Requirement linkage (coarse ordering)

### R1-R3 (Balance Transfer) → R4-R5 (Dice Games) + R6 (Game Token) + R7 (Currency Config)
全部可並行開發。

- Anchor order hint: `INT-001` (共同前置) → `INT-002` / `INT-003` (各自獨立)
- Narrative glue:
  - 所有 PBT 共享 test-infra 的 container + seed factory
  - Balance Transfer 是最基礎的業務邏輯，建議先完成
  - DiceGame 依賴 GameRewardService（組合 BalanceAdjustmentService），在 Balance Transfer 驗證通過後更有信心
  - Game Token 和 Currency Config 完全獨立，可隨時進行

## Data & persistence (design-level)

| Resource                      | Typical readers/writers (module keys) | Consistency expectation (ordering, idempotency) |
| ----------------------------- | ------------------------------------- | ------------------------------------------------ |
| `currency_account` table      | BalanceAdjustmentService (write), BalanceService (read) | 每次轉帳為 atomic transaction |
| `currency_transaction` table  | CurrencyTransactionService (write)    | 每筆轉帳產生一筆 record，不可重複 |
| `currency_config` table       | CurrencyConfigService (read/write)    | Guild-level singleton config |
| `token_account` table         | GameTokenService (read/write)         | 每次調整為 atomic |
| `dice_config` table           | DiceConfigService (read/write)        | Guild-level singleton config |

## Invariants (system-level)

| Invariant | What breaks it architecturally | Symptoms if violated |
| --------- | ------------------------ | -------------------- |
| `sum(allBalances)` 守恆 | 轉帳時 sender 扣減但 receiver 未增加（或反之） | 總貨幣供應量漂移、經濟失衡 |
| 賠率正確: `payout = bet × multiplier` | 乘法計算使用錯誤精度或錯誤的 multiplier | 用戶獲得不正確的 payout |
| Token 不可為負 | remove 時未檢查上限 | 用戶 token 變負數 |

## Tradeoffs inherited by implementation

| Decision | Rejected alternative | Locks in (for **`tasks.md`**) |
| -------- | -------------------- | ---------------------------- |
| 直接測試 service 層而非 handler 層 | 測試 handler（需 mock Discord interaction） | 測試走 service→repository→DB 管線，跳過 Discord 層 |
| 每個 PBT 獨立 seed 而非共享 state | 共享 state 累積（PBT 需乾淨起點） | `beforeAll` reset + seed |

## Batch-only

Economy PBT 依賴 test-infra 建立的 `createTestContainer`、`seedGuild`、`seedUserAccount`、`positiveAmount` arbitrary。不可修改這些介面。
