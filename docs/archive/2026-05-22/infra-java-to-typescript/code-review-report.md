# Code Review Report

- **Spec**: infra-java-to-typescript (batch: cache-invalidation-listener, localization-centralization, infra-verification)
- **Date**: 2026-05-23
- **Reviewer**: QA Agent (六維度並行審查)
- **Result**: PASS (需修正 1 個 P1)

---

## 發現的問題

### P0 — 嚴重缺陷

無。

### P1 — 重要問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | 缺少 guildId/userId 時未記錄警告日誌就直接返回，與 spec 錯誤處理要求「記錄警告並跳過」不符 | 缺少欄位的事件無法被追蹤，運維排查困難 | `packages/shared/src/infra/cache/cache-invalidation-listener.ts` | L37, L48 |

### P2 — 一般問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | 測試中定義了 `mockCacheService.get` 和 `mockCacheService.put` 但從未被使用 | 測試程式碼冗余，不影響功能 | `packages/shared/src/__tests__/cache-invalidation-listener.test.ts` | L16-20 |
| 2 | `beforeEach` 中 `vi.clearAllMocks()` 後又呼叫 `.mockReset()` 重複清除 | 執行冗余，不影響正確性 | `packages/shared/src/__tests__/migration-runner.test.ts` | L41-47 |
| 3 | DiceGame1 / DiceGame2 指令的 option description 使用內聯字串 `'要使用的代幣數量'` 而非從 `CommandLocalizations.OPTION_DESCRIPTION_LOCALIZATIONS['tokens']` 取得 | 在地化集中化不完整，文字一致但來源不一致 | `packages/admin/src/commands/registration/EconomySlashCommands.ts` | L40, L50 |
| 4 | RedeemCodeSlashCommand 使用內聯在地化，`CommandLocalizations` 中無 `redeem-code` 鍵 | 非 spec 範圍內（Java 版 12 個指令不含 redeem-code），但集中化不完整 | `packages/admin/src/commands/registration/SlashCommandRegistrar.ts` | L42-43 |

### P3 — 建議改善

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | `CommandLocalizations` 的五個 accessor 函數和三個型別別名匯出後未被任何消費者使用（但屬於 spec T1.1 明確要求） | 公共 API 膨脹，不影響功能 | `packages/shared/src/localization/command-localizations.ts` | L79-102 |
| 2 | 測試中 snowflake-length 鍵格式測試與基本格式測試重複（純字串插值無分支） | 測試冗余，不影響覆蓋率 | `packages/shared/src/__tests__/cache-key-generator.test.ts` | L17-29 |
| 3 | 多個 handler 中 `.replace()` 鏈式呼叫產生中間字串物件 | 微小 GC 壓力，非熱路徑 | `packages/economy/src/commands/dice-game-2-handler.ts` 等 | L122-131 |
| 4 | `[...arr].join()` 展開運算符冗余，`.join()` 不改變原陣列 | 多餘的陣列複製，不影響正確性 | `packages/economy/src/commands/dice-game-2-handler.ts` | L105, L109, L115 |

---

## 解決方案

### P1 修復

#### P1-1: 缺少 guildId/userId 時補上警告日誌

- **涉及檔案**：`packages/shared/src/infra/cache/cache-invalidation-listener.ts` > `onEvent()`（L36-38, L47-49）
- **根因**：實作時在兩個事件分支中只做了 `return` 跳過，遺漏了 spec 要求的「記錄警告」步驟
- **修復方案**：在兩個 `if (!evt.guildId || !evt.userId)` 區塊的 `return` 之前加入 `this.logger.warn({ eventType: event.eventType }, 'Event missing required fields guildId or userId, skipping cache invalidation')`
- **驗證方式**：`pnpm vitest run --project @ltdjms/shared -t "CacheInvalidationListener"` — 現有測試「should not invalidate cache when event is missing userId/guildId」需擴充以驗證 logger.warn 被呼叫

### P2 修復

#### P2-1: 移除測試中未使用的 mock 屬性

- **涉及檔案**：`packages/shared/src/__tests__/cache-invalidation-listener.test.ts` > `beforeEach`（L16-20）
- **根因**：mock 物件定義了 `get` 和 `put` 但測試中只使用 `invalidate`
- **修復方案**：從 mock 物件中移除 `get` 和 `put` 屬性
- **驗證方式**：`pnpm vitest run --project @ltdjms/shared -t "CacheInvalidationListener"` 確認全部通過

#### P2-2: 移除 beforeEach 中冗余的 .mockReset()

- **涉及檔案**：`packages/shared/src/__tests__/migration-runner.test.ts` > `beforeEach`（L41-47）
- **根因**：`vi.clearAllMocks()` 已清除所有 mock 狀態，`.mockReset()` 屬重複操作
- **修復方案**：移除四個 `.mockReset()` 呼叫
- **驗證方式**：`pnpm vitest run --project @ltdjms/shared -t "runMigrations"` 確認全部通過

#### P2-3: DiceGame 指令 option description 改用集中的在地化

- **涉及檔案**：`packages/admin/src/commands/registration/EconomySlashCommands.ts` > DiceGame1SlashCommand, DiceGame2SlashCommand（L40, L50）
- **根因**：option description 內聯字串而非從 CommandLocalizations 取得
- **修復方案**：將 `description: '要使用的代幣數量'` 改為從 `CommandLocalizations.OPTION_DESCRIPTION_LOCALIZATIONS['tokens']` 取得。注意 Discord API 的 option description 欄位型別可能非 `Record<string, string>`，需確認 SlashCommandDefinition 中的 options 型別定義是否支援 `descriptionLocalizations`
- **驗證方式**：`make build` 通過

### P3 改善

P3 項目均為非功能性改善，不影響 spec 需求的滿足。建議在後續重構中處理：
- P3-1（accessor 函數未被使用）：保留（spec 明確要求），未來消費者可直接使用 accessor 函數而非直接索引物件
- P3-2（測試重複）：合併或標註不同意圖
- P3-3（`.replace()` 鏈）：使用單次替換函數優化
- P3-4（冗余展開）：直接呼叫 `.join()` 不展開

---

## 已知既有問題（非本次 spec 引入）

以下問題在本次實作前已存在，不屬於 spec 實作品質問題，但審查過程中發現並記錄：

| # | 問題描述 | 嚴重度 | 檔案 |
|---|--------|--------|------|
| E1 | `CurrencyConfigSlashCommand` / `GameTokenAdjustSlashCommand` / `AdjustBalanceSlashCommand` 缺少 `options` 定義，導致 Discord API 不會提示使用者輸入必要參數 | P1 | `EconomySlashCommands.ts` |
| E2 | `main.ts` 匯入了 `DomainError` 但從未使用 | P2 | `apps/bot/src/main.ts` L18 |
| E3 | `getAllDefinitions()` 靜態方法未被任何呼叫者使用（死碼） | P2 | `SlashCommandRegistrar.ts` L78 |
| E4 | `DICE_EMOJI` 靜態映射內嵌在 handler 中，違反 handler 薄度原則 | P3 | `dice-game-1-handler.ts` L24-32 |
| E5 | `DomainEventPublisher._lastEvent` 在生產環境中保留最後事件的強參考，阻止 GC | P3 | `domain-event-publisher.ts` L77 |
| E6 | `CurrencyConfigService.getConfig()` 和 `DiceGameService.play()` 可並行化但序列呼叫 | P3 | `dice-game-1-handler.ts` L93-99 |

---

## 審查維度摘要

| 維度 | 結果 | 關鍵發現 |
|------|------|---------|
| 無幻覺代碼 | PASS | 所有 import、型別、方法參照均正確存在（`make build` 驗證通過） |
| 無冗余代碼 | PASS (含建議) | P1: accessor 函數未被使用（但 spec 要求）；P2: 測試冗余 mock 屬性 |
| 無 spec 偏移 | PASS (含 1 修正) | P1: 缺少 guildId/userId 警告日誌 |
| 無 spec 遺漏 | PASS (含 1 修正) | 同上；其餘 30+ 需求全部滿足 |
| 無架構瑕疵 | PASS | CacheInvalidationListener switch 語句為 Java 1:1 移植；DiceGameMessages 位置符合 spec |
| 無性能隱患 | PASS | 無熱路徑瓶頸；P3 級改善建議不影響功能正確性 |

---

## 審查結論

本次 **infra-java-to-typescript** batch spec 的三份子 spec 全部實作完成，核心功能正確且符合需求。唯一的實質問題是 **P1-1**（缺少 guildId/userId 警告日誌），修正成本低（加入兩行 `this.logger.warn()` 並擴充對應測試斷言）。

審查結果：**PASS**（修正 P1-1 後即為完全通過）。
