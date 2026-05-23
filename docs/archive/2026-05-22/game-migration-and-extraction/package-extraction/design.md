# Design: package-extraction

- Date: 2026-05-22
- Feature: package-extraction
- Change Name: package-extraction

## Traceability

|                             |                                                                              |
| --------------------------- | ---------------------------------------------------------------------------- |
| Requirement IDs             | R1.1-R10.6                                                                   |
| In-scope modules            | `packages/games/` (new), `packages/economy/`, `packages/admin/`, `apps/bot/` |
| External systems touched    | None                                                                         |
| Batch coordination          | `../coordination.md`                                                         |

## Target vs baseline

|                       | Baseline (today) | Target (after this change) |
| --------------------- | ---------------- | --------------------------- |
| Structure / ownership | 遊戲代碼散落在 @ltdjms/economy (dice/、token/、commands/、domain/) 和 @ltdjms/admin (facades/、handlers/) | 所有遊戲代碼集中在 @ltdjms/games；economy 僅含貨幣系統；admin 從 games 取得遊戲服務 |
| Dependency chain       | admin → economy → shared | admin → games → economy → shared |
| Package count          | 6 packages | 7 packages (+games) |

## Boundaries

- Entry surface(s): Discord slash command → @ltdjms/games handler → game services → reply
- Trust boundary crossed: None
- Outside → inside: Discord user → `/dice-game-1` → DiceGame1Handler (in games) → DiceGame1Service (in games) → GameRewardService (in games) → BalanceAdjustmentService (in economy) → DB

## Modules

| Module key | Responsibility | Owned artifacts |
| ---------- | -------------- | --------------- |
| `games/dice` | 骰子遊戲邏輯（DiceGame1、DiceGame2）和獎勵發放 | services (5 files)、repositories (1 file) |
| `games/token` | 遊戲代幣帳戶管理和交易記錄 | services (2 files)、repositories (2 files) |
| `games/commands` | 遊戲相關 Discord slash command 處理 | 5 handler classes |
| `games/domain` | 遊戲領域類型、枚舉、常數、Drizzle schema | types.ts、schema.ts |
| `games/events` | 遊戲領域事件定義 | GameTokenChangedEvent、DiceGameConfigChangedEvent、GameType |
| `games/facades` | 遊戲管理面板的業務聚合層 | GameConfigManagementFacade、GameTokenManagementFacade |
| `games/panel` | 遊戲管理面板的 Discord 互動 handler | GameSettingsHandler、TokenManagementHandler |
| `games/di` | DI container 配置 | games-module.ts (GAMES_TOKENS + configureGamesContainer) |
| `economy/currency` | 貨幣帳戶、設定、交易（不變） | services、repositories |
| `economy/common` | 泛型 Repository/Service 基底類別（不變，供 games 引用） | BaseAccountRepository、BaseTransactionService |

---

## Interaction anchors (`INT-###`)

| ID        | Intent | Caller → Callee | Coupling kind | Information crossing | Failure propagation |
| --------- | ------ | --------------- | ------------- | -------------------- | ------------------- |
| `INT-001` | 遊戲發放獎勵 | GameRewardService (games) → BalanceAdjustmentService (economy) | sync call (DI resolve) | guildId、userId、rewardAmount、source enum | economy 服務失敗時向上拋出 DomainError |
| `INT-002` | 遊戲 handler 取得貨幣顯示資訊 | DiceGameHandler (games) → CurrencyConfigService (economy) | sync call (DI resolve) | guildId → currencyName、currencyIcon | 查詢失敗時使用預設值 |
| `INT-003` | 管理面板編輯遊戲設定 | GameSettingsHandler (games) → GameConfigManagementFacade (games) → DiceConfigService (games) | sync call | guildId、config values | 驗證失敗回傳 error message |
| `INT-004` | Token repo 使用共用基底 | TokenAccountRepository (games) → BaseAccountRepository (economy) | class inheritance (import) | Drizzle db instance、table schema | N/A（編譯期檢查） |
| `INT-005` | Admin module 註冊遊戲 handler | AdminModule (admin) → GAMES_TOKENS (games) | DI resolve | Handler instances | container resolve 失敗時 throw |

**Ordering / concurrency:** DI 初始化必須按 shared → economy → games → admin 順序。games 依賴 economy container 中已註冊的 BalanceAdjustmentService 和 CurrencyConfigService。

## Requirement linkage

### Package 骨架 → 領域遷移 → 服務遷移 → Handler 遷移 → DI → Admin 更新 → 驗收
- Anchor order: R1 (骨架) → R2 (領域) → R3 (服務) → R4 (handler) → R5 (DI) → R6 (facade) → R7+R8 (API) → R9 (admin 更新) → R10 (啟動+驗收)
- 必須按此順序：後續步驟依賴前面步驟建立的檔案和 import 路徑
- R2 和 R3 有內部依賴（服務 import 領域類型）
- R6 依賴 R5（facade import GAMES_TOKENS）
- R7/R8 可並行（互不依賴）

## Data & persistence

| Resource | Typical readers/writers | Consistency expectation |
| -------- | ----------------------- | ----------------------- |
| game_token_account 表 | GameTokenService (games) r/w, GameTokenManagementFacade (games) r/w | 與 economy currency_account 表同一個 PostgreSQL DB，共用 pg Pool |
| dice_game1_config 表 | DiceConfigService (games) r/w, GameConfigManagementFacade (games) r/w | 同上 |
| game_token_transaction 表 | GameTokenTransactionService (games) r/w | 同上 |
| PG Pool | shared container → economy drizzle + games drizzle（兩個 drizzle 實例，同一個 pool） | Pool 由 shared 管理，不重複建立 |

## Invariants

| Invariant | What breaks it | Symptoms if violated |
| --------- | -------------- | -------------------- |
| 兩個 drizzle 實例共用同一個 pg Pool | games 自己建立新的 Pool | 連線數翻倍、資源洩漏 |
| DI 初始化順序 shared → economy → games → admin | games 在 economy 之前初始化 | container resolve 失敗，bot 無法啟動 |
| economy 的 currency types 不變 | 刪除 currency 相關類型 | admin/shop/dispatch 的 import 失敗 |
| 現有測試的 mock 保持有效 | 測試 import 路徑未更新 | 測試失敗 |

## Tradeoffs

| Decision | Rejected alternative | Locks in |
| -------- | -------------------- | -------- |
| games 依賴 economy（而非反之） | 將 currency services 也移到 shared | economy 保持對貨幣的擁有權；games 是上層消費者 |
| common/ base class 留在 economy，games 跨 package import | 複製 base class 到 games 或移到 shared | 單一事實來源；games 對 economy 的依賴不僅是 currency services |
| 完整刪除 economy 中的遊戲檔案（不留 shim） | 保留 re-export shim | 更乾淨的邊界；所有 consumer（admin）同步更新 |
| 兩個獨立 drizzle 實例（economy + games）共用一個 pool | 單一 drizzle 實例跨 package 共享 | 每個 package 獨立管理自己的 schema；不產生跨 package 的 Drizzle 關係依賴 |

## Batch-only

訊息格式對齊（`../message-alignment/`）必須在本 spec 之前完成，以避免移動檔案後的 merge conflict。
