# Design: membership-spend-ledger

## Schema (`V030`)

```sql
CREATE TABLE membership_spend_entry (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  discord_user_id     BIGINT NOT NULL,
  guild_id            BIGINT NOT NULL,
  list_price_twd      BIGINT NOT NULL,
  escort_option_code  VARCHAR(64),
  source_type         VARCHAR(32) NOT NULL DEFAULT 'FIAT_ORDER',
  source_reference    VARCHAR(128) NOT NULL,
  paid_at             TIMESTAMPTZ NOT NULL,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (source_type, source_reference)
);
CREATE INDEX idx_mse_user_paid ON membership_spend_entry (discord_user_id, paid_at);
```

```sql
ALTER TABLE fiat_order ADD COLUMN list_price_twd BIGINT;
ALTER TABLE fiat_order ADD COLUMN charged_amount_twd BIGINT; -- 折後實付，payment-discount spec 寫入
```

## M 解析

```java
long resolveListPriceM(Product p, long guildId) {
  if (p.escortOptionCode() != null) {
    return catalogRepository.findByCode(p.escortOptionCode())
        .map(EscortOptionCatalog::priceTwd)
        .orElse(p.fiatPriceTwd());
  }
  return p.fiatPriceTwd();
}
```

## Hook 位置

`FiatOrderPostPaymentWorker.processSingleOrder()` — **markPaid 之後、fulfillment 成功路徑**，呼叫：

```java
membershipSpendService.recordFiatEscortPayment(order, fulfillmentProduct);
```

## 青銅 flag

在同一 transaction 或緊接 transaction 更新 `global_member_membership.has_qualifying_bronze_order` if M >= 500。

## 錯誤策略

- spend 寫入失敗：LOG.error + metric；不 throw 以免阻斷商品 fulfillment
- 後續可加 reconciliation job（out of scope）
