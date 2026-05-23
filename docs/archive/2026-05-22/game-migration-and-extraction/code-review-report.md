# Code Review Report

- **Spec**: game-migration-and-extraction (message-alignment + package-extraction)
- **Date**: 2026-05-23
- **Reviewer**: QA Agent (6-dimension automated review)
- **Result**: PASS (有保留意見)

---

## 總體判定

本次實作（commit `086bf38` + fix `aa7a20d`）**通過審查**。

- **message-alignment**: 骰子遊戲訊息格式與 Java bot 1:1 對齊。所有錯誤訊息模板、千分位格式化、條件渲染均已正確實作。`DiceGameMessages` 常數完整，舊常數已清理。
- **package-extraction**: `@ltdjms/games` package 骨架完整。依賴鏈正確 (admin → games → economy → shared)，DI container 初始化順序正確，兩個 drizzle 實例共用同一個 pg Pool。

`make build` 和 `make test`（799+ tests）通過。

發現 **0 個 P0、0 個 P1、7 個 P2、5 個 P3** 問題。

---

## 發現的問題

### P0 — 嚴重缺陷

無。

### P1 — 重要問題

無。上一次審查發現的 P1 問題（currencyConfig 無 fallback、vitest alias 缺失、PBT 檔案錯置）已在 commit `aa7a20d` 全部修復。

### P2 — 一般問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | 重複的事件類型定義：`GameTokenChangedEvent`、`DiceGameConfigChangedEvent`、`GameType` 在 `shared/src/types/events/` 和 `games/src/events/` 各自定義一份，結構完全相同 | shared 中的版本未匯出到 public API 故目前無實際衝突；但若兩邊各自演進產生分歧，TypeScript 結構化型別不會報錯 | `packages/shared/src/types/events/index.ts`, `packages/games/src/events/index.ts` | L8-L40 / L4-L18 |
| 2 | `TransactionPage<T>` 泛型介面在 economy 和 games 的 domain/types.ts 中重複定義 | 若兩邊定義未來偏離會產生難以察覺的型別相容 bug | `packages/economy/src/domain/types.ts`, `packages/games/src/domain/types.ts` | L60 / L98 |
| 3 | `MAX_ADJUSTMENT_AMOUNT` 常數與 `isValidAdjustmentAmount` 函數在 economy 和 games 各自定義，值相同但彼此獨立 | 若一邊修改而另一邊遺漏會導致行為不一致 | `packages/economy/src/domain/types.ts`, `packages/games/src/domain/types.ts` | L102-L109 / L127-L134 |
| 4 | 骰子遊戲配置（DiceGame1Config / DiceGame2Config）完全無快取，每次遊戲執行都直接查詢資料庫 | 同 package 內的 GameTokenService 已有完備的快取 + stampede protection，但 DiceConfigService 無任何快取層 | `packages/games/src/dice/services/dice-config-service.ts`, `packages/games/src/dice/repositories/dice-config-repo.ts` | L62, L86 / L125-L158 |
| 5 | `BaseAccountRepository` / `BaseTransactionService` 放在 economy 的 `common/` 目錄，但本質上是共享基礎設施（使用泛型、不依賴貨幣邏輯）。games 的 token repos 必須依賴 economy 才能取得這些泛型基類 | 跨 package 耦合。Economy 修改這些基類的 public API 時可能意外破壞 games | `packages/economy/src/common/base-account-repo.ts`, `packages/economy/src/common/base-tx-service.ts` | — |
| 6 | `packages/economy/dist/dice/` 殘留舊編譯產物（repositories/ 和 services/ 子目錄含 .js/.d.ts/.js.map） | 若 Node.js 解析器意外載入殘留檔案可能使用過時的遊戲邏輯 | `packages/economy/dist/dice/` | — |
| 7 | Spec R6.3/R6.4 要求將 `GameSettingsHandler` / `TokenManagementHandler` 遷移至 `@ltdjms/games/src/panel/`，但兩個 handler 仍留在 `@ltdjms/admin` | 兩個 handler 繼承 `BaseAdminHandler` 並依賴 `AdminPanelSessionManager`、`BotErrorHandler` 等 admin 內部模組。遷移會造成循環依賴 (admin → games → admin)。此為合理的架構取捨，但與 spec 文字不一致 | `packages/admin/src/panel/admin/handlers/GameSettingsHandler.ts`, `packages/admin/src/panel/admin/handlers/TokenManagementHandler.ts` | — |

### P3 — 建議改善

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | `games/src/index.ts` 導出 5 個無外部消費者的類型/常數：`DiceGame1Result`、`DiceGame2Result`、`DiceGame1ConfigUpdate`、`DiceGame2ConfigUpdate`、`DICE_GAME_2_DICE_PER_TOKEN` | API 表面積不必要的膨脹 | `packages/games/src/index.ts` | L21-L22, L30, L73 |
| 2 | `GameConfigManagementFacade` 注入 `eventPublisher` 但從未使用（事件由 `DiceConfigService` 發布，上次修復已移除 facade 的重複 publish，但遺留了未使用的注入參數） | 誤導程式碼閱讀者以為 facade 也會發布事件 | `packages/games/src/facades/GameConfigManagementFacade.ts`, `packages/games/src/di/games-module.ts` | L41 / DI 註冊處 |
| 3 | `DiceGame1ConfigHandler` / `DiceGame2ConfigHandler` 呼叫 `upsertDice1Config/upsertDice2Config` 時未傳入 `previousVersion`，導致 service 內部多做一次 SELECT | 管理員操作頻率極低，無實際效能影響，但與 facade 路徑不一致 | `packages/games/src/commands/dice-config-handlers.ts` | L50, L142 |
| 4 | `GameTokenManagementFacade.setTokens` 先呼叫 `getBalance` 再呼叫 `tryAdjustTokens`，兩者內部各自做一次 `findOrCreate`（第二次通常命中 cache） | 管理員操作，實際開銷微小 | `packages/games/src/facades/GameTokenManagementFacade.ts` | L85, L102 |
| 5 | 代幣不足錯誤路徑：`tryDeductTokens` 內部 `findOrCreate` 結果未寫入 cache，handler 接著呼叫 `getBalance` 又做一次 DB 查詢 | 僅在錯誤路徑發生，不影響正常遊戲流程 | `packages/games/src/commands/dice-game-1-handler.ts`, `packages/games/src/commands/dice-game-2-handler.ts` | L88-L101 / L89-L102 |

---

## 解決方案

### P2 修復

#### P2-1: 重複的事件類型定義

- **涉及檔案**：`packages/shared/src/types/events/index.ts`（L8-L40）、`packages/games/src/events/index.ts`（L4-L18）
- **根因**：事件類型最初在 infra-java-to-typescript spec 時定義在 shared 中，games package 建立時又自行定義了一份。shared 的版本從未匯出到 public API
- **修復方案**：
  1. 從 `packages/shared/src/types/events/index.ts` 移除 `GameType`、`GameTokenChangedEvent`、`DiceGameConfigChangedEvent`
  2. 保留 `packages/games/src/events/index.ts` 中的定義為唯一來源
- **驗證方式**：`grep -r "shared.*GameTokenChangedEvent\|shared.*DiceGameConfigChangedEvent" packages/` 返回空；`make build` 通過

#### P2-2: TransactionPage<T> 重複定義

- **涉及檔案**：`packages/games/src/domain/types.ts`（L98-L104）、`packages/economy/src/domain/types.ts`（L60-L66）
- **根因**：games 從 economy 拆分時複製了類型，`TransactionPage<T>` 是通用分頁容器不屬於任一領域
- **修復方案**：games 從 `@ltdjms/economy` import `TransactionPage`（games 已依賴 economy），從 games domain/types.ts 移除重複定義
- **驗證方式**：`make build && make test` 通過

#### P2-3: MAX_ADJUSTMENT_AMOUNT / isValidAdjustmentAmount 重複定義

- **涉及檔案**：`packages/economy/src/domain/types.ts`（L102-L109）、`packages/games/src/domain/types.ts`（L127-L134）
- **根因**：games 拆分時複製了 domain constants
- **修復方案**：games 從 `@ltdjms/economy` import 這兩個項目。從 games domain/types.ts 移除重複定義，更新所有 games 內部 import 指向 economy
- **驗證方式**：`grep "MAX_ADJUSTMENT_AMOUNT" packages/games/src/domain/types.ts` 返回空

#### P2-4: 骰子遊戲配置無快取

- **涉及檔案**：`packages/games/src/dice/services/dice-config-service.ts`（L30-L112）
- **根因**：DiceConfigService 設計時未考量快取需求
- **修復方案**：在 `DiceConfigService` 加入快取層（注入 `CacheService`），以 `guildId` 為 key。在 `upsert` 成功後 invalidate 對應 cache。TTL 建議 300-600 秒
- **驗證方式**：`make build && make test` 通過；連續兩次查詢僅觸發一次 DB query

#### P2-5: BaseAccountRepository / BaseTransactionService 跨邊界放置

- **涉及檔案**：`packages/economy/src/common/base-account-repo.ts`、`packages/economy/src/common/base-tx-service.ts`
- **根因**：這些泛型基類本質上屬於共享基礎設施，最初放在 economy 是因為當時只有 economy 使用
- **修復方案**：將兩個檔案移至 `packages/shared/src/`（shared 已有 `drizzle-orm` 依賴），更新 economy 和 games 的 import 路徑
- **驗證方式**：`make build && make test` 通過

#### P2-6: economy/dist/dice/ 殘留編譯產物

- **涉及檔案**：`packages/economy/dist/dice/`（整個目錄）
- **根因**：遷移後未清理 TypeScript 編譯輸出目錄
- **修復方案**：刪除 `packages/economy/dist/` 目錄後重新執行 `make build`
- **驗證方式**：`ls packages/economy/dist/dice/ 2>/dev/null` 返回空

#### P2-7: Spec R6.3/R6.4 與實作不一致（panel handler 未遷移）

- **涉及檔案**：`packages/admin/src/panel/admin/handlers/GameSettingsHandler.ts`、`TokenManagementHandler.ts`
- **根因**：兩個 handler 繼承 `BaseAdminHandler` 並深度依賴 admin 內部模組（`AdminPanelSessionManager`、`BotErrorHandler`、`AdminPanelViewState` 等）。遷移會造成循環依賴 (admin → games → admin)。此為正確的架構取捨
- **修復方案**：更新 spec R6.3/R6.4 以反映實際架構約束，或標記為 deferred（需先重構 admin panel 基礎設施）。不需修改程式碼
- **驗證方式**：spec 更新後與實作一致

### P3 改善

#### P3-1: games index.ts 導出過多內部類型

- **涉及檔案**：`packages/games/src/index.ts`（L21-L22, L30, L73）
- **根因**：為了完整性導出了所有 domain types，但部分類型僅在 games 內部使用
- **修復方案**：從 index.ts 移除無外部消費者的導出：`DiceGame1Result`、`DiceGame2Result`、`DiceGame1ConfigUpdate`、`DiceGame2ConfigUpdate`、`DICE_GAME_2_DICE_PER_TOKEN`
- **驗證方式**：`grep -r "from '@ltdjms/games'" packages/ | grep -v "packages/games"` 確認無外部引用這些類型；`make build` 通過

#### P3-2: GameConfigManagementFacade 未使用的 eventPublisher

- **涉及檔案**：`packages/games/src/facades/GameConfigManagementFacade.ts`（L41）、`packages/games/src/di/games-module.ts`（DI 註冊處）
- **根因**：上次修復已從 facade 移除重複的 `eventPublisher.publish()` 呼叫，但未移除注入參數
- **修復方案**：從 `GameConfigManagementFacade` 建構子移除 `eventPublisher` 參數；從 `games-module.ts` 移除對應的 DI 注入
- **驗證方式**：`make build && make test` 通過

#### P3-3: Config Handler 未傳入 previousVersion

- **涉及檔案**：`packages/games/src/commands/dice-config-handlers.ts`（L50, L142）
- **根因**：Handler 呼叫 `upsertDice1Config(config)` 不帶 `previousVersion`，觸發 service 內部的 fallback 查詢
- **修復方案**：在 handler 中先查詢 current config 再傳入，或改為使用 `GameConfigManagementFacade`（已正確實作 `previousVersion` 模式）
- **驗證方式**：對照 facade 實作模式確認一致

#### P3-4: GameTokenManagementFacade.setTokens 雙重 findOrCreate

- **涉及檔案**：`packages/games/src/facades/GameTokenManagementFacade.ts`（L85, L102）
- **根因**：`getBalance` 和 `tryAdjustTokens` 各自內部呼叫 `findOrCreate`。第二次通常命中 cache
- **修復方案**：為 `tryAdjustTokens` 新增可選的 `knownCurrentBalance` 參數（比照 `DiceConfigService.upsert` 的 `previousVersion` 模式）
- **驗證方式**：`make test` 通過

#### P3-5: INSUFFICIENT_TOKENS 錯誤路徑雙重 findOrCreate

- **涉及檔案**：`packages/games/src/commands/dice-game-1-handler.ts`（L88-L101）、`dice-game-2-handler.ts`（L89-L102）
- **根因**：`tryDeductTokens` 內部 `findOrCreate` 在錯誤路徑中未將結果寫入 cache，handler 後續的 `getBalance` 再次觸發 DB 查詢
- **修復方案**：在 `tryAdjustTokens` 的 Err 路徑中也將當前餘額寫入 cache，或讓 Err 回傳值包含當前餘額供 handler 直接使用
- **驗證方式**：`make test` 通過

---

## 審查維度摘要

| 維度 | 結論 | 發現數 |
|------|------|--------|
| 無幻覺代碼 | 通過 — 所有 import、型別引用、DI token 均正確對應到真實存在的模組和定義 | 0 |
| 無冗余代碼 | 有保留意見 — 3 處跨 package 重複定義 (P2-1/2/3) | 3 |
| 無 spec 偏移 | 有保留意見 — 1 處合理的架構取捨導致與 spec 文字不一致 (P2-7) | 1 |
| 無 spec 遺漏 | 通過 — 所有 spec 明確要求的功能均已實作 | 0 |
| 無架構瑕疵 | 有保留意見 — 事件類型重複、common/ 跨邊界放置、dist 殘留 (P2-1/5/6) | 3 |
| 無性能隱患 | 有保留意見 — 骰子配置無快取 (P2-4)；3 處低頻路徑微優化 (P3-3/4/5) | 4 |
