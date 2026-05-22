# Spec: message-alignment

- Date: 2026-05-22
- Feature: message-alignment
- Owner: laitszkin

## Goal

使 TypeScript 端骰子遊戲的 Discord 輸出訊息與 Java bot 1:1 完全一致，確保玩家看到的遊戲結果、錯誤訊息格式完全相同。

## Scope

### In Scope
- DiceGame1 遊戲結果訊息格式（zh-TW）
- DiceGame2 遊戲結果訊息格式（zh-TW）
- 遊戲相關錯誤訊息（代幣不足、數量超出範圍、未輸入數量）
- `DiceGameMessages` 常數更新
- 兩個 handler 的訊息組裝邏輯

### Out of Scope
- 遊戲玩法邏輯修改（骰子隨機、獎勵計算公式不變）
- 英文 locale 支援（Java 雖有英文 fallback，TS 端統一使用 zh-TW）
- Discord 指令參數定義變更
- 貨幣/代幣系統修改

## Functional Behaviors (BDD)

### Requirement 1: DiceGame1 結果訊息對齊 Java
**GIVEN** 玩家成功執行 `/dice-game-1 tokens:3` 且骰子結果為 [1, 2, 3]
**AND** 公會貨幣設定為 icon=💰 name=G
**AND** rewardPerDiceValue=250000
**WHEN** 系統顯示遊戲結果
**THEN** 訊息標題為 **骰子遊戲結果**（不含 emoji、不含數字後綴）
**AND** 骰子以 Discord number emoji 顯示（`:one:` `:two:` `:three:`），以空格分隔
**AND** 獎勵以「總獎勵：💰 1,500,000 G」格式顯示（含千分位逗號、貨幣 icon、貨幣名稱）
**AND** 新餘額以「新餘額：💰 X,XXX,XXX G」格式顯示
**AND** 不出現骰子加總、餘額變動箭頭、描述 footer

**Requirements**:
- [ ] R1.1 `DiceGameMessages` 常數更新：GAME_1_TITLE 改為「骰子遊戲結果」
- [ ] R1.2 `DiceGameMessages` 常數更新：GAME_1_RESULT 改為匹配 Java 格式（骰子結果 / 總獎勵 / 新餘額）
- [ ] R1.3 `DiceGameMessages` 移除不再使用的 GAME_1_DESCRIPTION
- [ ] R1.4 `DiceGame1Handler` 訊息組裝邏輯更新：移除 diceSum、餘額變動、描述 footer
- [ ] R1.5 `DiceGame1Handler` 獎勵/餘額數字加入千分位格式化
- [ ] R1.6 `DiceGame1Handler` 顯示 currencyIcon + currencyName（已在 handler 內取得 currencyConfig）

### Requirement 2: DiceGame2 結果訊息對齊 Java
**GIVEN** 玩家成功執行 `/dice-game-2 tokens:2` 且骰子結果包含順子 [1,2,3]、三條 [4,4,4]、剩餘骰子 [5,6]
**AND** 公會貨幣設定為 icon=💰 name=G
**WHEN** 系統顯示遊戲結果
**THEN** 訊息標題為 **骰子遊戲2結果**（不含 emoji）
**AND** 骰子以 Discord number emoji 顯示，以空格分隔
**AND** 順子獎勵以「順子：💰 100,000 G」格式顯示（僅在有順子時顯示）
**AND** 三條獎勵以「三條：💰 1,500,000 G（1 組）」格式顯示（含組數、僅在有時顯示）
**AND** 基礎獎勵以「基礎：💰 40,000 G」格式顯示（僅在 >0 時顯示）
**AND** 總獎勵以「**總獎勵：** 💰 1,640,000 G」格式顯示（粗體）
**AND** 新餘額以「**新餘額：** 💰 X,XXX,XXX G」格式顯示（粗體）
**AND** 不出現順子區段/三條區段的骰子列表、不出現餘額變動箭頭

**Requirements**:
- [ ] R2.1 `DiceGameMessages` 常數更新：GAME_2_TITLE 改為「骰子遊戲2結果」
- [ ] R2.2 `DiceGameMessages` 常數更新：GAME_2_RESULT 改為匹配 Java 格式
- [ ] R2.3 `DiceGameMessages` 移除不再使用的 GAME_2_DESCRIPTION
- [ ] R2.4 `DiceGame2Handler` 訊息組裝邏輯更新：移除 segment 顯示、餘額變動
- [ ] R2.5 `DiceGame2Handler` 三條區段加入組數顯示（`tripleSegments.length`）
- [ ] R2.6 `DiceGame2Handler` 順子/三條/基礎獎勵僅在有值時顯示（條件渲染）
- [ ] R2.7 `DiceGame2Handler` 所有獎勵/餘額數字加入千分位格式化
- [ ] R2.8 `DiceGame2Handler` 總獎勵和新餘額使用粗體

### Requirement 3: 錯誤訊息對齊 Java
**GIVEN** 玩家觸發各種錯誤情境
**WHEN** 系統回覆錯誤訊息
**THEN** 錯誤訊息格式與 Java bot 一致

**Requirements**:
- [ ] R3.1 未輸入代幣數量時，顯示：「請輸入本局要投入的遊戲代幣數量！\n必須介於 X ~ Y 代幣之間」（含實際 min/max）
- [ ] R3.2 代幣數量低於最低限制時，顯示：「代幣投入數量超出範圍！\n您輸入的數量：X\n允許範圍：min ~ max 代幣」
- [ ] R3.3 代幣數量高於最高限制時，顯示：同上 R3.2 格式
- [ ] R3.4 代幣不足時，顯示：「遊戲代幣不足！\n需要：X 代幣\n目前餘額：Y 代幣」（含千分位格式化）
- [ ] R3.5 `DiceGameMessages` 常數更新：移除 INVALID_TOKEN_COUNT、TOKEN_COUNT_TOO_LOW、TOKEN_COUNT_TOO_HIGH、TOKEN_INSUFFICIENT，替換為含參數的模板字串
- [ ] R3.6 `DiceGame1Handler` 和 `DiceGame2Handler` 更新錯誤處理分支使用新訊息格式

## Error and Edge Cases
- [ ] 當 currencyConfig 查詢失敗時，遊戲結果仍能顯示（使用預設貨幣名稱/icon）
- [ ] 當骰子值非 1-6 時，emoji mapping 有 fallback 顯示數字
- [ ] 代幣數量為 0 或負數時的邊界處理（現有行為保留：視為無效輸入）

## Clarification Questions
None

## References
- Java source files:
  - `src/main/java/ltdjms/discord/shared/localization/DiceGameMessages.java`
  - `src/main/java/ltdjms/discord/gametoken/commands/DiceGame1CommandHandler.java`
  - `src/main/java/ltdjms/discord/gametoken/commands/DiceGame2CommandHandler.java`
  - `src/main/java/ltdjms/discord/gametoken/services/DiceGame1Service.java`
  - `src/main/java/ltdjms/discord/gametoken/services/DiceGame2Service.java`
- TypeScript source files:
  - `packages/shared/src/localization/dice-game-messages.ts`
  - `packages/economy/src/commands/dice-game-1-handler.ts`
  - `packages/economy/src/commands/dice-game-2-handler.ts`
