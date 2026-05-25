# Design: shop-member-discount-display

## 格式化 API（EscortPriceQuote）

```java
/** Embed: ~~NT$3,500~~ NT$3,150（9 折） */
public String formatFiatEmbedLine() { ... }

public String formatCurrencyEmbedLine() { ... }

/** Select menu: max ~100 chars, no markdown */
public String formatFiatSelectDescription() { ... }

public String formatCurrencySelectDescription() { ... }

/** Discount label from MembershipTierLabels.discountLabel(appliedTier) */
private String discountRateLabel() { ... }
```

既有 `formatFiatPriceLine()` / `formatCurrencyPriceLine()` 可 delegate 到 embed 版或標記 `@Deprecated` 後替換呼叫點。

## 列表 Embed 流程

```
ShopCommandHandler / ShopButtonHandler
  → products = shopService.getShopPage(...)
  → quotes = shopService.quoteEscortPrices(userId, products, guildId)
  → ShopView.buildShopEmbed(products, page, totalPages, guildId, quotes)
       for each product:
         quote = quotes.get(product.id())
         if quote.hasFiatDiscount() → formatFiatEmbedLine()
         else → product.formatFiatPriceTwd()
```

`quoteEscortPrices` 實作：stream products，skip non-escort，其餘呼叫 `membershipPricingService.quoteEscortPrice`。

## ShopView 簽名變更

```java
public static MessageEmbed buildShopEmbed(
    List<Product> products,
    int currentPage,
    int totalPages,
    long guildId,
    Map<Long, EscortPriceQuote> quotesByProductId)

public static List<ActionRow> buildBuyMenu(
    List<Product> allProducts,
    Map<Long, EscortPriceQuote> quotesByProductId)
```

向後相容：quotes map 為 empty 時等同 list price。

## Select Menu 精簡格式範例

| 情境 | Description |
|------|-------------|
| 僅法幣折扣 | `NT$3,150 (9折)` |
| 僅貨幣折扣 | `90 幣 (9折)` |
| 雙價 | `90幣/NT$3150 (9折)` — 超 100 字元則截斷法幣 |

## 檔案

| 檔案 | 變更 |
|------|------|
| `EscortPriceQuote.java` | embed + select formatters |
| `ShopService.java` | `quoteEscortPrices` |
| `ShopView.java` | embed/buy/search buy menu |
| `ShopCommandHandler.java` | pass userId + quotes |
| `ShopButtonHandler.java` | pagination, search, buy menu |
| `ShopSelectMenuHandler.java` | confirm embeds 用新 format |
| `docs/features/shop-and-payment.md` | 列表折扣展示 |

## 測試策略

| ID | 層級 | 目標 | Oracle |
|----|------|------|--------|
| UT-01 | Unit | `formatFiatEmbedLine` 有折扣 | 含 `~~` 與 `（9 折）` |
| UT-02 | Unit | 無折扣 embed | 無 `~~` |
| UT-03 | Unit | `formatFiatSelectDescription` | length ≤ 100 |
| UT-04 | Unit | `buildShopEmbed` with quotes | embed description 含劃線價 |
| UT-05 | Unit | `buildBuyMenu` compact | description 無 `~~` |
| IT-01 | Integration | ShopService.quoteEscortPrices | mock pricing 回傳 map |

跳過 E2E：Discord 渲染由 embed 字串 unit test 覆蓋。
