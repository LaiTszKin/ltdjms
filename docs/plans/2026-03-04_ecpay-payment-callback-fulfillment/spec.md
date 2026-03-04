# Spec: 綠界付款回推履約與通知時機調整

- Date: 2026-03-04
- Feature: 綠界付款回推履約與通知時機調整
- Owner: Codex

## Goal
讓法幣商品從「取號即觸發後續」改為「付款完成才履約與通知」，避免未付款訂單被提前派單或發貨。

## Scope
- In scope:
  - 新增法幣訂單持久化（可追蹤待付款與付款完成狀態）
  - 接收並解析綠界付款結果推送，更新訂單狀態
  - 僅在付款完成（或等價已付款狀態）時觸發商品履約
  - 護航型商品管理員私訊改為付款完成後發送
  - 下單產生的超商代碼持續透過私訊發送給買家
- Out of scope:
  - 退款/撤銷/退貨流程
  - 完整金流對帳後台與人工補單工具
  - 非綠界法幣通道整合

## Functional Behaviors (BDD)

### Requirement 1: 法幣下單建立待付款排單
**GIVEN** 商品為限定法幣支付商品
**AND** 綠界取號成功回傳訂單編號與超商代碼
**WHEN** 使用者在商店執行法幣下單
**THEN** 系統建立一筆待付款法幣訂單並持久化
**AND** 系統僅私訊買家超商代碼與訂單資訊，不提前觸發履約或護航管理員通知

**Requirements**:
- [ ] R1.1 建立法幣訂單時需落庫（包含 guildId、userId、productId、orderNumber、paymentNo、金額、狀態）。
- [ ] R1.2 建單成功後僅回覆/私訊買家，不得在此階段呼叫履約 API。

### Requirement 2: 綠界付款狀態推送監控與狀態流轉
**GIVEN** 綠界對 ReturnURL 發送付款結果推送
**AND** 推送內容可解析出 MerchantTradeNo 與付款狀態欄位
**WHEN** 系統收到推送
**THEN** 系統更新對應法幣訂單狀態（待付款/已付款）
**AND** 若狀態為已付款或等價已付款狀態，觸發一次且僅一次商品履約

**Requirements**:
- [ ] R2.1 回推處理需支援重複通知冪等（同一訂單不可重複履約）。
- [ ] R2.2 需可識別「已付款或等價狀態」並在該狀態觸發履約。

### Requirement 3: 護航型商品管理員通知改為付款後
**GIVEN** 商品配置了護航後端整合（auto escort option）
**AND** 對應法幣訂單收到已付款（或等價）狀態
**WHEN** 系統完成付款後履約觸發
**THEN** 系統私訊管理員「有新訂單可派單」
**AND** 不得在「超商代碼剛產生」時提前推送給管理員

**Requirements**:
- [ ] R3.1 管理員通知觸發點需從「法幣建單」改為「付款完成」。
- [ ] R3.2 管理員通知訊息需包含可追蹤資訊（至少買家、商品、訂單編號）。

## Error and Edge Cases
- [ ] 回推找不到對應訂單（未知 MerchantTradeNo）時需記錄警告並安全回應，不中斷服務。
- [ ] 回推重複到達或狀態倒序（先未付款後已付款）時，狀態更新與履約需維持冪等。
- [ ] 回推解密/解析失敗或簽章資料異常時需拒絕觸發履約並記錄可追蹤錯誤。

## Clarification Questions
None

## References
- Official docs:
  - https://developers.ecpay.com.tw/?p=28005
  - https://developers.ecpay.com.tw/?p=16538
- Related code files:
  - src/main/java/ltdjms/discord/shop/services/FiatOrderService.java
  - src/main/java/ltdjms/discord/shop/services/EcpayCvsPaymentService.java
  - src/main/java/ltdjms/discord/shop/services/ProductFulfillmentApiService.java
  - src/main/java/ltdjms/discord/shop/commands/ShopSelectMenuHandler.java
  - src/main/java/ltdjms/discord/currency/bot/DiscordCurrencyBot.java
  - src/main/resources/db/migration/
