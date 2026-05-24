# Code Review Report

- **Spec**: user-panel-and-deps
- **Date**: 2026-05-24
- **Reviewer**: QA Agent
- **Result**: **NOT PASS**

---

## 業務需求滿足度

**判定：核心實作已到位，但 batch 尚未達可簽收狀態。**

| 需求域 | 狀態 | 證據 | 缺口 / 不確定性 |
|--------|------|------|-----------------|
| `@ltdjms/user-panel` 獨立 package | ✅ 滿足 | `packages/user-panel/` 存在；`packages/admin/src/panel/user/` 已移除；`AdminModule` 僅 import public API | 無 |
| Java 1:1 customId / embed / buttons / history | ✅ 滿足（自動化） | `user_panel_*` 常數；36/36 user-panel tests 通過；oracle fixtures 比對通過 | 手動 Discord 視覺 smoke 未執行 |
| Session 15 分鐘固定 TTL（createdAt） | ✅ 滿足 | `BaseSessionManager.isExpired()` 使用 `createdAt`；`PanelSessionManager.test.ts` 覆蓋 | 無 |
| 依賴升級至最新穩定版 | ✅ 滿足 | `make verify` exit 0（build + test + lint + format-check） | ECPay E2E、Docker start-dev log smoke 未在本輪驗證 |
| `make verify` batch 閘門 | ✅ 滿足 | `Makefile` L34 `verify: build test lint format-check` | 無 |
| Architecture atlas 合併 | ✅ 滿足 | `resources/project-architecture/atlas/atlas.index.yaml` 含 `user-panel` feature | atlas 文案仍有 Redis 誤述（見 P2-7） |
| 手動 Discord smoke | ❌ 未滿足 | `user-panel-java-parity/checklist.md` L27–30 仍為 `[ ]` | 需實機 bot 操作確認 |

**結論：** 程式碼層面的 extraction、Java parity 與依賴升級已基本完成且自動化驗證全綠；因 **手動 smoke 未完成**、**guild-wide 推送更新存在 P1 性能缺口**、**關鍵互動路徑測試覆蓋不足**，本次審查判定 **NOT PASS**。

---

## 六維度審查摘要

| 維度 | 結論 | 摘要 |
|------|------|------|
| 無幻覺代碼 | ✅ 通過 | 生產路徑所有 `@ltdjms/*` import 與 method call 均可解析；IT-101 mock 方法名稱錯誤（僅測試） |
| 無冗余代碼 | ⚠️ 有改善空間 | admin 殘留 user-panel i18n/colors、CommandHandler shim、user-panel 未使用 i18n 與 dead exports |
| 無 spec 偏移 | ✅ 通過 | customId、embed、分頁、session TTL、DI 結構均符合 spec；ROUTING_PREFIX 文案與 spec 字面略有差異但不影響行為 |
| 無 spec 遺漏 | ⚠️ 部分遺漏 | 手動 smoke 未簽收；R5.4/R6.1/R4.4/R5.2 缺 handler 層回歸測試 |
| 無架構瑕疵 | ⚠️ 有 P1 | user-panel DI 已 idempotent；admin DI 無 guard；handler 註冊仍耦合在 admin |
| 无性能隱患 | ⚠️ 有 P1 | guild-wide currency config 更新為 O(n) 串行 REST；admin listener 已有 debounce/concurrency 模式未移植 |

---

## 發現的問題

### P0 — 嚴重缺陷

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| — | 無 | — | — | — |

### P1 — 重要問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | 手動 Discord smoke 四項均未簽收 | batch 最終驗收無法確認 UI 與 Java 視覺/互動一致 | `user-panel-java-parity/checklist.md` | L27–30 |
| 2 | `currency_config_changed` guild-wide 推送為串行 O(n)，無 debounce / concurrency cap / channel grouping | 多使用者同時開啟面板時，管理員改貨幣設定可能觸發長時間串行 Discord REST，有 429 風險 | `packages/user-panel/src/listeners/UserPanelUpdateListener.ts` | L51–56, L80–86 |
| 3 | 關鍵 parity 互動路徑缺 handler 層測試 | `/redeem-code` 無 session、modal 結構、back 返回、session 僅在初次 `/user-panel` 建立等行為無回歸保護 | `packages/user-panel/src/handlers/`, `packages/user-panel/src/commands/` | — |
| 4 | `configureAdminContainer()` 無 idempotent guard | 若重複呼叫會重複 `client.on('interactionCreate')` 與 domain event handler 註冊，造成雙重 dispatch | `packages/admin/src/di/AdminModule.ts` | L121+ |
| 5 | user-panel DI 生命週期 split：events 在 `configureUserPanelContainer`，slash handlers 在 `registerUserPanelHandlers` | dispose/reconfigure 後若未重跑 handler 註冊，listener 仍持有舊 handler 實例 | `packages/user-panel/src/di/user-panel-module.ts`, `packages/admin/src/di/AdminModule.ts` | L51+, L383 |

### P2 — 一般問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | IT-101 DI 測試 mock 使用不存在的方法名 `getBalanceView` / `getTokenBalance` | 測試僅驗 resolve，若擴展為行為測試會 runtime fail | `packages/user-panel/src/di/__tests__/user-panel-module.test.ts` | L48–62 |
| 2 | admin 殘留 user-panel i18n 與 color 常數 | 死碼與 live UI 文案不一致（`用戶面板` vs `個人面板`） | `packages/admin/src/i18n/zh-TW.ts`, `packages/admin/src/constants/colors.ts` | L259–270, L27–37 |
| 3 | `CommandHandler` shim 仍被 handler import | 三層 indirection，migration 未完成 | `packages/user-panel/src/infra/CommandHandler.ts`, `packages/admin/src/commands/infra/CommandHandler.ts` | L1 |
| 4 | user-panel i18n 大部分未使用，UI 字串硬編碼 | 維護者易誤判 i18n 已覆蓋；與 oracle 同步風險 | `packages/user-panel/src/i18n/zh-TW.ts`, `UserPanelEmbedBuilder.ts`, `UserPanelHistoryViewFactory.ts` | — |
| 5 | 推送更新使用 channel/message fetch 而非 InteractionHook | 每次 push 多 2 次 Discord REST，高頻 balance 事件下 API 負載高於 Java | `packages/user-panel/src/listeners/UserPanelUpdateListener.ts` | L80–86 |
| 6 | `getAllForGuild` 掃描全域 session Map | 多 guild 接近 MAX_SESSIONS 時，單次 guild 更新成本偏高 | `packages/shared/src/session/BaseSessionManager.ts` | L143–157 |
| 7 | Architecture atlas 宣稱 panel session 使用 Redis | 文件與實作不符（in-memory only） | `resources/project-architecture/atlas/features/user-panel.yaml`, `panel-session.html` | — |
| 8 | plan fixtures 與 package test fixtures 不同步 | `docs/plans/.../java-history-oracle.json` 標題/描述與 Java 及 package fixture 不一致 | `docs/plans/.../fixtures/`, `packages/user-panel/src/__tests__/fixtures/` | — |
| 9 | `@ltdjms/admin` 依賴 `@ltdjms/user-panel` 僅為 handler 註冊 | admin package 邊界模糊，member-facing 功能耦合在 administration 模組 | `packages/admin/package.json`, `AdminModule.ts` | L383 |
| 10 | dead exports：`emptyHistoryPage`、`getRedemptionHistory`、UserPanelService shop re-exports | 公開/內部 API 表面過寬 | `transaction-display.ts`, `MemberInfoFacade.ts`, `UserPanelService.ts` | — |

### P3 — 建議改善

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | `ROUTING_PREFIX` 為 `'user_panel'`，spec/fixture 字面寫 `'user_panel_'` | 行為正確但文件/常數命名不一致 | `packages/user-panel/src/constants/UserPanelConstants.ts` | L20–22 |
| 2 | `configureUserPanelContainer` 在 resolve 完成前即設 `_configured = true` | 中途 throw 會導致重試 no-op | `packages/user-panel/src/di/user-panel-module.ts` | L51–52 |
| 3 | `USER_PANEL_TOKENS` 從 public index 導出 | 外部可 bypass 封裝直接 resolve 內部服務 | `packages/user-panel/src/index.ts` | L11–15 |
| 4 | `ensureDeferred` 在 admin/user-panel 重複 | 可抽取至 `@ltdjms/shared/discord` | `packages/user-panel/src/infra/ensureDeferred.ts`, `BaseAdminHandler.ts` | — |
| 5 | user-panel `package.json` 含未使用的 `tsyringe` runtime dep | 依賴圖膨脹 | `packages/user-panel/package.json` | L19 |
| 6 | parity checklist sign-off 仍寫「repo 無 make verify」 | 規劃文件過時 | `user-panel-java-parity/checklist.md` | L33 |

---

## 解決方案

### P1 修復

#### P1-1: 完成手動 Discord smoke

- **涉及檔案**：`user-panel-java-parity/checklist.md`
- **根因**：parity spec 要求實機 Discord 驗證，無法由 CI 替代
- **修復方案**：在 staging guild 依 checklist 執行四項：開啟 `/user-panel`、三種 history + back、兌換成功/失敗、餘額變更自動刷新；勾選 L27–30
- **驗證方式**：checklist 全 `[x]`；截圖或簡短錄屏存檔（可選）

#### P1-2: Guild-wide 推送更新加入 debounce 與 bounded concurrency

- **涉及檔案**：`packages/user-panel/src/listeners/UserPanelUpdateListener.ts` > `updateAllGuildPanels`（L51–56）
- **根因**：直接 `for await` 串行更新所有 session，未借鑑 `AdminPanelUpdateListener` 的 debounce + channel grouping + concurrency limit
- **修復方案**：移植 admin 模式——`currency_config_changed` debounce 500ms；按 `channelId` 分組；`processWithConcurrencyLimit(3)`；同 channel 只 fetch 一次
- **驗證方式**：新增 UT-206 模擬 N 個 session，assert fetch/edit 次數與並發上限；手動改貨幣名稱觀察延遲可接受

#### P1-3: 補齊 handler 層 parity 回歸測試

- **涉及檔案**：`UserPanelButtonHandler.test.ts`, `RedeemCodeCommandHandler`（新建 test）
- **根因**：REG-201 僅測 shop helper；R5.4/R6.1/R4.4/R5.2 無 handler 覆蓋
- **修復方案**：
  - `/redeem-code` 無 session 仍可開 modal 並提交
  - `user_panel_back` 返回主面板且不 `createSession`
  - modal `customId`/field min-max 16–20
  - redemption success/fail ephemeral 文案經 handler 路徑
- **驗證方式**：`pnpm vitest run --project @ltdjms/user-panel` 新增用例全綠

#### P1-4: Admin DI idempotent guard

- **涉及檔案**：`packages/admin/src/di/AdminModule.ts` > `configureAdminContainer`（L121）
- **根因**：無 `_configured` flag，重複呼叫會重複註冊 Discord listener 與 event handler
- **修復方案**：加入 `let _adminConfigured = false` guard；`SlashCommandListener.listen()` 加 `if (this.listening) return`
- **驗證方式**：新增 admin DI idempotency 測試，類比 `user-panel-module.test.ts`

#### P1-5: 統一 user-panel DI 生命週期

- **涉及檔案**：`user-panel-module.ts`, `AdminModule.ts`, `apps/bot/src/main.ts`
- **根因**：container reconfigure 與 handler 註冊分離
- **修復方案**：`registerUserPanelHandlers` 開頭 assert container 已 register tokens；或將 handler 註冊移入 `configureUserPanelContainer(registrar)`；bot re-init 路徑文件化
- **驗證方式**：integration test dispose → reconfigure → handler 仍 dispatch 至新 instance

### P2 修復

#### P2-1: 修正 IT-101 mock 方法名

- **涉及檔案**：`packages/user-panel/src/di/__tests__/user-panel-module.test.ts`（L48–62）
- **根因**：mock 使用 `getBalanceView`/`getTokenBalance`，真實 API 為 `getBalanceUnchecked`/`getBalance`
- **修復方案**：更名 mock 方法；補齊 `BalanceView` 必要欄位
- **驗證方式**：IT-101 通過；可選加 `MemberInfoFacade` resolve + `getUserPanelView` smoke

#### P2-2: 清理 admin 殘留 user-panel 字串

- **涉及檔案**：`packages/admin/src/i18n/zh-TW.ts`, `packages/admin/src/constants/colors.ts`
- **根因**：extraction 後未刪除 dead keys/constants
- **修復方案**：刪除 `userPanel*` i18n block 與 `Colors.USER_PANEL`/`HISTORY_*`
- **驗證方式**：`grep userPanel packages/admin/src` 無結果；`make lint` 通過

#### P2-3: 移除 CommandHandler shim

- **涉及檔案**：`packages/user-panel/src/infra/CommandHandler.ts`, `packages/admin/src/commands/infra/CommandHandler.ts`
- **根因**：已抽取至 `@ltdjms/shared`，shim 無存在必要
- **修復方案**：handler 直接 `import type { CommandHandler } from '@ltdjms/shared'`；刪除 shim 檔
- **驗證方式**：`make build` 通過

#### P2-4: 統一 user-panel 字串來源

- **涉及檔案**：`packages/user-panel/src/i18n/zh-TW.ts`, embed/history/handler
- **根因**：parity 要求固定文案，但 i18n 僅部分 wired
- **修復方案**：將 embed/history/modal 字串改引用 `ZhTwStrings`，或刪除未用 i18n keys 並在 constants 集中管理
- **驗證方式**：oracle tests 仍通過；無 duplicate 硬編碼

#### P2-5: 評估 InteractionHook 儲存以減少 REST

- **涉及檔案**：`UserPanelCommand.ts`, `UserPanelUpdateListener.ts`, `PanelSessionData`
- **根因**：僅存 channelId/messageId，push 需雙 fetch
- **修復方案**：session 可選存 `editReply` callback 或 webhook URL；需評估 discord.js ephemeral hook 生命週期
- **驗證方式**：benchmark push 路徑 REST 次數；與 Java hook.edit 行為對齊

#### P2-6 ~ P2-10: 文件同步、dead code 清理、邊界調整

- **P2-6**：`getAllForGuild` 可維持現狀（MAX_SESSIONS=1000 可接受）；若優化則加 guild 二級 index
- **P2-7**：更新 atlas `panel-session` 為 in-memory TTL
- **P2-8**：sync `docs/plans/.../fixtures/` 與 package fixtures
- **P2-9**：將 `registerUserPanelHandlers` 呼叫移至 `apps/bot/src/main.ts`，移除 admin 對 user-panel 依賴
- **P2-10**：刪除 `emptyHistoryPage`、`getRedemptionHistory`、UserPanelService 冗餘 re-export

### P3 改善

#### P3-1 ~ P3-6

- **P3-1**：更新 spec 或將 `ROUTING_PREFIX` 改為 `'user_panel_'`（行為等價）
- **P3-2**：`_configured = true` 移至 function 末尾
- **P3-3**：`USER_PANEL_TOKENS` 移至 `@ltdjms/user-panel/testing` entry
- **P3-4**：抽取 shared `ensureDeferred`
- **P3-5**：移除 user-panel 未用 `tsyringe` dep
- **P3-6**：更新 parity checklist L33 反映 `make verify` 已存在

---

## 驗證證據

```bash
make verify          # exit 0 (2026-05-24)
pnpm vitest run --project @ltdjms/user-panel  # 9 files, 36 tests passed
grep -r "user_history_\|user_redeem_" packages/  # no matches
```

---

## 簽收建議

1. **必須（P1）**：完成 Discord 手動 smoke；修復 guild-wide 推送 debounce/concurrency；補 handler 測試
2. **建議（P2）**：清理 dead code、修正 IT-101 mocks、同步 plan fixtures 與 atlas
3. **可選（P3）**：API 表面收斂、文件用語統一

完成 P1 後可重新執行 `/qa` 請求 PASS 複審。
