# Tasks: Issue 70 履約 URL DNS Rebinding 強化

- Date: 2026-04-09
- Feature: Issue 70 履約 URL DNS Rebinding 強化

## **Task 1: 固化 target snapshot 不變式**

對應 `R1.1-R1.2`，核心目標是讓 validation 與 transport 間的資料邊界明確，避免後續重構把 hostname 重新變成連線真相。

- 1. [x] 盤點 `ProductFulfillmentApiService` 現有 `ResolvedTarget` 與 `PinnedHttpsFulfillmentTransport` 的責任邊界
  - 1.1 [x] 將「驗證後 target snapshot」語意寫成清楚命名或 helper，不讓 transport 自行推導 host / port / path
  - 1.2 [x] 確認 transport 僅以 `resolvedAddress` 建立 socket，原始 host 僅用於 Host/SNI

## **Task 2: 收斂拒絕規則與失敗處理**

對應 `R2.1-R2.3`，核心目標是把 public-only 規則、非 2xx / network failure 的 fail-closed 行為寫成明確且可回歸驗證的邏輯。

- 2. [x] 收斂 URL 驗證與 transport 失敗映射
  - 2.1 [x] 明確保留 `https://` only、non-public address block、unknown host block 規則
  - 2.2 [x] 明確保留「不自動 follow redirect」的 transport 行為與註解／測試說明

## **Task 3: 補齊安全回歸測試**

對應 `R1.x-R2.x`，核心目標是用現有 mock transport / fake resolver 測試證明 DNS rebinding 類型風險不會回歸。

- 3. [x] 擴充 `ProductFulfillmentApiServiceTest`
  - 3.1 [x] 新增已驗證 IP 必須被傳入 transport、不得二次解析的 regression case
  - 3.2 [x] 新增 non-public / unknown host / non-2xx / transport failure 的 adversarial cases

## Notes
- 本 spec 偏向「驗證與收斂既有防線」，不是重新設計 fulfillment pipeline。
- 若盤點後確認 issue #70 已被現有實作部分修正，仍需以 regression coverage 固化該修正，而不是刪除 spec。
