# Coordination: TypeScript Native Port

- Date: 2026-05-20
- Batch: typescript-native-port

## Business Goals

將 LTDJMS 專案從 Java/Maven 完整原生移植到 TypeScript/pnpm monorepo，在功能完全一致的前提下，達成更輕量的 runtime 資源消耗、更活躍的 AI 智能體生態整合、以及更低的維護成本。

- Batch members: [shared-infrastructure, guild-economy, shop-payment, escort-dispatch, ai-chat-agent, administration]
- Shared outcome: 一個完整的 TypeScript Discord bot，功能與 Java 原版 100% 一致，所有關鍵測試案例移植完成且通過
- Out of scope: 新功能開發、業務邏輯變更、資料庫 schema 變更、Discord API 行為變更

## Design Principles

- Current baseline: Java 17 + Maven + JDA 5.2.2 + PostgreSQL + Redis，commit c50d10a (v0.35.4)，695 個 Java 檔案、~85,000 行業務程式碼
- Shared invariants:
  - 所有 Discord 用戶可見的輸出完全一致（embed 格式、按鈕文字、錯誤訊息、zh-TW 在地化）
  - 資料庫 schema 保持不變（相同的 table、column、constraint、index）
  - 所有冪等機制保持不變（Conditional UPDATE `WHERE col IS NULL RETURNING *`、Claim/Release `processing_at` 模式）
  - 所有狀態機轉換邏輯保持不變（FiatOrder PENDING_PAYMENT→PAID→fulfilled、EscortDispatchOrder 7 狀態轉換）
  - DomainEvent 的觸發時機與 payload 語義保持不變
  - ECPay 加密/解密/CheckMacValue 演算法輸出逐 byte 一致
- Shared constraints:
  - Runtime: Node.js 20 LTS+、pnpm 9+、TypeScript 5.5+
  - 專案結構: pnpm workspaces monorepo，每個 member spec 對應一個 workspace package
  - 共享基礎設施: `packages/shared/`
  - 功能模組: `packages/economy/`、`packages/shop/`、`packages/dispatch/`、`packages/ai/`、`packages/admin/`
  - Discord library: discord.js v14
  - 資料庫: Drizzle ORM + node-postgres（替代 JOOQ/JDBC/HikariCP）
  - 測試: Vitest（替代 JUnit + Mockito）
  - 不對資料庫 schema 做任何變更——直接以現有 Flyway migration SQL 作為 Drizzle schema 定義的參考
- Legacy direction: Java 程式碼庫在移植驗證完成後被完全取代。移植期間 Java 版本繼續在 production 運行，TypeScript 版本獨立開發並在 staging 環境驗證。
- Compatibility window: 並行運行期——TypeScript staging + Java production，直到 TypeScript 版本通過完整 E2E 驗證且行為完全一致
- Cleanup after cutover: 刪除 `src/` Java 原始碼、`pom.xml`、Maven wrapper；保留 `src/main/resources/db/migration/` SQL 作為 schema 歷史參考

## Spec Boundaries

### Ownership Map

#### Spec Set 1: shared-infrastructure
- Primary concern: TypeScript 專案基礎設施與跨模組共享型別——DI 容器、Config 管理、Database 連線與 migration、Redis 快取、DomainEvent 事件系統、Result<T, E> / DomainError 型別、Logging、Discord.js 抽象層（DiscordInteraction、DiscordContext、DiscordEmbedBuilder、DiscordRuntimeGateway）
- Allowed touch points:
  - `packages/shared/`（新建，所有原始碼）
  - 根層級: `package.json`、`pnpm-workspace.yaml`、`tsconfig.json`、`.env.example`
  - 所有其他 package 的型別依賴（透過 `import from '@ltdjms/shared'`）
- Must not change: 資料庫 schema、任何業務邏輯

#### Spec Set 2: guild-economy
- Primary concern: Guild 貨幣系統（餘額管理、交易記錄）、遊戲代幣系統（代幣管理、交易記錄）、骰子遊戲 1 和 2（遊戲邏輯、獎勵計算）
- Allowed touch points:
  - `packages/economy/`（新建，所有原始碼）
  - 依賴 `@ltdjms/shared` 的型別與基礎設施
- Must not change: 貨幣/代幣的數學規則、骰子獎勵計算邏輯、事件發布語義

#### Spec Set 3: shop-payment
- Primary concern: 商店瀏覽與搜尋、貨幣購買、法幣 (ECPay) 付款流程、兌換碼生成與兌換、付款後履約 worker、對帳 worker、通知服務
- Allowed touch points:
  - `packages/shop/`（新建，所有原始碼）
  - 依賴 `@ltdjms/shared` 和 `@ltdjms/economy`（僅透過 interface）
- Must not change: ECPay API 互動邏輯（AES 解密、CheckMacValue 計算、URL 編碼規則）、付款狀態機、對帳冪等邏輯

#### Spec Set 4: escort-dispatch
- Primary concern: 護航派單完整生命週期（建立→分配→確認→完成→售後）、從商店購買自動交接（handoff）、售後人員管理、Guild 層級護航定價覆寫
- Allowed touch points:
  - `packages/dispatch/`（新建，所有原始碼）
  - 依賴 `@ltdjms/shared`（僅透過 interface）
- Must not change: 派單 7 狀態機、交接冪等邏輯（findBySourceIdentity）、通知流程與時機

#### Spec Set 5: ai-chat-agent
- Primary concern: AI 聊天（頻道/分類白名單、路由決策）、AI Agent（17 個 LangChain Discord 管理工具）、Markdown 驗證/修正/分頁處理管線
- Allowed touch points:
  - `packages/ai/`（新建，所有原始碼）
  - 依賴 `@ltdjms/shared`（僅透過 interface）
- Must not change: AI 路由決策矩陣、工具授權規則（僅限 ADMINISTRATOR）、Markdown 驗證規則清單

#### Spec Set 6: administration
- Primary concern: 管理面板（貨幣/代幣/遊戲/AI/產品/護航設定）、用戶面板（餘額/交易記錄/兌換碼輸入）、Facade 聚合層（CurrencyManagement、GameTokenManagement、GameConfig、AIConfig、MemberInfo）、面板 Session 管理與即時更新
- Allowed touch points:
  - `packages/admin/`（新建，所有原始碼）
  - 依賴所有其他 package 的 facade/service 介面
- Must not change: 面板互動流程（按鈕/選單/Modal 行為）、Session TTL 邏輯、即時更新事件監聽

### Collisions & Integration

- Shared files & edit rules:
  - `packages/shared/src/types/` — shared-infrastructure 定義，其他 spec 唯讀 import
  - `packages/shared/src/infra/` — shared-infrastructure 實作，其他 spec 透過 DI 使用
  - 根 `package.json`、`pnpm-workspace.yaml` 由 shared-infrastructure 初始化，其他 spec 僅在自己 package 的 `package.json` 中宣告依賴
  - 各 spec 的 package 之間的依賴必須透過 `@ltdjms/<package-name>` 的 export map
- Shared API / schema freeze:
  - shared-infrastructure 必須最先完成核心介面定義並凍結
  - 其他 spec 在 shared-infrastructure 的型別與介面凍結後才能開始實作
  - 如需新增 shared 型別/介面，必須在 shared-infrastructure spec 中新增 tasks
- Compatibility shim retention: None（全新 TypeScript 專案）
- Merge order:
  1. shared-infrastructure（阻斷性前置——必須最先完成並通過所有測試）
  2. guild-economy（可與 shop-payment 並行；shop-payment 依賴 guild-economy 的 BalanceService/GameTokenService 介面，可透過 interface mock 先行開發）
  3. shop-payment（可與 guild-economy 並行）
  4. escort-dispatch + ai-chat-agent（可並行，兩者獨立）
  5. administration（必須最後，依賴所有其他 package 的 Facade 介面）
- Integration checkpoints:
  - CP1: shared-infrastructure 完成 → DI 容器啟動、DB 連線成功、Config 載入驗證通過
  - CP2: guild-economy 完成 → 貨幣/代幣 CRUD + 骰子遊戲邏輯通過整合測試（對接真實 PostgreSQL）
  - CP3: shop-payment 完成 → ECPay callback 解密 + 對帳查單通過 E2E 測試（使用 ECPay stage 環境）
  - CP4: escort-dispatch 完成 → 完整派單生命週期通過整合測試
  - CP5: ai-chat-agent 完成 → AI 聊天 + Agent 工具通過整合測試（使用 AI API sandbox）
  - CP6: administration 完成 → 所有面板互動通過整合測試
  - CP7: 全系統 E2E → TypeScript bot 在 staging 環境與 Java bot 並行運行，比對關鍵行為（slash command 回覆、embed 內容、button 互動）
- Re-coordination trigger: 任何 spec 發現需要修改資料庫 schema、變更 shared 型別簽名、或與 Java 原版行為不一致且無法在該 spec 內解決時，必須立即通知所有相關 spec 並更新 coordination.md
