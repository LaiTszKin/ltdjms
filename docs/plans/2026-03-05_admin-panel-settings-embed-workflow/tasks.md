# Tasks: 管理面板設定交互改造（Modal → 嵌入式設定面板）

- Date: 2026-03-05
- Feature: 管理面板設定交互改造（Modal → 嵌入式設定面板）

## **Task 1: 護航定價顯示與操作入口改造**

對應 `R1.1`, `R1.2`, `R2.1`，核心目標是提升清單可讀性並把護航定價修改流程切為面板式操作。

- 1. [x] 改造護航定價清單渲染格式
  - 1.1 [x] 更新 `EscortOptionPricingService.OptionPriceView` 的顯示字串結構（非表格化）
  - 1.2 [x] 保留 `AdminPanelButtonHandler` 既有分段欄位機制，確保長內容不超過 embed 限制
- 1. [x] 護航定價操作改為設定面板流程
  - 1.1 [x] 新增護航定價面板 session 狀態（操作類型、option code、暫存 price）
  - 1.2 [x] 新增確認按鈕與驗證流程（確認前不得落庫）

## **Task 2: 管理面板多欄位設定互動流程（AdminPanel）**

對應 `R2.1`, `R3.1`, `R3.2`，核心目標是以 ephemeral 設定面板取代直接 modal，並支援即時預覽。

- 2. [x] 新增護航定價設定面板元件
  - 2.1 [x] 以 StringSelectMenu 提供操作模式與 option code 選擇
  - 2.2 [x] 透過按鈕開啟數值 modal（僅作為局部輸入），回填暫存狀態後重新渲染面板
- 2. [x] 確認提交流程
  - 2.1 [x] 新增「確認提交」按鈕與必要欄位驗證
  - 2.2 [x] 成功後刷新主面板/提示結果，失敗時回傳錯誤且保留可重試狀態

## **Task 3: 商品接入設定改為嵌入式設定面板（AdminProduct）與測試補強**

對應 `R2.2`, `R3.1`, `R3.2`，核心目標是把商品接入設定由一次性 modal 改成可預覽、可確認的面板流程。

- 3. [x] `AdminProductPanelHandler` 接入設定流程改造
  - 3.1 [x] 新增接入設定面板 session 狀態與動態 embed 預覽
  - 3.2 [x] 支援下拉選單（自動護航開單、護航 option code）與 modal 輔助輸入（backend URL）
- 3. [ ] 測試與驗證
  - 3.1 [x] 單元測試：護航定價顯示格式、面板狀態切換、確認提交驗證
  - 3.2 [ ] 整合/鏈路測試：handler 互動事件下設定更新與面板刷新（若無現成 E2E 基礎，改以 integration + mock hook 強化）
