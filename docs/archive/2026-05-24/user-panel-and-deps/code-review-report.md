# Code Review Report

- **Spec**: user-panel-and-deps（preparation + 6 member specs）
- **Date**: 2026-05-24
- **Reviewer**: QA Agent
- **Result**: **PASS**

---

## 業務需求驗收

**結論：本次 batch 已滿足規劃中的核心業務要求。** TypeScript 個人面板已 1:1 對齊 Java oracle（customId、embed、button、history、modal、session、即時更新）；程式碼已抽成獨立 `@ltdjms/user-panel` package；monorepo 依賴已升級且 `make verify` 全綠（build + test + lint + format-check）。

| 需求域 | 判定 | 證據 | 缺口 / 不確定性 |
|--------|------|------|----------------|
| Java 1:1 UI parity | ✅ 滿足 | `UserPanelConstants` 對齊 `fixtures/java-custom-ids.json`（UT-204）；embed/components（UT-201/202）；history（UT-203）；handler 回歸測試 | push footer oracle fixture 文案過時（實作與 Java 一致，見 P2-1） |
| Package 抽離 | ✅ 滿足 | `packages/user-panel/` 獨立編譯；`packages/admin/src/panel/user/` 已移除；handler 註冊在 `apps/bot/src/main.ts` | `SlashCommandRegistrar` 仍 import user-panel 定義（見 P2-2） |
| 依賴升級 | ✅ 滿足 | TS6/Vitest4/ESLint10、zod4、drizzle 0.45、pino10、langchain 1.x、express5 均已 bump；`make verify` exit 0 | member checklist 部分項目未同步勾選（文檔 lag，非功能缺口） |
| 即時更新 | ✅ 滿足 | `UserPanelUpdateListener` debounce + channel grouping + concurrency（UT-205/206）；embed-only edit（L157-158） | 單用戶 burst 更新未 coalesce（見 P2-3，Java 亦無 hook 以外優化） |
| `/redeem-code` session gap | ✅ 滿足 | `RedeemCodeCommandHandler` 直接開 modal；測試覆蓋 | — |
| Batch 品質閘 | ✅ 滿足 | `make verify` 2026-05-24 複審通過 | staging Discord 視覺 smoke 仍建議人工複核 |

---

## 六維度審查摘要

| 維度 | 結論 |
|------|------|
| 無幻覺代碼 | ✅ `MemberInfoFacade` 所有 service 呼叫均對應真實 API（`getBalanceUnchecked`、`getBalance`、`getTransactionPage`、`redeemCode`） |
| 無冗余代碼 | ✅ 已移除 admin user-panel shim、`CommandHandler` shim、dead exports；listener 與 admin 仍有可抽取的 batch-update 模式（P3-2） |
| 無 spec 實作偏移 | ⚠️ 輕微：`USER_PANEL_TOKENS` 改至 `testing` entry（P2-4）；session 過期文案未提及 `/user-panel`（P3-1） |
| 無 spec 實作遺漏 | ✅ R1–R6、extraction R1–R4、deps specs 均已落地；parity checklist 僅 architecture diff validate 未勾（P3-3） |
| 無架構瑕疵 | ⚠️ admin↔user-panel slash 定義耦合、`package.json`/lockfile 不同步（P2-2）；其餘 DI 生命週期、bot 啟動順序正確 |
| 無性能隱患 | ⚠️ guild-wide 更新已優化；per-user balance/token 更新無 throttle（P2-3）；可接受於目前規模 |

---

## 發現的問題

### P0 — 嚴重缺陷

（無）

### P1 — 重要問題

（無）

### P2 — 一般問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | `java-main-panel-oracle.json` 的 `footerPushUpdate` 仍為 `"資料已更新"`，與 Java/TS 實作（`點擊下方按鈕查看流水紀錄`）不一致 | Oracle 文檔誤導後續 parity 維護；不影響執行期行為 | `packages/user-panel/src/__tests__/fixtures/java-main-panel-oracle.json` | L22 |
| 2 | `SlashCommandRegistrar` import `@ltdjms/user-panel`，但 `packages/admin/package.json` 未宣告依賴；`pnpm-lock.yaml` 仍保留 admin→user-panel link | 違反 coordination 邊界意圖；lockfile 與 manifest 漂移，未來 `pnpm install` 可能不一致 | `packages/admin/src/commands/registration/SlashCommandRegistrar.ts` | L3, L53 |
| 3 | `balance_changed` / `game_token_changed` 路徑無 per-user debounce 或 in-flight guard | 大量連續餘額變更時可能並發 REST + DB 查詢，有 Discord 429 風險 | `packages/user-panel/src/listeners/UserPanelUpdateListener.ts` | L47-56, L116-128 |
| 4 | `USER_PANEL_TOKENS` 未從 `@ltdjms/user-panel` 主入口導出（僅 `./testing`） | 與 extraction spec R4.1 字面要求偏移；屬刻意封裝，但 spec 未更新 | `packages/user-panel/src/index.ts` | L1-14 |
| 5 | guild-wide debounce 更新與 per-user 即時更新可並發，同一 message 可能 last-writer-wins | 短暫 UI 不一致或額外 REST 呼叫 | `packages/user-panel/src/listeners/UserPanelUpdateListener.ts` | L42-56 vs L59-114 |
| 6 | `updateUserPanel` 任意錯誤即 `removeSession` | 暫時性 Discord 5xx/timeout 會提前終止 push 更新 | `packages/user-panel/src/listeners/UserPanelUpdateListener.ts` | L126-128 |
| 7 | `disposeUserPanelContainer()` 未清除 pending debounce timers | 關閉/重配 DI 時舊 timer 可能仍觸發 | `packages/user-panel/src/di/user-panel-module.ts` | L156-175 |

### P3 — 建議改善

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | Session 過期 ephemeral 為「面板已過期，請重新執行指令」，未明確提示 `/user-panel` | 與 spec edge case 文案略有差異 | `packages/user-panel/src/i18n/zh-TW.ts` | L5 |
| 2 | user-panel 與 admin 的 batch panel update 邏輯重複 | 長期 drift 風險 | `UserPanelUpdateListener.ts` vs `AdminPanelUpdateListener.ts` | — |
| 3 | parity checklist「architecture diff validate」未勾 | 文檔 sign-off 不完整 | `user-panel-java-parity/checklist.md` | L36 |
| 4 | `startAdminSlashCommandListener()` 未使用，bot 直接 resolve listener | 死 API surface | `packages/admin/src/di/AdminModule.ts` | L409-415 |
| 5 | `main.ts` 啟動步驟註解與實際順序不完全一致 | 維護者易誤解 wiring 順序 | `apps/bot/src/main.ts` | L74-78, L221-242 |
| 6 | 無 dedicated user-panel 分頁 clamp 回歸測試 | 行為委託 downstream service，覆蓋間接 | — | — |
| 7 | DI 測試 mock 使用 duck-typed `Result` 而非 `Err()` | 擴展行為測試時可能踩坑 | `packages/user-panel/src/di/__tests__/user-panel-module.test.ts` | L64-66 |

---

## 解決方案

### P2 修復

#### P2-1: 修正 push footer oracle fixture

- **涉及檔案**：`packages/user-panel/src/__tests__/fixtures/java-main-panel-oracle.json`（L22）
- **根因**：fix 階段更新了 TS 常數與測試，但未同步 fixture 的 `footerPushUpdate` 欄位
- **修復方案**：將 `footerPushUpdate` 改為 `"點擊下方按鈕查看流水紀錄"`；可選在 `UserPanelEmbedBuilder.test.ts` 增加 push footer 斷言
- **驗證方式**：`pnpm vitest run --project @ltdjms/user-panel -t UserPanelEmbedBuilder`

#### P2-2: 解耦 admin 與 user-panel slash 定義聚合

- **涉及檔案**：`packages/admin/src/commands/registration/SlashCommandRegistrar.ts`（L3, L50-68）、`apps/bot/`、`packages/admin/package.json`
- **根因**：slash 註冊聚合留在 admin，但 handler/DI 已移至 bot + user-panel；fix 移除 admin 對 user-panel 的 package.json 依賴時未一併搬移定義
- **修復方案**：（擇一）(A) 在 `apps/bot` 建立 command composer，合併 admin/economy/user-panel/shop/dispatch 定義後呼叫 REST 註冊；(B) 若短期保留 admin 聚合，則在 `packages/admin/package.json` 與 `tsconfig.json` 正式宣告 `@ltdjms/user-panel` 並 `pnpm install` 同步 lockfile
- **驗證方式**：`pnpm exec tsc --noEmit`（admin 獨立）；`make verify`

#### P2-3: Per-user push update coalescing

- **涉及檔案**：`packages/user-panel/src/listeners/UserPanelUpdateListener.ts` > `onEvent` / `updateUserPanel`（L47-56, L116-128）
- **根因**：僅 guild-wide `currency_config_changed` 有 debounce；per-user 事件每次觸發完整 fetch+edit
- **修復方案**：新增 `Map<guildId:userId, timer>` debounce（200–500ms）或 in-flight promise guard；可參考 `AdminPanelUpdateListener` throttle map
- **驗證方式**：新增 UT：短時間連續 `balance_changed` 僅觸發一次 `message.edit`；`make verify`

#### P2-4: 同步 spec 與 `USER_PANEL_TOKENS` 導出策略

- **涉及檔案**：`packages/user-panel/src/index.ts`、`user-panel-package-extraction/spec.md` R4.1
- **根因**：fix 階段將 token 移至 `./testing` 以封裝 DI，spec 未回寫
- **修復方案**：更新 spec R4.1 為「主入口不導出 token；測試透過 `@ltdjms/user-panel/testing`」；或恢復主入口 re-export 並在 spec 註明測試用途
- **驗證方式**：文檔與 `packages/user-panel/package.json` exports 一致

#### P2-5: 序列化 guild-wide 與 per-user 更新

- **涉及檔案**：`packages/user-panel/src/listeners/UserPanelUpdateListener.ts`
- **根因**：兩條更新路徑無 per-session mutex
- **修復方案**：以 `(guildId, userId)` 或 `(channelId, messageId)` 為 key 的 Promise 鏈 / 簡易 queue
- **驗證方式**：並發 event 單元測試 assert edit 順序或最終 embed 正確

#### P2-6: 收窄 session 移除條件

- **涉及檔案**：`packages/user-panel/src/listeners/UserPanelUpdateListener.ts` > `updateUserPanel`（L126-128）
- **根因**：catch-all 移除策略過於激進
- **修復方案**：僅在 Discord 404 / Unknown Message / channel 不可達時 `removeSession`；5xx/429 記 log 並保留 session
- **驗證方式**：mock 5xx 不應 remove；mock 404 應 remove

#### P2-7: dispose 時清除 debounce timers

- **涉及檔案**：`packages/user-panel/src/di/user-panel-module.ts`、`UserPanelUpdateListener.ts`
- **根因**：listener 實例的 `debounceTimers` 在 dispose 後仍可能觸發
- **修復方案**：新增 `UserPanelUpdateListener.dispose()` 清除所有 timer；`disposeUserPanelContainer()` 呼叫之
- **驗證方式**：fake timers 測試 dispose 後 timer callback 不執行

### P3 改善

#### P3-1: Session 過期文案

- **涉及檔案**：`packages/user-panel/src/i18n/zh-TW.ts` > `sessionExpired`（L5）
- **根因**：parity spec edge case 建議提及 `/user-panel`，實作使用通用文案
- **修復方案**：改為 `面板已過期，請重新執行 /user-panel`（需確認與 Java 一致後再改）
- **驗證方式**：對照 Java i18n；更新 handler 測試斷言

#### P3-2: 抽取共用 panel push coordinator

- **涉及檔案**：`UserPanelUpdateListener.ts`、`AdminPanelUpdateListener.ts`
- **根因**：channel grouping + concurrency 邏輯重複
- **修復方案**：在 `@ltdjms/shared` 抽取 `processPanelSessionsByChannel` helper
- **驗證方式**：兩 listener 測試仍綠；`make verify`

#### P3-3: 完成 architecture diff validate

- **涉及檔案**：`user-panel-java-parity/checklist.md`（L36）
- **根因**：sign-off 項目遺漏
- **修復方案**：執行 atlas validate 並勾選 checklist
- **驗證方式**：checklist `[x]`

#### P3-4: 清理 `startAdminSlashCommandListener` 死 API

- **涉及檔案**：`packages/admin/src/di/AdminModule.ts`、`apps/bot/src/main.ts`
- **根因**：bot 改為直接 resolve listener
- **修復方案**：bot 改用 exported helper，或移除 export
- **驗證方式**：`make build`

#### P3-5: 更新 `main.ts` 啟動註解

- **涉及檔案**：`apps/bot/src/main.ts`（L74-78, L221-242）
- **根因**：user-panel wiring 加入後註解未更新
- **修復方案**：重排步驟編號，明確記載 configure → register handlers → listen
- **驗證方式**：人工 review

#### P3-6: 分頁 clamp 回歸測試（可選）

- **涉及檔案**：`packages/user-panel/src/services/__tests__/UserPanelHistoryViewFactory.test.ts`
- **根因**：clamp 邏輯在 economy/games/shop service，user-panel 未直接覆蓋
- **修復方案**：以 mock facade 回傳超界 page，assert factory 顯示 clamp 後頁
- **驗證方式**：`pnpm vitest run --project @ltdjms/user-panel -t pagination`

#### P3-7: DI 測試使用正式 `Err()` mock

- **涉及檔案**：`packages/user-panel/src/di/__tests__/user-panel-module.test.ts`（L64-66）
- **根因**：IT-101 僅測 resolve，mock 簡化
- **修復方案**：`redeemCode: vi.fn().mockResolvedValue(Err(...))`
- **驗證方式**：`pnpm vitest run --project @ltdjms/user-panel -t user-panel-module`

---

## 複審結論

相較上一輪 review，先前 P1 項目（guild-wide 性能、handler 回歸測試、DI 冪等、admin/user-panel 解耦、dead code 清理）均已修復並通過 `make verify`。

**本次判定 PASS**：核心業務需求已正確實作，無幻覺代碼，無 P0/P1 阻斷項；殘留 P2/P3 為可維護性、文檔同步與高負載優化，不影響 batch 功能驗收與合併。

建議合併前可選處理 **P2-1**（fixture 同步）與 **P2-2**（package 邊界/lockfile 一致），其餘 P2/P3 可後續 incremental PR 處理。
