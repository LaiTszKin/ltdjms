# Tasks: package-extraction

- Date: 2026-05-22
- Feature: package-extraction

## **Task 1: 建立 @ltdjms/games package 骨架**

Purpose: 建立新 package 的目錄結構、設定檔和 workspace 集成
Requirements: R1.1-R1.6
Scope: `packages/games/`, `pnpm-workspace.yaml`, root `tsconfig.json`, `apps/bot/`
Out of scope: 任何代碼遷移（僅建立空骨架）

- T1.1 [ ] **建立目錄結構** — `packages/games/src/{dice/services,dice/repositories,token/services,token/repositories,commands,domain,events,di,facades,panel}`
  - Verify: `ls packages/games/src/` 顯示所有子目錄

- T1.2 [ ] **建立 package.json** — name=`@ltdjms/games`，dependencies: `@ltdjms/shared`、`@ltdjms/economy`、`drizzle-orm`、`pg`，devDependencies 參考 economy
  - Verify: `cat packages/games/package.json | jq .name` → `"@ltdjms/games"`

- T1.3 [ ] **建立 tsconfig.json** — references: `../shared`、`../economy`，compilerOptions 參考 economy
  - Verify: `cat packages/games/tsconfig.json | jq .references`

- T1.4 [ ] **更新 pnpm-workspace.yaml** — 加入 `packages/games`
  - Verify: `grep "packages/games" pnpm-workspace.yaml`

- T1.5 [ ] **更新 root tsconfig.json** — references 陣列加入 `{ "path": "packages/games" }`
  - Verify: `cat tsconfig.json | grep "packages/games"`

- T1.6 [ ] **更新 apps/bot package.json** — dependencies 加入 `"@ltdjms/games": "workspace:*"`
  - Verify: `cat apps/bot/package.json | jq .dependencies | grep "@ltdjms/games"`

- T1.7 [ ] **更新 apps/bot tsconfig.json** — references 加入 `{ "path": "../packages/games" }`
  - Verify: `cat apps/bot/tsconfig.json | jq .references`

- T1.8 [ ] **pnpm install** — 安裝依賴並 link workspace packages
  - Verify: `pnpm install` exit code 0

## **Task 2: 遷移遊戲領域類型、事件和 Schema**

Purpose: 從 economy 拆分遊戲相關的 domain types、events、Drizzle schema 到 games
Requirements: R2.1-R2.6
Scope: `packages/games/src/domain/`, `packages/games/src/events/`, `packages/economy/src/domain/`, `packages/economy/src/events/`
Out of scope: 服務和 repository 遷移（Task 3）

- T2.1 [ ] **建立 games domain/types.ts** — 從 economy domain/types.ts 複製遊戲相關類型：GameTokenTransactionSource enum、GameTokenAccount、GameTokenTransaction、DiceGame1Config、DiceGame2Config、DiceGame1Result、DiceGame2Result、TokenAdjustmentResult、TransactionPage、domain constants（MAX_ADJUSTMENT_AMOUNT、TOKEN_CACHE_TTL、BALANCE_CACHE_TTL、DEFAULT_PAGE_SIZE、DICE_GAME_2_DICE_PER_TOKEN、isValidAdjustmentAmount）
  - Verify: `grep "GameTokenTransactionSource\|DiceGame1Config\|DiceGame2Config" packages/games/src/domain/types.ts`

- T2.2 [ ] **建立 games events/index.ts** — 從 economy events/index.ts 複製遊戲相關事件：GameTokenChangedEvent、DiceGameConfigChangedEvent、GameType enum
  - Verify: `grep "GameTokenChangedEvent\|DiceGameConfigChangedEvent\|GameType" packages/games/src/events/index.ts`

- T2.3 [ ] **建立 games domain/schema.ts** — 從 economy domain/schema.ts 複製遊戲表格定義：gameTokenAccount、gameTokenTransaction、diceGame1Config、diceGame2Config（含 Drizzle pgTable 定義和關係），建立獨立 drizzle 實例
  - Verify: `grep "gameTokenAccount\|diceGame1Config\|diceGame2Config" packages/games/src/domain/schema.ts`

- T2.4 [ ] **更新 economy domain/types.ts** — 移除遊戲相關類型，僅保留 CurrencyTransactionSource、GuildCurrencyConfig、MemberCurrencyAccount、CurrencyTransaction、BalanceView、BalanceAdjustmentResult
  - Verify: `grep "GameToken\|DiceGame" packages/economy/src/domain/types.ts` 返回空（除了 CurrencyTransactionSource 中的 DICE_GAME_1_WIN / DICE_GAME_2_WIN 枚舉值需要保留）

- T2.5 [ ] **更新 economy events/index.ts** — 僅保留 BalanceChangedEvent、CurrencyConfigChangedEvent
  - Verify: `grep "GameTokenChanged\|DiceGameConfigChanged\|GameType" packages/economy/src/events/index.ts` 返回空

- T2.6 [ ] **更新 economy domain/schema.ts** — 移除遊戲表格定義：gameTokenAccount、gameTokenTransaction、diceGame1Config、diceGame2Config 的 pgTable 定義和關係，僅保留 memberCurrencyAccount、currencyTransaction、guildCurrencyConfig
  - Verify: `grep "gameToken\|diceGame" packages/economy/src/domain/schema.ts` 返回空

## **Task 3: 遷移遊戲服務和 Repository**

Purpose: 將 dice/ 和 token/ 目錄完整遷移到 games package
Requirements: R3.1-R3.6
Scope: `packages/games/src/dice/`, `packages/games/src/token/`, `packages/economy/src/dice/`, `packages/economy/src/token/`
Out of scope: command handler（Task 4）、DI module（Task 5）

- T3.1 [ ] **移動 dice/services/** — 將 economy 的 dice/services/ 下所有 .ts 檔案移動到 games 對應目錄
  - Verify: `ls packages/games/src/dice/services/` 有 5 個檔案；`ls packages/economy/src/dice/services/` 為空

- T3.2 [ ] **移動 dice/repositories/** — 將 economy 的 dice/repositories/ 下所有 .ts 檔案移動到 games
  - Verify: `ls packages/games/src/dice/repositories/` 有檔案；economy 對應目錄為空

- T3.3 [ ] **移動 token/services/** — 將 economy 的 token/services/ 下所有 .ts 檔案移動到 games
  - Verify: `ls packages/games/src/token/services/` 有 2 個檔案（game-token-service.ts、game-token-tx-service.ts）

- T3.4 [ ] **移動 token/repositories/** — 將 economy 的 token/repositories/ 下所有 .ts 檔案移動到 games
  - Verify: `ls packages/games/src/token/repositories/` 有 2 個檔案（token-account-repo.ts、token-tx-repo.ts）

- T3.5 [ ] **更新 import 路徑** — 所有移動後的檔案：
  - `@ltdjms/shared` import 保持不變
  - 內部相對 import 更新（`../token/`、`../dice/`、`../currency/` → 正確的 games 內部路徑或跨 package import）
  - 對 `@ltdjms/economy` currency services 的 import 更新為跨 package import
  - 對 `@ltdjms/economy` common/ base class 的 import 更新
  - Verify: 無殘留錯誤的相對路徑 import

- T3.6 [ ] **token repos 更新 base class import** — token-account-repo.ts 和 token-tx-repo.ts 從 `@ltdjms/economy` 引入 BaseAccountRepository / BaseTransactionService
  - Verify: import 路徑正確指向 `@ltdjms/economy`

## **Task 4: 遷移遊戲 Command Handler**

Purpose: 將遊戲相關 slash command handler 遷移到 games package
Requirements: R4.1-R4.3
Scope: `packages/games/src/commands/`, `packages/economy/src/commands/`
Out of scope: BalanceHandler、CurrencyConfigHandler（保留在 economy）

- T4.1 [ ] **移動遊戲 handler** — 將 dice-game-1-handler.ts、dice-game-2-handler.ts、dice-config-handlers.ts、game-token-adjust-handler.ts 移動到 `packages/games/src/commands/`
  - Verify: `ls packages/games/src/commands/` 有 4 個 handler 檔案

- T4.2 [ ] **更新 handler import 路徑** — `@ltdjms/shared` 保持不變；`../dice/` → 正確的 games 內部路徑；`../token/`、`../currency/` → 更新
  - Verify: TypeScript 無 import 錯誤

- T4.3 [ ] **更新 economy commands/index.ts** — 僅保留 BalanceHandler、CurrencyConfigHandler 的 export
  - Verify: `grep "DiceGame\|GameTokenAdjust" packages/economy/src/commands/index.ts` 返回空

## **Task 5: 建立 games DI module 並更新 economy DI**

Purpose: 建立 games 的 DI 容器配置，從 economy DI 移除遊戲註冊
Requirements: R5.1-R5.6
Scope: `packages/games/src/di/games-module.ts`, `packages/economy/src/di/economy-module.ts`
Out of scope: admin DI（Task 9）

- T5.1 [ ] **建立 games DI module** — `packages/games/src/di/games-module.ts`：定義 GAMES_TOKENS（Repository、Service、Handler token），建立 configureGamesContainer() 函數
  - Verify: 檔案存在且有 export GAMES_TOKENS 和 configureGamesContainer

- T5.2 [ ] **games DI 註冊 repository** — 從 shared container resolve DatabasePool，建立 drizzle 實例，註冊 DiceConfigRepository、TokenAccountRepository、TokenTransactionRepository
  - Verify: GAMES_TOKENS 包含三個 repo token

- T5.3 [ ] **games DI 註冊服務** — 從 economy container resolve BalanceAdjustmentService、CurrencyConfigService。建立並註冊 GameTokenService、GameTokenTransactionService、GameRewardService、DiceGame1Service、DiceGame2Service、DiceConfigService
  - Verify: GAMES_TOKENS 包含六個服務 token

- T5.4 [ ] **games DI 註冊 handler** — 建立並註冊 DiceGame1Handler、DiceGame2Handler、DiceGame1ConfigHandler、DiceGame2ConfigHandler、GameTokenAdjustHandler
  - Verify: GAMES_TOKENS 包含五個 handler token

- T5.5 [ ] **更新 economy DI module** — 移除遊戲相關 import（dice/、token/、遊戲 handler），移除遊戲相關的服務/repository/handler 註冊程式碼，移除 ECONOMY_TOKENS 中的遊戲 token
  - Verify: `grep "DiceGame\|GameToken\|GameReward\|DiceConfig" packages/economy/src/di/economy-module.ts` 返回空（除了跨 package 邊界需要的極少數引用）

- T5.6 [ ] **更新 economy index.ts** — 移除 ECONOMY_TOKENS 中已刪除 token 的 re-export（如果 index 中沒有直接引用就不用改，TOKENS 已自動更新）

## **Task 6: 遷移 admin 端遊戲 Facade 和 Panel Handler**

Purpose: 將 admin 中的遊戲相關 facade 和 panel handler 遷移到 games
Requirements: R6.1-R6.5
Scope: `packages/games/src/facades/`, `packages/games/src/panel/`, `packages/admin/src/facades/`, `packages/admin/src/panel/admin/handlers/`
Out of scope: MemberInfoFacade、CurrencyManagementFacade（保留在 admin）

- T6.1 [ ] **移動 GameConfigManagementFacade** — 從 admin 移動到 `packages/games/src/facades/`
  - Verify: `ls packages/games/src/facades/GameConfigManagementFacade.ts`

- T6.2 [ ] **移動 GameTokenManagementFacade** — 從 admin 移動到 `packages/games/src/facades/`
  - Verify: `ls packages/games/src/facades/GameTokenManagementFacade.ts`

- T6.3 [ ] **移動 GameSettingsHandler** — 從 admin 移動到 `packages/games/src/panel/`
  - Verify: `ls packages/games/src/panel/GameSettingsHandler.ts`

- T6.4 [ ] **移動 TokenManagementHandler** — 從 admin 移動到 `packages/games/src/panel/`
  - Verify: `ls packages/games/src/panel/TokenManagementHandler.ts`

- T6.5 [ ] **更新移動後檔案的 import 路徑** — 更新對 `@ltdjms/economy`、`@ltdjms/shared`、games 內部相對路徑的 import
  - Verify: 無 import 錯誤

## **Task 7: 建立 @ltdjms/games public API**

Purpose: 建立 games package 的 index.ts 導出所有公共 API
Requirements: R8.1-R8.6
Scope: `packages/games/src/index.ts`
Out of scope: economy index.ts（已在 Task 5.6 處理）

- T7.1 [ ] **games index.ts** — 導出所有外部需要的類型：domain types、events、services（type-only）、command handlers（type-only）、facade（type-only）、GAMES_TOKENS 和 configureGamesContainer
  - Verify: `cat packages/games/src/index.ts` 包含所有必要的 export

## **Task 8: 更新 @ltdjms/economy public API**

Purpose: 更新 economy 的 public API 僅保留貨幣相關內容
Requirements: R7.1-R7.4
Scope: `packages/economy/src/index.ts`
Out of scope: 無

- T8.1 [ ] **更新 economy index.ts** — 移除所有遊戲相關的 type export 和 enum/const export（見 R7.1-R7.3 清單）
  - Verify: `grep "GameToken\|GameReward\|DiceGame\|DiceConfig" packages/economy/src/index.ts` 返回空（除了需要在 CurrencyTransactionSource 中保留的 DICE_GAME_1_WIN / DICE_GAME_2_WIN 枚舉值，但那是在 domain/types.ts 中定義，index.ts 只導出 enum 本身）

- T8.2 [ ] **economy index.ts 新增 common/ export** — 導出 BaseAccountRepository、BaseTransactionService 的 type（供 games 使用）
  - Verify: `grep "BaseAccountRepository\|BaseTransactionService" packages/economy/src/index.ts`

## **Task 9: 更新 @ltdjms/admin 依賴和 import**

Purpose: 更新 admin package 使其從 @ltdjms/games 取得遊戲相關類別
Requirements: R9.1-R9.6
Scope: `packages/admin/package.json`, `packages/admin/tsconfig.json`, `packages/admin/src/di/AdminModule.ts`, `packages/admin/src/facades/MemberInfoFacade.ts`
Out of scope: admin panel handlers（GameSettingsHandler、TokenManagementHandler 已移至 games，admin 不再直接引用）

- T9.1 [ ] **admin package.json** — dependencies 加入 `"@ltdjms/games": "workspace:*"`
  - Verify: `cat packages/admin/package.json | jq .dependencies | grep "@ltdjms/games"`

- T9.2 [ ] **admin tsconfig.json** — references 加入 `{ "path": "../games" }`
  - Verify: `cat packages/admin/tsconfig.json | jq .references`

- T9.3 [ ] **更新 AdminModule.ts import** — 遊戲相關類別從 `@ltdjms/games` import（GAMES_TOKENS、GameTokenService、DiceConfigService、GameTokenTransactionService、DiceGame1Handler、DiceGame2Handler 等），貨幣相關保持從 `@ltdjms/economy` import
  - Verify: 所有 import 正確，無 red squiggly

- T9.4 [ ] **更新 AdminModule.ts DI 註冊** — GameTokenManagementFacade、GameConfigManagementFacade 改從 GAMES_TOKENS resolve（或直接 new 從 games DI container）；slash command listener 中的遊戲 handler 改從 GAMES_TOKENS resolve；遊戲 panel handler（GameSettingsHandler、TokenManagementHandler）改從 GAMES_TOKENS resolve
  - Verify: AdminModule.ts 中所有遊戲相關 resolve 使用 GAMES_TOKENS

- T9.5 [ ] **更新 MemberInfoFacade import** — GameTokenService、GameTokenTransactionService 從 `@ltdjms/games` import
  - Verify: `grep "@ltdjms/games" packages/admin/src/facades/MemberInfoFacade.ts`

## **Task 10: 更新 apps/bot 啟動順序和驗收測試**

Purpose: 確保 bot 啟動時 DI 初始化順序正確，並通過所有測試
Requirements: R10.1-R10.3
Scope: `apps/bot/`, 所有測試檔案
Out of scope: 無

- T10.1 [ ] **更新 apps/bot main.ts** — 在 `configureEconomyContainer()` 之後加入 `configureGamesContainer()`，確保順序：shared → economy → games → admin
  - Verify: `grep "configureGamesContainer" apps/bot/src/main.ts`

- T10.2 [ ] **更新受影響的測試檔案** — 將使用 ECONOMY_TOKENS 遊戲 token 的測試更新為使用 GAMES_TOKENS；更新測試中的 import 路徑
  - Verify: `grep -r "ECONOMY_TOKENS.*Game\|ECONOMY_TOKENS.*Dice\|ECONOMY_TOKENS.*Token" packages/` 返回空

- T10.3 [ ] **make build** — 確保 TypeScript 編譯通過
  - Verify: `make build` exit code 0

- T10.4 [ ] **make test** — 確保全部測試通過
  - Verify: `make test` exit code 0，所有測試通過

- T10.5 [ ] **make verify** — 完整驗證
  - Verify: `make verify` exit code 0

- T10.6 [ ] **清理 economy 殘留** — 確認 `packages/economy/src/dice/`、`packages/economy/src/token/` 目錄已空，移除空目錄
  - Verify: `ls packages/economy/src/dice/` 和 `ls packages/economy/src/token/` 不存在或為空
