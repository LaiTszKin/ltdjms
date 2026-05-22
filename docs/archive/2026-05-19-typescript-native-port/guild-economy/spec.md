# Spec: Guild Economy

- Date: 2026-05-20
- Feature: Guild Economy
- Owner: [To be filled]

## Goal

將 Java 貨幣系統、遊戲代幣系統與骰子遊戲完整移植到 TypeScript，確保所有數學計算、餘額管理、交易記錄與事件發布行為與原版完全一致。

## Scope

### In Scope

- Guild 貨幣系統：`GuildCurrencyConfig`、`MemberCurrencyAccount`、`CurrencyTransaction` domain models
- 貨幣服務：`BalanceService`、`BalanceAdjustmentService`、`CurrencyConfigService`、`CurrencyTransactionService`
- 貨幣 Repository：`MemberCurrencyAccountRepository`、`GuildCurrencyConfigRepository`、`CurrencyTransactionRepository`
- 遊戲代幣系統：`GameTokenAccount`、`GameTokenTransaction` domain models
- 代幣服務：`GameTokenService`、`GameTokenTransactionService`
- 代幣 Repository：`GameTokenAccountRepository`、`GameTokenTransactionRepository`
- 骰子遊戲 1：`DiceGame1Config`、`DiceGame1Service`（roll N dice、reward = sum × rewardPerDiceValue）
- 骰子遊戲 2：`DiceGame2Config`、`DiceGame2Service`（straights ≥3、triples =3、base multiplier）
- 橋接服務：`GameRewardService`（遊戲獎勵 → 貨幣帳戶入帳）
- Slash command handlers：`/balance`、`/currency-config`、`/dice-game-1`、`/dice-game-2`、`/dice-game-1-config`、`/dice-game-2-config`、`/game-token-adjust`
- 所有相關的 zh-TW 在地化訊息（`DiceGameMessages`、`CommandLocalizations`）

### Out of Scope

- 管理面板／用戶面板（屬於 administration spec）
- Facade 聚合層（屬於 administration spec）
- 商店購買與付款（屬於 shop-payment spec）

## Functional Behaviors (BDD)

### Requirement 1: 貨幣餘額查詢與管理
**GIVEN** 一個 guild 成員存在於資料庫（或首次查詢時自動建立帳戶）
**WHEN** 查詢餘額
**THEN** 回傳 `BalanceView`（balance + currencyName + currencyIcon）
**AND** 貨幣名稱和圖標從 `GuildCurrencyConfig` 讀取（不存在時使用預設 "Coins" / "🪙"）
**AND** 餘額被快取 300 秒

**Requirements**:
- [ ] R1.1 `BalanceService.getBalance(guildId, userId)` 回傳 `Result<BalanceView, DomainError>`
- [ ] R1.2 首次查詢時自動建立 `MemberCurrencyAccount`（balance=0）
- [ ] R1.3 快取 key 為 `cache:balance:{guildId}:{userId}`，TTL 300 秒
- [ ] R1.4 `BalanceAdjustmentService.adjustBalance()` 使用 `Math.addExact`-等價的 overflow 檢測

### Requirement 2: 貨幣餘額調整
**GIVEN** 管理員操作或系統事件觸發
**WHEN** 調整成員貨幣餘額（add/deduct/set）
**THEN** 驗證新餘額不為負（deduct 時 `balance + delta >= 0`）
**AND** 記錄 `CurrencyTransaction`（含 Source enum、amount、balanceAfter、description）
**AND** 發布 `BalanceChangedEvent`
**AND** 更新快取

**Requirements**:
- [ ] R2.1 `adjustBalance(guildId, userId, delta, source, description)` 回傳 `Result<BalanceAdjustmentResult, DomainError>`
- [ ] R2.2 deduct 導致負數時回傳 `DomainError.insufficientBalance()`
- [ ] R2.3 overflow 時回傳 `DomainError.invalidInput()`
- [ ] R2.4 交易記錄包含完整的 Source enum（ADMIN_ADJUSTMENT、DICE_GAME_1_WIN、DICE_GAME_2_WIN、REDEMPTION_CODE、PRODUCT_REWARD、PRODUCT_PURCHASE、PRODUCT_PURCHASE_REFUND）

### Requirement 3: Guild 貨幣設定
**GIVEN** 管理員執行 `/currency-config`
**WHEN** 更新貨幣名稱或圖標
**THEN** 名稱最長 50 字元、圖標最長 64 字元
**AND** 圖標若為自訂 emoji 需通過 `EmojiValidator` 驗證
**AND** 發布 `CurrencyConfigChangedEvent`

**Requirements**:
- [ ] R3.1 `CurrencyConfigService.updateConfig(guildId, name, icon)` 驗證長度限制
- [ ] R3.2 Emoji 驗證支援 Discord 自訂 emoji 格式（`<a:name:id>` 或 `<:name:id>`）
- [ ] R3.3 首次設定時自動建立 config（saveOrUpdate）

### Requirement 4: 遊戲代幣管理
**GIVEN** 一個 guild 成員
**WHEN** 查詢或調整遊戲代幣
**THEN** 行為與貨幣系統對稱（自動建立帳戶、非負驗證、交易記錄、事件發布、快取）

**Requirements**:
- [ ] R4.1 `GameTokenService.getBalance(guildId, userId)` 回傳代幣數量
- [ ] R4.2 `deductTokens()` 餘額不足時回傳 `DomainError.insufficientTokens()`
- [ ] R4.3 發布 `GameTokenChangedEvent`
- [ ] R4.4 快取 key 為 `cache:gametoken:{guildId}:{userId}`，TTL 300 秒

### Requirement 5: 骰子遊戲 1
**GIVEN** 玩家有足夠代幣（≥ minTokensPerPlay）
**WHEN** 執行 `/dice-game-1`（使用 N 個代幣）
**THEN** 先扣除 N 個代幣、記錄代幣交易
**AND** 擲 N 顆骰子（每顆 1-6）
**AND** 獎勵 = sum(dice) × rewardPerDiceValue（單位：貨幣）
**AND** 透過 `GameRewardService` 將獎勵加入貨幣帳戶
**AND** 顯示擲骰結果與獎勵（zh-TW 在地化）

**Requirements**:
- [ ] R5.1 代幣扣除與遊戲執行在同一個逻辑流程中（扣除失敗則不執行遊戲）
- [ ] R5.2 每顆骰子的值來自可注入的 `Random`（測試時可用 seeded random）
- [ ] R5.3 獎勵計算完全等價於 Java `DefaultDiceGame1Service`
- [ ] R5.4 獎勵若超過 `MAX_ADJUSTMENT_AMOUNT`，分割為多次 adjustBalance 呼叫

### Requirement 6: 骰子遊戲 2
**GIVEN** 玩家有足夠代幣（≥ minTokensPerPlay，每代幣 = 3 顆骰子）
**WHEN** 執行 `/dice-game-2`
**THEN** 獎勵分三部分計算：
  - Straight segments（連續遞增、長度 ≥3）→ segmentLength × straightMultiplier
  - Triple segments（恰好 3 個相同值、非 4+）→ tripleLowBonus 或 tripleHighBonus
  - 剩餘骰子 → count × baseMultiplier
**AND** Straights 優先於 triples 分配（non-overlapping）
**AND** 計算邏輯完全等價於 Java `DefaultDiceGame2Service`

**Requirements**:
- [ ] R6.1 Straight 偵測：連續遞增、長度 ≥3、優先分配
- [ ] R6.2 Triple 偵測：恰好 3 個相同值、非 4+、不與 straight 重疊
- [ ] R6.3 三種獎勵獨立計算後加總
- [ ] R6.4 使用可注入的 Random（測試可控）

## Error and Edge Cases

- [ ] 餘額不足時 deduct 回傳 `INSUFFICIENT_BALANCE` / `INSUFFICIENT_TOKENS`
- [ ] 代幣扣除成功但遊戲邏輯失敗時，不應自動退款（已在 Java 中確認此行為）
- [ ] 大額獎勵分割：獎勵超過 MAX_ADJUSTMENT_AMOUNT 時分割為多筆 adjustBalance
- [ ] 非正整數的 adjust 請求（delta=0、NaN、Infinity）
- [ ] Guild currency config 不存在時使用預設值、不拋錯
- [ ] 並發 adjustBalance：資料庫 constraint `balance >= 0` 作為最後防線

## Clarification Questions

None

## References

- Official docs: discord.js v14 EmbedBuilder, SlashCommandBuilder
- Related Java files:
  - `src/main/java/ltdjms/discord/currency/domain/*.java` (6 files)
  - `src/main/java/ltdjms/discord/currency/services/*.java` (8 files)
  - `src/main/java/ltdjms/discord/currency/persistence/*.java` (7 files)
  - `src/main/java/ltdjms/discord/gametoken/domain/*.java` (5 files)
  - `src/main/java/ltdjms/discord/gametoken/services/*.java` (6 files)
  - `src/main/java/ltdjms/discord/gametoken/persistence/*.java` (8 files)
  - `src/main/java/ltdjms/discord/currency/bot/BotErrorHandler.java`
  - `src/main/java/ltdjms/discord/shared/localization/DiceGameMessages.java`
