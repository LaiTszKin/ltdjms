# Contract: shop-member-discount-display

## `EscortPriceQuote`

**New methods:**

```java
String formatFiatEmbedLine();
String formatCurrencyEmbedLine();
String formatFiatSelectDescription();      // ≤ 100 chars
String formatCurrencySelectDescription();  // ≤ 100 chars
```

**Embed format (when has discount):**
- Fiat: `~~NT$%d~~ NT$%d（%s）` where `%s` = `MembershipTierLabels.discountLabel(appliedTier)`
- Currency: `~~%,d 貨幣~~ %,d 貨幣（%s）`

**Select format (when has discount):**
- Plain text, no markdown; must fit Discord 100-char option description limit

## `ShopService.quoteEscortPrices(userId, products, guildId)`

**Returns:** `Map<Long, EscortPriceQuote>` keyed by `product.id()`

**Behavior:**
- Only escort-linked products appear in map
- Non-escort omitted (caller treats missing as list price)

## `ShopView.buildShopEmbed(..., Map<Long, EscortPriceQuote> quotes)`

**Behavior:**
- For each product, if quote present and has discount → use embed formatters
- Else existing list price format

## Dependencies

- `MembershipPricingService` (existing, unchanged algorithm)
- `MembershipTierLabels.discountLabel`
- `EscortProductRules.isEscortLinked`

## External

- Discord embed markdown `~~strikethrough~~` in description/field values
- Discord String Select option description: max 100 characters, no markdown rendering
