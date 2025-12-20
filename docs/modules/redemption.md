# 兌換模組設計與實作

本文件說明 LTDJMS Discord Bot 的兌換模組，負責兌換碼的生成、驗證與兌換流程，並與產品模組整合以實現自動獎勵發放。

## 1. 概述

兌換模組提供完整的兌換碼管理系統，允許管理員為產品生成唯一兌換碼，使用者輸入代碼即可兌換並獲得對應獎勵。系統支援到期時間設定、一碼一兌，並記錄兌換歷史。

主要功能：
- 兌換碼生成與管理
- 代碼驗證與兌換
- 到期時間支援
- 自動獎勵發放
- 兌換歷史追蹤

## 2. 領域模型

### 2.1 RedemptionCode

兌換碼實體，代表可兌換的代碼。

```java
// src/main/java/ltdjms/discord/redemption/domain/RedemptionCode.java
public record RedemptionCode(
    Long id,
    Long guildId,
    Long productId,
    String code,
    Long redeemedBy,        // 使用者 ID，若已兌換
    Instant redeemedAt,     // 兌換時間，若已兌換
    Instant expiresAt,      // 到期時間，可選
    Instant createdAt
) {
    // 商業規則驗證
    public RedemptionCode {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("Code cannot be empty");
        }
        if ((redeemedBy == null) != (redeemedAt == null)) {
            throw new IllegalArgumentException("Redeemed by and redeemed at must be consistent");
        }
        // ... 其他驗證
    }

    public boolean isRedeemed() {
        return redeemedBy != null;
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
```

關鍵商業規則：
- 代碼唯一性
- 兌換狀態一致性（若已兌換，必須有兌換時間）
- 到期檢查

### 2.2 RedemptionCodeGenerator

負責生成唯一兌換碼。

```java
// src/main/java/ltdjms/discord/redemption/services/RedemptionCodeGenerator.java
public class RedemptionCodeGenerator {
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 8;

    public String generateUniqueCode() {
        // 生成隨機代碼並確保唯一性
        // 實作會檢查資料庫是否已存在
    }
}
```

## 3. 服務層

### 3.1 RedemptionService

負責兌換相關的業務邏輯。

```java
// src/main/java/ltdjms/discord/redemption/services/RedemptionService.java
public class RedemptionService {
    private final RedemptionCodeRepository redemptionCodeRepository;
    private final ProductRepository productRepository;
    private final BalanceService balanceService;
    private final GameTokenService gameTokenService;
    private final RedemptionCodeGenerator codeGenerator;

    public Result<List<RedemptionCode>, DomainError> generateCodes(Long productId, int count, Instant expiresAt) {
        // 驗證產品存在
        var productResult = productRepository.findById(productId);
        if (productResult.isErr()) return Result.err(productResult.getErr());

        var product = productResult.getOk().orElse(null);
        if (product == null) {
            return Result.err(DomainError.of(Category.INVALID_INPUT, "Product not found"));
        }

        // 生成多個唯一代碼
        List<RedemptionCode> codes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String code = codeGenerator.generateUniqueCode(product.guildId());
            var redemptionCode = new RedemptionCode(
                null, product.guildId(), productId, code, null, null, expiresAt, Instant.now()
            );
            codes.add(redemptionCode);
        }

        // 批次儲存
        return redemptionCodeRepository.saveAll(codes);
    }

    public Result<Void, DomainError> redeemCode(String code, Long userId, Long guildId) {
        // 查詢代碼
        var codeResult = redemptionCodeRepository.findByCodeAndGuild(code, guildId);
        if (codeResult.isErr()) return Result.err(codeResult.getErr());

        var redemptionCode = codeResult.getOk().orElse(null);
        if (redemptionCode == null) {
            return Result.err(DomainError.of(Category.INVALID_INPUT, "Code not found"));
        }

        // 驗證狀態
        if (redemptionCode.isRedeemed()) {
            return Result.err(DomainError.of(Category.INVALID_INPUT, "Code already redeemed"));
        }

        if (redemptionCode.isExpired()) {
            return Result.err(DomainError.of(Category.INVALID_INPUT, "Code expired"));
        }

        // 取得產品並發放獎勵
        var productResult = productRepository.findById(redemptionCode.productId());
        if (productResult.isErr()) return Result.err(productResult.getErr());

        var product = productResult.getOk().orElse(null);
        if (product == null) {
            return Result.err(DomainError.of(Category.INVALID_INPUT, "Product not found"));
        }

        var rewardResult = grantReward(userId, product);
        if (rewardResult.isErr()) return Result.err(rewardResult.getErr());

        // 標記已兌換
        return redemptionCodeRepository.markAsRedeemed(redemptionCode.id(), userId);
    }

    private Result<Void, DomainError> grantReward(Long userId, Product product) {
        if (product.rewardType() == RewardType.CURRENCY) {
            return balanceService.addBalance(product.guildId(), userId, product.rewardAmount());
        } else if (product.rewardType() == RewardType.TOKENS) {
            return gameTokenService.addTokens(product.guildId(), userId, product.rewardAmount());
        }
        return Result.ok(null);
    }
}
```

主要方法：
- `generateCodes`: 為產品生成多個兌換碼
- `redeemCode`: 驗證並兌換代碼
- `getCodesByProduct`: 取得產品的所有代碼
- `getUnusedCodesByProduct`: 取得未使用的代碼

## 4. 持久層

### 4.1 RedemptionCodeRepository

兌換碼資料存取介面。

```java
// src/main/java/ltdjms/discord/redemption/persistence/RedemptionCodeRepository.java
public interface RedemptionCodeRepository {
    Result<RedemptionCode, DomainError> save(RedemptionCode code);
    Result<List<RedemptionCode>, DomainError> saveAll(List<RedemptionCode> codes);
    Result<Optional<RedemptionCode>, DomainError> findById(Long id);
    Result<Optional<RedemptionCode>, DomainError> findByCode(String code);
    Result<Optional<RedemptionCode>, DomainError> findByCodeAndGuild(String code, Long guildId);
    Result<List<RedemptionCode>, DomainError> findByProductId(Long productId);
    Result<List<RedemptionCode>, DomainError> findUnusedByProductId(Long productId);
    Result<Void, DomainError> markAsRedeemed(Long id, Long userId);
}
```

### 4.2 JdbcRedemptionCodeRepository

JDBC 實作。

```java
// src/main/java/ltdjms/discord/redemption/persistence/JdbcRedemptionCodeRepository.java
public class JdbcRedemptionCodeRepository implements RedemptionCodeRepository {
    private final DSLContext dsl;

    @Override
    public Result<Void, DomainError> markAsRedeemed(Long id, Long userId) {
        try {
            dsl.update(REDEMPTION_CODE)
                .set(REDEMPTION_CODE.REDEEMED_BY, userId)
                .set(REDEMPTION_CODE.REDEEMED_AT, Instant.now())
                .where(REDEMPTION_CODE.ID.eq(id))
                .execute();
            return Result.ok(null);
        } catch (Exception e) {
            return Result.err(DomainError.of(Category.PERSISTENCE_FAILURE, e.getMessage()));
        }
    }

    // ... 其他實作
}
```

## 5. 整合方式

### 5.1 與管理面板整合

兌換碼管理透過管理面板進行：

- 查看產品的代碼統計
- 生成新代碼
- 查看兌換歷史

```java
// AdminPanelService 中
public Result<List<RedemptionCodeSummary>, DomainError> getRedemptionCodeSummary(Long productId) {
    var totalResult = redemptionCodeRepository.countByProductId(productId);
    var usedResult = redemptionCodeRepository.countUsedByProductId(productId);
    // ... 計算摘要
}
```

### 5.2 與產品模組整合

兌換系統依賴產品定義：

- 代碼生成時參考產品
- 兌換時取得獎勵資訊
- 產品刪除檢查是否有未使用代碼

## 6. 使用範例

### 6.1 生成兌換碼

管理員為產品生成代碼：

1. 在產品管理中選擇產品
2. 點擊「生成代碼」
3. 指定數量（例如 100）和到期時間
4. 系統生成唯一代碼並儲存

### 6.2 兌換代碼

使用者兌換流程：

1. 管理員提供代碼給使用者
2. 使用者輸入代碼（可能透過專用指令或DM）
3. 系統驗證代碼有效性
4. 發放對應獎勵並標記已兌換

### 6.3 查看兌換統計

管理員查看產品兌換狀況：

- 總代碼數
- 已兌換數
- 剩餘可用數
- 最近兌換記錄

## 7. 錯誤處理

兌換模組的錯誤類型：

- `INVALID_INPUT`: 代碼不存在、重複兌換、已到期
- `INSUFFICIENT_BALANCE`: 理論上不會發生，因為是增加餘額
- `PERSISTENCE_FAILURE`: 資料庫操作失敗

## 8. 安全性考量

- 代碼唯一性確保無法重複使用
- 到期時間防止長期有效
- 兌換記錄追蹤使用情況
- 伺服器隔離防止跨伺服器兌換

## 9. 測試策略

- **單元測試**: 代碼生成邏輯、驗證規則
- **整合測試**: 完整兌換流程、資料庫互動
- **效能測試**: 大量代碼生成與查詢

---

兌換模組為產品系統提供完整的兌換機制，確保安全、可靠的獎勵發放。