# Checklist: shop-java-parity

- Date: 2026-05-24
- Feature: shop-java-parity

## Pre-implementation
- [ ] preparation P2 shop fixtures 完成
- [ ] external-deps-adoption 完成（JSON snapshot helper + @robojs/mock PoC）

## Parity verification
- [ ] R1.1-R1.3 customId 逐字對齊 Java
- [ ] R2.1-R2.3 browse embed + 兩列 pagination 對齊
- [ ] R3.1-R3.3 ShopService 分頁契約對齊
- [ ] R4.1-R4.5 browse 互動對齊
- [ ] R5.1-R5.6 purchase 流程對齊
- [ ] R6.1-R6.3 buy menu + search components 對齊

## Automated tests
- [ ] UT-301 ShopService pagination
- [ ] UT-302 ShopView embed + modal
- [ ] UT-303 ShopView pagination components
- [ ] UT-304 ShopView buy/search/confirm
- [ ] UT-305 getAllPurchasableProducts
- [ ] UT-306 ShopCommandHandler
- [ ] UT-307 ShopButtonHandler (browse/search)
- [ ] UT-308 ShopSelectMenuHandler (purchase)
- [ ] REG-301 既有 shop PBT 無 regression

## Manual smoke (Discord)
- [ ] `/shop` 分頁與 Java 視覺一致
- [ ] 搜尋 → 購買 → 貨幣 confirm 流程
- [ ] 法幣購買 DM + interaction summary

## Sign-off
- [ ] `make verify`
- [ ] architecture diff validate
