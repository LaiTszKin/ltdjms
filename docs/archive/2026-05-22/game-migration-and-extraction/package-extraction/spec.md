# Spec: package-extraction

- Date: 2026-05-22
- Feature: package-extraction
- Owner: laitszkin

## Goal

將所有遊戲相關代碼從 `@ltdjms/economy` 和 `@ltdjms/admin` 中抽出，建立獨立的 `@ltdjms/games` package，使遊戲邏輯與貨幣系統分離。

## Scope

### In Scope
- 建立 `packages/games/` 新 package（`@ltdjms/games`）
- 從 `@ltdjms/economy` 遷移 dice/、token/、遊戲 command handlers、遊戲 domain types、遊戲 events、遊戲 schema
- 從 `@ltdjms/admin` 遷移 GameConfigManagementFacade、GameTokenManagementFacade、GameSettingsHandler、TokenManagementHandler
- 拆分 `domain/schema.ts`（Drizzle 表格定義）為 economy 和 games 兩部分
- 更新 `@ltdjms/admin` 的 import 來源和 package.json 依賴
- 更新 `@ltdjms/economy` 的 public API（移除遊戲相關 export、新增 common/ base class export）
- 更新 `pnpm-workspace.yaml`、root `tsconfig.json`、`apps/bot` 的啟動順序

### Out of Scope
- 貨幣系統修改（BalanceService、BalanceAdjustmentService、CurrencyConfigService 保留在 economy）
- 非遊戲 admin panel handlers（BalanceManagementHandler、AI config、dispatch、product）
- MemberInfoFacade 和 UserPanelEmbedBuilder（這些同時使用貨幣和遊戲服務，保留在 admin）
- Shop、AI、Dispatch 模組
- 資料庫 migration 變更（表格結構不變，僅程式碼組織變更）
- DiceGameMessages 常數（已在 shared 中，不移動）

## Functional Behaviors (BDD)

### Requirement 1: 建立 @ltdjms/games package 骨架
**GIVEN** monorepo 具有 packages/ 目錄結構和 workspace 配置
**WHEN** 建立 `@ltdjms/games` package
**THEN** `packages/games/` 目錄存在且包含 `package.json`、`tsconfig.json`、`src/index.ts`
**AND** `package.json` name 為 `@ltdjms/games`，依賴 `@ltdjms/shared` 和 `@ltdjms/economy`
**AND** `tsconfig.json` 設定 project references 到 shared 和 economy
**AND** workspace 配置更新以包含新 package

**Requirements**:
- [x] R1.1 建立 `packages/games/` 目錄結構（src/dice/, src/token/, src/commands/, src/domain/, src/events/, src/di/）
- [x] R1.2 建立 `package.json`，name=`@ltdjms/games`，dependencies 含 `@ltdjms/shared`、`@ltdjms/economy`、`drizzle-orm`、`pg`
- [x] R1.3 建立 `tsconfig.json` 含 project references
- [x] R1.4 更新 `pnpm-workspace.yaml` 加入 `packages/games`
- [x] R1.5 更新 root `tsconfig.json` references 加入 `packages/games`
- [x] R1.6 更新 `apps/bot` 的 package.json 和 tsconfig.json 加入 games 依賴

### Requirement 2: 遷移遊戲領域類型、事件和 Schema
**GIVEN** 遊戲相關類型、事件和 Drizzle 表格定義存在於 `@ltdjms/economy`
**WHEN** 執行遷移
**THEN** 遊戲相關類型移至 `@ltdjms/games/src/domain/types.ts`
**AND** 遊戲相關事件移至 `@ltdjms/games/src/events/index.ts`
**AND** 遊戲 Drizzle 表格定義移至 `@ltdjms/games/src/domain/schema.ts`
**AND** `@ltdjms/economy` 僅保留貨幣相關內容

**Requirements**:
- [x] R2.1 建立 `@ltdjms/games/src/domain/types.ts`：含 GameTokenTransactionSource、GameTokenAccount、GameTokenTransaction、DiceGame1Config、DiceGame2Config、DiceGame1Result、DiceGame2Result、TokenAdjustmentResult、TransactionPage、domain constants（MAX_ADJUSTMENT_AMOUNT、TOKEN_CACHE_TTL、BALANCE_CACHE_TTL、DEFAULT_PAGE_SIZE、DICE_GAME_2_DICE_PER_TOKEN、isValidAdjustmentAmount）
- [x] R2.2 建立 `@ltdjms/games/src/events/index.ts`：含 GameTokenChangedEvent、DiceGameConfigChangedEvent、GameType enum
- [x] R2.3 建立 `@ltdjms/games/src/domain/schema.ts`：含 gameTokenAccount、gameTokenTransaction、diceGame1Config、diceGame2Config Drizzle 表格定義，建立獨立 drizzle 實例
- [x] R2.4 更新 `@ltdjms/economy/src/domain/types.ts`：移除遊戲相關類型，僅保留 CurrencyTransactionSource、GuildCurrencyConfig、MemberCurrencyAccount、CurrencyTransaction、BalanceView、BalanceAdjustmentResult
- [x] R2.5 更新 `@ltdjms/economy/src/events/index.ts`：僅保留 BalanceChangedEvent、CurrencyConfigChangedEvent
- [x] R2.6 更新 `@ltdjms/economy/src/domain/schema.ts`：移除遊戲表格定義（gameTokenAccount 等四表），僅保留貨幣表格

### Requirement 3: 遷移遊戲服務和 Repository
**GIVEN** dice/ 和 token/ 目錄存在於 `@ltdjms/economy/src/`
**WHEN** 執行遷移
**THEN** 完整 `dice/` 目錄移至 `@ltdjms/games/src/dice/`（services + repositories）
**AND** 完整 `token/` 目錄移至 `@ltdjms/games/src/token/`（services + repositories）
**AND** 所有 import 路徑更新為指向新位置

**Requirements**:
- [x] R3.1 移動 `dice/services/`：dice-game-1-service.ts、dice-game-2-service.ts、game-reward-service.ts、dice-config-service.ts、random.ts
- [x] R3.2 移動 `dice/repositories/`：dice-config-repo.ts
- [x] R3.3 移動 `token/services/`：game-token-service.ts、game-token-tx-service.ts
- [x] R3.4 移動 `token/repositories/`：token-account-repo.ts、token-tx-repo.ts
- [x] R3.5 所有檔案內部 import 更新：`@ltdjms/shared` 不變；內部相對 import 改為 games 內路徑；對 `@ltdjms/economy` 的 import 改為跨 package import（currency services、common base classes）
- [x] R3.6 token repos 從 `@ltdjms/economy` 的 common/ 引入 BaseAccountRepository、BaseTransactionService

### Requirement 4: 遷移遊戲 Command Handler
**GIVEN** 遊戲相關 slash command handler 存在於 `@ltdjms/economy/src/commands/`
**WHEN** 執行遷移
**THEN** DiceGame1Handler、DiceGame2Handler、DiceGame1ConfigHandler、DiceGame2ConfigHandler、GameTokenAdjustHandler 移至 `@ltdjms/games/src/commands/`
**AND** BalanceHandler、CurrencyConfigHandler 保留在 economy

**Requirements**:
- [x] R4.1 移動五個遊戲 handler 到 `@ltdjms/games/src/commands/`
- [x] R4.2 更新 handler 內部 import 路徑（從 `../dice/` → `../dice/` 路徑正確、從 economy 的 import 更新）
- [x] R4.3 `@ltdjms/economy` commands/index.ts 僅保留 BalanceHandler、CurrencyConfigHandler

### Requirement 5: 建立 games DI module
**GIVEN** 遊戲服務需要 DI 註冊
**WHEN** 建立 games DI module
**THEN** `@ltdjms/games/src/di/games-module.ts` 註冊所有遊戲 repository、服務、command handler
**AND** 從 shared container 取得 DatabasePool、CacheService、DomainEventPublisher
**AND** 從 economy container 取得 BalanceService、BalanceAdjustmentService、CurrencyConfigService

**Requirements**:
- [x] R5.1 建立 `GAMES_TOKENS` object，含所有遊戲相關 DI token（repository、服務、handler）
- [x] R5.2 建立 `configureGamesContainer()` 函數：建立 drizzle 實例、註冊 repository、服務、handler
- [x] R5.3 從 shared container resolve 共用基礎設施（DatabasePool、CacheService、CacheKeyGenerator、DomainEventPublisher）
- [x] R5.4 從 economy container resolve BalanceAdjustmentService、CurrencyConfigService（供 GameRewardService 和 game handler 使用）
- [x] R5.5 `@ltdjms/economy` di/economy-module.ts 移除遊戲相關 import 和註冊（保留 currency 部分）
- [x] R5.6 `@ltdjms/economy` di/economy-module.ts 移除 ECONOMY_TOKENS 中的遊戲 token（DiceConfigRepo、GameTokenService 等）

### Requirement 6: 遷移 admin 端遊戲 Facade 和 Panel Handler
**GIVEN** admin package 中的 GameConfigManagementFacade、GameTokenManagementFacade、GameSettingsHandler、TokenManagementHandler 屬於遊戲領域
**WHEN** 執行遷移
**THEN** 這些檔案移至 `@ltdjms/games` 的對應目錄

**Requirements**:
- [x] R6.1 移動 GameConfigManagementFacade 到 `@ltdjms/games/src/facades/`
- [x] R6.2 移動 GameTokenManagementFacade 到 `@ltdjms/games/src/facades/`
- [x] R6.3 移動 GameSettingsHandler 到 `@ltdjms/games/src/panel/`
- [x] R6.4 移動 TokenManagementHandler 到 `@ltdjms/games/src/panel/`
- [x] R6.5 更新這些檔案的 import 路徑

### Requirement 7: 更新 @ltdjms/economy public API
**GIVEN** economy 先前導出遊戲相關類型和服務
**WHEN** 完成遷移
**THEN** economy 的 index.ts 僅導出貨幣相關內容，並新增 common/ base class export

**Requirements**:
- [x] R7.1 更新 `@ltdjms/economy` index.ts：移除 GameTokenService、GameTokenTransactionService、GameRewardService、DiceConfigService 的 type export
- [x] R7.2 更新 `@ltdjms/economy` index.ts：移除 DiceGame1Handler、DiceGame2Handler、DiceGame1ConfigHandler、DiceGame2ConfigHandler、GameTokenAdjustHandler 的 type export
- [x] R7.3 更新 `@ltdjms/economy` index.ts：移除 GameTokenChangedEvent、DiceGameConfigChangedEvent、GameType、GameTokenTransactionSource、GameTokenTransaction、DiceGame1Config、DiceGame2Config、TokenAdjustmentResult、TransactionPage 的 export
- [x] R7.4 新增 common/ 相關 export（BaseAccountRepository、BaseTransactionService type）

### Requirement 8: 建立 @ltdjms/games public API
**GIVEN** games package 包含遊戲相關的所有類別
**WHEN** 建立 public API
**THEN** `@ltdjms/games` index.ts 導出所有外部需要的類型和值

**Requirements**:
- [x] R8.1 games index.ts 導出 domain types（GameTokenTransactionSource、相關 interface）
- [x] R8.2 games index.ts 導出 events（GameTokenChangedEvent、DiceGameConfigChangedEvent、GameType）
- [x] R8.3 games index.ts 導出 services type（GameTokenService、GameTokenTransactionService、GameRewardService、DiceConfigService）
- [x] R8.4 games index.ts 導出 command handler type（DiceGame1Handler、DiceGame2Handler、DiceGame1ConfigHandler、DiceGame2ConfigHandler、GameTokenAdjustHandler）
- [x] R8.5 games index.ts 導出 facade type（GameConfigManagementFacade、GameTokenManagementFacade）
- [x] R8.6 games index.ts 導出 DI（GAMES_TOKENS、configureGamesContainer）

### Requirement 9: 更新 @ltdjms/admin 依賴
**GIVEN** admin package 原先從 economy 取得遊戲相關類別
**WHEN** 完成遷移
**THEN** admin 從 `@ltdjms/games` 取得遊戲相關類別，從 `@ltdjms/economy` 取得貨幣相關類別

**Requirements**:
- [x] R9.1 admin package.json 新增 `@ltdjms/games` dependency
- [x] R9.2 admin tsconfig.json 新增 games project reference
- [x] R9.3 AdminModule.ts 更新 import 來源：遊戲相關從 `@ltdjms/games`，貨幣相關從 `@ltdjms/economy`
- [x] R9.4 AdminModule.ts 更新 GAMES_TOKENS replace ECONOMY_TOKENS 中的遊戲 token 引用
- [x] R9.5 MemberInfoFacade 同時依賴 `@ltdjms/games`（GameTokenService、GameTokenTransactionService）和 `@ltdjms/economy`（BalanceService、CurrencyTransactionService）
- [x] R9.6 EconomySlashCommands.ts 的 DiceGameMessages import 保持從 `@ltdjms/shared`

### Requirement 10: 更新 apps/bot 啟動順序
**GIVEN** bot 啟動時需要按順序初始化 DI container
**WHEN** 加入 games package
**THEN** 啟動順序為 shared → economy → games → admin

**Requirements**:
- [x] R10.1 apps/bot main.ts（或啟動腳本）在 `configureEconomyContainer()` 之後調用 `configureGamesContainer()`
- [x] R10.2 確保 games container 在 admin container 之前初始化
- [x] R10.3 apps/bot package.json 新增 `@ltdjms/games` dependency

## Error and Edge Cases
- [x] DI 初始化順序：games 依賴 economy 的 BalanceAdjustmentService 和 CurrencyConfigService，必須確保 economy container 先初始化
- [x] 兩個 drizzle 實例（economy + games）共用同一個 pg Pool：通過 shared TOKENS.DatabasePool 取得，不會重複建立連線池
- [x] 現有測試中使用 ECONOMY_TOKENS 的遊戲 token：更新測試 import 到 GAMES_TOKENS
- [x] @ltdjms/shop 若引用 economy 中的類型（如 CurrencyTransactionSource）：保留在 economy 中不變，不影響

## Clarification Questions
None

## References
- 現有 economy package 結構:
  - `packages/economy/src/dice/` — 遊戲邏輯服務和 repository
  - `packages/economy/src/token/` — 遊戲代幣服務和 repository
  - `packages/economy/src/commands/` — slash command handlers
  - `packages/economy/src/domain/types.ts` — 所有 domain types
  - `packages/economy/src/domain/schema.ts` — 所有 Drizzle schema
  - `packages/economy/src/events/index.ts` — 所有 domain events
  - `packages/economy/src/di/economy-module.ts` — DI container 配置
  - `packages/economy/src/index.ts` — public API
  - `packages/economy/src/common/` — BaseAccountRepository、BaseTransactionService
- 現有 admin package 結構:
  - `packages/admin/src/di/AdminModule.ts` — admin DI + import 來源
  - `packages/admin/src/facades/` — GameConfigManagementFacade、GameTokenManagementFacade
  - `packages/admin/src/panel/admin/handlers/` — GameSettingsHandler、TokenManagementHandler
- Workspace config:
  - `pnpm-workspace.yaml` — workspace packages
  - `tsconfig.json` — root project references
  - `apps/bot/` — bot 啟動入口
