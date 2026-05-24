# Spec: user-panel-java-parity

- Date: 2026-05-24
- Feature: user-panel-java-parity
- Owner: laitszkin

## Goal

將 TypeScript `@ltdjms/user-panel` 的 Discord UI 與互動行為 1:1 對齊 Java bot 個人面板，包含 embed 結構、button customId/label/style/rows、modal、分頁邏輯、session 行為與即時更新。

## Scope

### In Scope
- **customId 對齊**：`user_history_*` / `user_redeem_*` → Java `user_panel_*` 常數
- **主面板 embed**：title、description（user mention + 的帳戶資訊）、inline fields（貨幣餘額、遊戲代幣餘額）、footer、color `#5865F2`
- **主面板 buttons**：兩列布局（3+1），動態貨幣按鈕 label `{currencyIcon} 查看貨幣流水`，redeem 為 SUCCESS style
- **History view factory**：集中 `UserPanelHistoryViewFactory`（對齊 Java），含 back/prev/next 分頁
- **Redemption modal**：`user_panel_modal_redeem`，field `code` 16–20 chars
- **UserPanelService 薄層**：handler 經 service 呼叫 MemberInfoFacade（對齊 Java 架構）
- **即時更新**：BalanceChanged/GameTokenChanged/CurrencyConfigChanged 行為對齊 Java（embed-only update；history 子視圖被覆蓋為已知 Java 行為）
- **`/redeem-code` session gap 修復**：開啟 modal 前建立或不要求 panel session（對齊 Java 行為）
- **Parity 測試**：以 Java 測試與 preparation fixtures 為 oracle 的 snapshot/structural tests

### Out of Scope
- 修改 Java bot
- 修改 economy/shop/games 業務規則
- ProductRedemptionUpdateListener 主動刷新（Java 亦為 stub）
- 管理面板

## Functional Behaviors (BDD)

### Requirement 1: customId 1:1 對齊
**GIVEN** Java `UserPanelButtonHandler` 定義所有 `user_panel_*` 常數
**WHEN** TypeScript handler 註冊 customId
**THEN** 所有 customId 與 Java 常數逐字一致
**AND** SlashCommandListener 以 `user_panel_` 前綴路由

**Requirements**:
- [x] R1.1 定義 `UserPanelConstants` 模組，mirror Java 常數
- [x] R1.2 合併 button handler 為單一 `UserPanelButtonHandler`（或等價 routing），prefix=`user_panel_`
- [x] R1.3 modal id=`user_panel_modal_redeem`
- [x] R1.4 parity 測試比對 `fixtures/java-custom-ids.json`

### Requirement 2: 主面板 embed 1:1
**GIVEN** Java `UserPanelEmbedBuilderTest` oracle
**WHEN** 執行 `/user-panel`
**THEN** embed JSON 結構與 Java 一致（title、description、fields、footer、color）

**Requirements**:
- [x] R2.1 description = `{userMention} 的帳戶資訊`
- [x] R2.2 field[0].name = `{currencyName}餘額`，field[1].name = `遊戲代幣餘額`
- [x] R2.3 footer = `點擊下方按鈕查看流水紀錄或兌換碼`（即時更新時使用較短 footer，對齊 Java）
- [x] R2.4 color = `0x5865F2`
- [x] R2.5 UT snapshot 測試對齊 `fixtures/java-main-panel-oracle.json`

### Requirement 3: 主面板 buttons 1:1
**GIVEN** Java `buildPanelComponents`
**WHEN** 渲染 action rows
**THEN** row1: 貨幣/代幣/商品 history（SECONDARY）；row2: 兌換碼（SUCCESS）

**Requirements**:
- [x] R3.1 貨幣 button label = `{currencyIcon} 查看貨幣流水`（動態）
- [x] R3.2 代幣 label = `📜 查看遊戲代幣流水`；商品 = `🛒 查看商品流水`；兌換 = `🎫 兌換碼`
- [x] R3.3 兩列 layout（3 buttons + 1 button）

### Requirement 4: 交易歷史分頁 1:1
**GIVEN** Java `UserPanelHistoryViewFactory`
**WHEN** 使用者點擊 history 或分頁
**THEN** embed 標題/內容/空狀態/分頁 indicator 與 Java 一致
**AND** pagination buttons 含 `🔙 返回主頁`（`user_panel_back`）+ 可選 prev/next

**Requirements**:
- [x] R4.1 建立 `UserPanelHistoryViewFactory.ts` mirror Java
- [x] R4.2 分頁 customId：`user_panel_currency_page_{n}`、`user_panel_token_page_{n}`、`user_panel_product_redemption_page_{n}`
- [x] R4.3 page indicator = `第 {current}/{total} 頁（共 {count} 筆）`
- [x] R4.4 `user_panel_back` 返回主面板 embed + components
- [x] R4.5 parity 測試對齊 `fixtures/java-history-oracle.json`

### Requirement 5: 兌換碼流程 1:1
**GIVEN** Java redemption flow
**WHEN** 使用者點擊兌換碼並提交 modal
**THEN** 成功/失敗 ephemeral 訊息格式對齊 Java

**Requirements**:
- [x] R5.1 button `user_panel_redeem` 開啟 modal
- [x] R5.2 modal field `code` SHORT, min 16 max 20, required
- [x] R5.3 成功 `✅ {formatSuccessMessage()}`；失敗 `❌ 兌換失敗：{error}`
- [x] R5.4 `/redeem-code` 不依賴既有 panel session（或自動建立 session）

### Requirement 6: Session 與即時更新 1:1
**GIVEN** Java `PanelSessionManager` 15min TTL
**WHEN** balance/token/config 變更
**THEN** 開啟中的主面板 embed 自動更新；session 過期則移除

**Requirements**:
- [x] R6.1 session 僅在初始 `/user-panel` 成功時 register（非 back/history 導航時）
- [x] R6.2 `UserPanelUpdateListener` 對 guild-wide currency config 更新所有 open panels
- [x] R6.3 更新僅 edit embed（不 refresh buttons），對齊 Java
- [x] R6.4 listener 單元測試覆蓋三種 event

## Error and Edge Cases
- [x] Session 過期時 button 點擊 — ephemeral 提示重新 `/user-panel`
- [x] 空交易歷史 — 各 type 空狀態文案對齊 Java
- [x] 無效兌換碼 — domain error mapping 對齊 Java 訊息
- [x] 分頁超出範圍 — clamp 至有效頁（對齊 Java TransactionPage）

## Clarification Questions
- **驗收粒度**：預設採 **structural parity test**（embed fields、customId、button styles/rows 逐欄位比對 Java oracle），不要求 Discord API 二進位級別完全一致，但 customId 需逐字一致。

## References
- Java: `UserPanelEmbedBuilder.java`, `UserPanelButtonHandler.java`, `UserPanelHistoryViewFactory.java`, `UserPanelUpdateListener.java`
- Java tests: `UserPanelEmbedBuilderTest.java`, `UserPanelHistoryViewFactoryTest.java`
- Fixtures: `../user-panel-java-parity/fixtures/`（preparation 建立）
