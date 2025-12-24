# 商店模組設計與實作

本文件說明 LTDJMS Discord Bot 的商店模組，提供成員瀏覽與兌換產品的功能介面。

## 1. 概述

商店模組提供一個直觀的介面，讓伺服器成員可以瀏覽管理員建立的產品，並透過兌換碼領取獎勵。商店頁面支援分頁瀏覽，並與產品模組緊密整合。

主要功能：
- 瀏覽伺服器可兌換的產品列表
- 產品資訊顯示（名稱、描述、獎勵）
- 分頁導航支援
- 與產品管理面板的整合

## 2. 領域模型

商店模組本身不定義獨立的領域模型，而是直接使用 `Product` 實體作為資料來源。

```java
// src/main/java/ltdjms/discord/product/domain/Product.java
public record Product(
    Long id,
    Long guildId,
    String name,
    String description,
    RewardType rewardType,
    Long rewardAmount,
    Instant createdAt,
    Instant updatedAt
) {
    // 獎勵格式化方法
    public boolean hasReward() {
        return rewardType != null && rewardAmount > 0;
    }

    public String formatReward() {
        if (rewardType == RewardType.CURRENCY) {
            return "🪙 " + rewardAmount + " 貨幣";
        } else if (rewardType == RewardType.TOKENS) {
            return "🎮 " + rewardAmount + " 遊戲代幣";
        }
        return "";
    }
}
```

## 3. 服務層

### 3.1 ShopService

負責商店頁面的資料查詢與分頁邏輯。

```java
// src/main/java/ltdjms/discord/shop/services/ShopService.java
public class ShopService {

    private static final int DEFAULT_PAGE_SIZE = 5;

    private final ProductRepository productRepository;

    public ShopService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * 取得商店指定頁面的產品。
     *
     * @param guildId 伺服器 ID
     * @param page    頁碼（從 0 開始）
     * @return 商店頁面資料
     */
    public ShopPage getShopPage(long guildId, int page) {
        long totalCount = productRepository.countByGuildId(guildId);
        int totalPages = (int) Math.ceil((double) totalCount / DEFAULT_PAGE_SIZE);

        // 確保頁碼在有效範圍內
        int validPage = Math.max(0, Math.min(page, totalPages - 1));

        List<Product> products = productRepository.findByGuildIdPaginated(
            guildId, validPage, DEFAULT_PAGE_SIZE
        );

        return new ShopPage(products, validPage + 1, totalPages);
    }

    /**
     * 檢查商店是否有任何產品。
     */
    public boolean hasProducts(long guildId) {
        return productRepository.countByGuildId(guildId) > 0;
    }

    /**
     * 商店頁面資料。
     */
    public record ShopPage(
        List<Product> products,
        int currentPage,
        int totalPages
    ) {
        public boolean isEmpty() {
            return products.isEmpty();
        }

        public boolean hasPreviousPage() {
            return currentPage > 1;
        }

        public boolean hasNextPage() {
            return currentPage < totalPages;
        }

        public String formatPageIndicator() {
            if (totalPages <= 1) {
                return "共 " + products.size() + " 個商品";
            }
            return "第 " + currentPage + " / " + totalPages + " 頁";
        }
    }
}
```

主要方法：
- `getShopPage`: 取得指定頁面的產品列表
- `hasProducts`: 檢查是否有可顯示的產品

## 4. 指令與介面

### 4.1 ShopCommandHandler

處理 `/shop` 指令，展示商店頁面。

```java
// src/main/java/ltdjms/discord/shop/commands/ShopCommandHandler.java
public class ShopCommandHandler implements SlashCommandListener.CommandHandler {

    private final ShopService shopService;

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.reply("此功能只能在伺服器中使用").setEphemeral(true).queue();
            return;
        }

        long guildId = event.getGuild().getIdLong();

        try {
            ShopService.ShopPage shopPage = shopService.getShopPage(guildId, 0);

            MessageEmbed embed;
            List<ActionRow> components;

            if (shopPage.isEmpty()) {
                embed = ShopView.buildEmptyShopEmbed();
                components = List.of();
            } else {
                embed = ShopView.buildShopEmbed(
                    shopPage.products(),
                    shopPage.currentPage(),
                    shopPage.totalPages(),
                    guildId
                );
                components = ShopView.buildShopComponents(
                    shopPage.currentPage(),
                    shopPage.totalPages()
                );
            }

            event.replyEmbeds(embed)
                .addComponents(components)
                .queue();

        } catch (Exception e) {
            LOG.error("Error displaying shop page for guildId={}", guildId, e);
            event.reply("發生錯誤，請稍後再試").setEphemeral(true).queue();
        }
    }
}
```

### 4.2 ShopButtonHandler

處理商店頁面的分頁按鈕互動。

```java
// src/main/java/ltdjms/discord/shop/commands/ShopButtonHandler.java
public class ShopButtonHandler {

    private final ShopService shopService;

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        long guildId = event.getGuild().getIdLong();
        String buttonId = event.getButton().getId();

        if (buttonId.startsWith(ShopView.BUTTON_PREV_PAGE)) {
            int page = parsePageNumber(buttonId, ShopView.BUTTON_PREV_PAGE);
            updateShopPage(event, guildId, page - 1);
        } else if (buttonId.startsWith(ShopView.BUTTON_NEXT_PAGE)) {
            int page = parsePageNumber(buttonId, ShopView.BUTTON_NEXT_PAGE);
            updateShopPage(event, guildId, page + 1);
        }
    }

    private void updateShopPage(ButtonInteractionEvent event, long guildId, int page) {
        ShopService.ShopPage shopPage = shopService.getShopPage(guildId, page - 1);

        MessageEmbed embed = ShopView.buildShopEmbed(
            shopPage.products(),
            shopPage.currentPage(),
            shopPage.totalPages(),
            guildId
        );
        List<ActionRow> components = ShopView.buildShopComponents(
            shopPage.currentPage(),
            shopPage.totalPages()
        );

        event.editMessageEmbeds(embed)
            .setActionRow(components)
            .queue();
    }
}
```

## 5. 視圖設計

### 5.1 ShopView

負責建構 Discord Embed 與互動元件。

```java
// src/main/java/ltdjms/discord/shop/services/ShopView.java
public class ShopView {

    private static final Color EMBED_COLOR = new Color(0x5865F2);
    private static final int PAGE_SIZE = 5;

    public static final String BUTTON_PREV_PAGE = "shop_prev_";
    public static final String BUTTON_NEXT_PAGE = "shop_next_";

    /**
     * 建構空商店頁面的 Embed。
     */
    public static MessageEmbed buildEmptyShopEmbed() {
        return new EmbedBuilder()
            .setTitle("🏪 商店")
            .setColor(EMBED_COLOR)
            .setDescription("目前沒有可購買的商品")
            .build();
    }

    /**
     * 建構商店頁面的 Embed。
     */
    public static MessageEmbed buildShopEmbed(
        List<Product> products,
        int currentPage,
        int totalPages,
        long guildId
    ) {
        EmbedBuilder builder = new EmbedBuilder()
            .setTitle("🏪 商店")
            .setColor(EMBED_COLOR);

        StringBuilder sb = new StringBuilder();
        for (Product product : products) {
            sb.append("**").append(product.name()).append("**");

            if (product.description() != null && !product.description().isBlank()) {
                sb.append("\n").append(product.description());
            }

            if (product.hasReward()) {
                sb.append("\n獎勵：").append(product.formatReward());
            }

            sb.append("\n\n");
        }

        builder.setDescription(sb.toString());

        // 分頁指示器
        if (totalPages > 1) {
            builder.setFooter("第 " + currentPage + " / " + totalPages + " 頁");
        } else {
            builder.setFooter("共 " + products.size() + " 個商品");
        }

        return builder.build();
    }

    /**
     * 建構商店頁面的導航按鈕。
     */
    public static List<ActionRow> buildShopComponents(int currentPage, int totalPages) {
        Button prevButton;
        Button nextButton;

        if (currentPage == 1) {
            prevButton = Button.secondary(BUTTON_PREV_PAGE + (currentPage - 1), "⬅️ 上一頁")
                .asDisabled();
        } else {
            prevButton = Button.secondary(BUTTON_PREV_PAGE + (currentPage - 1), "⬅️ 上一頁");
        }

        if (currentPage >= totalPages) {
            nextButton = Button.secondary(BUTTON_NEXT_PAGE + (currentPage + 1), "下一頁 ➡️")
                .asDisabled();
        } else {
            nextButton = Button.secondary(BUTTON_NEXT_PAGE + (currentPage + 1), "下一頁 ➡️");
        }

        return List.of(ActionRow.of(prevButton, nextButton));
    }
}
```

### 5.2 商店頁面格式

**空商店狀態：**
```
🏪 商店
目前沒有可購買的商品
```

**一般商店狀態：**
```
🏪 商店

**VIP 會員**
獲得額外福利
獎勵：🪙 1000 貨幣

**每日登入禮**
連續登入 7 天領取
獎勵：🎮 50 遊戲代幣

第 1 / 2 頁
```

**底部按鈕：**
- `⬅️ 上一頁`（在第一頁時禁用）
- `下一頁 ➡️`（在最後一頁時禁用）

## 6. 使用方式

### 6.1 成員視角

1. 成員輸入 `/shop` 指令
2. 系統顯示商店頁面，包含所有可兌換的產品
3. 透過底部按鈕瀏覽不同頁面的產品
4. 若要兌換產品，需聯繫管理員取得兌換碼

### 6.2 管理員視角

管理員透過管理面板管理產品：

1. 在 `/admin-panel` 中點選「產品管理」
2. 新增或編輯產品
3. 為產品生成兌換碼
4. 將兌換碼發放給成員

## 7. 整合方式

### 7.1 與產品模組整合

商店模組依賴 `ProductRepository` 取得產品資料：

```java
// 商店模組依賴的 ProductRepository 方法
Result<List<Product>, DomainError> findByGuildId(Long guildId);
Result<List<Product>, DomainError> findByGuildIdPaginated(Long guildId, int page, int pageSize);
Result<Long, DomainError> countByGuildId(Long guildId);
```

### 7.2 與領域事件整合

商店頁面可以訂閱 `ProductChangedEvent`，在產品異動時自動更新顯示。

## 8. 錯誤處理

商店模組的錯誤類型：

- `PERSISTENCE_FAILURE`: 資料庫查詢失敗
- `UNEXPECTED_FAILURE`: 其他非預期錯誤

錯誤發生時會以 ephemeral 訊息回覆使用者。

## 9. 擴充建議

若要擴充商店功能，建議考慮：

1. **兌換功能整合**
   - 在商店頁面直接輸入兌換碼
   - 驗證並立即發放獎勵

2. **產品分類**
   - 依獎勵類型分類顯示
   - 新增篩選功能

3. **產品圖片**
   - 支援產品圖片顯示
   - 使用 Embed thumbnail

4. **購買數量**
   - 支援查看剩餘數量
   - 限時商品顯示

---

熟悉以上設計後，您可以依照相同模式擴充商店功能，例如新增直接兌換介面或產品搜尋功能。
