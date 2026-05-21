# Tasks: Guild Economy

- Date: 2026-05-20
- Feature: Guild Economy

## Task 1: Drizzle Schema 定義

Purpose: 定義所有貨幣/代幣/骰子遊戲相關的 Drizzle schema，確保與 Java Flyway migration SQL 完全一致。
Requirements: R1, R2, R3, R4, R5, R6
Scope: `packages/economy/src/currency/domain/schema.ts`、`packages/economy/src/token/domain/schema.ts`、`packages/economy/src/dice/domain/schema.ts`
Out of scope: Repository 實作

- T1.1 [ ] **`packages/economy/src/currency/domain/schema.ts`** — 定義 `guildCurrencyConfig`、`memberCurrencyAccount`、`currencyTransaction` 三個 Drizzle pgTable。欄位名稱、型別、constraint 必須與對應的 Flyway migration SQL 完全一致（含 `balance BIGINT NOT NULL DEFAULT 0 CHECK (balance >= 0)`）
  - Verify: 比對 Drizzle schema 與 `V001__baseline.sql` + `V004-V009` migration SQL → 所有欄位一致

- T1.2 [ ] **`packages/economy/src/token/domain/schema.ts`** — 定義 `gameTokenAccount`、`gameTokenTransaction` pgTable
  - Verify: 比對 schema 與 migration SQL → 一致

- T1.3 [ ] **`packages/economy/src/dice/domain/schema.ts`** — 定義 `diceGame1Config`、`diceGame2Config` pgTable
  - Verify: 比對 schema 與 migration SQL → 一致

## Task 2: 貨幣 Domain Models 與 Repository

Purpose: 移植貨幣領域模型與 repository 實作。
Requirements: R1.1-R1.4, R2.1-R2.4
Scope: `packages/economy/src/currency/domain/`、`packages/economy/src/currency/persistence/`

- T2.1 [ ] **`packages/economy/src/currency/domain/types.ts`** — 定義 `GuildCurrencyConfig`、`MemberCurrencyAccount`、`CurrencyTransaction`、`BalanceView` TypeScript type。移植 Java record 的 compact constructor 驗證邏輯
  - Verify: 單元測試驗證 validation rules（名稱 ≤50、圖標 ≤64、balance ≥0、non-null 欄位）

- T2.2 [ ] **`packages/economy/src/currency/persistence/currency-account-repository.ts`** — 實現 `CurrencyAccountRepository`: `findOrCreate(guildId, userId)`、`adjustBalance(guildId, userId, delta)`（使用 `balance + ${delta} >= 0` WHERE guard + RETURNING）、`setBalance()`、`delete()`
  - Verify: 整合測試（對接真實 PostgreSQL）：adjustBalance 成功 → 餘額正確；negative balance → 0 rows affected

- T2.3 [ ] **`packages/economy/src/currency/persistence/currency-config-repository.ts`** — 實現 `CurrencyConfigRepository`: `findByGuildId()`、`saveOrUpdate()`（upsert）、`deleteByGuildId()`
  - Verify: 整合測試：upsert 成功、findByGuildId 回傳正確 config

- T2.4 [ ] **`packages/economy/src/currency/persistence/currency-transaction-repository.ts`** — 實現 `CurrencyTransactionRepository`: `save()`、`findByGuildIdAndUserId()`（含 limit/offset 分頁）、`count()`、`delete()`
  - Verify: 整合測試：save → find 回傳正確；分頁 offset/limit 正確

## Task 3: 貨幣服務層

Purpose: 移植 BalanceService、BalanceAdjustmentService、CurrencyConfigService、CurrencyTransactionService。
Requirements: R1.1-R1.4, R2.1-R2.4, R3.1-R3.3
Scope: `packages/economy/src/currency/services/`

- T3.1 [ ] **`packages/economy/src/currency/services/balance-service.ts`** — 實現 `BalanceService`: `getBalance(guildId, userId)` → 自動建立帳戶 → 讀取 guild currency config → 組合 BalanceView。Cache: get 時檢查 Redis → miss 時查 DB 並 cache（TTL 300s）
  - Verify: 單元測試（mock repository + cache）：首次查詢 → 自動建立帳戶、查詢 cache → DB 不應被呼叫

- T3.2 [ ] **`packages/economy/src/currency/services/balance-adjustment-service.ts`** — 實現 `BalanceAdjustmentService`: `adjustBalance(guildId, userId, delta, source, description)` → overflow 檢測 → `repository.adjustBalance()` → 記錄 transaction → 發布 `BalanceChangedEvent` → 更新快取
  - Verify: 單元測試：deduct 成功、deduct 不足回傳 Err、overflow 回傳 Err、事件已發布

- T3.3 [ ] **`packages/economy/src/currency/services/currency-config-service.ts`** — 實現 `CurrencyConfigService`: `getConfig(guildId)`、`updateConfig(guildId, name, icon)` → 驗證長度 + emoji → upsert → 發布 `CurrencyConfigChangedEvent`
  - Verify: 單元測試：名稱超長 → Err、有效設定 → 成功 + 事件發布

- T3.4 [ ] **`packages/economy/src/currency/services/currency-transaction-service.ts`** — 實現 `CurrencyTransactionService`: `getTransactionPage(guildId, userId, page)`、`recordTransaction(guildId, userId, amount, balanceAfter, source, description)`
  - Verify: 單元測試：分頁正確

## Task 4: 遊戲代幣 Domain + Repository + Service

Purpose: 移植遊戲代幣的完整 stack（對稱於貨幣系統）。
Requirements: R4.1-R4.4
Scope: `packages/economy/src/token/`

- T4.1 [ ] **`packages/economy/src/token/domain/types.ts`** — 定義 `GameTokenAccount`、`GameTokenTransaction` type
  - Verify: 單元測試驗證 validation

- T4.2 [ ] **`packages/economy/src/token/persistence/token-account-repository.ts`** — 實現 `TokenAccountRepository`: `findOrCreate`、`adjustTokens`（`tokens + ${delta} >= 0`）、`tryAdjustTokens`（回傳 Result）、`setTokens`、`delete`
  - Verify: 整合測試

- T4.3 [ ] **`packages/economy/src/token/persistence/token-transaction-repository.ts`** — 實現 `TokenTransactionRepository`: `save`、`findByGuildIdAndUserId`（分頁）、`count`、`delete`
  - Verify: 整合測試

- T4.4 [ ] **`packages/economy/src/token/services/game-token-service.ts`** — 實現 `GameTokenService`: `getBalance`、`adjustTokens`、`hasEnoughTokens`、`deductTokens`、`tryAdjustTokens`、`tryDeductTokens`。Cache 300s。發布 `GameTokenChangedEvent`
  - Verify: 單元測試：deduct 成功、不足 → Err、事件發布

- T4.5 [ ] **`packages/economy/src/token/services/game-token-transaction-service.ts`** — 實現交易記錄查詢
  - Verify: 單元測試

## Task 5: 骰子遊戲 Domain + Repository

Purpose: 移植骰子遊戲配置與 repository。
Requirements: R5, R6
Scope: `packages/economy/src/dice/`

- T5.1 [ ] **`packages/economy/src/dice/domain/types.ts`** — 定義 `DiceGame1Config`、`DiceGame2Config` type。預設值: minTokens=1/maxTokens=10/rewardPerDice=250000 (game1)、minTokens=5/maxTokens=50/straightMultiplier=100000/baseMultiplier=20000/tripleLowBonus=1500000/tripleHighBonus=2500000 (game2)
  - Verify: 單元測試：預設值與 Java 一致

- T5.2 [ ] **`packages/economy/src/dice/persistence/dice-game-config-repository.ts`** — 實現 `DiceGame1ConfigRepository` 和 `DiceGame2ConfigRepository`（findOrCreateDefault、update、delete）
  - Verify: 整合測試

## Task 6: 骰子遊戲邏輯服務

Purpose: 移植兩個骰子遊戲的核心邏輯——這是移植正確性最關鍵的部分。
Requirements: R5.1-R5.4, R6.1-R6.4
Scope: `packages/economy/src/dice/services/`

- T6.1 [ ] **`packages/economy/src/dice/services/dice-game-1-service.ts`** — 實現 `DiceGame1Service`: `play(guildId, userId, tokenCount, random)` → 扣除 tokens → 記錄 token transaction → 擲 N 顆骰子（`random.nextInt(1, 7)`）→ 獎勵 = sum × rewardPerDice → 呼叫 `GameRewardService.creditReward()`
  - Verify: 單元測試（seeded random, e.g. seed=42）: tokenCount=3 → 固定骰子結果 → 獎勵金額與 Java 版本完全一致（需要先用 Java 跑一次取得 golden value）

- T6.2 [ ] **`packages/economy/src/dice/services/dice-game-2-service.ts`** — 實現 `DiceGame2Service`: `play(guildId, userId, tokenCount, random)`。三階段分析: (1) 找 straight（連續遞增 ≥3）→ (2) 在剩餘骰子中找 triple（恰好 3 相同）→ (3) 剩餘骰子 × baseMultiplier
  - Verify: 單元測試（seeded random）: 獎勵三項明細與 Java 版本完全一致（使用 Java golden value 對比）
  - Verify: Property-based test: 對隨機骰子序列，TypeScript 和 Java 產出相同獎勵總額（需要建立 cross-check harness）

- T6.3 [ ] **`packages/economy/src/dice/services/game-reward-service.ts`** — 實現 `GameRewardService`: `creditReward(guildId, userId, amount, source, description)` → 若 amount > MAX_ADJUSTMENT_AMOUNT → 分割為多筆 `adjustBalance` 呼叫
  - Verify: 單元測試：大額獎勵分割測試

## Task 7: Slash Command Handlers

Purpose: 移植 7 個 slash command handler，確保 Discord 用戶體驗與 Java 完全一致。
Requirements: R1, R3, R5, R6
Scope: `packages/economy/src/commands/`

- T7.1 [ ] **`packages/economy/src/commands/dice-game-1-handler.ts`** — `/dice-game-1` handler: 驗證 token 範圍 → 呼叫 DiceGame1Service → 格式化結果（zh-TW dice emoji `:one:` ~ `:six:`）→ reply embed
  - Verify: 整合測試（mock Discord interaction）：驗證 embed 格式與 Java 輸出一致

- T7.2 [ ] **`packages/economy/src/commands/dice-game-2-handler.ts`** — `/dice-game-2` handler
  - Verify: 整合測試

- T7.3 [ ] **`packages/economy/src/commands/currency-config-handler.ts`** — `/currency-config` handler（admin only）
  - Verify: 整合測試

- T7.4 [ ] **`packages/economy/src/commands/dice-config-handlers.ts`** — `/dice-game-1-config` + `/dice-game-2-config` handlers（admin only）
  - Verify: 整合測試

- T7.5 [ ] **`packages/economy/src/commands/game-token-adjust-handler.ts`** — `/game-token-adjust` handler（admin only）
  - Verify: 整合測試

- T7.6 [ ] **`packages/economy/src/commands/balance-handler.ts`** — `/balance` handler
  - Verify: 整合測試

- T7.7 [ ] **`packages/economy/src/localization/dice-game-messages.ts`** — 移植 `DiceGameMessages.java`，zh-TW + English fallback
  - Verify: 單元測試：兩種 locale 的訊息內容與 Java 一致

## Internal Dependencies

- **currency (低層)** → **dice (高層)**: dice 模組依賴 currency 模組（GameRewardService → BalanceAdjustmentService）。不可反向依賴。
- All services are independent of each other at the same level.

## Task 8: DI 註冊

Purpose: 在 `@ltdjms/shared` 的 DI 容器中註冊所有 economy 服務。
Scope: `packages/economy/src/di/`

- T8.1 [ ] **`packages/economy/src/di/economy-module.ts`** — 註冊所有 currency/token/dice 的 repository、service、command handler 為 singleton
  - Verify: `container.resolve(DiceGame1Service)` 成功
