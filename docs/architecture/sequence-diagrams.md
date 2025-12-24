# 時序圖說明

本文件提供 LTDJMS Discord Bot 核心業務流程的時序圖（Sequence Diagrams），協助開發者理解系統內部元件之間的互動方式。

## 1. 產品刪除流程

當管理員刪除產品時，系統會自動失效所有關聯的兌換碼：

```mermaid
sequenceDiagram
    actor Admin as 管理員
    participant Handler as AdminProductPanelHandler
    participant Service as ProductService
    participant RCodeRepo as RedemptionCodeRepository
    participant PRepo as ProductRepository
    participant EventBus as DomainEventPublisher
    participant DB as 資料庫

    Admin->>Handler: 點擊刪除產品按鈕
    Handler->>Service: deleteProduct(productId)

    Service->>PRepo: findById(productId)
    PRepo-->>Service: Optional<Product>

    alt 產品不存在
        Service-->>Handler: Result.err(找不到商品)
        Handler-->>Admin: 顯示錯誤訊息
    else 產品存在
        Service->>RCodeRepo: invalidateByProductId(productId)
        RCodeRepo->>DB: UPDATE redemption_code<br/>SET invalidated_at = NOW()<br/>WHERE product_id = ?
        DB-->>RCodeRepo: 影響行數
        RCodeRepo-->>Service: invalidatedCount

        Service->>PRepo: deleteById(productId)
        PRepo->>DB: DELETE FROM product<br/>WHERE id = ?
        Note over DB: 外鍵約束 ON DELETE SET NULL<br/>自動將 product_id 設為 NULL
        DB-->>PRepo: true
        PRepo-->>Service: deleted

        Service->>EventBus: publish(ProductChangedEvent)
        EventBus-->>Service: (事件發布完成)

        Service-->>Handler: Result.okVoid()
        Handler-->>Admin: 顯示刪除成功訊息
    end
```

**關鍵點**：
1. 先失效兌換碼，再刪除產品（確保資料一致性）
2. 外鍵約束 `ON DELETE SET NULL` 會自動將 `product_id` 設為 NULL
3. 發布 `ProductChangedEvent` 通知其他模組（如管理員面板）更新狀態

---

## 2. 兌換碼生成流程

當管理員為產品生成兌換碼時：

```mermaid
sequenceDiagram
    actor Admin as 管理員
    participant Handler as AdminProductPanelHandler
    participant Service as RedemptionService
    participant Generator as RedemptionCodeGenerator
    participant Repo as RedemptionCodeRepository
    participant EventBus as DomainEventPublisher
    participant DB as 資料庫

    Admin->>Handler: 選擇產品並輸入數量
    Handler->>Service: generateCodes(productId, count, expiresAt)

    Service->>Service: 驗證 count > 0 && count <= 100
    Service->>Repo: findByProductId(productId)
    Repo-->>Service: Product

    loop 生成 count 個代碼
        Service->>Generator: generate()
        Generator-->>Service: code (16字元)
        Service->>Repo: existsByCode(code)
        Repo-->>Service: boolean
        alt 代碼已存在
            Service->>Generator: 重新生成（最多10次）
        end
    end

    Service->>Repo: saveAll(codes)
    Repo->>DB: INSERT INTO redemption_code<br/>VALUES (...)
    DB-->>Repo: List<RedemptionCode>
    Repo-->>Service: savedCodes

    Service->>EventBus: publish(RedemptionCodesGeneratedEvent)
    EventBus-->>Service: (事件發布完成)

    Service-->>Handler: Result.ok(savedCodes)
    Handler-->>Admin: 顯示生成的代碼列表
```

**關鍵點**：
1. 單次最多生成 100 個代碼（`MAX_BATCH_SIZE`）
2. 生成時檢查唯一性，最多重試 10 次
3. 發布 `RedemptionCodesGeneratedEvent` 通知管理員面板即時更新統計

---

## 3. 兌換碼兌換流程

當使用者使用兌換碼時：

```mermaid
sequenceDiagram
    actor User as 使用者
    participant Handler as RedemptionCommandHandler
    participant Service as RedemptionService
    participant RCodeRepo as RedemptionCodeRepository
    participant PRepo as ProductRepository
    participant BalanceSvc as BalanceAdjustmentService
    participant TokenSvc as GameTokenService
    participant TxnSvc as CurrencyTransactionService
    participant DB as 資料庫

    User->>Handler: 輸入兌換碼
    Handler->>Service: redeemCode(code, guildId, userId)

    Service->>Service: 驗證並正規化代碼
    Service->>RCodeRepo: findByCode(code)
    RCodeRepo-->>Service: Optional<RedemptionCode>

    alt 代碼不存在
        Service-->>Handler: Result.err(兌換碼無效)
        Handler-->>User: 顯示錯誤訊息
    else 代碼存在
        Service->>Service: 檢查狀態
        Note over Service: isValid() =<br/>!isInvalidated() &&<br/>!isRedeemed() &&<br/>!isExpired()

        alt 代碼已失效
            Service-->>Handler: Result.err(此兌換碼已失效)
        alt 代碼已兌換
            Service-->>Handler: Result.err(此兌換碼已被使用)
        alt 代碼已過期
            Service-->>Handler: Result.err(此兌換碼已過期)
        else 代碼可用
            Service->>Service: 檢查 productId 是否為 NULL

            alt productId 為 NULL
                Service-->>Handler: Result.err(此兌換碼已失效)
            else productId 不為 NULL
                Service->>PRepo: findById(productId)
                PRepo-->>Service: Optional<Product>

                alt 產品不存在
                    Service-->>Handler: Result.err(商品資料異常)
                else 產品存在
                    Service->>RCodeRepo: update(withRedeemed(userId))
                    RCodeRepo->>DB: UPDATE redemption_code<br/>SET redeemed_by = ?,<br/>redeemed_at = NOW()
                    DB-->>RCodeRepo: updated

                    alt 產品有獎勵
                        alt 獎勵類型 = CURRENCY
                            Service->>BalanceSvc: tryAdjustBalance(guildId, userId, amount)
                            BalanceSvc-->>Service: Result<BalanceAdjustment, DomainError>
                            Service->>TxnSvc: recordTransaction(...)
                            TxnSvc->>DB: INSERT INTO currency_transaction
                        else 獎勵類型 = TOKEN
                            Service->>TokenSvc: tryAdjustTokens(guildId, userId, amount)
                            TokenSvc-->>Service: Result<TokenAdjustment, DomainError>
                            Service->>TxnSvc: recordTransaction(...)
                            TxnSvc->>DB: INSERT INTO game_token_transaction
                        end
                    end

                    Service-->>Handler: Result.ok(RedemptionResult)
                    Handler-->>User: 顯示成功訊息與獎勵
                end
            end
        end
    end
```

**關鍵點**：
1. 多層次驗證：代碼存在、伺服器、失效狀態、兌換狀態、過期狀態
2. 檢查 `productId` 是否為 NULL（產品是否已被刪除）
3. 根據 `RewardType` 發放對應獎勵（貨幣或代幣）
4. 記錄交易流水以便追蹤

---

## 4. 產品建立與事件發布

當管理員建立新產品時：

```mermaid
sequenceDiagram
    actor Admin as 管理員
    participant Handler as AdminProductPanelHandler
    participant Service as ProductService
    participant Repo as ProductRepository
    participant EventBus as DomainEventPublisher
    participant PanelListener as AdminPanelUpdateListener
    participant DB as 資料庫

    Admin->>Handler: 填寫產品表單並送出
    Handler->>Service: createProduct(guildId, name, description, rewardType, rewardAmount)

    Service->>Service: 驗證名稱長度、描述長度
    Service->>Repo: existsByGuildIdAndName(guildId, name)

    alt 名稱重複
        Repo-->>Service: true
        Service-->>Handler: Result.err(商品名稱已存在)
        Handler-->>Admin: 顯示錯誤訊息
    else 名稱可用
        Repo-->>Service: false
        Service->>Service: Product.create(...) (建立領域物件)
        Service->>Repo: save(product)
        Repo->>DB: INSERT INTO product
        DB-->>Repo: savedProduct
        Repo-->>Service: savedProduct

        Service->>EventBus: publish(ProductChangedEvent.CREATED)
        EventBus-->>PanelListener: 接收事件
        PanelListener->>PanelListener: 刷新產品列表

        Service-->>Handler: Result.ok(product)
        Handler-->>Admin: 顯示成功訊息
    end
```

**關鍵點**：
1. 先驗證名稱唯一性，再建立產品
2. 使用靜態工廠方法 `Product.create()` 建立領域物件
3. 發布 `ProductChangedEvent` 通知監聽器更新顯示

---

## 5. 商店分頁瀏覽流程

當使用者瀏覽商店時：

```mermaid
sequenceDiagram
    actor User as 使用者
    participant Handler as ShopCommandHandler
    participant Service as ShopService
    participant Repo as ProductRepository
    participant DB as 資料庫

    User->>Handler: 點擊下一頁按鈕
    Handler->>Service: getShopPage(guildId, page)

    Service->>Repo: countByGuildId(guildId)
    Repo->>DB: SELECT COUNT(*)<br/>FROM product<br/>WHERE guild_id = ?
    DB-->>Repo: totalCount
    Repo-->>Service: totalCount

    Service->>Service: 計算 totalPages = ceil(totalCount / pageSize)
    Service->>Service: 限制 page 在有效範圍內

    Service->>Repo: findByGuildIdPaginated(guildId, validPage, pageSize)
    Repo->>DB: SELECT *<br/>FROM product<br/>WHERE guild_id = ?<br/>LIMIT ? OFFSET ?
    DB-->>Repo: List<Product>
    Repo-->>Service: products

    Service->>Service: 建立 ShopPage(products, currentPage, totalPages)

    Service-->>Handler: ShopPage
    Handler->>Handler: 顯示商品列表與分頁按鈕
    Handler-->>User: 顯示商店頁面

    Note over User,Handler: 使用者可繼續點擊上一頁/下一頁
```

**關鍵點**：
1. 先查詢總數以計算總頁數
2. 確保 page 參數在有效範圍內（避免越界）
3. 使用 `LIMIT/OFFSET` 進行分頁查詢

---

## 6. 商品兌換完整流程（V008 新增）

當使用者使用兌換碼並建立交易記錄時：

```mermaid
sequenceDiagram
    actor User as 使用者
    participant Handler as RedemptionCommandHandler
    participant Service as RedemptionService
    participant RCodeRepo as RedemptionCodeRepository
    participant PRepo as ProductRepository
    participant TxService as ProductRedemptionTransactionService
    participant TxRepo as ProductRedemptionTransactionRepository
    participant EventBus as DomainEventPublisher
    participant PanelListener as ProductRedemptionUpdateListener
    participant BalanceSvc as BalanceAdjustmentService
    participant DB as 資料庫

    User->>Handler: 輸入兌換碼
    Handler->>Service: redeemCode(code, guildId, userId)

    Service->>RCodeRepo: findByCode(code)
    RCodeRepo-->>Service: Optional<RedemptionCode>

    alt 代碼無效或不可用
        Service-->>Handler: Result.err(錯誤訊息)
        Handler-->>User: 顯示錯誤訊息
    else 代碼可用
        Service->>PRepo: findById(productId)
        PRepo-->>Service: Optional<Product>

        Service->>RCodeRepo: update(withRedeemed(userId))
        RCodeRepo->>DB: UPDATE redemption_code<br/>SET redeemed_by = ?, redeemed_at = NOW()

        alt 產品有獎勵
            Service->>BalanceSvc: tryAdjustBalance(guildId, userId, amount)
            BalanceSvc-->>Service: Result.ok(adjustment)
        end

        Service->>TxService: recordTransaction(guildId, userId, productId, productName, code, quantity, rewardType, rewardAmount)
        TxService->>TxRepo: save(ProductRedemptionTransaction)
        TxRepo->>DB: INSERT INTO product_redemption_transaction<br/>(guild_id, user_id, product_id,<br/>product_name, redemption_code,<br/>quantity, reward_type, reward_amount)
        DB-->>TxRepo: savedTransaction
        TxRepo-->>TxService: ProductRedemptionTransaction

        TxService->>EventBus: publish(ProductRedemptionCompletedEvent)
        EventBus-->>PanelListener: 接收事件

        alt 使用者面板正在顯示兌換歷史
            PanelListener->>PanelListener: refreshRedemptionHistory(session)
            PanelListener-->>User: 面板自動更新
        end

        TxService-->>Service: Result.ok(transaction)

        Service-->>Handler: Result.ok(RedemptionResult)
        Handler-->>User: 顯示成功訊息與獎勵
    end
```

**關鍵點**（V008 新增）：
1. **交易記錄**：每次兌換都會建立 `ProductRedemptionTransaction` 記錄
2. **產品名稱快照**：交易記錄保存 `product_name`，即使產品被刪除仍可顯示
3. **事件發布**：發布 `ProductRedemptionCompletedEvent` 觸發面板即時更新
4. **遮蔽代碼**：顯示時只顯示前後 4 碼（如 `ABCD****1234`）
5. **面板更新**：如果使用者正在查看兌換歷史，面板會自動刷新

---

## 7. 錯誤處理模式

系統統一使用 `Result<T, DomainError>` 處理錯誤：

```mermaid
sequenceDiagram
    participant Client as 呼叫方
    participant Service as Service層
    participant Repository as Repository層
    participant DB as 資料庫

    Client->>Service: 執行操作
    Service->>Repository: 查詢資料
    Repository->>DB: SQL查詢

    alt 查詢成功
        DB-->>Repository: 結果
        Repository-->>Service: Result.ok(data)
        Service->>Service: 執行業務邏輯
        Service-->>Client: Result.ok(result)
    else 查詢失敗
        DB-->>Repository: 例外
        Repository-->>Service: Result.err(DomainError)
        Service-->>Client: Result.err(DomainError)
        Client->>Client: 根據錯誤類型顯示訊息
    end
```

**錯誤類型**：
- `INVALID_INPUT`: 使用者輸入錯誤（如代碼不存在、名稱重複）
- `PERSISTENCE_FAILURE`: 資料庫操作失敗
- `UNEXPECTED_FAILURE`: 非預期錯誤

---

以上時序圖涵蓋了 LTDJMS 核心業務流程的主要互動模式。開發者可以參考這些圖表來理解：
- 元件之間的呼叫順序
- 資料流的轉換過程
- 錯誤處理的分支邏輯
- 事件驅動的互動模式
