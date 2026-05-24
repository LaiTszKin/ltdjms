# Coordination: user-panel-and-deps

- Date: 2026-05-24
- Batch: user-panel-and-deps

## Business Goals

將 Java bot 個人面板（`/user-panel`）1:1 原生復刻至 TypeScript bot，並抽成獨立 `@ltdjms/user-panel` package；同時將 monorepo 所有外部依賴升級至最新穩定版本。

- Batch members: [[dependency-upgrade-tooling], [dependency-upgrade-core-runtime], [dependency-upgrade-langchain], [dependency-upgrade-express], [user-panel-package-extraction], [user-panel-java-parity]]
- Shared outcome: TypeScript 個人面板與 Java bot 在 Discord UI（embed、button、modal、customId、分頁）與後端行為（資料聚合、兌換、即時更新）完全一致；個人面板程式碼獨立於 `@ltdjms/user-panel`；全 monorepo 外部依賴達最新穩定版且 `make verify` 通過
- Out of scope: 新增個人面板功能、修改 Java bot、修改貨幣/商店/護航/AI 業務規則、引入第三方 Discord bot 框架

## Design Principles

- Current baseline: 個人面板程式碼在 `@ltdjms/admin/src/panel/user/`，customId 為 `user_history_*` / `user_redeem_*`，與 Java `user_panel_*` 不一致；外部依賴多為 2025 初版本（zod 3、vitest 3、typescript 5.5、@langchain 0.x）
- Shared invariants: 不引入新的 Discord interaction 路由框架；沿用 `@ltdjms/shared` 的 `EmbedView`/`ButtonView`/`DiscordInteraction` 抽象；session TTL 維持 15 分鐘；分頁大小維持 10
- Shared constraints: Node.js 22 LTS 為唯一執行基線；不新增 `@ltdjms/admin` 對 user-panel 的直接依賴（改由 `@ltdjms/user-panel` 提供）；依賴升級不得改變對外業務 API 語意
- Legacy direction: Java `UserPanelEmbedBuilder`、`UserPanelButtonHandler`、`UserPanelHistoryViewFactory` 為 UI oracle；TypeScript 現有 `user_history_*` customId 需替換為 Java 對齊的 `user_panel_*`
- Compatibility window: `@ltdjms/admin` 在 `user-panel-package-extraction` 完成前可保留舊路徑 re-export；`user-panel-java-parity` 完成後移除
- Cleanup after cutover: 刪除 `packages/admin/src/panel/user/`、`MemberInfoFacade`（若已移入 user-panel）、admin DI 中 user-panel 相關 token

## Spec Boundaries

### Ownership Map

#### Spec Set 1: dependency-upgrade-tooling
- Primary concern: 開發工具鏈 major 升級（TypeScript 6、Vitest 4、ESLint 10、Prettier、typescript-eslint）
- Allowed touch points: 根 `package.json`、`eslint.config.mjs`、`vitest.config.ts`、各 package `tsconfig.json`、CI workflow
- Must not change: 業務邏輯、runtime dependency 版本（zod、discord.js 等）

#### Spec Set 2: dependency-upgrade-core-runtime
- Primary concern: 核心 runtime 依賴升級（zod 4、drizzle-orm、drizzle-kit、pg、pino 10、discord.js、ioredis、tsyringe、reflect-metadata）
- Allowed touch points: 所有 `packages/*/package.json`、schema 驗證程式碼、logger 設定
- Must not change: `@langchain/*`（langchain spec 負責）、express（express spec 負責）

#### Spec Set 3: dependency-upgrade-langchain
- Primary concern: AI 模組 LangChain 生態升級（@langchain/core 1.x、@langchain/openai 1.x、marked 18）
- Allowed touch points: `packages/ai/`、`packages/admin` 中 AI 相關 import（若有）
- Must not change: shop express、user-panel 程式碼

#### Spec Set 4: dependency-upgrade-express
- Primary concern: `@ltdjms/shop` 的 Express 4 → 5 升級
- Allowed touch points: `packages/shop/` callback server、middleware、型別定義
- Must not change: ECPay callback 業務邏輯語意、user-panel

#### Spec Set 5: user-panel-package-extraction
- Primary concern: 建立 `@ltdjms/user-panel` package，從 admin 遷移 panel 程式碼、MemberInfoFacade、session、listener、DI module
- Allowed touch points: 新建 `packages/user-panel/`、`packages/admin/src/di/AdminModule.ts`、`apps/bot/` 啟動順序
- Must not change: Java 對齊的 customId/embed 細節（parity spec 負責）；依賴版本（deps spec 負責）

#### Spec Set 6: user-panel-java-parity
- Primary concern: TypeScript 個人面板與 Java 1:1 對齊（customId、embed 結構、button layout、history factory、modal、即時更新行為）
- Allowed touch points: `packages/user-panel/` 內所有 panel 相關檔案、parity 測試
- Must not change: package 邊界與 DI 結構（extraction spec 已建立）；admin 管理面板

### Collisions & Integration

- Shared files & edit rules:
  - 根/各 package `package.json` — tooling spec 先改 devDeps；core-runtime spec 改 runtime deps；langchain/express spec 各自改子集；禁止同一 PR 重複 bump 同一 package
  - `packages/admin/src/di/AdminModule.ts` — deps spec 不可修改；extraction spec 移除 user-panel 註冊改為 import user-panel module；parity spec 僅改 user-panel 內 handler
  - `pnpm-lock.yaml` — 每次 merge 後必須 `pnpm install` 並提交 lockfile
- Shared API / schema freeze: `@ltdjms/economy`、`@ltdjms/games`、`@ltdjms/shop` 對外 service 介面在 batch 期間 additive-only
- Compatibility shim retention: admin 對 `MemberInfoFacade` 的 re-export 保留至 parity spec 驗收完成
- Merge order: `preparation` → `dependency-upgrade-tooling` → `dependency-upgrade-core-runtime` → (`dependency-upgrade-langchain` ∥ `dependency-upgrade-express`) → `user-panel-package-extraction` → `user-panel-java-parity`
- Integration checkpoints:
  - 每個 deps spec 完成後：`make build && make test`
  - extraction 完成後：`/user-panel` 仍可開啟（功能 regression）
  - parity 完成後：parity 測試全綠 + 手動 Discord smoke test
  - batch 完成後：`make verify`
- Re-coordination trigger: 若 zod 4 或 langchain 1.x 升級需改動 user-panel 驗證邏輯，暫停 user-panel spec 並更新 coordination
