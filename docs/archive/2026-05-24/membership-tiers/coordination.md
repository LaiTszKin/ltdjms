# Coordination: membership-tiers

- Date: 2026-05-24
- Batch: membership-tiers

## Business Goals

在 Java bot 建立**全域會員等級制度**：以 Discord User ID 追蹤護航相關法幣原價消費（M），依**最早加入伺服器日期**為個人結算日，每結算週期重算等級；付款時對接入護航的商品套用折扣；結算日發放贈幣（可累積、無上限）；`/user-panel` 展示等級與進度。

- Batch members: [membership-core, membership-join-tracking, membership-spend-ledger, membership-settlement, membership-payment-discount, membership-benefits-ui]
- Shared outcome: 六等會員（青銅～黑金）完整運作；門檻與權益符合定稿財務模型；`make verify` 全綠；Architecture Atlas 新增 membership 功能模塊
- Out of scope: TypeScript bot parity、管理員手動調等 UI、非法幣消費計入 M、未接入護航的商店商品計入 M

## Design Principles

- Current baseline: Java bot 已有 shop 法幣/貨幣購買、escort handoff、GameTokenService；**無**會員等級、**無** GuildMemberJoin 監聽、**無** GUILD_MEMBERS intent
- Shared invariants:
  - 身份鍵為 **Discord User ID（全域）**，消費跨 guild 累加
  - 計入 M 的消費僅 **法幣 TWD**、且商品 **已接入護航**（`escortOptionCode` 非空或 `shouldAutoCreateEscortOrder`）
  - M 為 **catalog 原價**（`escort_option_catalog.price_twd`），非折後實付
  - 結算日 = **最早加入任一伺服器的日期**（日曆日錨點）；贈幣與評級均在結算日處理
  - 評級週期內指標為 **月平均 M**（該結算週期區間內 spend 總和 ÷ 1 個月等價，即區間總 M 作為該周期消費量；實作定義見 membership-settlement design）
  - 青銅：完成 ≥1 筆 M≥500 的 qualifying 法幣護航單後取得，**永久保底**
  - 白銀～黑金：每結算日依週期平均 M 重算，**可升可降**
  - 代幣贈送可累積、無上限
- Shared constraints:
  - Flyway migration：`membership-core` 從 **V029** 起分配版本號（P2 確認後可調整）
  - 折扣必須在 **建立訂單/扣款前** 寫入 `amount_twd`，確保 ECPay callback 金額一致
  - 新 `GameTokenTransaction.Source` 值 `MEMBERSHIP_GRANT` 由 `membership-benefits-ui` 新增，其他 spec 只讀
  - Dagger module：新增 `MembershipModule`，各 spec 依 merge 順序註冊
- Legacy direction: 無既有會員系統；shop 直接使用 `product.fiatPriceTwd` / `product.currencyPrice()`
- Compatibility window: 上線後舊訂單不回填 spend ledger（僅新付款計入）；可選 admin 腳本 out of scope
- Cleanup after cutover: 無

## 定稿等級參數（全 batch 共用）

| 等級 | 代碼 | 折扣 | d | 贈幣/月 | 門檻 M/月 | 淨利率策略 |
|------|------|------|---|---------|-----------|-----------|
| 青銅 | BRONZE | 95折 | 5% | 0 | ≥500（單次 qualifying 單） | 目標50%→實際42.1% |
| 白銀 | SILVER | 9折 | 10% | 100 | ≥14,000 | 80%×r∞≈31% |
| 黃金 | GOLD | 85折 | 15% | 200 | ≥33,000 | 80%×r∞≈28% |
| 鉑金 | PLATINUM | 8折 | 20% | 500 | ≥100,000 | 25% |
| 鑽石 | DIAMOND | 75折 | 25% | 1,000 | ≥120,000 | 15% |
| 黑金 | BLACK | 7折 | 30% | 2,000 | ≥250,000 | 10% |

## Spec Boundaries

### Ownership Map

#### Spec Set 1: membership-core
- Primary concern: 等級常數、domain、DB schema（membership 主表）、repository、純函式 tier 判定
- Allowed touch points: `src/main/java/ltdjms/discord/membership/**`、`db/migration/V029__*.sql`、`MembershipModule.java`
- Must not change: shop payment flow、panel UI、JDA listeners

#### Spec Set 2: membership-join-tracking
- Primary concern: GuildMemberJoin 監聽、最早加入日、結算日錨點初始化
- Allowed touch points: `GuildMemberJoinListener.java`、`DiscordCurrencyBot.java`（註冊 listener）、membership repository
- Must not change: spend ledger、settlement scheduler、discount logic

#### Spec Set 3: membership-spend-ledger
- Primary concern: 法幣付款成功後寫入 M；idempotent
- Allowed touch points: `FiatOrderPostPaymentWorker`、membership spend repository、fiat_order 擴充欄位（list_price_twd）
- Must not change: tier 重算邏輯、token grant、shop 折扣計算（可讀 tier）

#### Spec Set 4: membership-settlement
- Primary concern: 結算排程、週期平均 M、等級重算、青銅保底
- Allowed touch points: `MembershipSettlementScheduler`、settlement service、membership 主表 tier 欄位
- Must not change: payment discount、token grant 實作

#### Spec Set 5: membership-payment-discount
- Primary concern: 付款時護航商品折扣；shop 確認頁展示折後價
- Allowed touch points: `MembershipPricingService`、`FiatOrderService`、`CurrencyPurchaseService`、`ShopView`、`ShopSelectMenuHandler`
- Must not change: settlement、token grant

#### Spec Set 6: membership-benefits-ui
- Primary concern: 結算日贈幣、user-panel 等級展示
- Allowed touch points: `GameTokenTransaction.Source`、`GameTokenService` 呼叫處、UserPanel 相關類
- Must not change: core tier 常數（只讀）、payment 金額邏輯

### Collisions & Integration

- Shared files & edit rules:
  - `global_member_membership` 表 — **membership-core** 建立；join/settlement/benefits 各寫不同欄位（core 定義 schema，其他 spec 只 ALTER ADD 若需）
  - `MembershipModule.java` — core 建立；後續 spec 追加 `@Provides`
  - `DiscordCurrencyBot.buildEventListeners` — join-tracking 註冊 listener；settlement 註冊 scheduler start/stop
  - `FiatOrderPostPaymentWorker` — spend-ledger 加 hook；payment-discount 不改 worker 順序，只改 order 建立時金額
- Shared API freeze: `MembershipTierConfig` 常數在 core 落地後 **additive-only**（不可改門檻語意 without 全 batch 重算）
- Compatibility shim: 無 tier 記錄的用戶視為 `NONE`（無折扣、無贈幣）
- Merge order: `preparation` → `membership-core` → `membership-join-tracking` → `membership-spend-ledger` → (`membership-settlement` ∥ `membership-payment-discount`) → `membership-benefits-ui`
- Integration checkpoints:
  - core 完成：tier 純函式 unit test 綠
  - join-tracking 完成：join 寫入 earliest_join_at
  - spend-ledger 完成：法幣 escort 單寫入 M
  - settlement 完成：結算日重算 tier
  - payment-discount 完成：ECPay 金額 = 折後價；callback 成功
  - benefits-ui 完成：結算日發幣 + user-panel 顯示
  - batch 完成：`make verify` + `apltk architecture validate`
- Re-coordination trigger: 門檻/折扣/贈幣數值變更；M 計入範圍擴大到貨幣購買；結算週期改為日曆月
