# PRD：綠界法幣付款與商品新台幣價值擴充

- 日期：2026-02-24
- 功能名稱：綠界法幣付款 + 商品新台幣實際價值
- 需求摘要：使用者在商品頁面對「限定法幣支付」商品下單時，系統透過綠界產生超商代碼，並將「訂單編號 + 超商代碼」私訊給使用者；管理員可於管理面板設定商品實際價值（新台幣）。

## Reference

- 參考程式碼：
  - `src/main/java/ltdjms/discord/product/domain/Product.java`
  - `src/main/java/ltdjms/discord/product/services/ProductService.java`
  - `src/main/java/ltdjms/discord/product/persistence/JdbcProductRepository.java`
  - `src/main/java/ltdjms/discord/panel/commands/AdminProductPanelHandler.java`
  - `src/main/java/ltdjms/discord/shop/services/ShopView.java`
  - `src/main/java/ltdjms/discord/shop/commands/ShopButtonHandler.java`
  - `src/main/java/ltdjms/discord/shop/commands/ShopSelectMenuHandler.java`
  - `src/main/java/ltdjms/discord/shared/EnvironmentConfig.java`
  - `src/main/resources/db/migration/`
- 綠界官方文件（主來源）：
  - 測試環境參數（MerchantID / HashKey / HashIV）
    - https://developers.ecpay.com.tw/?p=2861
  - 幕後產生超商代碼 API（GenPaymentCode）
    - https://developers.ecpay.com.tw/?p=27995
  - 幕後 API 請求加解密（AES/CBC/PKCS7 + URL Encode）
    - https://developers.ecpay.com.tw/?p=32135
- 需要新增/修改檔案（預計）：
  - `src/main/resources/db/migration/V017__add_fiat_price_twd_to_product.sql`（新增）
  - `src/main/java/ltdjms/discord/product/domain/Product.java`
  - `src/main/java/ltdjms/discord/product/services/ProductService.java`
  - `src/main/java/ltdjms/discord/product/persistence/JdbcProductRepository.java`
  - `src/main/java/ltdjms/discord/panel/commands/AdminProductPanelHandler.java`
  - `src/main/java/ltdjms/discord/shop/services/ShopView.java`
  - `src/main/java/ltdjms/discord/shop/commands/ShopButtonHandler.java`
  - `src/main/java/ltdjms/discord/shop/commands/ShopSelectMenuHandler.java`
  - `src/main/java/ltdjms/discord/shop/services/`（新增綠界法幣下單服務）
  - `src/main/java/ltdjms/discord/shared/EnvironmentConfig.java`
  - `src/main/java/ltdjms/discord/shared/di/CommandHandlerModule.java`
  - `src/test/java/ltdjms/discord/product/*`
  - `src/test/java/ltdjms/discord/shop/*`

## 核心需求

- [ ] 商品需新增「實際價值（新台幣）」欄位，並支援建立、編輯、查詢顯示。
- [ ] 管理員可在管理面板（商品建立/編輯 modal）設定「實際價值（新台幣）」。
- [ ] 系統需能識別「限定法幣支付商品」：本次定義為「有新台幣實際價值、且不可用遊戲貨幣直接購買」。
- [ ] 使用者在商品頁面對限定法幣商品下單時，需呼叫綠界 API 取得超商繳費代碼。
- [ ] 系統需產生訂單編號，並將「訂單編號 + 超商代碼 + 到期資訊（如有）」私訊給使用者。
- [ ] 若私訊失敗，需回覆使用者可理解錯誤訊息，不可靜默失敗。
- [ ] 金流參數需以環境變數配置（MerchantID、HashKey、HashIV、ReturnURL、是否 Stage）。

## 業務邏輯流程

1. 管理員設定商品新台幣實際價值
   - 入口：`/admin-panel` → 商品管理 → 建立/編輯商品
   - 輸入：`fiat_price_twd`（Long，可為空）
   - 輸出：商品資料成功儲存
   - 驗證：若有值，必須為正整數

2. 使用者進入商店頁
   - 入口：`/shop`
   - 輸出：顯示商品列表；若存在限定法幣商品，顯示法幣下單操作入口

3. 使用者對限定法幣商品下單
   - 入口：商店互動（按鈕 + 選單）
   - 驗證：
     - 商品存在且屬於該 guild
     - 商品屬於限定法幣商品
     - 綠界必要設定完整
   - 呼叫：綠界 `GenPaymentCode`（`ChoosePayment=CVS`）
   - 輸出：取得 `MerchantTradeNo`（作為訂單編號）與 `PaymentNo`（超商代碼）

4. 私訊通知使用者
   - 內容：商品名稱、訂單編號、超商代碼、到期時間（若 API 回傳）
   - 失敗處理：若無法開啟或送出私訊，回覆提示使用者開啟私訊後重試

## 需要澄清的問題

- ReturnURL 第一版是否已有可公開接收的 callback endpoint？
  - 本次先以環境變數要求提供；若未提供則下單直接失敗並提示設定未完成。
- 是否需要在本次建立「付款訂單持久化資料表」？
  - 本次先不新增完整訂單生命週期表，先完成「取號 + 私訊通知」最小閉環。
- 商品若同時設定「遊戲貨幣價格」與「新台幣價值」是否允許法幣下單？
  - 本次預設：僅「限定法幣商品」可走法幣下單（即不可用遊戲貨幣購買）。

## 測試規劃

### 單元測試

| ID | 情境 | 期望結果 |
| --- | --- | --- |
| UT-01 | 商品建立時設定 `fiat_price_twd` 成功 | 成功建立並保留欄位值 |
| UT-02 | 商品建立/更新時 `fiat_price_twd <= 0` | 回傳 INVALID_INPUT |
| UT-03 | 法幣下單目標商品不存在或 guild 不符 | 回傳 INVALID_INPUT |
| UT-04 | 法幣下單目標商品非限定法幣商品 | 回傳 INVALID_INPUT |
| UT-05 | 綠界設定缺漏 | 回傳 INVALID_INPUT 並包含可理解訊息 |
| UT-06 | 綠界回應成功 | 可取得訂單編號、超商代碼、到期資訊 |
| UT-07 | 綠界回應失敗或格式錯誤 | 回傳 UNEXPECTED_FAILURE |
| UT-08 | Shop 互動觸發法幣下單後 DM 成功 | 回覆已私訊成功訊息 |
| UT-09 | Shop 互動觸發法幣下單後 DM 失敗 | 回覆 DM 失敗提示 |

### 整合測試（必要時）

| ID | 範圍 | 情境 | 目的 |
| --- | --- | --- | --- |
| IT-01 | Flyway migration | 套用 V017 後 `product` 有 `fiat_price_twd` | 驗證 migration 正確 |
| IT-02 | JDBC Product Repository | CRUD 後可正確讀寫 `fiat_price_twd` | 驗證映射正確 |
