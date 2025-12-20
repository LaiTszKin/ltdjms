# 產品模組設計與實作

本文件說明 LTDJMS Discord Bot 的產品模組，負責定義可兌換的產品項目，並支援自動獎勵發放。

## 1. 概述

產品模組允許管理員在每個 Discord 伺服器定義虛擬產品，這些產品可以透過兌換碼兌換，並自動發放貨幣或遊戲代幣作為獎勵。產品模組與兌換模組緊密整合，形成完整的禮品兌換系統。

主要功能：
- 產品定義與管理（名稱、描述、獎勵類型與數量）
- 伺服器範圍的產品隔離
- 自動獎勵發放（貨幣或代幣）
- 與兌換系統的整合

## 2. 領域模型

### 2.1 Product

產品實體定義可兌換的項目。

```java
// src/main/java/ltdjms/discord/product/domain/Product.java
public record Product(
    Long id,
    Long guildId,
    String name,
    String description,
    RewardType rewardType,  // CURRENCY 或 TOKENS
    Long rewardAmount,
    Instant createdAt,
    Instant updatedAt
) {
    // 商業規則驗證
    public Product {
        if (rewardAmount < 0) {
            throw new IllegalArgumentException("Reward amount must be non-negative");
        }
        if (rewardType == null && rewardAmount > 0) {
            throw new IllegalArgumentException("Reward type required when amount > 0");
        }
        // ... 其他驗證
    }
}
```

關鍵商業規則：
- 獎勵數量不得為負
- 若有獎勵，必須指定類型（貨幣或代幣）
- 產品名稱在伺服器內唯一

## 3. 服務層

### 3.1 ProductService

負責產品的業務邏輯與操作。

```java
// src/main/java/ltdjms/discord/product/services/ProductService.java
public class ProductService {
    private final ProductRepository productRepository;

    public Result<Product, DomainError> createProduct(Long guildId, String name, String description, RewardType rewardType, Long rewardAmount) {
        // 驗證產品名稱唯一性
        if (productRepository.existsByGuildAndName(guildId, name)) {
            return Result.err(DomainError.of(Category.INVALID_INPUT, "Product name already exists"));
        }
        
        var product = new Product(null, guildId, name, description, rewardType, rewardAmount, Instant.now(), Instant.now());
        return productRepository.save(product);
    }

    public Result<List<Product>, DomainError> getProductsByGuild(Long guildId) {
        return Result.ok(productRepository.findByGuildId(guildId));
    }

    // ... 其他方法
}
```

主要方法：
- `createProduct`: 建立新產品，驗證名稱唯一性
- `getProductsByGuild`: 取得伺服器所有產品
- `updateProduct`: 更新產品資訊
- `deleteProduct`: 刪除產品（需檢查是否有未使用的兌換碼）

## 4. 持久層

### 4.1 ProductRepository

產品資料存取介面。

```java
// src/main/java/ltdjms/discord/product/persistence/ProductRepository.java
public interface ProductRepository {
    Result<Product, DomainError> save(Product product);
    Result<Optional<Product>, DomainError> findById(Long id);
    Result<List<Product>, DomainError> findByGuildId(Long guildId);
    Result<Boolean, DomainError> existsByGuildAndName(Long guildId, String name);
    Result<Void, DomainError> deleteById(Long id);
}
```

### 4.2 JdbcProductRepository

JDBC 實作，使用 jOOQ 進行型別安全查詢。

```java
// src/main/java/ltdjms/discord/product/persistence/JdbcProductRepository.java
public class JdbcProductRepository implements ProductRepository {
    private final DSLContext dsl;

    @Override
    public Result<Product, DomainError> save(Product product) {
        try {
            var record = dsl.newRecord(PRODUCT)
                .setGuildId(product.guildId())
                .setName(product.name())
                .setDescription(product.description())
                .setRewardType(product.rewardType() != null ? product.rewardType().name() : null)
                .setRewardAmount(product.rewardAmount())
                .setCreatedAt(product.createdAt())
                .setUpdatedAt(product.updatedAt());

            if (product.id() != null) {
                record.setId(product.id());
                record.update();
            } else {
                record.insert();
                product = product.withId(record.getId());
            }

            return Result.ok(product);
        } catch (Exception e) {
            return Result.err(DomainError.of(Category.PERSISTENCE_FAILURE, e.getMessage()));
        }
    }

    // ... 其他實作
}
```

## 5. 整合方式

### 5.1 與管理面板整合

產品管理透過管理面板進行：

- **AdminProductPanelHandler**: 處理產品相關的面板互動
- **AdminPanelService**: 提供產品 CRUD 操作給面板使用

```java
// 在 AdminPanelService 中
public Result<List<Product>, DomainError> getProductsForGuild(Long guildId) {
    return productService.getProductsByGuild(guildId);
}

public Result<Product, DomainError> createProductViaPanel(Long guildId, String name, String description, RewardType rewardType, Long rewardAmount) {
    return productService.createProduct(guildId, name, description, rewardType, rewardAmount);
}
```

### 5.2 與兌換系統整合

產品與兌換碼緊密整合：

- 兌換時參考產品定義決定獎勵
- 產品刪除受限於是否存在未使用的兌換碼

```java
// RedemptionService 中
public Result<Void, DomainError> redeemCode(String code, Long userId) {
    // 查詢兌換碼
    var codeResult = redemptionCodeRepository.findByCode(code);
    if (codeResult.isErr()) return Result.err(codeResult.getErr());

    var redemptionCode = codeResult.getOk().orElse(null);
    if (redemptionCode == null) {
        return Result.err(DomainError.of(Category.INVALID_INPUT, "Code not found"));
    }

    // 驗證狀態
    if (redemptionCode.redeemedBy() != null) {
        return Result.err(DomainError.of(Category.INVALID_INPUT, "Code already redeemed"));
    }

    // 取得產品資訊
    var productResult = productRepository.findById(redemptionCode.productId());
    if (productResult.isErr()) return Result.err(productResult.getErr());

    var product = productResult.getOk().orElse(null);
    if (product == null) {
        return Result.err(DomainError.of(Category.INVALID_INPUT, "Product not found"));
    }

    // 發放獎勵
    var rewardResult = grantReward(userId, product);
    if (rewardResult.isErr()) return Result.err(rewardResult.getErr());

    // 標記兌換
    return redemptionCodeRepository.markAsRedeemed(redemptionCode.id(), userId);
}
```

## 6. 使用範例

### 6.1 建立產品

管理員透過管理面板建立新產品：

1. 點擊「產品管理」按鈕
2. 選擇「新增產品」
3. 填寫表單：名稱「VIP 會員」、描述「獲得額外福利」、獎勵類型「貨幣」、數量 1000
4. 送出後，系統驗證並儲存產品

### 6.2 產品列表查看

管理面板顯示伺服器所有產品：

- 產品名稱與描述
- 獎勵資訊
- 未使用兌換碼數量
- 建立時間

### 6.3 產品更新與刪除

- 更新：修改產品資訊，系統驗證變更
- 刪除：僅允許當沒有未使用兌換碼時刪除

## 7. 錯誤處理

產品模組使用 `Result<T, DomainError>` 處理錯誤：

- `INVALID_INPUT`: 產品名稱重複、參數無效
- `PERSISTENCE_FAILURE`: 資料庫操作失敗
- `UNEXPECTED_FAILURE`: 其他非預期錯誤

## 8. 測試策略

- **單元測試**: ProductService 的業務邏輯
- **整合測試**: Repository 與資料庫互動
- **契約測試**: 與兌換系統的整合行為

---

若需擴充產品功能（如多種獎勵類型或條件兌換），建議參考現有分層設計，並在領域模型中新增對應的商業規則。