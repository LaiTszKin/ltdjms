# Tasks: message-alignment

- Date: 2026-05-22
- Feature: message-alignment

## **Task 1: 更新 DiceGameMessages 常數**

Purpose: 將 `DiceGameMessages` 的模板字串更新為匹配 Java `DiceGameMessages.formatXxxZhTw()` 的輸出格式
Requirements: R1.1, R1.2, R1.3, R2.1, R2.2, R2.3, R3.5
Scope: `packages/shared/src/localization/dice-game-messages.ts`
Out of scope: handler 邏輯、遊戲服務

- T1.1 [x] **DiceGameMessages 常數** — GAME_1_TITLE 改為 `'骰子遊戲結果'`
  - Verify: `grep "GAME_1_TITLE" packages/shared/src/localization/dice-game-messages.ts`

- T1.2 [x] **DiceGameMessages 常數** — GAME_1_RESULT 改為匹配 Java formatDiceGame1ResultZhTw 格式：`'骰子結果：{dice}\n\n總獎勵：{icon} {reward} {name}\n新餘額：{icon} {newBalance} {name}'`
  - Verify: 對照 Java `DiceGameMessages.java:129-143`

- T1.3 [x] **DiceGameMessages 常數** — 移除 GAME_1_DESCRIPTION（Java 無描述 footer）
  - Verify: `grep "GAME_1_DESCRIPTION" packages/shared/src/localization/dice-game-messages.ts` 返回空

- T1.4 [x] **DiceGameMessages 常數** — GAME_2_TITLE 改為 `'骰子遊戲2結果'`
  - Verify: `grep "GAME_2_TITLE" packages/shared/src/localization/dice-game-messages.ts`

- T1.5 [x] **DiceGameMessages 常數** — 新增 GAME_2_STRAIGHT_REWARD: `'順子：{icon} {reward} {name}'`
  - Verify: 對照 Java `DiceGameMessages.java:158-159`

- T1.6 [x] **DiceGameMessages 常數** — 新增 GAME_2_TRIPLE_REWARD: `'三條：{icon} {reward} {name}（{count} 組）'`
  - Verify: 對照 Java `DiceGameMessages.java:163-166`

- T1.7 [x] **DiceGameMessages 常數** — 新增 GAME_2_BASE_REWARD: `'基礎：{icon} {reward} {name}'`
  - Verify: 對照 Java `DiceGameMessages.java:169-170`

- T1.8 [x] **DiceGameMessages 常數** — 新增 GAME_2_TOTAL_REWARD: `'**總獎勵：** {icon} {reward} {name}'`
  - Verify: 對照 Java `DiceGameMessages.java:174-175`

- T1.9 [x] **DiceGameMessages 常數** — 新增 GAME_2_NEW_BALANCE: `'**新餘額：** {icon} {balance} {name}'`
  - Verify: 對照 Java `DiceGameMessages.java:176`

- T1.10 [x] **DiceGameMessages 常數** — 移除 GAME_2_RESULT（被 T1.5-T1.9 取代）、GAME_2_DESCRIPTION
  - Verify: `grep "GAME_2_RESULT\|GAME_2_DESCRIPTION" packages/shared/src/localization/dice-game-messages.ts` 返回空

- T1.11 [x] **DiceGameMessages 常數** — 新增 MISSING_TOKENS_ERROR: `'請輸入本局要投入的遊戲代幣數量！\n必須介於 {min} ~ {max} 代幣之間'`
  - Verify: 對照 Java `DiceGameMessages.java:118-119`

- T1.12 [x] **DiceGameMessages 常數** — 新增 TOKEN_RANGE_ERROR: `'代幣投入數量超出範圍！\n您輸入的數量：{input}\n允許範圍：{min} ~ {max} 代幣'`
  - Verify: 對照 Java `DiceGameMessages.java:96-98`

- T1.13 [x] **DiceGameMessages 常數** — 新增 TOKEN_INSUFFICIENT_ERROR: `'遊戲代幣不足！\n需要：{required} 代幣\n目前餘額：{current} 代幣'`
  - Verify: 對照 Java `DiceGameMessages.java:75-76`

- T1.14 [x] **DiceGameMessages 常數** — 移除 INVALID_TOKEN_COUNT, TOKEN_COUNT_TOO_LOW, TOKEN_COUNT_TOO_HIGH, TOKEN_INSUFFICIENT（被 T1.11-T1.13 取代）
  - Verify: 確認舊常數不再存在

## **Task 2: 更新 DiceGame1Handler 訊息組裝**

Purpose: 將 DiceGame1Handler 的訊息輸出改成匹配 Java DiceGame1CommandHandler + formatDiceGame1ResultZhTw
Requirements: R1.4, R1.5, R1.6, R3.6
Scope: `packages/economy/src/commands/dice-game-1-handler.ts`
Out of scope: DiceGame1Service 遊戲邏輯、代幣扣款邏輯

- T2.1 [x] **DiceGame1Handler message assembly** — 移除 diceSum 顯示（Java 不顯示骰子加總）
  - Verify: 訊息輸出不包含「總和」

- T2.2 [x] **DiceGame1Handler message assembly** — 獎勵行改為 GAME_1_RESULT 模板（含 {icon} {reward} {name}）
  - Verify: 輸出格式為「總獎勵：💰 1,500,000 G」

- T2.3 [x] **DiceGame1Handler message assembly** — 新增新餘額顯示行（使用 GAME_1_RESULT 中 {newBalance} 和 {icon}/{name}）
  - Verify: 輸出格式為「新餘額：💰 X,XXX,XXX G」

- T2.4 [x] **DiceGame1Handler message assembly** — 移除餘額箭頭變動行（「餘額變動：before → after」）
  - Verify: 訊息不包含「→」

- T2.5 [x] **DiceGame1Handler message assembly** — 移除描述 footer（GAME_1_DESCRIPTION）
  - Verify: 訊息不包含「擲 X 顆骰子」

- T2.6 [x] **DiceGame1Handler message assembly** — 獎勵/餘額數字使用 `toLocaleString()` 千分位格式化
  - Verify: `1500000` 顯示為 `1,500,000`

- T2.7 [x] **DiceGame1Handler error handling** — 無效 token 改用 MISSING_TOKENS_ERROR 模板（含 config min/max 值）
  - Verify: 輸出一致於 Java formatMissingTokensError

- T2.8 [x] **DiceGame1Handler error handling** — token 超出範圍改用 TOKEN_RANGE_ERROR 模板（含輸入值和範圍）
  - Verify: 輸出一致於 Java formatTokenRangeError

- T2.9 [x] **DiceGame1Handler error handling** — 代幣不足改用 TOKEN_INSUFFICIENT_ERROR 模板（含需要/目前餘額）
  - Verify: 輸出一致於 Java formatInsufficientTokens。需要從 deductResult error 中取得目前餘額資訊，或透過 gameTokenService.getBalance() 查詢

## **Task 3: 更新 DiceGame2Handler 訊息組裝**

Purpose: 將 DiceGame2Handler 的訊息輸出改成匹配 Java DiceGame2CommandHandler + formatDiceGame2ResultZhTw
Requirements: R2.4, R2.5, R2.6, R2.7, R2.8, R3.6
Scope: `packages/economy/src/commands/dice-game-2-handler.ts`
Out of scope: DiceGame2Service 遊戲邏輯、代幣扣款邏輯

- T3.1 [x] **DiceGame2Handler message assembly** — 移除 segment 顯示（straightSegments、tripleSegments 的骰子值列表）
  - Verify: 訊息不包含 `[1、2、3]` 格式的 segment 輸出

- T3.2 [x] **DiceGame2Handler message assembly** — 順子獎勵改為條件渲染：僅在 straightSegments.length > 0 時顯示，使用 GAME_2_STRAIGHT_REWARD 模板
  - Verify: 無順子時不顯示，有順子時格式為「順子：💰 100,000 G」

- T3.3 [x] **DiceGame2Handler message assembly** — 三條獎勵改為條件渲染：僅在 tripleSegments.length > 0 時顯示，使用 GAME_2_TRIPLE_REWARD 模板（含組數 {count}）
  - Verify: 無三條時不顯示，有三條時格式為「三條：💰 1,500,000 G（1 組）」

- T3.4 [x] **DiceGame2Handler message assembly** — 基礎獎勵改為條件渲染：僅在 nonStraightReward > 0 時顯示，使用 GAME_2_BASE_REWARD 模板
  - Verify: 基礎獎勵為 0 時不顯示，>0 時格式為「基礎：💰 40,000 G」

- T3.5 [x] **DiceGame2Handler message assembly** — 總獎勵使用 GAME_2_TOTAL_REWARD 模板（粗體 + currencyIcon + currencyName）
  - Verify: 格式為「**總獎勵：** 💰 X,XXX,XXX G」

- T3.6 [x] **DiceGame2Handler message assembly** — 新餘額使用 GAME_2_NEW_BALANCE 模板（粗體 + currencyIcon + currencyName）
  - Verify: 格式為「**新餘額：** 💰 X,XXX,XXX G」

- T3.7 [x] **DiceGame2Handler message assembly** — 移除餘額變動行（「餘額變動：before → after」）和貨幣行（「貨幣：💰G」）
  - Verify: 訊息不包含「→」和「貨幣：」

- T3.8 [x] **DiceGame2Handler message assembly** — 所有數字使用千分位格式化
  - Verify: 數字格式化正確

- T3.9 [x] **DiceGame2Handler error handling** — 同 T2.7-T2.9，更新三個錯誤分支使用新的錯誤訊息模板
  - Verify: 錯誤訊息與 Java 一致

## **Task 4: 驗收測試**

Purpose: 確保訊息格式變更後所有現有測試繼續通過，手動驗證訊息格式
Requirements: All
Scope: 全部變更檔案
Out of scope: 新增測試（本次僅確保現有測試通過 + 訊息格式正確）

- T4.1 [x] **make build** — 確保 TypeScript 編譯通過
  - Verify: `make build` exit code 0

- T4.2 [x] **make test** — 確保全部測試通過
  - Verify: `make test` exit code 0，所有測試綠色

- T4.3 [x] **訊息格式手動審查** — 對照 Java 原始碼逐項確認 DiceGame1/DiceGame2 成功訊息、三種錯誤訊息格式一致
  - Verify: 對照 `DiceGameMessages.java` 中 formatDiceGame1ResultZhTw / formatDiceGame2ResultZhTw / formatInsufficientTokens / formatTokenRangeError / formatMissingTokensError
