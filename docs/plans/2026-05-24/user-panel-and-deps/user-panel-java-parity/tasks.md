# Tasks: user-panel-java-parity

- Date: 2026-05-24
- Feature: user-panel-java-parity

## **Task 1: 建立 Java oracle fixtures**

Requirements: R1.4, R2.5, R4.5
Scope: `fixtures/`

- T1.1 [ ] **建立 fixtures/java-custom-ids.json** — 所有 Java 常數
- T1.2 [ ] **建立 fixtures/java-main-panel-oracle.json** — embed + buttons oracle
- T1.3 [ ] **建立 fixtures/java-history-oracle.json** — 三種 history + pagination
  - Verify: JSON schema 可被測試 load

## **Task 2: UserPanelConstants + routing**

Requirements: R1.1-R1.3
Scope: `src/constants/UserPanelConstants.ts`, handler routing

- T2.1 [ ] **建立 UserPanelConstants.ts** — mirror Java public static finals
- T2.2 [ ] **合併 TransactionHistoryHandler + RedemptionCodeHandler** → `UserPanelButtonHandler.ts`，routing prefix `user_panel_`
- T2.3 [ ] **更新 SlashCommandListener 註冊** — single handler prefix `user_panel`
  - Verify: UT-204 constants test 全綠

## **Task 3: UserPanelService 薄層**

Requirements: R5.x（間接）
Scope: `src/services/UserPanelService.ts`

- T3.1 [ ] **建立 UserPanelService** — delegate to MemberInfoFacade（mirror Java）
- T3.2 [ ] **更新 Command/Handler** — 改 inject UserPanelService 而非直接 facade
  - Verify: 既有 facade 測試仍通過

## **Task 4: Embed builder parity**

Requirements: R2.1-R2.4, R3.1-R3.3
Scope: `UserPanelEmbedBuilder.ts`

- T4.1 [ ] **重構 buildPanelEmbed** — EmbedView with fields, mention description, footer, color
- T4.2 [ ] **重構 buildPanelComponents** — 2 rows, dynamic currency label, SUCCESS redeem
- T4.3 [ ] **新增 UserPanelEmbedBuilder.test.ts** — structural parity vs oracle
  - Verify: UT-201, UT-202 全綠

## **Task 5: History view factory**

Requirements: R4.1-R4.5
Scope: `UserPanelHistoryViewFactory.ts`

- T5.1 [ ] **建立 UserPanelHistoryViewFactory.ts** — mirror Java buildHistoryEmbed + buildPaginationButtons
- T5.2 [ ] **實作 user_panel_back** — 重建主面板 embed + components
- T5.3 [ ] **移除 handler 內 inline embed 建構**
- T5.4 [ ] **新增 UserPanelHistoryViewFactory.test.ts**
  - Verify: UT-203 全綠

## **Task 6: Redemption + /redeem-code**

Requirements: R5.1-R5.4
Scope: modal handling, RedeemCodeCommandHandler

- T6.1 [ ] **Modal user_panel_modal_redeem** — field code 16-20
- T6.2 [ ] **成功/失敗訊息格式** — 對齊 Java
- T6.3 [ ] **修復 /redeem-code session gap** — 不要求 pre-existing session
  - Verify: REG-201 redemption message tests

## **Task 7: Update listener parity**

Requirements: R6.1-R6.4
Scope: UserPanelUpdateListener

- T7.1 [ ] **session register 時機** — 僅 initial command
- T7.2 [ ] **guild-wide currency config update**
- T7.3 [ ] **embed-only edit on push update**
- T7.4 [ ] **新增 UserPanelUpdateListener.test.ts**
  - Verify: UT-205 全綠

## **Task 8: 清理與回歸**

Requirements: 全部
Scope: user-panel package

- T8.1 [ ] **移除舊 customId 常數與 dead code**
- T8.2 [ ] **admin re-export shim 移除**（若 extraction 保留）
- T8.3 [ ] **make verify**
  - Verify: `make verify` exit code 0
