# Checklist: 管理面板設定交互改造（Modal → 嵌入式設定面板）

- Date: 2026-03-05
- Feature: 管理面板設定交互改造（Modal → 嵌入式設定面板）

## Clarification & Approval Gate
- [x] User clarification responses are recorded: `N/A`（本次需求已明確提供現況與目標互動流程，且定義純數值 modal 排除）。
- [x] Affected specs are reviewed/updated: `N/A`（尚無二次澄清，不需再調整文件）。
- [x] Explicit user approval on updated specs is obtained（date/conversation reference: 2026-03-05，使用者回覆 `accept`）

## Behavior-to-Test Checklist

- [x] CL-01 護航定價清單改為非表格化可掃讀格式，並維持長內容分段
  - Requirement mapping: `R1.1`, `R1.2`
  - Actual test case IDs: `UT-AP-01`, `UT-AP-02`
  - Test level: `Unit`
  - Test result: `PASS`
  - Notes (optional): `AdminPanelButtonHandlerTest` 驗證字串格式與每欄 <= 1024。

- [x] CL-02 護航定價改為面板式設定（下拉選擇 + 數值 modal + 確認提交）
  - Requirement mapping: `R2.1`, `R3.1`, `R3.2`
  - Actual test case IDs: `UT-AP-03`
  - Test level: `Unit`
  - Test result: `PASS`
  - Notes (optional): 驗證按鈕改為開啟嵌入面板而非直接 modal。

- [x] CL-03 商品接入設定改為面板式設定並即時預覽
  - Requirement mapping: `R2.2`, `R3.1`, `R3.2`
  - Actual test case IDs: `UT-PP-01`
  - Test level: `Unit`
  - Test result: `PASS`
  - Notes (optional): 驗證按鈕改為開啟嵌入面板而非直接 modal。

## E2E Decision Record
- [x] Do not build E2E; cover with integration tests instead（alternative case: `IT-AP-01`, `IT-PP-01`；reason: 目前專案未建立 Discord 元件互動 E2E 測試基礎，改以 handler mock/integration 覆蓋互動風險）。

## Execution Summary
- [x] Unit tests: `PASS`
- [ ] Property-based tests: `N/A`（UI 互動狀態機與字串顯示改造，無可泛化不變式需求）
- [ ] Integration tests: `NOT RUN`
- [ ] E2E tests: `NOT RUN`

## Completion Rule
- [x] Agent has updated checkboxes, test outcomes, and necessary notes based on real execution (including added/removed items).
