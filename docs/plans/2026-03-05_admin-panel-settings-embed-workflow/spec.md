# Spec: 管理面板設定交互改造（Modal → 嵌入式設定面板）

- Date: 2026-03-05
- Feature: 管理面板設定交互改造（Modal → 嵌入式設定面板）
- Owner: Codex

## Goal
在 Discord 不支援 Markdown 表格渲染的限制下，提升護航定價可讀性，並把多欄位設定流程從「直接彈 modal」改為「先開啟僅用戶可見的設定嵌入面板，再確認提交」，降低輸入錯誤與理解成本。

## Scope
- In scope:
  - 護航定價列表顯示改造（避免表格感格式，提升可掃讀性）。
  - `AdminPanelButtonHandler` 的護航定價設定，改為嵌入式設定面板流程（下拉選擇 + 數值 modal + 確認提交）。
  - `AdminProductPanelHandler` 的接入設定（backend URL / auto escort / option code），改為嵌入式設定面板流程（下拉選擇 + 輔助 modal + 即時預覽 + 確認提交）。
  - 設定面板在用戶每次操作後即時更新預覽內容（ephemeral embed edit）。
- Out of scope:
  - 純數值輸入 modal（例如餘額、代幣、純數值遊戲參數）本次不改。
  - 商品建立/完整編輯流程全面重做（本次先覆蓋接入設定類型流程）。
  - 新增資料庫欄位或 schema 變更。

## Functional Behaviors (BDD)

### Requirement 1: 護航定價列表可讀性優化
**GIVEN** 管理員開啟護航定價設定
**AND** Discord 對 markdown 表格不支援
**WHEN** 系統渲染護航定價清單
**THEN** 清單應以非表格化、可掃讀的格式呈現每個選項資訊
**AND** 內容仍需保留代碼、品項資訊、實際價格與覆蓋狀態

**Requirements**:
- [ ] R1.1 每列護航定價必須在 Discord 內可直接辨識「代碼 + 名稱/類型 + 價格 + 狀態」。
- [ ] R1.2 embed 欄位長度仍需遵守 Discord 限制（單欄 <= 1024 字元）並可在資料量大時分段顯示。

### Requirement 2: 設定改為嵌入面板驅動
**GIVEN** 使用者需要修改多欄位設定
**AND** 該設定不屬於純數值單欄位輸入
**WHEN** 使用者點擊設定按鈕
**THEN** 系統送出新的 ephemeral 嵌入設定面板（非直接彈出最終 modal）
**AND** 面板中可透過下拉選單選擇可用 options，必要時可開啟 modal 補充輸入

**Requirements**:
- [ ] R2.1 護航定價設定按鈕流程改為「開面板 → 下拉選項 →（必要）數值 modal → 面板確認」。
- [ ] R2.2 商品接入設定按鈕流程改為「開面板 → 下拉/輸入設定 → 面板確認」。

### Requirement 3: 即時預覽與確認提交
**GIVEN** 使用者正在操作設定嵌入面板
**AND** 使用者逐步更新下拉選項或 modal 輸入
**WHEN** 任一設定值改變
**THEN** 嵌入面板需即時反映目前暫存值
**AND** 僅在使用者按下確認按鈕後才真正寫入服務層

**Requirements**:
- [ ] R3.1 面板需維持 session 狀態（暫存值）並能回寫成 embed 預覽內容。
- [ ] R3.2 按下確認前不得更新實際設定；按下確認後需回覆成功/失敗訊息。

## Error and Edge Cases
- [ ] 非管理員或非 guild 環境觸發互動時，需維持既有拒絕行為（ephemeral 錯誤）。
- [ ] 使用者未完成必要欄位（例如未選 option code）即確認時，需阻止提交並提示缺漏。
- [ ] modal 輸入格式錯誤（數值、URL、布林）時，需保留面板狀態並可重新輸入。

## Clarification Questions
None

## References
- Official docs:
  - https://jda.wiki/using-jda/interactions/
  - https://discord.com/developers/docs/interactions/message-components
- Related code files:
  - src/main/java/ltdjms/discord/panel/commands/AdminPanelButtonHandler.java
  - src/main/java/ltdjms/discord/panel/commands/AdminProductPanelHandler.java
  - src/main/java/ltdjms/discord/dispatch/services/EscortOptionPricingService.java
  - src/main/java/ltdjms/discord/product/domain/EscortOrderOptionCatalog.java
  - src/test/java/ltdjms/discord/panel/commands/AdminPanelButtonHandlerTest.java
  - src/test/java/ltdjms/discord/panel/commands/AdminProductPanelHandlerTest.java
