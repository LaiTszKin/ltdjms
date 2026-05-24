# Spec: user-panel-package-extraction

- Date: 2026-05-24
- Feature: user-panel-package-extraction
- Owner: laitszkin

## Goal

將個人面板（User Panel）從 `@ltdjms/admin` 抽出，建立獨立 `@ltdjms/user-panel` package，包含 slash command、interaction handlers、embed builder、session manager、domain event listener、MemberInfoFacade 與 DI module。

## Scope

### In Scope
- 建立 `packages/user-panel/`（`@ltdjms/user-panel`）
- 從 admin 遷移：
  - `src/panel/user/`（UserPanelCommand、UserPanelEmbedBuilder、handlers、definitions）
  - `src/panel/listeners/UserPanelUpdateListener.ts`
  - `src/facades/MemberInfoFacade.ts`（及測試）
  - user-panel 專用 session：`PanelSessionManager`（或抽取 user-panel 專用 variant）
- 建立 `user-panel-module.ts` DI 配置（`USER_PANEL_TOKENS` + `configureUserPanelContainer()`）
- 更新 `@ltdjms/admin`：移除 user-panel 註冊，改為 resolve user-panel 提供的 listener/handler 並註冊至 `SlashCommandListener`
- 更新 workspace：`pnpm-workspace.yaml`、root `tsconfig.json`、`apps/bot` 依賴與啟動順序
- package 依賴：`@ltdjms/shared`、`@ltdjms/economy`、`@ltdjms/games`、`@ltdjms/shop`

### Out of Scope
- Java 1:1 customId/embed 對齊（parity spec）
- 管理面板（admin panel）程式碼
- 資料庫 schema 變更
- `/redeem-code` 獨立 slash command 行為變更（可一併遷移但 parity 在下一 spec 修復 session gap）

## Functional Behaviors (BDD)

### Requirement 1: 建立 @ltdjms/user-panel package 骨架
**GIVEN** monorepo 具有 packages/ workspace 結構
**WHEN** 建立 `@ltdjms/user-panel`
**THEN** package 可獨立編譯且被 bot 引用

**Requirements**:
- [x] R1.1 建立 `packages/user-panel/` 目錄結構（src/commands/、src/handlers/、src/services/、src/facades/、src/session/、src/listeners/、src/di/、src/i18n/）
- [x] R1.2 `package.json` name=`@ltdjms/user-panel`，dependencies 含 shared/economy/games/shop
- [x] R1.3 更新 workspace 與 tsconfig project references
- [x] R1.4 `apps/bot` 加入依賴並在 DI 啟動順序中 configure user-panel

### Requirement 2: 遷移個人面板程式碼
**GIVEN** user panel 程式碼在 admin package
**WHEN** 執行遷移
**THEN** admin 中不再包含 user-panel 源碼（可保留短期 re-export shim）
**AND** 功能行為與遷移前一致（允許 customId 尚未對齊 Java）

**Requirements**:
- [x] R2.1 遷移 UserPanelCommand、UserPanelEmbedBuilder、TransactionHistoryHandler、RedemptionCodeHandler、RedeemCodeCommandHandler
- [x] R2.2 遷移 UserPanelUpdateListener
- [x] R2.3 遷移 MemberInfoFacade 及測試
- [x] R2.4 遷移/抽取 PanelSessionManager（user_panel: prefix）
- [x] R2.5 更新所有內部 import 路徑

### Requirement 3: DI 與 bot 集成
**GIVEN** bot 透過 admin module 啟動所有 handler
**WHEN** user-panel 獨立後
**THEN** `configureUserPanelContainer()` 註冊所有 handler/listener
**AND** admin/bot 透過 public API 取得 handler 並註冊至 SlashCommandListener

**Requirements**:
- [x] R3.1 建立 `USER_PANEL_TOKENS` 與 `configureUserPanelContainer()`
- [x] R3.2 從 economy/games/shop container resolve 所需 service 注入 MemberInfoFacade
- [x] R3.3 AdminModule 移除 user-panel 直接 construction，改 import user-panel module
- [x] R3.4 `/user-panel` slash command 註冊仍正常

### Requirement 4: Public API
**GIVEN** 其他 package 可能需要引用 user-panel 類型
**WHEN** 完成 extraction
**THEN** `@ltdjms/user-panel` 透過 `src/index.ts` 導出 DI module 與必要類型

**Requirements**:
- [x] R4.1 `index.ts` 導出 `configureUserPanelContainer`；`USER_PANEL_TOKENS` 僅透過 `@ltdjms/user-panel/testing` 供測試使用
- [x] R4.2 admin 不再直接 import user-panel 內部路徑

## Error and Edge Cases
- [x] MemberInfoFacade 可選 RedemptionTransactionService 缺失 — 維持既有 graceful error
- [x] Session Redis 不可用 — 維持 in-memory fallback
- [x] DI 循環依賴 — user-panel 僅依賴 economy/games/shop，不依賴 admin

## Clarification Questions
- **MemberInfoFacade 歸屬**：預設移入 `@ltdjms/user-panel`（本 spec 採用此方案）。若需共用給 admin 其他功能，parity 完成後再評估是否抽出 `@ltdjms/member-info`。
- **PanelSessionManager**：若 admin panel 共用 BaseSessionManager，保留 base 在 admin 或移至 shared；user-panel 僅保留 `user_panel:` prefix 子類或獨立 manager。

## References
- Java reference: `src/main/java/ltdjms/discord/panel/`
- TS baseline: `packages/admin/src/panel/user/`
- Prior art: `docs/archive/2026-05-22/game-migration-and-extraction/package-extraction/`
