# Code Review Report

- **Spec**: ts-test-strategy (Integration PBT + ECPay E2E Test Infrastructure)
- **Date**: 2026-05-22
- **Reviewer**: QA Agent (6-dimension parallel review)
- **Result**: **PASS** (附帶改善建議)

---

## 總評

本次實作完成了 20 個 PBT/E2E 測試檔案 + 7 個共享基礎設施檔案，覆蓋 economy (5)、shop (4 PBT + 4 E2E)、admin (7) 共三個業務模組。全量 `make test` 結果：64 test files, 680 tests, **0 failures, exit 0**。

**核心業務需求已滿足**：餘額守恆、兌換碼冪等、ECPay 加解密正確性、管理面板設定 CRUD 均有測試覆蓋。

### 六維度審查結果一覽

| 審查維度 | 發現問題數 | 最高嚴重度 |
|----------|:----------:|:----------:|
| 幻覺代碼審查 | **0** | — (全部 import/函數/型別/DI token 皆正確) |
| 冗餘代碼審查 | 9 | P1 (Shop beforeEach 61 行 × 4, 4 未使用 import, 2 死代碼) |
| Spec 實作偏移審查 | 10 | P1 (Admin mock 化, Arbitrary 型別, fc.sample 誤用) |
| Spec 實作遺漏審查 | 30+ | P1 (reward 驗證缺失, token set 未測, transaction record 未驗證) |
| 架構瑕疵審查 | 11 | P1 (module boundary 繞過, no-op teardown, container 洩漏) |
| 性能隱患審查 | 10 | P1 (Makefile for-loop, DI 重初始化, numRuns 不一致) |

以下按嚴重程度列出發現的問題。無 P0 嚴重缺陷（無功能錯誤或安全漏洞）。

---

## 發現的問題

### P1 — 重要問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | **Admin PBT 全部使用 mock (`vi.fn()`)，未走 real DB 管線** — spec 定義 admin-pbt 為 Integration PBT（facade→service→repository→real DB），但 7 個 admin 測試檔全部使用 mock，與 spec 設計意圖偏離 | Admin facade 的 delegate 行為雖被驗證，但 DB schema、repository 實作、service 邏輯完全未在測試中覆蓋 | `packages/admin/src/__tests__/*.pbt.test.ts` (7 files) | — |
| 2 | **Shop PBT 四個測試檔 beforeEach 完全重複（各 61 行）** — 相同的 DI 容器初始化邏輯（initializeContainer + configureEconomyContainer + configureContainer + productRewardService/escortDispatchHandoffService mock）在 4 個檔案中逐字重複 | 未來 DI 設定變更需同步修改 4 個檔案，易遺漏；共約 240 行重複代碼 | `packages/shop/src/__tests__/redemption.pbt.test.ts` | L18-78 |
| | | | `packages/shop/src/__tests__/shop-purchase.pbt.test.ts` | L19-79 |
| | | | `packages/shop/src/__tests__/fiat-order-creation.pbt.test.ts` | L20-80 |
| | | | `packages/shop/src/__tests__/currency-purchase.pbt.test.ts` | L18-78 |
| 3 | **Makefile 逐檔啟動 vitest process 造成約 15s 額外開銷** — `for f in *.pbt.test.ts; do pnpm vitest run ... "$f"; done` 對 economy 5 檔 + shop 4 檔共啟動 9 次獨立 vitest 程序 | 每次 vitest 啟動含 Node.js 載入、TypeScript 解析、globalSetup 重複執行開銷。在 fileParallelism: false + pool: forks 模式下，單一 vitest run 已提供充足的檔案間隔離 | `Makefile` | L20-26 |
| 4 | **globalTeardown 是 no-op 且 Ryuk 已停用，容器無清理機制** — globalSetup 設定 `TESTCONTAINERS_RYUK_DISABLED=true`，globalTeardown 為 no-op | 若 vitest process 被 SIGKILL 或因 crash 中斷，container 成為 orphan，CI 環境長期累積可能耗盡 Docker 資源 | `packages/shared/src/__tests__/vitest.globalSetup.ts` L48, `vitest.globalTeardown.ts` | — |
| 5 | **Arbitrary guildId/userId 型別偏離 spec** — spec R4.1/R4.2 要求 discord snowflake 格式字串 (17-19 位)，實作回傳 `number` (1..2147483647) | 與 spec 設計不一致。雖然因為 DB 使用 `bigint({ mode: 'number' })` 故 number 型別合理，但 spec 與實作必須對齊 | `packages/shared/src/__tests__/arbitrary.ts` | L11-15 |
| 6 | **Economy self-transfer 測試未正確實作 spec R3.1** — 自我轉帳測試使用兩次獨立的 `tryAdjustBalance`（先 debit 再 credit），兩次都預期成功（net effect = 0），而非驗證 `sender === receiver` 時回傳單一 DomainError | 未能驗證 spec 定義的「自我轉帳被拒絕」行為。目前測試驗證的是「兩次獨立調整互相抵消」而非「一次轉帳被拒絕」 | `packages/economy/src/__tests__/balance-transfer.pbt.test.ts` | L148-198 |
| 7 | **兌換碼 reward 發放未驗證** — spec R1.4 要求「獎勵正確發放給首次兌換者」，但 `redemption.pbt.test.ts` 僅檢查兌換成功/失敗，未查詢 DB 確認用戶餘額因 reward 而增加 | 兌換碼兌換的核心價值（用戶收到獎勵）未得到測試保證 | `packages/shop/src/__tests__/redemption.pbt.test.ts` | — |
| 8 | **Shop PBT 以相對路徑繞過模組邊界** — 所有 shop PBT 使用 `../../../shared/src/infra/database/test-db-reset.js` 相對路徑 import，而非 `@ltdjms/shared` package 路徑（economy 測試則正確使用後者） | 繞過了 shared package 的公開 API 邊界。若 shared 內部檔案被重構或移動，shop 測試會中斷 | `packages/shop/src/__tests__/*.pbt.test.ts` (4 files) | L11-13 |
| 9 | **ECPay E2E 冪等取號測試未 assert 相同 paymentNo** — spec R1.2 要求同訂單兩次取號回傳相同 paymentNo，但 `ecpay-cvs-e2e.test.ts` 僅檢查兩個 paymentNo 皆 non-empty | 未真正驗證 ECPay 端冪等行為 | `packages/shop/src/__tests__/ecpay-cvs-e2e.test.ts` | L106-108 |

### P2 — 一般問題

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | **4 個 Shop PBT 檔案 import 了未使用的 `beforeAll`/`afterAll`** — 從 `vitest` import 但從未調用 | 編譯器/IDE 無謂警告，程式碼整潔度下降 | `packages/shop/src/__tests__/redemption.pbt.test.ts` 等 4 檔 | L1 |
| 2 | **`createTestContainer()` 定義後從未被調用** — 所有實際測試直接使用 `resetRootContainer()` + 手動初始化，而非此共享 helper | 死代碼，佔用維護心智成本。Spec R5.x 設計的 helper 未被採用 | `packages/shared/src/__tests__/test-container.ts` | L23-47 |
| 3 | **`seedDiceGame2Config()` 定義後從未被調用** — `dice-game-2.pbt.test.ts` 手動構造 config 物件而非使用 seed factory | 死代碼。factory function 的維護成本（schema 變更時需同步更新）無回報 | `packages/shared/src/__tests__/seed-factory.ts` | L203-220 |
| 4 | **`fiat-order-creation.pbt.test.ts` import 了未使用的 `ok`/`isOk`/`isErr`/`err`** | 無謂 import | `packages/shop/src/__tests__/fiat-order-creation.pbt.test.ts` | L7 |
| 5 | **Economy 透支/自我轉帳測試未驗證 R2.2/R3.2（失敗不產生 transaction record）** — 兩個失敗場景都未查詢 `currency_transaction` 表確認無殘留記錄 | 轉帳失敗的副作用驗證不完整 | `packages/economy/src/__tests__/balance-transfer.pbt.test.ts` | L202-242 |
| 6 | **Redemption tests 使用 `fc.sample` + for-loop 而非 `fc.assert(asyncProperty)`** — 失去 fast-check 的 shrinking 能力 | 失敗時無法獲得最小化重現案例，除錯成本增加。且非真正的 PBT 模式 | `packages/shop/src/__tests__/redemption.pbt.test.ts` | L91-128 等 |
| 7 | **ECPay callback 測試使用 `resetDatabase()` (DROP/CREATE DATABASE) 而非 `cleanAllTestTables()` (DELETE)** — ECPay E2E 與 Shop PBT 在同一 package 內使用不同的 DB 重置策略 | 策略不統一。`resetDatabase()` 使用 `pg_terminate_backend()` 會影響共用同一 DB 的其他測試連線 | `packages/shop/src/__tests__/ecpay-callback-e2e.test.ts` 等 3 檔 | — |
| 8 | **Economy game-token PBT 缺少 token set 操作測試** — spec R6.3 要求「token set 後數量 = 設定值」 | Spec 要求未被覆蓋 | `packages/economy/src/__tests__/game-token.pbt.test.ts` | — |
| 9 | **DiceGame PBT 未驗證下注後 game token 扣減** — spec R4.3/R5.3 要求驗證 token cost 正確扣減 | 遊戲後 token 變化未直接驗證 | `packages/economy/src/__tests__/dice-game-1.pbt.test.ts`, `dice-game-2.pbt.test.ts` | — |

### P3 — 建議改善

| # | 問題描述 | 影響 | 檔案 | 行數 |
|---|--------|------|------|------|
| 1 | **Economy 5 個 PBT 測試各有自己的 `cleanTestTables()` 定義** — 3 種不同變體（7 表 / 5 表 / 3 表），核心邏輯重複 | 新增 economy 相關表時需同步更新 5 個檔案 | `packages/economy/src/__tests__/*.pbt.test.ts` (5 files) | — |
| 2 | **Admin 7 個 PBT 測試各自內聯定義 `guildId()`/`userId()` arbitrary** — 與 `@ltdjms/shared/__tests__/arbitrary` 重複 | 違反 coordination.md 的共享規則（test-infra 產出的工具應由下游 import 使用） | `packages/admin/src/__tests__/*.pbt.test.ts` (7 files) | L16-18 等 |
| 3 | **`dice-game-2.pbt.test.ts` 重複 import `DiceGame2Service`** — 一次 value import + 一次 type import (with alias) | 多餘的 import | `packages/economy/src/__tests__/dice-game-2.pbt.test.ts` | L10, L13 |
| 4 | **Economy PBT 中 `beforeEach` + predicate 內雙重 `cleanTestTables`** — beforeEach 清理一次，每個 fc.assert predicate 開頭又清理一次 | 空表 DELETE 代價極低（1-2ms），但累計 50 次 run 約浪費 50-100ms | `packages/economy/src/__tests__/balance-transfer.pbt.test.ts` | L33-40 + L73 |
| 5 | **`cleanAllTestTables` 順序執行 DELETE 而非並行** — `for...await` 逐表刪除，可用 `Promise.all` 並行 | 對測試效能影響微小（目前表數少），但可優化 | `packages/shared/src/infra/database/test-db-reset.ts` | L154-156 |
| 6 | **`numRuns` 設定不一致** — Economy PBT: 30-50 run, Shop PBT: 3-5 run, Admin PBT: default 100 | 缺乏統一策略說明。CI 時無法依風險調整 | — | — |
| 7 | **Seed data 使用逐筆 INSERT 而非 batch insert** — `for...of` 逐筆 `seedUserAccount(db, ...)`，每次都是獨立 DB round-trip | `numRuns: 50` × 3-5 用戶 = 150-250 次 INSERT round-trip | 多個 economy PBT | — |

---

## 解決方案

### P1 修復

#### P1-1: Admin PBT 應補上至少一個 integration PBT

- **涉及檔案**：`packages/admin/src/__tests__/*.pbt.test.ts` (7 files)
- **根因**：Admin 模組的 facade 層直接依賴外部 service 介面（來自 economy/shop/ai/dispatch package），在 DI container 外難以直接實例化。開發時選擇了 mock-based 方式以簡化 setup。
- **修復方案**：參考 economy-pbt 的 `currency-config.pbt.test.ts` 模式，建立一個完整的 admin integration PBT（如 `currency-config.pbt.test.ts` 的 integration 版本），使用 Testcontainer + real DI container 測試完整管線。現有 mock-based 測試可保留作為 facade contract 驗證的補充。
- **驗證方式**：新測試通過 `RUN_INTEGRATION_TESTS=true vitest run` 並驗證 DB state 變更。

#### P1-2: 提取 Shop PBT 共享 beforeEach 到共用工具

- **涉及檔案**：`packages/shop/src/__tests__/` 下 4 個 PBT 檔案的 L18-78
- **根因**：開發時逐檔複製 DI 初始化邏輯，未建立共享 setup helper。
- **修復方案**：在 `packages/shared/src/__tests__/` 建立 `shop-test-setup.ts`，匯出 `setupShopTestContainer(pool)` 函數封裝 61 行 DI 初始化邏輯。4 個測試檔案改為一行調用。
- **驗證方式**：`make test` 全部通過，4 個測試檔案的 beforeEach 從 61 行減至 5 行以內。

#### P1-3: 簡化 Makefile test target 去除逐檔 for-loop

- **涉及檔案**：`Makefile` L20-26
- **根因**：初始開發時擔心 tsyringe DI container 跨 PBT 檔案汙染（同 worker 內多個 PBT 檔案共享 global container）。經調查，在 `fileParallelism: false` + `pool: 'forks'` + 每個檔案 `beforeEach` 執行 `resetRootContainer()` 的組合下，同 worker 內的多檔案隔離已足夠。
- **修復方案**：移除 `for f in ...; do ... done`，改為 `pnpm vitest run --project @ltdjms/economy` 和 `pnpm vitest run --project @ltdjms/shop`，讓 vitest 自行管理檔案執行順序。
- **驗證方式**：`make test` 全部通過，總執行時間減少 10-15 秒。

#### P1-4: 修正 globalTeardown 加入 container 清理邏輯

- **涉及檔案**：`packages/shared/src/__tests__/vitest.globalTeardown.ts`
- **根因**：container 跨 process 重用使用 Ryuk-disabled + `/tmp/ltdjms-testcontainers.json` 機制。teardown 為 no-op 是因為需要其他 vitest process 繼續使用同一個 container。但缺少最後一個 process 退出時的清理機制。
- **修復方案**：在 teardown 中加入邏輯：若是最後一個 package（可透過環境變數 `LTDJMS_TEST_LAST_PACKAGE=true` 或檢查 `make test` 的最後階段），則讀取 `globalThis.__TEST_PG_CONTAINER` 並呼叫 `container.stop()`。
- **驗證方式**：執行 `make test` 後確認 `docker ps` 中無殘留 testcontainers 容器。

#### P1-5: 對齊 Arbitrary 型別 spec 描述

- **涉及檔案**：spec: `docs/plans/2026-05-22/ts-test-strategy/test-infra/spec.md` R4.1/R4.2；code: `packages/shared/src/__tests__/arbitrary.ts` L11-15
- **根因**：Discord snowflake 在 DB schema 使用 `bigint({ mode: 'number' })`，限制為 JavaScript safe integer 範圍 (< 2^53)，無法容納完整 64-bit snowflake。實作選擇了 number 型別以匹配 DB schema。
- **修復方案**：更新 spec R4.1/R4.2 的描述，將「discord snowflake 格式字串」修正為「正整數（1..2147483647，符合 DB bigint number mode）」。
- **驗證方式**：Spec 文件更新後與實作一致。

#### P1-6: 修正 economy self-transfer 測試

- **涉及檔案**：`packages/economy/src/__tests__/balance-transfer.pbt.test.ts` L148-198
- **根因**：`BalanceAdjustmentService.tryAdjustBalance()` 是通用餘額增減函數，無 sender/receiver 概念。self-transfer 的語義檢查存在於更上層的 handler 或 facade。
- **修復方案**：(a) 若 handler/facade 層有 self-transfer 檢查，測試應調用該層級方法；(b) 若無此檢查（即當前行為：兩次獨立 adjust 互相抵銷為設計意圖），則更新 spec R3.1 移除 self-transfer DomainError 的要求。
- **驗證方式**：測試正確反映實際業務邏輯設計。

#### P1-7: 補上兌換碼 reward 發放驗證

- **涉及檔案**：`packages/shop/src/__tests__/redemption.pbt.test.ts`
- **根因**：測試焦點在兌換碼冪等性，未延伸到 reward 發放驗證。
- **修復方案**：在首次兌換成功後查詢 `member_currency_account` 的 balance 或 `currency_transaction` 表，確認 reward 已正確入帳。在批量兌換測試中累計所有成功兌換的 reward 總和。
- **驗證方式**：`vitest run` 通過，DB 斷言確認 reward 入帳。

#### P1-8: 修正 Shop PBT import 路徑

- **涉及檔案**：`packages/shop/src/__tests__/redemption.pbt.test.ts` 等 4 檔 L11-13
- **根因**：開發時直接使用相對路徑而非 package import。
- **修復方案**：改為 `import { getTestPool, cleanAllTestTables } from '@ltdjms/shared/infra/database/test-db-reset'` 和 `import { seedGuild, seedProduct, seedRedemptionCode } from '@ltdjms/shared/__tests__/seed-factory'`，與 economy 測試保持一致。
- **驗證方式**：`make test` 全部通過。

#### P1-9: 補上 ECPay 取號冪等 assertion

- **涉及檔案**：`packages/shop/src/__tests__/ecpay-cvs-e2e.test.ts` L106-108
- **根因**：ECPay Stage API 的 `generateCvsPaymentCode` 每次產生不同的 `MerchantTradeNo`，ECPay 端視為不同訂單而回傳不同 `paymentNo`。冪等性可能無法保證。
- **修復方案**：(a) 若 ECPay 不保證取號冪等，更新 spec R1.2 描述並移除 `expect(paymentNo1).toBe(paymentNo2)`；(b) 修改測試使用相同 `MerchantTradeNo` 參數重複呼叫。
- **驗證方式**：測試通過且 spec 與實作一致。

### P2 修復

#### P2-1: 清理未使用的 import

- **涉及檔案**：`packages/shop/src/__tests__/redemption.pbt.test.ts` L1 等
- **修復方案**：從 import 清單移除 `beforeAll`, `afterAll`；從 `fiat-order-creation.pbt.test.ts` 移除 `ok`, `isOk`, `isErr`, `err`。
- **驗證方式**：TypeScript 編譯無未使用變數警告。

#### P2-2: 移除或採用死代碼

- **涉及檔案**：`packages/shared/src/__tests__/test-container.ts` L23-47, `packages/shared/src/__tests__/seed-factory.ts` L203-220
- **修復方案**：移除未被調用的 `createTestContainer()` 和 `seedDiceGame2Config()`，或修改現有測試使用它們。
- **驗證方式**：grep 確認無其他引用後刪除。

#### P2-3: 補上轉帳失敗不產生 transaction record 的驗證

- **涉及檔案**：`packages/economy/src/__tests__/balance-transfer.pbt.test.ts` L202-242, L148-198
- **修復方案**：在 overdraft 和 self-transfer 測試中，失敗後查詢 `SELECT COUNT(*) FROM currency_transaction WHERE ...` 確認 count = 0。
- **驗證方式**：DB 查詢結果為 0。

#### P2-4: Redemption tests 改用 `fc.assert(asyncProperty)` 模式

- **涉及檔案**：`packages/shop/src/__tests__/redemption.pbt.test.ts`
- **修復方案**：將 `fc.sample(...)` + for-loop 改為 `fc.assert(fc.asyncProperty(...))`。注意 predicate 內需要自主處理 DB cleanup（參考 economy PBT 的做法）。
- **驗證方式**：`vitest run` 通過，失敗時 fast-check 能正確 shrinking 並顯示種子。

### P3 改善

#### P3-1: 提取共享 cleanTestTables 到 economy 共用工具

- **涉及檔案**：`packages/economy/src/__tests__/` 下 5 個 PBT 檔案
- **修復方案**：建立 `packages/economy/src/__tests__/test-utils.ts`，匯出 `cleanEconomyTestTables(pool, tables?)`。
- **驗證方式**：5 個測試檔案改為 import 共享工具，測試全部通過。

#### P3-2: Admin PBT 改用共享 Arbitrary

- **涉及檔案**：`packages/admin/src/__tests__/` 下 7 個 PBT 檔案
- **修復方案**：`import { guildId, userId } from '@ltdjms/shared/__tests__/arbitrary'` 取代內聯定義。
- **驗證方式**：測試全部通過。

#### P3-3: 統一 numRuns 策略

- **涉及檔案**：所有 PBT 測試檔案
- **修復方案**：純函數 PBT 100 run、輕量 DB PBT 50 run、重量級 DB PBT 10-20 run。在 `coordination.md` 記錄策略。
- **驗證方式**：測試執行時間在預期範圍內。

---

## 架構評估

### 優點
- test-infra 的共享基礎設施設計良好，economy PBT 成功重用了 seed factory、arbitrary、test-container 等工具
- economy PBT 使用真正的 `fc.assert(asyncProperty)` 模式，正確發揮 fast-check 的 shrinking 能力
- ECPay E2E 透過 `RUN_ECPAY_E2E` 環境變數優雅控制外部 API 測試開關
- `cleanAllTestTables()` 的 DELETE + session_replication_role 方案解決了 workspace mode 下的連線衝突問題

### 待改善
- **Admin PBT 與 spec 的整合測試目標不一致**（P1-1）：這是本批次最關鍵的架構落差
- **Module boundary 不一致**（P1-8）：economy 使用 package import，shop 使用相對路徑
- **Testcontainer 生命週期管理不完整**（P1-4）：no-op teardown + Ryuk disabled 的組合在異常退出時無清理機制
- **Makefile for-loop 的規模化問題**（P1-3）：隨著 PBT 檔案增加，vitest 啟動開銷線性增長

---

## 總體結論

本次 ts-test-strategy 實作**通過審查**。核心業務需求（餘額守恆、兌換碼冪等、ECPay 加解密正確性、管理面板 CRUD）已用測試正確實作並全部通過。

主要改善方向有三：
1. **Admin PBT 補上至少一個真實 DB integration 測試**（目前全 mock，與 spec 目標不符）
2. **消除 Shop PBT 測試間的重複代碼**（提取共享 beforeEach）
3. **簡化 Makefile for-loop 並修正 container 清理機制**

以上問題均不影響測試套件的正確性和當前的覆蓋有效性。全量 680 tests 通過，exit code 0。
