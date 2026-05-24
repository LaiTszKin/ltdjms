# Tasks: user-panel-package-extraction

- Date: 2026-05-24
- Feature: user-panel-package-extraction

## **Task 1: 建立 package 骨架**

Requirements: R1.1-R1.4
Scope: `packages/user-panel/`、workspace config

- T1.1 [ ] **建立目錄結構** — commands/handlers/services/facades/session/listeners/di/i18n
- T1.2 [ ] **建立 package.json** — name `@ltdjms/user-panel`，deps: shared/economy/games/shop/discord.js/zod/reflect-metadata
- T1.3 [ ] **建立 tsconfig.json** — references shared/economy/games/shop
- T1.4 [ ] **更新 pnpm-workspace.yaml、root tsconfig、apps/bot**
- T1.5 [ ] **pnpm install**
  - Verify: `pnpm --filter @ltdjms/user-panel exec tsc --noEmit`

## **Task 2: 遷移 Facade 與 Session**

Requirements: R2.3, R2.4
Scope: MemberInfoFacade、PanelSessionManager、BaseSessionManager（若需）

- T2.1 [ ] **移動 MemberInfoFacade.ts + 測試** → `packages/user-panel/src/facades/`
- T2.2 [ ] **移動/抽取 PanelSessionManager** — 保留 `user_panel:` prefix；若 BaseSessionManager 在 admin，評估移至 shared 或複製最小子集
- T2.3 [ ] **更新 import 指向 economy/games/shop services**
  - Verify: `pnpm vitest run --project @ltdjms/user-panel -t MemberInfoFacade`

## **Task 3: 遷移 Command 與 Handlers**

Requirements: R2.1, R2.5
Scope: panel/user/* → user-panel

- T3.1 [ ] **移動 UserPanelCommand、UserPanelEmbedBuilder、definitions/**
- T3.2 [ ] **移動 TransactionHistoryHandler、RedemptionCodeHandler、RedeemCodeCommandHandler**
- T3.3 [ ] **移動 i18n 字串與 colors 常數**（或從 admin 複製 user-panel 專用部分）
- T3.4 [ ] **更新內部 import**
  - Verify: user-panel package 編譯通過

## **Task 4: 遷移 Listener**

Requirements: R2.2
Scope: UserPanelUpdateListener

- T4.1 [ ] **移動 UserPanelUpdateListener** → `packages/user-panel/src/listeners/`
- T4.2 [ ] **註冊至 DomainEventPublisher**（在 user-panel DI module 內）
  - Verify: listener 可被 resolve

## **Task 5: 建立 DI module**

Requirements: R3.1-R3.4, R4.1
Scope: `packages/user-panel/src/di/user-panel-module.ts`

- T5.1 [ ] **定義 USER_PANEL_TOKENS**
- T5.2 [ ] **實作 configureUserPanelContainer()** — resolve economy/games/shop services，wire 所有 handler/listener
- T5.3 [ ] **導出 index.ts** public API
  - Verify: bot 可 import `@ltdjms/user-panel`

## **Task 6: 更新 Admin 與 Bot**

Requirements: R3.3, R4.2
Scope: AdminModule、apps/bot

- T6.1 [ ] **AdminModule 移除 user-panel 直接 new** — 改呼叫 configureUserPanelContainer 並 register handlers
- T6.2 [ ] **刪除 admin 中已遷移檔案**（或保留 re-export shim 一個 sprint）
- T6.3 [ ] **更新 bot 啟動順序** — shared → economy → games → shop → user-panel → admin
  - Verify: `make build && make test`

## **Task 7: Slash command 註冊**

Requirements: R3.4
Scope: SlashCommandRegistrar

- T7.1 [ ] **確認 `/user-panel` 與 `/redeem-code` 仍註冊**
  - Verify: `grep user-panel packages/*/src/**/SlashCommandRegistrar.ts` 或等價註冊點
