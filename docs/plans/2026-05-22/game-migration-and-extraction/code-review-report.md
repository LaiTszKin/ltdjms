# Code Review Report

- **Spec**: game-migration-and-extraction (message-alignment + package-extraction)
- **Date**: 2026-05-23
- **Reviewer**: QA Agent (6-dimension automated review)
- **Result**: PASS

## 總體判定

本次實作 **通過審查**。兩個子 spec 的核心需求均已正確實現：

- **message-alignment**: 骰子遊戲訊息格式與 Java bot 1:1 對齊，所有錯誤訊息模板、千分位格式化、條件渲染均已正確實作
- **package-extraction**: `@ltdjms/games` package 骨架完整，依賴鏈正確 (admin → games → economy → shared)，DI container 初始化順序正確，兩個 drizzle 實例共用同一個 pg Pool

關於 panel handlers (GameSettingsHandler, TokenManagementHandler) 未移至 games 的 spec 偏差：這是因兩者繼承 `BaseAdminHandler`，移動會造成 circular dependency (admin → games → admin)，屬正確的設計決策，不是實作遺漏。

`make test` 全部 799+ 測試通過，`make build` 編譯成功。

---

## 發現的問題

### P0 — 嚴重缺陷

無。

### P1 — 重要問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | currencyConfig 查詢失敗時無 fallback，遊戲結果會遺失且代幣不退還 | 若 currencyConfig 暫時失效，玩家已扣代幣但看不到結果 | `packages/games/src/commands/dice-game-1-handler.ts`, `dice-game-2-handler.ts` | L106-108 |
| 2 | games vitest.config.ts 缺少 @ltdjms/economy 的 source alias | games 測試在開發環境中 (無 dist) 可能無法解析 @ltdjms/economy | `packages/games/vitest.config.ts` | L8-12 |
| 3 | 3 個 games PBT 測試檔案錯置在 economy package | 模組邊界模糊，economy 因此有不必要的 games devDependency | `packages/economy/src/__tests__/dice-game-1.pbt.test.ts`, `dice-game-2.pbt.test.ts`, `game-token.pbt.test.ts` | - |

### P2 — 一般問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | 4 個類型/常數在 economy 和 games 的 domain/types.ts 中重複定義 (TransactionPage, MAX_ADJUSTMENT_AMOUNT, isValidAdjustmentAmount, DEFAULT_PAGE_SIZE) | 若只更新一邊會導致行為不一致 | `packages/economy/src/domain/types.ts`, `packages/games/src/domain/types.ts` | L59-115, L97-140 |
| 2 | economy package.json 有不必要的 @ltdjms/games devDependency | 違反單向依賴原則 (games → economy) | `packages/economy/package.json` | L25 |
| 3 | economy vitest.config.ts 有不必要的 @ltdjms/games source alias | economy 不應知道 games 的 source 路徑 | `packages/economy/vitest.config.ts` | L15 |
| 4 | Dice game configs 缺少快取，每次玩遊戲都查詢 DB | 高頻遊戲場景下增加不必要的 DB 查詢 | `packages/games/src/dice/repositories/dice-config-repo.ts` | L21-29 |
| 5 | Config 更新時重複 SELECT (facade + service 各自查詢 createdAt) | 每次 admin 更新 config 執行兩次相同 SELECT | `packages/games/src/facades/GameConfigManagementFacade.ts`, `packages/games/src/dice/services/dice-config-service.ts` | L88, L59 |
| 6 | 代幣不足錯誤路徑中多餘的 getBalance 查詢 (tryDeductTokens 已查過) | 錯誤路徑中額外一次 DB round-trip | `packages/games/src/commands/dice-game-1-handler.ts`, `dice-game-2-handler.ts` | L91-97, L92-98 |

### P3 — 建議改善

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | currencyConfig 查詢失敗時應有 fallback 預設值 (spec Error and Edge Cases 要求) | 邊界情況處理不完整 | `packages/games/src/commands/dice-game-1-handler.ts`, `dice-game-2-handler.ts` | L106-108 |
| 2 | 「骰子結果：」字串在 dice-game-2-handler 中硬編碼，未放入 DiceGameMessages 常數 | 與 GAME_1 不一致，修改時容易遺漏 | `packages/games/src/commands/dice-game-2-handler.ts` | L134 |
| 3 | 零獎勵路徑中不必要的 DB 查詢 (rewardAmount === 0 時仍查 getBalance) | 極端邊界情況下的輕微性能浪費 | `packages/games/src/dice/services/game-reward-service.ts` | L46-51 |
| 4 | Config 更新時重複發布 DiceGameConfigChangedEvent (facade 和 service 各發一次) | listener 收到兩次事件，一次缺少 oldConfig/newConfig | `packages/games/src/facades/GameConfigManagementFacade.ts`, `packages/games/src/dice/services/dice-config-service.ts` | L101, L63 |
| 5 | configureGamesContainer 在啟動時 eager 建立所有 handler/service 實例 | 冷啟動稍慢，不影響運行 | `packages/games/src/di/games-module.ts` | L66-161 |
| 6 | 測試 import 從非導出模組取得符號 (GameRewardService 從 dice-game-1-service.js、DefaultRandom 從 dice-game-1-service.js)，雖然 vitest 能正確解析但不符合 ESM 語義 | 程式碼品質 | `packages/games/src/__tests__/dice-game-1.test.ts`, `dice-game-2.test.ts` | L3 |
| 7 | BaseAccountRepository / BaseTransactionService 在 economy 中但被 games 跨 package 繼承，概念上應屬於 shared | 未來其他 package 如需使用會被迫依賴 economy | `packages/economy/src/common/` | - |
| 8 | games/src/panel/ 目錄為空 | 目錄樹雜亂 | `packages/games/src/panel/` | - |

---

## 解決方案

### P0 修復

無。

### P1 修復

#### P1-1: currencyConfig 查詢失敗時遊戲結果遺失

- **涉及檔案**：`packages/games/src/commands/dice-game-1-handler.ts` > `execute`（L106-108）、`packages/games/src/commands/dice-game-2-handler.ts` > `execute`（L107-109）
- **根因**：`currencyConfigService.getConfig()` 被放在主 try-catch 區塊內，若拋出例外則整個 handler 進入 catch 顯示 UNEXPECTED_ERROR，已執行的遊戲邏輯（扣代幣、發獎勵）的結果全部遺失
- **修復方案**：在呼叫遊戲服務之前，先用 try-catch 包裹 currencyConfig 查詢，查詢失敗時使用預設值 `{ currencyName: 'G', currencyIcon: '🪙' }`，確保後續訊息組裝不受影響
- **驗證方式**：mock currencyConfigService.getConfig 拋出例外，確認遊戲結果仍正常顯示（使用預設 icon/name）

#### P1-2: games vitest.config.ts 缺少 @ltdjms/economy source alias

- **涉及檔案**：`packages/games/vitest.config.ts`（L8-12）
- **根因**：games 測試檔案從 @ltdjms/economy import 類型（CurrencyTransactionSource, BalanceAdjustmentResult），但 vitest alias 只設定了 @ltdjms/shared，缺少 economy
- **修復方案**：在 resolve.alias 陣列中加入 `{ find: /^@ltdjms\/economy$/, replacement: economySrc }`（economySrc = path.resolve(__dirname, '../economy/src')）
- **驗證方式**：`pnpm vitest run --project @ltdjms/games` 通過

#### P1-3: PBT 測試檔案錯置

- **涉及檔案**：`packages/economy/src/__tests__/dice-game-1.pbt.test.ts`、`dice-game-2.pbt.test.ts`、`game-token.pbt.test.ts`
- **根因**：package-extraction 遷移過程中遺漏了 PBT 測試檔案的移動。這些檔案測試 games package 的服務，應放在 games package 中
- **修復方案**：將三個檔案移至 `packages/games/src/__tests__/`，在 economy 的 Makefile test 目標中移除相關 PBT test 路徑（它們通過 `@ltdjms/economy` 項目執行），確保它們在 games 的 Makefile test 目標中被執行
- **驗證方式**：移動後 `make test` 全部通過

### P2 修復

#### P2-1: 重複的跨 package 類型/常數定義

- **涉及檔案**：`packages/economy/src/domain/types.ts`（L59-66, L102, L107-109, L115）、`packages/games/src/domain/types.ts`（L97-104, L127, L132-134, L140）
- **根因**：package-extraction 將遊戲類型複製到 games 時，共用類型 (TransactionPage, MAX_ADJUSTMENT_AMOUNT, isValidAdjustmentAmount, DEFAULT_PAGE_SIZE) 在兩處各留了一份
- **修復方案**：
  - `TransactionPage<T>`: 已在 shared 的 infra 中有類似定義則復用；否則保留在 economy，games 通過 `import type` 從 economy 取得
  - `MAX_ADJUSTMENT_AMOUNT`、`isValidAdjustmentAmount`: games 從 economy import（games 已依賴 economy）
  - `DEFAULT_PAGE_SIZE`: games 不再需要（內部無使用），從 games domain/types.ts 和 index.ts 移除
- **驗證方式**：`make build` 通過、`make test` 通過

#### P2-2: economy 不必要的 games devDependency

- **涉及檔案**：`packages/economy/package.json`（L25）
- **根因**：P1-3 的錯置 PBT 測試迫使 economy 在 devDependencies 中依賴 games
- **修復方案**：P1-3 修復後，從 economy package.json 移除 `"@ltdjms/games": "workspace:*"` devDependency
- **驗證方式**：`pnpm install` 成功、`make build` 通過

#### P2-3: economy vitest.config.ts 不必要的 games alias

- **涉及檔案**：`packages/economy/vitest.config.ts`（L15）
- **根因**：同上，為支援錯置的 PBT 測試
- **修復方案**：P1-3 修復後，從 economy vitest.config.ts alias 陣列移除 `{ find: /^@ltdjms\/games$/, replacement: gamesSrc }` 及相關的 gamesSrc 變數
- **驗證方式**：`make test` 所有 economy 測試通過

#### P2-4: Dice game configs 缺少快取

- **涉及檔案**：`packages/games/src/dice/repositories/dice-config-repo.ts`（L21-29, L72-80）
- **根因**：每次 `/dice-game-1` 或 `/dice-game-2` 都直接查詢 DB 取得 config，而 game token 已有 Redis 快取模式（TOKEN_CACHE_TTL = 300）
- **修復方案**：在 DiceConfigService 中加入 Redis 快取層（與 GameTokenService 的快取模式一致），TTL 可設為 300 秒。Config 更新時 (upsert) 清除快取
- **驗證方式**：連續兩次 `/dice-game-1` 只產生一次 DB SELECT（第二次命中快取）

#### P2-5: Config 更新時重複 SELECT

- **涉及檔案**：`packages/games/src/facades/GameConfigManagementFacade.ts`（L88）、`packages/games/src/dice/services/dice-config-service.ts`（L59）
- **根因**：Facade 層先查一次 config 取得 createdAt，再呼叫 service.upsert；service 內部又查一次 config 為了產生 event payload
- **修復方案**：讓 upsert 方法回傳包含 createdAt 的完整 config 物件，facade 不再需要事先查詢；或將 createdAt 保留邏輯統一在 service 層處理
- **驗證方式**：檢查 admin 更新 dice config 時的 SQL 日誌，確認只有一次 SELECT + 一次 UPSERT

#### P2-6: 代幣不足錯誤路徑中多餘的 getBalance

- **涉及檔案**：`packages/games/src/commands/dice-game-1-handler.ts`（L91-97）、`dice-game-2-handler.ts`（L92-98）
- **根因**：`tryDeductTokens` 內部已查詢帳戶餘額（findOrCreate），但 handler 在捕捉到 INSUFFICIENT_TOKENS 錯誤後又呼叫 `getBalance` 再查一次
- **修復方案**：讓 `tryDeductTokens` 在錯誤結果中附帶目前餘額資訊，或讓 handler 在錯誤路徑中直接使用 tryDeductTokens 回傳的餘額
- **驗證方式**：觸發代幣不足錯誤時確認只有一次 DB SELECT on game_token_account

### P3 改善

#### P3-1: 「骰子結果：」字串應移入 DiceGameMessages

- **涉及檔案**：`packages/games/src/commands/dice-game-2-handler.ts`（L134）
- **根因**：GAME_1 的骰子結果字串在 GAME_1_RESULT 模板中，但 GAME_2 的同等字串直接寫在 handler 內
- **修復方案**：在 DiceGameMessages 中新增 `GAME_2_DICE_RESULT: '骰子結果：{dice}'`，handler 改用此常數
- **驗證方式**：GAME_2 結果輸出格式不變

#### P3-2: 零獎勵路徑中的多餘 DB 查詢

- **涉及檔案**：`packages/games/src/dice/services/game-reward-service.ts`（L46-51）
- **根因**：rewardAmount === 0 時仍呼叫 getBalance 取得餘額，但此時 previousBalance === newBalance
- **修復方案**：rewardAmount === 0 時直接回傳 `{ previousBalance: 0, newBalance: 0 }` 或快取餘額
- **驗證方式**：rewardAmount 為 0 的測試案例不產生 DB 查詢

#### P3-3: Config 更新時重複發布事件

- **涉及檔案**：`packages/games/src/facades/GameConfigManagementFacade.ts`（L101）、`dice-config-service.ts`（L63）
- **根因**：facade 和 service 各自發布 DiceGameConfigChangedEvent，facade 的事件只有 guildId 沒有 oldConfig/newConfig
- **修復方案**：移除 facade 層的事件發布，只保留 service 層的完整事件
- **驗證方式**：更新 config 後確認只觸發一次事件，且包含完整 oldConfig/newConfig

#### P3-4: 測試 import 應從正確模組取得符號

- **涉及檔案**：`packages/games/src/__tests__/dice-game-1.test.ts`（L3）、`dice-game-2.test.ts`（L3）
- **根因**：`GameRewardService` 應從 `game-reward-service.js` import，`DefaultRandom` 應從 `random.js` import，而非從 `dice-game-1-service.js`
- **修復方案**：修正 import 來源：`import { GameRewardService } from '../dice/services/game-reward-service.js'`；`import { DefaultRandom } from '../dice/services/random.js'`
- **驗證方式**：測試繼續通過，import 路徑正確

#### P3-5: 清空遺留的 panel/ 目錄

- **涉及檔案**：`packages/games/src/panel/`（空目錄）
- **根因**：handlers 因 circular dependency 無法移至 games，但空目錄被保留下來
- **修復方案**：移除 `packages/games/src/panel/` 目錄
- **驗證方式**：目錄不存在

---

## 審查維度摘要

| 維度 | 結果 | P0 | P1 | P2 | P3 |
|------|------|----|----|----|-----|
| 幻覺代碼 | PASS | 0 | 0 | 0 | 2 |
| 冗余代碼 | PASS | 0 | 0 | 4 | 2 |
| Spec 實作偏移 | PASS | 0 | 1 | 0 | 1 |
| Spec 實作遺漏 | PASS | 0 | 3 | 0 | 1 |
| 架構瑕疵 | PASS | 0 | 2 | 2 | 1 |
| 性能隱患 | PASS | 0 | 0 | 3 | 3 |

**總計**: P0=0, P1=6, P2=9, P3=10
