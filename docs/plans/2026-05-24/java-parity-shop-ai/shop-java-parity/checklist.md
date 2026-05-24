# Checklist: shop-java-parity

- Date: 2026-05-24
- Feature: shop-java-parity

## Pre-implementation
- [x] preparation P2 shop fixtures 完成
- [x] external-deps-adoption 完成（JSON snapshot helper + @robojs/mock PoC）

## Parity verification
- [x] R1.1-R1.3 customId 逐字對齊 Java
- [x] R2.1-R2.3 browse embed + 兩列 pagination 對齊
- [x] R3.1-R3.3 ShopService 分頁契約對齊
- [x] R4.1-R4.5 browse 互動對齊
- [x] R5.1-R5.6 purchase 流程對齊
- [x] R6.1-R6.3 buy menu + search components 對齊

## Automated tests
- [x] UT-301 ShopService pagination
- [x] UT-302 ShopView embed + modal
- [x] UT-303 ShopView pagination components
- [x] UT-304 ShopView buy/search/confirm
- [x] UT-305 getAllPurchasableProducts
- [x] UT-306 ShopCommandHandler
- [x] UT-307 ShopButtonHandler (browse/search)
- [x] UT-308 ShopSelectMenuHandler (purchase)
- [x] REG-301 既有 shop PBT 無 regression

## Manual smoke (Discord)
- [x] `/shop` 分頁與 Java 視覺一致
- [x] 搜尋 → 購買 → 貨幣 confirm 流程
- [x] 法幣購買 DM + interaction summary

## Sign-off
- [x] `make verify`
- [x] architecture diff validate
