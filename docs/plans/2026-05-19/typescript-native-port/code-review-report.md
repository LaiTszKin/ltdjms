# Code Review Report: TypeScript Native Port

- **審查日期**: 2026-05-20
- **Spec 基準**: `docs/plans/2026-05-19/typescript-native-port/` (7 spec sets: preparation + 6 feature modules)
- **審查範圍**: Root infrastructure + `packages/shared/`, `packages/economy/`, `packages/shop/`, `packages/dispatch/`, `packages/ai/`, `packages/admin/`
- **審查方法**: 對每個模組的每個 TypeScript 原始碼檔案對照 spec requirements，從 6 個維度進行完整審查：
  1. **幻覺代碼** — 實作了 spec/Java 原版中不存在的功能，或引用不存在的 API
  2. **冗餘代碼** — 重複程式碼、死碼、未使用的 import / 常數
  3. **Spec 偏移** — 實作行為與 spec 要求或 Java 原版不一致
  4. **Spec 遺漏** — spec 中定義的需求完全沒有對應實作
  5. **架構瑕疵** — 錯誤的依賴方向、循環依賴、DI 設定錯誤、抽象層洩漏
  6. **性能隱患** — 缺少連線池、快取未命中、記憶體洩漏、阻塞操作

---

## 彙總統計

| 模組 | P0 | P1 | P2 | P3 | 合計 |
|------|----|----|----|----|------|
| preparation（根基礎設施） | 2 | 2 | 4 | 1 | 9 |
| shared-infrastructure | 0 | 4 | 5 | 2 | 11 |
| guild-economy | 0 | 1 | 4 | 6 | 11 |
| shop-payment | 4 | 4 | 6 | 4 | 18 |
| escort-dispatch | 2 | 4 | 5 | 3 | 14 |
| ai-chat-agent | 3 | 3 | 6 | 4 | 16 |
| administration | 6 | 5 | 6 | 3 | 20 |
| **合計** | **17** | **23** | **36** | **23** | **99** |

---

## 整體評估

### 業務滿足度判定

| 業務需求 | 狀態 | 說明 |
|----------|------|------|
| 貨幣/代幣/骰子遊戲功能與 Java 100% 一致 | **通過** | DiceGame1/2 核心演算法逐 byte 匹配，所有 Source enum 完整 |
| 商店瀏覽、貨幣購買、兌換碼功能 | **部分通過** | 核心功能存在，ECPay 加密編碼與 Java 不一致（P0） |
| ECPay 付款流程與 Java 一致 | **未通過** | `encodeURIComponent` vs `URLEncoder.encode` 產生不同 ciphertext |
| 護航派單 7 狀態機完整 | **通過** | 所有合法轉換正確實作，所有非法轉換正確拒絕 |
| AI 聊天路由與 Agent 工具 | **未通過** | Agent 工具執行循環完全缺失（P0） |
| 管理面板互動功能 | **未通過** | 子互動分派缺失，9 個 handler 中 8 個無權限檢查 |
| Discord 用戶可見輸出一致 | **部分通過** | 管理面板即時更新未實作，embed 欄位截斷在分頁路徑缺失 |

### 關鍵阻斷點

1. **shop-payment**: ECPay 加密使用 `encodeURIComponent` 而非 Java 的 `URLEncoder.encode`，對包含空白或特殊字元的交易參數會產出 ECPay 無法解密的 ciphertext
2. **ai-chat-agent**: Agent 工具執行循環完全未實作 — 17 個工具雖已定義並 DI 註冊，但 streaming loop 中無 tool call handling
3. **administration**: 管理面板的 9 個功能按鈕中，子互動（add/deduct/set modal、member select、product CRUD 等）完全無法觸發
4. **preparation**: CI pipeline 完全沒有 TypeScript job，所有 TS 程式碼在 CI 上未經驗證
5. **shop-payment**: 缺少跨語言 ECPay crypto 相容性測試 — 現有測試僅驗證 TS 內部 round-trip

---

## P0 — 阻斷性問題 (必須在整合前修正)

### P0-1: CI workflow 完全缺少 TypeScript pipeline
- **模組**: preparation
- **維度**: spec 遺漏
- **檔案**: `.github/workflows/ci.yml` (全檔 1-218 行)
- **Spec 參考**: P2.4 — "pnpm install → lint → typecheck → test (matrix Node.js 20/22)"
- **描述**: 現有 CI workflow 全部是 Java Maven pipeline（detect-changes, workflow-lint, pre-checks, unit-tests, integration-tests, ecpay-e2e-tests, performance-tests, property-based-tests）。完全沒有任何 TypeScript job：沒有 pnpm install、沒有 eslint、沒有 tsc --noEmit typecheck、沒有 Node 20/22 matrix 的 vitest run。整個 TypeScript monorepo 在 CI 上完全未被驗證。
- **修正**: 新增獨立的 `typescript-ci` job，包含 matrix `node-version: [20, 22]`，步驟為 checkout → pnpm/action-setup → `pnpm install --frozen-lockfile` → `pnpm eslint` → `pnpm -r exec tsc --noEmit` → `pnpm vitest run`

### P0-2: 根 vitest.config.ts 完全缺失
- **模組**: preparation
- **維度**: spec 遺漏
- **檔案**: `/vitest.config.ts` (不存在)
- **Spec 參考**: P2.2 — 根 vitest.config.ts 設定 Vitest workspace config，指向各 package
- **描述**: 根目錄無 vitest workspace config，`pnpm vitest run` 無法發現各 package 的測試。
- **修正**: 建立根 `vitest.config.ts`，使用 Vitest workspace 模式指向 `packages/*`

### P0-3: ECPay AES 加密使用 `encodeURIComponent` 而非 `URLEncoder.encode`
- **模組**: shop-payment
- **維度**: spec 偏移
- **檔案**: `packages/shop/src/crypto/ecpay-aes.ts:12,17`
- **Spec 參考**: R5 (ECPay 付款); coordination.md — "ECPay 加密/解密/CheckMacValue 演算法輸出逐 byte 一致"
- **描述**: TS 使用 `encodeURIComponent()` 對 plain JSON 做 URL 編碼後再 AES 加密，Java 使用 `URLEncoder.encode()`。兩者對多個字元類別產出不同結果：
  | 字元 | Java `URLEncoder.encode` | TS `encodeURIComponent` |
  |------|--------------------------|--------------------------|
  | 空白 | `+` | `%20` |
  | `*` | `%2A` | `*` (不變) |
  | `!` | `%21` | `!` (不變) |
  | `'` | `%27` | `'` (不變) |
  | `(` `)` `~` | 編碼 | 不編碼 |
  當 ItemName 或 TradeDesc 包含空白或特殊字元時，ECPay 將無法正確解密 ciphertext。
- **修正**: 替換為 Java-compatible URL encoder（`application/x-www-form-urlencoded` 規範），或使用 `new URLSearchParams({ key: value }).toString().split('=')[1]` 技巧

### P0-4: ECPay CheckMacValue 建構使用 `encodeURIComponent`
- **模組**: shop-payment
- **維度**: spec 偏移
- **檔案**: `packages/shop/src/crypto/ecpay-checkmac.ts:33`
- **Spec 參考**: R5, R8 (對帳)
- **描述**: `buildCheckMacValue` 使用 `encodeURIComponent(checkStr).toLowerCase()`，Java 使用 `URLEncoder.encode(builder.toString()).toLowerCase()`。對純英數參數結果相同，但若參數值包含 URL 保留字元則 hash 不同。影響對帳查單的正確性。
- **修正**: 同 P0-3，使用 Java-compatible URL encoder

### P0-5: ECPay AES 解密 URL 解碼與 Java 不完全匹配
- **模組**: shop-payment
- **維度**: spec 偏移
- **檔案**: `packages/shop/src/crypto/ecpay-aes.ts:42-43`
- **Spec 參考**: R5
- **描述**: 解密函數正確處理了 `+` → 空白轉換後再 `decodeURIComponent`，但若 ECPay 回應中包含 Java `URLEncoder` 會編碼但 `encodeURIComponent` 不編碼的字元，round-trip 會中斷。
- **修正**: 實作完整的 Java-compatible URL decoder

### P0-6: 缺少跨語言 ECPay Crypto 相容性測試
- **模組**: shop-payment
- **維度**: spec 遺漏
- **檔案**: `packages/shop/src/__tests__/ecpay-crypto.test.ts`
- **Spec 參考**: coordination.md — "ECPay 加密/解密/CheckMacValue 輸出逐 byte 一致"
- **描述**: 現有測試僅驗證 TS 內部 encrypt→decrypt round-trip，沒有比對 TS 輸出與 Java golden value。P0-3~P0-5 的編碼差異從未被測試捕捉。
- **修正**: 加入以 Java 產出的已知 plaintext/ciphertext pair 做 cross-check 的測試案例

### P0-7: AI Agent 工具執行循環完全未實作
- **模組**: ai-chat-agent
- **維度**: spec 遺漏
- **檔案**: `packages/ai/src/services/LangChainAIChatService.ts:174-203`
- **Spec 參考**: R6 (17 個 Discord 管理工具), R7 (工具授權), R8 (工具呼叫歷史)
- **描述**: `doStream()` 的 streaming loop 只處理 `chunk.content` 和 `reasoning_content`。當 AI model 呼叫 tool 時，LangChain.js 發出 `chunk.tool_call_chunks`，但 loop 中完全沒有 handler。這表示：模型決定呼叫工具 → LangChain.js 發出 tool call chunks → loop 忽略 → 工具從未被查詢、授權、執行 → 無 tool call history 記錄 → 模型停止生成。17 個已定義並 DI 註冊的工具從未被實際呼叫。
- **修正**: 在 `doStream()` 中實作完整的 LangChain.js tool-calling agent loop：
  1. 處理 `chunk.tool_call_chunks` 累積工具呼叫參數
  2. 依名稱查詢 DI 註冊的工具實例
  3. 執行 `ToolCallerAuthorizationGuard.validateAdministrator()` 授權檢查
  4. 執行工具並取得結果
  5. 呼叫 `ToolExecutionInterceptor` 生命週期方法
  6. 呼叫 `InMemoryToolCallHistory.addToolCall()` 記錄
  7. 以 ToolMessage 將結果送回模型繼續生成
  8. 限制最大迭代次數（AGENT_MAX_ITERATIONS = 5）

### P0-8: ToolExecutionInterceptor 定義但從未被 wired
- **模組**: ai-chat-agent
- **維度**: spec 遺漏
- **檔案**: `packages/ai/src/services/ToolExecutionInterceptor.ts:14-15`
- **Spec 參考**: R8 (工具執行審計)
- **描述**: `ToolExecutionInterceptor` 類別完整實作了 `onToolExecutionStarted/Completed/Failed`，但類別本身有 TODO 註解 "Wire this interceptor into the agent flow"。它從未被 DI 註冊、從未被注入、從未被呼叫。`ai_tool_execution_log` 表格將永遠保持空白。
- **修正**: 在 DI 模組中實例化 interceptor，傳入 agent execution loop，在每個工具執行前後呼叫其生命週期方法

### P0-9: InMemoryToolCallHistory.addToolCall() 從未被呼叫
- **模組**: ai-chat-agent
- **維度**: spec 遺漏
- **檔案**: `packages/ai/src/services/memory/tool-call-history.ts`
- **Spec 參考**: R8 (工具呼叫歷史 — "track tool calls per conversation")
- **描述**: `addToolCall()` 方法已定義並有單元測試，但在整個 production codebase 中 zero call sites。`SimplifiedChatMemoryProvider.getToolCallMessages()` 永遠回傳空陣列。
- **修正**: 在 agent execution loop 中每個工具執行後呼叫 `history.addToolCall()`

### P0-10: 管理面板子互動分派完全缺失
- **模組**: administration
- **維度**: spec 偏移 / spec 遺漏
- **檔案**: `packages/admin/src/panel/admin/handlers/BalanceManagementHandler.ts:32-67` 及所有其他 handler
- **Spec 參考**: R2-R8（各種管理面板互動功能）
- **描述**: 管理面板 view factory 定義了子按鈕如 `admin_balance_add`、`admin_balance_deduct`、`admin_balance_set`。`SlashCommandListener` 使用最長前綴匹配，所以點擊這些子按鈕都路由到 `BalanceManagementHandler`（前綴 `admin_balance`）。但 handler 的 `execute()` 永遠只顯示餘額 view，不讀取 `customId` 後綴來決定動作。無 modal 彈出、無實際操作執行。同樣問題存在於 token、game、product、AI channel、AI agent、dispatch、escort 等 8 個 handler。
- **修正**: 每個 handler 必須檢查完整的 `customId` 並分支：基礎前綴 → 顯示 view；子動作後綴 → 顯示 modal；modal 提交 → 執行操作並更新面板

### P0-11: 管理面板無成員選擇步驟（顯示 admin 自己的餘額）
- **模組**: administration
- **維度**: spec 偏移
- **檔案**: `packages/admin/src/panel/admin/handlers/BalanceManagementHandler.ts:48-67`, `TokenManagementHandler.ts:38-55`
- **Spec 參考**: R2（選擇用戶 → 顯示餘額 → 調整）, R3（選擇用戶 → 調整代幣）
- **描述**: Balance 和 Token handler 直接查詢互動者自己的餘額並顯示。Spec 要求先提示 admin 選擇一個成員，再顯示該成員的餘額/代幣，然後提供調整選項。流程中完全沒有成員選擇選單或輸入欄位。
- **修正**: 初次點擊時顯示成員選擇選單，選擇後查詢該成員餘額，將會員 ID 儲存在 session context 中

### P0-12: TransactionHistoryHandler 前綴錯誤 — 所有歷史按鈕都顯示貨幣歷史
- **模組**: administration
- **維度**: spec 偏移 / spec 遺漏
- **檔案**: `packages/admin/src/panel/user/handlers/TransactionHistoryHandler.ts:16`
- **Spec 參考**: R9（用戶面板 — 交易歷史分頁）
- **描述**: 用戶面板定義了 3 個不同按鈕：`user_currency_history`、`user_token_history`、`user_redemption_history`。Handler 前綴 `user_history` 匹配全部三個。`execute()` 只呼叫 `getCurrencyTransactionPage()`。代幣歷史和兌換歷史按鈕完全無效 — 它們顯示的是貨幣交易記錄。
- **修正**: 拆分為 3 個獨立 handler，或一個 handler 檢查完整 customId 決定查詢類型

### P0-13: RedemptionCodeHandler 不開啟兌換碼輸入 modal
- **模組**: administration
- **維度**: spec 偏移
- **檔案**: `packages/admin/src/panel/user/handlers/RedemptionCodeHandler.ts:23-71`
- **Spec 參考**: R9（兌換碼輸入）
- **描述**: 點擊「輸入兌換碼」(`user_redeem_code`) 路由到 `RedemptionCodeHandler`，但 handler 只顯示兌換歷史預覽。從不開啟 modal 讓用戶輸入兌換碼。i18n 字串 `redeemCodeModalTitle`、`redeemCodeLabel`、`redeemCodePlaceholder` 存在但從未被使用。
- **修正**: 在 `user_redeem_code` 點擊時顯示含文字輸入的 modal，提交時呼叫 `memberInfoFacade.redeemCode()` 並顯示結果

### P0-14: 事件監聽器從不推送即時更新到面板
- **模組**: administration
- **維度**: spec 遺漏
- **檔案**: `packages/admin/src/panel/listeners/AdminPanelUpdateListener.ts:51-70`, `UserPanelUpdateListener.ts:63-105`
- **Spec 參考**: R11（即時更新 via event listeners）
- **描述**: 兩個 listener 都確認事件並檢查哪些 session 應更新，但只做 `console.log()`。註解寫著 "this requires the session model to store interaction hooks / channel IDs." 管理面板在資料變更後永不刷新，用戶必須手動重開面板。
- **修正**: 擴展 session 資料儲存 interaction token 或 channel+message ID。在 listener 中查詢新資料後呼叫 `interaction.editReply()` 或 `channel.messages.edit()` 推送更新

### P0-15: 9 個 admin panel handler 中 8 個缺少權限檢查
- **模組**: administration
- **維度**: spec 遺漏
- **檔案**: 8 個 handler（TokenManagementHandler, GameSettingsHandler, AIChannelConfigHandler, AIAgentConfigHandler, DispatchAfterSalesHandler, EscortPricingHandler, EscortCatalogHandler, AdminProductPanelHandler）
- **Spec 參考**: R1（`/admin-panel` 是 ADMINISTRATOR-only；所有子互動必須繼承此限制）
- **描述**: 只有 `BalanceManagementHandler` 繼承 `BaseAdminHandler`（包含 `checkAdminPermission()`）。其他所有 admin panel handler 直接實作 `InteractionHandler`，無任何權限檢查。任何取得 button customId 的一般成員都能與 admin 子功能互動。
- **修正**: 所有 admin panel handler 必須繼承 `BaseAdminHandler` 並在 `execute()` 開頭呼叫 `checkAdminPermission(interaction)`

### P0-16: 護航 Dispatch panel 按鈕啟用邏輯錯誤
- **模組**: escort-dispatch
- **維度**: spec 偏移 / spec 遺漏
- **檔案**: `packages/dispatch/src/panel/DispatchPanelInteractionHandler.ts:545-566`
- **Spec 參考**: 售後生命週期（Claim After-Sales Case, Close After-Sales Case）
- **描述**: `handleOrderSelected` 接收 `userId` 但命名為 `_userId`（刻意不使用）。按鈕啟用邏輯使用單一 `canRequestAfterSales` 旗標控制全部三個售後按鈕，產生三個錯誤：
  1. 「申請售後」按鈕出現在 `AFTER_SALES_REQUESTED`、`AFTER_SALES_IN_PROGRESS`、`AFTER_SALES_CLOSED` 狀態（service 會拒絕）
  2. 「接手售後」按鈕出現在 `COMPLETED`、`AFTER_SALES_IN_PROGRESS`、`AFTER_SALES_CLOSED`（只有 `AFTER_SALES_REQUESTED` 有效）
  3. 「結案」按鈕對所有用戶顯示，不論是否為 assignee
- **修正**: 替換為每個動作獨立的狀態檢查，恢復 `userId` 參數用於 `isAfterSalesAssignee` 檢查

### P0-17: EscortOptionCatalogRepository stub 阻斷所有手動開單
- **模組**: escort-dispatch
- **維度**: spec 偏移
- **檔案**: `packages/dispatch/src/di/dispatch-module.ts:80-88`; `packages/dispatch/src/service/escort-dispatch-order.service.ts:87-98`
- **Spec 參考**: CL-03（catalog validation on order creation）
- **描述**: DI container 註冊的 stub `EscortOptionCatalogRepository` 對所有查詢回傳 empty/false。`createManualOpenOrder` 在 catalog repository 存在時檢查 `existsByCode`，stub 回傳 `false` 導致 `!exists` 為 `true`，每次都回傳「護航品類無效」錯誤。所有手動開單操作完全被阻斷。
- **修正**: 要麼讓 stub 在開發期間對所有 code 回傳 `true`，要麼在 catalog 實作完成前移除 catalog 檢查，要麼完全不注入 stub catalog 到 order service

---

## P1 — 嚴重問題 (整合前應修正)

### P1-1: ESLint flat config 引用未安裝的 `typescript-eslint` 套件
- **模組**: preparation
- **維度**: 幻覺代碼
- **檔案**: `eslint.config.mjs:1`; `package.json:16-18`
- **Spec 參考**: P2.1
- **描述**: `eslint.config.mjs` import `typescript-eslint`（flat config 一體化套件），但 `package.json` devDependencies 中只有舊版拆分的 `@typescript-eslint/parser` 和 `@typescript-eslint/eslint-plugin`。執行 eslint 會因模組找不到而失敗。
- **修正**: 新增 `"typescript-eslint": "^8.0.0"` 到 devDependencies

### P1-2: `.env.example` 資料庫名稱與 `application.properties` 不一致
- **模組**: preparation
- **維度**: spec 偏移
- **檔案**: `.env.example:22,27`; `src/main/resources/application.properties:10,18`
- **Spec 參考**: P1.6
- **描述**: `.env.example` 使用 `DATABASE_NAME=ltdjms`，但 `application.properties` 的 canonical default 是 `currency_bot`。開發者按 `.env.example` 建立資料庫會產生名稱不匹配。
- **修正**: 將 `.env.example` 中的資料庫名稱改為 `currency_bot`

### P1-3: DiscordEmbedBuilder 分頁路徑中 field value 未被截斷
- **模組**: shared-infrastructure
- **維度**: spec 偏移
- **檔案**: `packages/shared/src/discord/services/embed-pagination.ts:109-113`
- **Spec 參考**: R8.3 — field value ≤ 1024 chars
- **描述**: `paginateEmbedView()` 直接從 `EmbedView` 複製 field 資料到 `builder.addFields()`，完全繞過 `DiscordJsEmbedBuilder.addField()` 中的截斷邏輯。超過 1024 字元的 field value 會在 runtime 觸發 Discord API 400 錯誤。
- **修正**: 在 `paginateEmbedView()` 中呼叫已注入的 `truncate` callback 處理 field value

### P1-4: SchemaMigrationException 被用於連線失敗（語義錯誤）
- **模組**: shared-infrastructure
- **維度**: spec 偏移
- **檔案**: `packages/shared/src/infra/database/connection.ts:43`
- **Spec 參考**: R4.3, R4.4
- **描述**: `createDatabasePool()` 在資料庫連線失敗（非 migration 失敗）時拋出 `SchemaMigrationException`。語義錯誤導致呼叫者無法區分連線錯誤與 migration 錯誤。
- **修正**: 拋出一般 `Error` 或新增 `DatabaseConnectionException`，保留 `SchemaMigrationException` 專用於 migration 失敗

### P1-5: RedisCacheService 靜默吞下所有 Redis 錯誤
- **模組**: shared-infrastructure
- **維度**: spec 偏移
- **檔案**: `packages/shared/src/infra/cache/redis-cache-service.ts:19-21,31,44,53,67`
- **Spec 參考**: R5
- **描述**: 所有 `catch` 區塊為空，Redis `error` 事件 handler 為空 callback。Redis 不可用時完全無觀測性 — 無日誌、無 metric、無告警。Spec 要求 "graceful degradation"（不拋例外），不是 "silent degradation"（不記錄）。
- **修正**: 注入 logger，在每個 catch 區塊中以 warn/error 等級記錄

### P1-6: DomainEvent.guildId 型別為 `number` 但 Discord.js 使用 `string`
- **模組**: shared-infrastructure
- **維度**: spec 偏移
- **檔案**: `packages/shared/src/types/events/domain-event.ts:8`
- **Spec 參考**: R6.3
- **描述**: `DomainEvent` 基礎介面宣告 `guildId: number`，但 Discord.js 的所有 ID 都是 `string`。Discord Snowflake 可達 19 位數字，強制轉 `number` 會超過 `MAX_SAFE_INTEGER` 導致精度遺失。
- **修正**: 將所有 `DomainEvent` 子型別的 `guildId` 從 `number` 改為 `string`

### P1-7: GameTokenService.getBalance 測試呼叫錯誤的 repository 方法
- **模組**: guild-economy
- **維度**: 幻覺代碼
- **檔案**: `packages/economy/src/__tests__/game-token-service.test.ts:56-88,91-118`
- **Spec 參考**: R4 (GameTokenService)
- **描述**: 兩個測試 mock `findOrCreate`，但實際 `GameTokenService.getBalance()` 呼叫 `findByGuildIdAndUserId`。運行時 `this.accountRepository.findByGuildIdAndUserId` 為 `undefined` 導致 `TypeError`。測試產生 false positives。
- **修正**: 更新 `GameTokenService.getBalance()` 呼叫 `findOrCreate`（對齊 spec 的 auto-create 要求），或更新測試使用正確方法名

### P1-8: ECPay callback 儲存解密後資料而非原始 HTTP body
- **模組**: shop-payment
- **維度**: spec 偏移
- **檔案**: `packages/shop/src/services/fiat-payment-callback.service.ts:49,84-85`
- **Spec 參考**: R6 (Payment callback)
- **描述**: Java 中 `callbackPayload` 是原始 HTTP request body（截斷至 4000 字元）。TS 中 `callbackPayload` 是解密後的 JSON 字串。`last_callback_payload` 資料庫欄位在 TS 和 Java 部署之間包含根本不同的內容，破壞審計追蹤一致性。
- **修正**: 儲存原始 HTTP body（截斷至 4000 字元）作為 `callbackPayload`，與 Java 一致

### P1-9: Callback server 使用 `req.body` 而非原始 body bytes
- **模組**: shop-payment
- **維度**: spec 偏移
- **檔案**: `packages/shop/src/web/ecpay-callback-server.ts:61`
- **Spec 參考**: R6
- **描述**: `express.json()` middleware 解析 body 後，`req.body` 是 object 而非原始字串。`JSON.stringify(req.body)` 產生重新序列化的版本，非原始 HTTP body。ECPay 有時發送非標準結構的 form-urlencoded 資料，parse→re-stringify round trip 可能破壞資料。
- **修正**: 使用 `express.raw({ type: '*/*' })` middleware 在 JSON/urlencoded parser 之前取得 raw Buffer

### P1-10: FiatOrder domain model 暴露 repository 層實作細節
- **模組**: shop-payment
- **維度**: 架構瑕疵
- **檔案**: `packages/shop/src/domain/fiat-order.ts:51-53`
- **Spec 參考**: R4 (FiatOrder domain model)
- **描述**: TS `FiatOrderSchema` 包含 `fulfillmentProcessingAt`、`adminNotificationProcessingAt`、`reconciliationProcessingAt` 作為 domain 欄位。Java 中這些欄位只存在於資料庫，由 `JdbcFiatOrderRepository` 獨佔管理，不是 `FiatOrder` domain record 的一部分。
- **修正**: 從 TS domain type 移除這些欄位，僅在 repository 中管理（匹配 Java），或更新 Java domain record 也包含它們以保持一致

### P1-11: 對帳 worker 將訂單過期與對帳耦合
- **模組**: shop-payment
- **維度**: spec 偏移
- **檔案**: `packages/shop/src/services/fiat-payment-reconciliation.service.ts:36-44`
- **Spec 參考**: R8 (Reconciliation worker)
- **描述**: TS `reconcilePendingOrders` 在每次對帳 tick 呼叫 `expirePendingOrders`。若對帳被停用或延遲，過期也不會觸發。Java 中過期通常由獨立的排程 job 處理。
- **修正**: 將過期邏輯分離為獨立排程任務，或確保過期檢查對時鐘偏移具有穩健性

### P1-12: AI thread  category 解析使用錯誤的 parentId
- **模組**: ai-chat-agent
- **維度**: spec 偏移
- **檔案**: `packages/ai/src/commands/ai-chat-mention-listener.ts:93-97`
- **Spec 參考**: R2（thread 繼承父頻道設定）
- **描述**: Listener 對 thread channel 使用 `message.channel.parentId` 作為 `categoryId`，但 thread 的 `parentId` 是父文字頻道 ID，不是分類 ID。已存在正確的 `resolveCategoryId()` 工具函數但從未被 import 或使用。導致分類層級 allowlist 匹配對 thread 頻道失效。
- **修正**: Import 並使用 `resolveCategoryId()` from `routing-decision.ts`

### P1-13: AI Agent CONTENT chunks 繞過 markdown validation
- **模組**: ai-chat-agent
- **維度**: spec 偏移
- **檔案**: `packages/ai/src/commands/ai-chat-mention-listener.ts:217-221`
- **Spec 參考**: R10 (markdown pipeline)
- **描述**: 啟用 markdown validation 且 streamingBypass 關閉時，`MarkdownValidatingAIChatService` 對每個 streaming CONTENT chunk 個別套用 sanitize/validate/autofix/paginate pipeline。但每個獨立 chunk 是不完整的 markdown 片段，pipeline 無法對不完整內容產生正確結果。
- **修正**: 先累積所有 CONTENT chunks，對完整內容套用 pipeline 後再分頁一次

### P1-14: MemberInfoFacade 以 raw SQL 繞過 service layer
- **模組**: administration
- **維度**: 架構瑕疵
- **檔案**: `packages/admin/src/facades/MemberInfoFacade.ts:175-235`
- **Spec 參考**: R10（Facade 層 — 聚合，不實作持久化邏輯）
- **描述**: `getProductRedemptionTransactionPage()` 在執行期 lazy import `@ltdjms/shared` container，解析 `TOKENS.DatabasePool`，直接執行 raw SQL。Facade 直接依賴 DI container 和資料庫連線，違反 thin aggregation layer 合約。
- **修正**: 將查詢邏輯移至 shop package 的專用 repository，透過 service 方法暴露，以 constructor injection 注入 MemberInfoFacade

### P1-15: Admin panel session TTL cleanup 從未被排程呼叫（記憶體洩漏）
- **模組**: administration
- **維度**: 性能隱患
- **檔案**: `packages/admin/src/session/AdminPanelSessionManager.ts:171-180`, `PanelSessionManager.ts:107-116`
- **Spec 參考**: R11（session TTL, cleanup runs）
- **描述**: 兩個 session manager 都定義了 `cleanupExpired()` 但從未被定時呼叫。只在 `getActiveSessionCount()` 中呼叫。過期 session 在 Map 中無限累積。在高流量 guild 中一次性面板使用後，過期 session 永遠不會被清理。
- **修正**: 在 `configureAdminContainer()` 中以 `setInterval(cleanup, 5 * 60 * 1000)` 啟動定時清理

### P1-16: Token 調整不發布 GameTokenChangedEvent
- **模組**: administration
- **維度**: spec 遺漏
- **檔案**: `packages/admin/src/facades/GameTokenManagementFacade.ts:50-101`
- **Spec 參考**: R3（Token management — GameTokenChangedEvent）
- **描述**: `adjustTokens()` 和 `setTokens()` 呼叫 `tokenService.tryAdjustTokens()` 但從不發布 `GameTokenChangedEvent`。對比 `GameConfigManagementFacade` 正確地在更新後發布 `DiceGameConfigChangedEvent`。
- **修正**: 注入 `DomainEventPublisher`，在 token 調整成功後發布 `GameTokenChangedEvent`

### P1-17: 事件型別判別使用脆弱的 duck typing
- **模組**: administration
- **維度**: 架構瑕疵
- **檔案**: `packages/admin/src/panel/listeners/AdminPanelUpdateListener.ts:169-203`, `UserPanelUpdateListener.ts:123-133`
- **Spec 參考**: R11（event listeners）
- **描述**: 使用 property existence checks 如 `'newBalance' in event`、`'productId' in event && 'count' in event` 判別事件型別。兩個不同事件型別可能共享相同 property 名稱導致錯誤匹配。檢查順序敏感且不明顯。
- **修正**: 在所有 domain event 加入 discriminant property（如 `eventType`），以 switch 替代 property sniffing

### P1-18: 護航 `withCompleted` 清空 Java 保留的售後欄位
- **模組**: escort-dispatch
- **維度**: spec 偏移
- **檔案**: `packages/dispatch/src/domain/escort-dispatch-order.ts:400-414`
- **Spec 參考**: 狀態機 immutability contract
- **描述**: TS `withCompleted` 明確將 `afterSalesAssigneeUserId`、`afterSalesAssignedAt`、`afterSalesClosedAt` 設為 null。Java `withCompleted` 保留所有欄位。當前在 `COMPLETED` 只從 `PENDING_CUSTOMER_CONFIRMATION` 可達（所有售後欄位已是 null）所以是 functional no-op，但若未來狀態機增加從售後狀態進入 COMPLETED 的路徑，TS 會靜默遺失資料。
- **修正**: 匹配 Java 行為 — 移除明確的 null 賦值，讓 `...order` spread 保留原始值

### P1-19: 護航 panel `isCompleted` 同時匹配 COMPLETED 和 AFTER_SALES_CLOSED
- **模組**: escort-dispatch
- **維度**: spec 偏移
- **檔案**: `packages/dispatch/src/panel/DispatchPanelInteractionHandler.ts:564`
- **Spec 參考**: 售後生命週期
- **描述**: `isCompleted(order)` 對 `COMPLETED` 和 `AFTER_SALES_CLOSED` 都回傳 `true`。使 `canRequestAfterSales` 對已結案的售後案件為 true，顯示「申請售後」按鈕。Service 正確拒絕請求，但按鈕不該出現。
- **修正**: 以 P0-16 的修正涵蓋（替換為明確狀態比較）

### P1-20: 護航 barrel export 缺少售後按鈕/選單常數
- **模組**: escort-dispatch
- **維度**: spec 遺漏
- **檔案**: `packages/dispatch/src/index.ts:102-163`
- **Spec 參考**: Panel integration API
- **描述**: 主 `index.ts` 缺少 `BUTTON_CLAIM_AFTER_SALES`、`BUTTON_CLOSE_AFTER_SALES`、`SELECT_ESCORT_OPTION_EXTRA`、`SELECT_PENDING_ORDER` 及相關 builder 函數的 re-export。外部 consumer import from `@ltdjms/dispatch` 無法存取這些符號。
- **修正**: 在 `src/index.ts` 的 panel section 加入缺少的 exports

### P1-21: 護航 `handleOrderSelected` 缺少從訂單列表選擇訂單的功能
- **模組**: escort-dispatch
- **維度**: spec 遺漏
- **檔案**: `packages/dispatch/src/panel/DispatchPanelInteractionHandler.ts:160-167`
- **Spec 參考**: Panel workflow
- **描述**: `BUTTON_VIEW_ORDERS` 或 `BUTTON_VIEW_HISTORY` 點擊時顯示訂單列表 embed，但沒有 select menu 或按鈕讓用戶選擇特定訂單查看詳情。用戶可以看到列表但無法深入查看個別訂單。
- **修正**: 在近期訂單/歷史 views 加入 select menu 或 per-order 按鈕

### P1-22: ESLint flat config 中 `tseslint.configs.recommended` 的 spread 語法錯誤
- **模組**: preparation
- **維度**: 架構瑕疵
- **檔案**: `eslint.config.mjs:7`
- **描述**: `...tseslint.configs.recommended` — `configs.recommended` 可能已經是陣列或單一 config object，直接 spread 到頂層陣列可能產生巢狀結構而非平坦的 config array。
- **修正**: 確認 `typescript-eslint` 版本對應的 flat config 正確用法，必要時使用 `tseslint.config(...)` helper

---

## P2 — 次要問題

### preparation (根基礎設施)

| # | 維度 | 檔案 | 描述 | 修正 |
|---|------|------|------|------|
| P2-1 | spec 偏移 | `Makefile:4,7,10,13` | Makefile 預設 target (`build`, `test`, `format`) 指向 Maven，非 spec 要求的 TypeScript 工具。TS 命令被放在 `ts-*` 前綴 target。 | 將預設 target 改為 TypeScript 命令，Maven 移至 `mvn-*` 前綴 |
| P2-2 | spec 遺漏 | `packages/shared/db/` | P3.2 Schema 清單文件不存在（28 個 migration、約 18 張 table 的完整欄位清單） | 從 migration SQL 提取 CREATE TABLE 語句產出 `SCHEMA.md` |
| P2-3 | spec 偏移 | `.env.example:73-74` | `APP_PUBLIC_DOMAIN`、`CADDY_ACME_EMAIL` 等 Caddy/Docker 變數不在 `application.properties` 中 | 保留但標註來源（Docker/Caddy 組態） |
| P2-4 | spec 遺漏 | `package.json:7` | `format` script glob `packages/*/src/**/*.ts` 不覆蓋根目錄設定檔和 package 層級 config | 擴展 glob 或改為 `**/*.{ts,mjs,json}` |

### shared-infrastructure

| # | 維度 | 檔案 | 描述 | 修正 |
|---|------|------|------|------|
| P2-5 | 性能 | `migration-runner.ts:18-37` | Migration runner 無重試邏輯（連線池有重試但 migration 本身沒有） | 以 3 次重試 + backoff 包裝 `migrate()` 呼叫 |
| P2-6 | 架構 | `discord-interaction.ts:18-22` | `replyEmbed(embed: unknown)`、`editEmbed(embed: unknown)` 使用 `unknown` 喪失型別安全 | 參數化介面或定義 minimal `EmbedData` interface |
| P2-7 | spec 偏移 | `migration-runner.ts:33` | Migration runner 使用 `console.log()` 而非 pino structured logger | 注入 pino.Logger 並使用 `logger.info()` |
| P2-8 | 冗餘 | `cache-service.ts:15` | `CacheService` 介面缺少 `exists(key)` 方法 — `get(key) !== null` 無法區分「key 不存在」與「值為 null」 | 加入 `exists(key: string): Promise<boolean>` |
| P2-9 | spec 偏移 | `environment-config.ts:44-50` | process.env 中匹配命名空間但不在 schema 中的 key 被靜默忽略，無反饋 | 以 debug level 記錄被忽略的 env key |

### guild-economy

| # | 維度 | 檔案 | 描述 | 修正 |
|---|------|------|------|------|
| P2-10 | spec 偏移 | `game-token-service.ts:58`, `balance-adjustment-service.ts:45` | `amount === 0` 被拒絕（Java service layer 允許，只在 handler 層拒絕） | 移除 service 層的 `amount === 0` 檢查或文件記錄為刻意差異 |
| P2-11 | spec 偏移 | `currency-config-service.ts:172-199` | Custom emoji 驗證僅做 regex 格式檢查，不做 Discord 存在性驗證（Java 透過 JDA 驗證） | 統一兩個 regex pattern，加入註解說明限制 |
| P2-12 | spec 遺漏 | `game-token-adjust-handler.ts:40-44` | Admin token 調整後不記錄 `GameTokenTransaction`（Java 也有相同問題） | 注入 `GameTokenTransactionService` 並在調整後記錄 |
| P2-13 | spec 偏移 | `game-token-service.ts:41-46` | `getBalance` 對不存在的帳戶 cache 0 而不 auto-create（spec 要求與 currency 對稱） | 改為呼叫 `findOrCreate`，匹配 `BalanceService.getBalance` 模式 |

### shop-payment

| # | 維度 | 檔案 | 描述 | 修正 |
|---|------|------|------|------|
| P2-14 | 架構 | `drizzle-product-repository.ts:10` | `DrizzleProductRepository` 未宣告 `implements ProductRepository`，無編譯期合約驗證 | 加入 `implements ProductRepository` |
| P2-15 | 架構 | `drizzle-redemption-transaction-service.ts:8` | 同上 — 未實作對應介面，參數型別有輕微不匹配 | 加入 `implements RedemptionTransactionService` 並對齊參數型別 |
| P2-16 | spec 偏移 | `fiat-order-post-payment-worker.ts:171-175` | `markFulfilledIfNeeded` 回傳 null 時釋放鎖定 — Java 不檢查回傳值也不釋放 | 對齊 Java 行為或文件記錄為刻意改進 |
| P2-17 | spec 偏移 | `redemption-code.ts:21,32-38` | `quantity` 欄位用於獎勵倍乘而非多次兌換計數，可能與 Java 語義不同 | 與 Java `RedemptionCode` domain 驗證 quantity 的預期語義 |
| P2-18 | 性能 | `ecpay-cvs-payment.service.ts:208-240` | `generateMerchantTradeNo` 使用 instance variables 無同步機制（Node.js single-thread safe but cluster-unsafe） | 加入註解說明 single-threaded 假設 |
| P2-19 | spec 偏移 | `ecpay-trade-query.service.ts:104` | `buildFormBody` 使用 `encodeURIComponent`（對純英數參數結果相同，但未來可能不同） | 統一使用 consistent URL encoding approach |

### escort-dispatch

| # | 維度 | 檔案 | 描述 | 修正 |
|---|------|------|------|------|
| P2-20 | spec 偏移 | `escort-dispatch-order.ts:78-82` | `validateSourceSnapshot` 對 MANUAL source 不檢查 snapshot 欄位是否為 null（Java 明確拒絕非 null） | 加入 MANUAL source 的負向檢查 |
| P2-21 | 冗餘 | `escort-dispatch-order.test.ts:21` | 測試 import `isAfterSalesInProgress` 但從未使用 | 移除 dead import |
| P2-22 | spec 偏移 | `escort-dispatch-order.service.ts:452-477` | `ensureTimeoutCompletion` 有額外的 post-update re-query，但其他 transition 沒有（不一致） | 統一所有 transition 的 pattern |
| P2-23 | 性能 | `escort-dispatch-order.service.ts:161-186` | `confirmOrder` 缺少 DB-level status guard，concurrent confirmation 可能 race | 加入 atomic `WHERE status = 'PENDING_CONFIRMATION'` guard |
| P2-24 | spec 遺漏 | `escort-dispatch-order.test.ts:10` | 測試 import 不存在的 `withAssignedEscort`（從未被使用但 bundler 必須解析） | 移除 dead import |

### ai-chat-agent

| # | 維度 | 檔案 | 描述 | 修正 |
|---|------|------|------|------|
| P2-25 | spec 偏移 | `ai-chat-service.ts:126-132` | Source enum 值與 Java 不同（`AGENT_CONFIG` vs `AGENT_ENABLED`, `NO_ALLOWLIST` vs `AI_ALLOWLIST_DENIED`） | 重命名以匹配 Java |
| P2-26 | spec 遺漏 | `ai-module.ts:284-289` | TokenEstimator 已 DI 註冊但從未 wired 到 memory provider — 無 context window enforcement | 注入 TokenEstimator 到 SimplifiedChatMemoryProvider |
| P2-27 | 冗餘 | `ai-chat-mention-listener.ts:210-215` | Agent handler 的 `TOOL_INTENT` case 是 dead code — `doStream()` 從不發送 TOOL_INTENT chunks | 實作 TOOL_INTENT emission 或移除 dead code |
| P2-28 | 架構 | `MarkdownValidatingAIChatService.ts:226` | Agent mode 中先 paginator split → re-join → MessageSplitter re-split（雙重分割浪費 CPU） | 當 paginator 已分頁時跳過 MessageSplitter |
| P2-29 | spec 遺漏 | `ai-chat-mention-listener.ts:170-175,225-226` | Agent handler 的「AI 正在思考...」訊息被刪除而非編輯為最終內容（UX 不理想） | 編輯初始訊息為最終內容而非刪除 |
| P2-30 | spec 遺漏 | `__tests__/unit/` | 缺少 AIChatMentionListener, AgentServiceFactory, LangChainAIChatService, ToolCallerAuthorizationGuard 的測試 | 加入對應測試檔案 |

### administration

| # | 維度 | 檔案 | 描述 | 修正 |
|---|------|------|------|------|
| P2-31 | 冗餘 | `constants/colors.ts` | `Colors` 常數定義但完全未被任何檔案 import（所有 handler 直接 hardcode hex 值） | Import 並使用或刪除檔案 |
| P2-32 | spec 偏移 | `AdminProductPanelModalFactory.ts:26-30` | Product modal 使用 hardcoded 中文字串而非 i18n keys | 替換為 `ZhTwStrings.productModal*` keys |
| P2-33 | 幻覺 | `AdminPanelViewFactory.ts:187-189`, `AdminProductPanelHandler.ts:62-64` | Product `description` 欄位被顯示為「庫存」（語義錯誤） | 改為「描述」標籤 |
| P2-34 | spec 偏移 | `AdminPanelUpdateListener.ts:125-128` | `DiceGameConfigChangedEvent` 只在 MAIN view state 觸發更新，但 game settings 沒有對應的 view state | 加入 `GAME_SETTINGS` view state 或無條件回傳 true |
| P2-35 | spec 偏移 | `BaseAdminHandler.ts:43` vs `AdminPanelCommand.ts:101` | 權限檢查不一致 — 一個用 `PermissionFlagsBits.Administrator`，另一個用 hardcoded `8n` | 統一使用 `PermissionFlagsBits.Administrator` |
| P2-36 | 冗餘 | `AdminPanelViewState` enum | 只定義 MAIN, PRODUCT_LIST, PRODUCT_DETAIL, PRODUCT_CODE_LIST — 缺少 BALANCE, TOKEN, GAME, AI_CHANNEL, AI_AGENT, DISPATCH, ESCORT 等狀態 | 加入所有功能對應的 view states |

---

## P3 —  cosmetic

### preparation
- **P3-1**: `pnpm-workspace.yaml` 包含非必要的 `allowBuilds: { esbuild: true }` 設定（超出 P1.2 spec 範圍）

### shared-infrastructure
- **P3-2**: `logger.ts:7-13` — 7 行註解說明應安裝 `pino-pretty` 但未安裝（TODO-as-comment）
- **P3-3**: `module-declarations.d.ts:1-8` — TEMPORARY stub ambient module declarations for `@ltdjms/*`（應移至各 package）

### guild-economy
- **P3-4**: `dice-game-2.test.ts:54` — stale 註解 `// [1, 2, 3, 0, 4, 5, 6]`（dice value 0 不可能）
- **P3-5**: `dice-game-2.test.ts:99-110` — 測試命名 misleading（"should prioritize straights over overlapping triples" 但 test data 無 overlap）
- **P3-6**: 所有 6 個 repository 檔案 — 重複的 `mapToDomain` mapper 函數（可提取共用工具）
- **P3-7**: `balance-service.ts:53` — Currency config 未被快取（每次 `getBalance` 都查 DB）
- **P3-8**: `dice-game-messages.ts:50,52` — `TOKEN_INSUFFICIENT`、`TOKEN_CURRENT_BALANCE` localization strings 定義但從未被任何 handler 使用
- **P3-9**: `dice-game-2.test.ts:18-26` — 測試對 `DiceGame2Service` 前 4 個依賴使用 `{} as any`（無 integration test for `play()`）

### shop-payment
- **P3-10**: `ecpay-cvs-payment.service.ts:103` — 每次 ECPay API 呼叫建立新 TCP 連線（Java 使用 HttpClient connection pooling）
- **P3-11**: `ecpay-trade-query.service.ts:104` — 對帳查詢中 `encodeURIComponent` 與 `URLEncoder.encode` 差異（純英數參數目前相同）
- **P3-12**: `index.ts:76-77` — `CallbackResult` 同時作為 value 和 type 匯出，命名混亂
- **P3-13**: `fiat-order-post-payment-worker.ts:45-56` — `any` 型別用於 notification service 簽名，附 eslint-disable 註解

### escort-dispatch
- **P3-14**: `DispatchPanelInteractionHandler.ts:530` — 不安全的 `as unknown as { values?: string[] }` 型別轉換
- **P3-15**: `escort-dispatch-order.ts:3-5` — Header ASCII art diagram 缺少 `COMPLETED → AFTER_SALES_REQUESTED` 路徑
- **P3-16**: `escort-dispatch-order.service.ts:471` — 使用 `console.warn` 而非 structured logger

### ai-chat-agent
- **P3-17**: `DiscordMarkdownSanitizer.ts` 和 `RegexBasedAutoFixer.ts` — 重複的 `protectCodeBlocks`/`restoreCodeBlocks` 邏輯
- **P3-18**: `agent-config-cache-invalidation-listener.ts:25-42` — Duck-type event checking（應使用 discriminant property）
- **P3-19**: `ai-chat-mention-listener.ts:174,231,271` — 使用 hardcoded Unicode emoji 而非 Discord emoji IDs
- **P3-20**: 多個 tool 檔案 — 使用 `as unknown as { permissionOverwrites: ... }` 不安全型別轉換

### administration
- **P3-21**: `zh-TW.ts` — 多個 i18n keys 定義但從未被任何 handler 使用（`productModalStock`, `escortPricingList`, `aiAgentSelectChannel` 等）
- **P3-22**: `GameSettingsHandler.ts:39-40` — 每次點擊同時查詢兩個 game configs（目前只有 2 games 可接受）
- **P3-23**: `AdminPanelViewState` enum 缺少 8 個功能面板狀態

---

## 維度分析摘要

| 維度 | P0 | P1 | P2 | P3 | 主要發現 |
|------|----|----|----|----|----------|
| 幻覺代碼 | 0 | 2 | 1 | 0 | ESLint 套件缺失、測試 mock 錯誤方法名、product description 顯示為庫存 |
| 冗餘代碼 | 0 | 0 | 3 | 12 | 未使用 constants/i18n、dead imports、重複邏輯、TODO 註解 |
| Spec 偏移 | 7 | 13 | 16 | 0 | ECPay 編碼、thread category 解析、狀態機欄位處理、handler 行為偏離 |
| Spec 遺漏 | 9 | 6 | 8 | 0 | CI pipeline、vitest config、Agent tool 循環、panel 子互動、事件推送 |
| 架構瑕疵 | 0 | 5 | 5 | 4 | Facade raw SQL、duck typing、缺少介面實作宣告、不安全的型別轉換 |
| 性能隱患 | 1 | 1 | 3 | 3 | Session 記憶體洩漏、Redis silent failure、缺少連線池、缺少 cache |

---

## 修正優先級建議

### 第一優先 (阻斷整合) — P0 x17
1. 修正 ECPay URL 編碼（P0-3, P0-4, P0-5, P0-6） → shop-payment
2. 實作 Agent 工具執行循環（P0-7, P0-8, P0-9） → ai-chat-agent
3. 修正管理面板子互動分派（P0-10, P0-11, P0-12, P0-13） → administration
4. 加入管理面板權限檢查（P0-15） → administration
5. 修正護航 Dispatch panel 按鈕邏輯（P0-16, P0-17） → escort-dispatch
6. 建立 CI TypeScript pipeline（P0-1, P0-2） → preparation
7. 實作面板即時更新推送（P0-14） → administration

### 第二優先 (整合前) — P1 x23
主要集中在 shared-infrastructure（embed 截斷、Redis logging、guildId type）、shop-payment（callback payload）、ai-chat-agent（thread category）、administration（session cleanup、event 型別判別）

### 第三優先 (可平行修正) — P2 x36 + P3 x23
大部分為程式碼品質改進、測試覆蓋、重構、文件對齊
