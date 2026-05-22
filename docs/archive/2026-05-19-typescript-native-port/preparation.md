# Preparation: TypeScript Native Port

- Date: 2026-05-20
- Batch: typescript-native-port

## Task P1: 初始化 TypeScript Monorepo 專案結構

Purpose: 在開始任何功能移植之前，必須先建立 pnpm workspace monorepo 骨架，確保所有 member spec 有共通的開發基礎。
Scope: 根層級設定檔、`packages/shared/` 的初始骨架
Out of scope: 任何業務邏輯、資料庫操作、Discord 互動

- P1.1 [x] **根 `package.json`** — 建立 monorepo 根 `package.json`，設定 `private: true`、`scripts`（build、test、lint、format、dev）、devDependencies（typescript、vitest、prettier、eslint）
  - Verify: `node -e "console.log(require('./package.json').private)"` 輸出 `true`

- P1.2 [x] **`pnpm-workspace.yaml`** — 建立 workspace 設定，宣告 `packages/*` 為 workspace member
  - Verify: `pnpm list --depth=0` 無錯誤

- P1.3 [x] **根 `tsconfig.json`** — 建立 base tsconfig（target ES2022、module NodeNext、strict true、declaration true、sourceMap true）
  - Verify: `npx tsc --showConfig` 輸出完整 compiler options

- P1.4 [x] **`packages/shared/package.json`** — 建立 shared package 的 `package.json`，name 為 `@ltdjms/shared`，設定 `main`/`types`/`exports` 欄位
  - Verify: `node -e "console.log(require('./packages/shared/package.json').name)"` 輸出 `@ltdjms/shared`

- P1.5 [x] **`packages/shared/tsconfig.json`** — 建立 shared package 的 tsconfig，extends 根 tsconfig，設定 outDir 為 `dist`
  - Verify: `cd packages/shared && npx tsc --noEmit` 無錯誤（空專案）

- P1.6 [x] **`.env.example`** — 從 Java `.env.example` 移植環境變數模板，保持所有變數名稱不變
  - Verify: 比對 Java `src/main/resources/application.properties` 的所有 key 都存在於 `.env.example` 的註解中

## Task P2: 建立開發與 CI 基礎設施

Purpose: 確保所有 member spec 的開發者有一致的格式化、linting、測試執行環境。
Scope: ESLint、Prettier、Vitest 設定、GitHub Actions CI
Out of scope: 業務邏輯測試、整合測試

- P2.1 [x] **根 `eslint.config.mjs`** — 設定 TypeScript ESLint（typescript-eslint recommended），啟用 `@typescript-eslint/no-floating-promises`、`@typescript-eslint/no-misused-promises`
  - Verify: `npx eslint --print-config packages/shared/src/index.ts`（空檔案）無錯誤

- P2.2 [x] **根 `vitest.config.ts`** — 設定 Vitest workspace config，指向各 package 的 vitest config
  - Verify: `npx vitest --version` 正常

- P2.3 [x] **`packages/shared/vitest.config.ts`** — 建立 shared package 的 Vitest config（include src、testTimeout 10000）
  - Verify: `cd packages/shared && npx vitest --run` 無錯誤（無測試）

- P2.4 [x] **`.github/workflows/ci.yml`** — 建立 CI workflow：pnpm install → lint → typecheck → test（matrix Node.js 20/22）
  - Verify: `cat .github/workflows/ci.yml` 語法正確（或等 push 後觀察 CI 運行）

- P2.5 [x] **`.prettierrc`** — 設定 Prettier（singleQuote、trailingComma all、printWidth 100、tabWidth 2）
  - Verify: `npx prettier --check packages/shared/package.json` 通過

- P2.6 [x] **`Makefile`** — 建立對應 Java `make` 命令的 TypeScript 等價命令：`make build`（tsc -b）、`make test`（vitest run）、`make format`（prettier --write）、`make format-check`（prettier --check）、`make lint`（eslint）
  - Verify: `make build` 無錯誤、`make test` 無錯誤（空測試）

## Task P3: 從 Java 複製並凍結 Database Schema 參考

Purpose: 將現有 Flyway migration SQL 複製到 TypeScript 專案中作為 Drizzle schema 的參考來源，確保 schema 完全一致。
Scope: SQL 檔案複製、schema 文件產出
Out of scope: Drizzle schema 定義（屬於 shared-infrastructure spec 的 tasks）

- P3.1 [x] **`packages/shared/db/migrations/`** — 複製 `src/main/resources/db/migration/V*.sql` 到新專案
  - Verify: `diff -r src/main/resources/db/migration/ packages/shared/db/migrations/` 無差異

- P3.2 [x] **Schema 清單文件** — 從 migration SQL 產出完整的 table/column/constraint 清單（共 28 個 migration，約 18 張 table）
  - Verify: 人工比對文件與 JdbcFiatOrderRepository、JdbcEscortDispatchOrderRepository 中引用的所有 column 名稱

## Validation

- Verification required:
  - `pnpm install` 在根目錄成功無錯誤
  - `pnpm -r exec tsc --noEmit` 在所有 package 中通過
  - `pnpm vitest run` 在根目錄成功（0 測試通過即為成功）
  - 所有 migration SQL 檔案與 Java 原始碼完全一致
- Expected results: 一個可編譯、可測試、CI 就緒的 TypeScript monorepo 骨架，為 6 個 member spec 的並行開發做好準備
- Regression risks covered: N/A（不涉及任何功能變更）
