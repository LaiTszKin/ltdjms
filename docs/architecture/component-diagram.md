# 系統架構圖

本文件使用 Mermaid 圖表展示 LTDJMS 的系統架構、請求流程與模組關係。

## 1. 高階系統架構圖

```mermaid
flowchart TB
    subgraph Discord["Discord 平台"]
        User["Discord 使用者"]
        Gateway["Discord Gateway"]
    end

    subgraph Bot["LTDJMS Discord Bot"]
        JDA["JDA 5.x"]
        SlashListener["SlashCommandListener"]

        subgraph Commands["指令處理層"]
            CurrencyCmd["/currency-config"]
            DiceGame1Cmd["/dice-game-1"]
            DiceGame2Cmd["/dice-game-2"]
            UserPanelCmd["/user-panel"]
            AdminPanelCmd["/admin-panel"]
            ShopCmd["/shop"]
        end

        subgraph Handlers["互動處理層"]
            UserPanelBtn["UserPanelButtonHandler"]
            AdminPanelBtn["AdminPanelButtonHandler"]
            ShopBtn["ShopButtonHandler"]
        end

        subgraph Services["服務層"]
            CurrencySvc["Currency Services"]
            GameTokenSvc["Game Token Services"]
            ProductSvc["Product Services"]
            RedemptionSvc["Redemption Services"]
            PanelSvc["Panel Services"]
            ShopSvc["Shop Service"]
        end

        subgraph Shared["共用模組"]
            DI["Dagger DI"]
            Result["Result<T,E>"]
            Events["Domain Events"]
            Config["EnvironmentConfig"]
        end

        subgraph Persistence["資料存取層"]
            JOOQ["jOOQ"]
            Repos["Repositories"]
        end
    end

    subgraph Database["PostgreSQL"]
        Schema["Schema + Migrations"]
    end

    User -->|Slash 指令| Gateway
    User -->|按鈕/Modal| Gateway
    Gateway --> JDA
    JDA --> SlashListener
    SlashListener --> Commands
    SlashListener --> Handlers
    Commands --> Services
    Handlers --> Services
    Services --> Shared
    Services --> Repos
    Repos --> JOOQ
    JOOQ --> Database
    Database --> Schema
```

## 2. 請求處理流程圖

```mermaid
sequenceDiagram
    participant User as Discord 使用者
    participant JDA as JDA
    participant Listener as SlashCommandListener
    participant Handler as Command Handler
    participant Service as Domain Service
    participant Repo as Repository
    participant DB as PostgreSQL

    User->>JDA: 輸入 /指令
    JDA->>Listener: SlashCommandInteractionEvent
    Listener->>Handler: dispatch(event)
    Note over Handler: 解析參數

    rect rgb(240, 248, 255)
    Note over Handler: 業務邏輯
    Handler->>Service: 呼叫 service 方法
    Service->>Repo: 資料庫操作
    Repo->>DB: SQL 查詢/更新
    DB-->>Repo: 查詢結果
    Repo-->>Service: Domain Object
    Service-->>Handler: Result<T, E>
    end

    alt isOk()
        Handler->>JDA: 回覆成功訊息
    else isErr()
        Handler->>JDA: 回覆錯誤訊息
    end

    JDA-->>User: Embed/文字回應
```

## 3. 模組關係圖

```mermaid
flowchart LR
    subgraph Currency["貨幣模組"]
        C_Domain["domain/"]
        C_Svc["services/"]
        C_Cmd["commands/"]
    end

    subgraph GameToken["遊戲代幣模組"]
        GT_Domain["domain/"]
        GT_Svc["services/"]
        GT_Cmd["commands/"]
    end

    subgraph Panel["面板模組"]
        P_Svc["services/"]
        P_Cmd["commands/"]
    end

    subgraph Product["產品模組"]
        Prod_Domain["domain/"]
        Prod_Svc["services/"]
    end

    subgraph Redemption["兌換模組"]
        R_Domain["domain/"]
        R_Svc["services/"]
    end

    subgraph Shop["商店模組"]
        S_Svc["services/"]
        S_Cmd["commands/"]
    end

    subgraph Shared["共用模組"]
        S_DI["di/"]
        S_Events["events/"]
        S_Config["Config/"]
        S_Result["Result/"]
    end

    %% 關係定義
    C_Svc --> S_Result
    GT_Svc --> S_Result
    P_Svc --> C_Svc
    P_Svc --> GT_Svc
    Prod_Svc --> S_Result
    R_Svc --> Prod_Svc
    R_Svc --> C_Svc
    R_Svc --> GT_Svc
    S_Cmd --> Prod_Svc
    S_Cmd --> S_Result
    S_Svc --> Prod_Svc
    S_Svc --> S_Result
```

## 4. 資料庫 Schema 關係圖

```mermaid
erDiagram
    guild_currency_config ||--|| member_currency_account : "伺服器配置"
    guild_currency_config {
        bigint guild_id PK
        varchar currency_name
        varchar currency_icon
        timestamptz created_at
        timestamptz updated_at
    }

    member_currency_account {
        bigint guild_id PK,FK
        bigint user_id PK
        bigint balance
        timestamptz created_at
        timestamptz updated_at
    }

    game_token_account {
        bigint guild_id PK,FK
        bigint user_id PK
        bigint tokens
        timestamptz created_at
        timestamptz updated_at
    }

    game_token_transaction {
        bigint id PK
        bigint guild_id FK
        bigint user_id FK
        bigint amount
        bigint balance_after
        varchar source
        varchar description
        timestamptz created_at
    }

    dice_game1_config {
        bigint guild_id PK
        bigint tokens_per_play
        timestamptz created_at
        timestamptz updated_at
    }

    dice_game2_config {
        bigint guild_id PK
        bigint tokens_per_play
        timestamptz created_at
        timestamptz updated_at
    }

    product {
        bigint id PK
        bigint guild_id FK
        varchar name
        varchar description
        varchar reward_type
        bigint reward_amount
        timestamptz created_at
        timestamptz updated_at
    }

    redemption_code {
        bigint id PK
        bigint guild_id FK
        bigint product_id FK
        varchar code
        bigint redeemed_by
        timestamptz redeemed_at
        timestamptz expires_at
        timestamptz created_at
    }

    member_currency_account ||--o{ game_token_transaction : "交易記錄"
    product ||--o{ redemption_code : "包含代碼"
```

## 5. 面板互動流程圖

```mermaid
flowchart TD
    subgraph UserPanel["/user-panel 流程"]
        UP1["使用者輸入 /user-panel"]
        UP2["UserPanelCommandHandler 處理"]
        UP3["UserPanelService 取得餘額資料"]
        UP4["顯示餘額 Embed + 流水按鈕"]
        UP5["使用者點擊流水按鈕"]
        UP6["UserPanelButtonHandler 處理"]
        UP7["GameTokenTransactionService 取得分頁資料"]
        UP8["顯示流水 Embed + 分頁按鈕"]
    end

    subgraph AdminPanel["/admin-panel 流程"]
        AP1["管理員輸入 /admin-panel"]
        AP2["AdminPanelCommandHandler 處理"]
        AP3["顯示管理面板 Embed + 功能按鈕"]

        AP4A["點擊餘額管理"]
        AP4B["點擊代幣管理"]
        AP4C["點擊遊戲設定"]
        AP4D["點擊產品管理"]

        AP5A["顯示餘額調整 Modal"]
        AP5B["顯示代幣調整 Modal"]
        AP5C["顯示遊戲選擇選單 → Modal"]
        AP5D["顯示產品管理面板"]

        AP6["管理員填寫表單"]
        AP7["AdminPanelButtonHandler 處理"]
        AP8["呼叫對應 Service 執行操作"]
        AP9["顯示操作結果"]
    end

    UP1 --> UP2 --> UP3 --> UP4 --> UP5 --> UP6 --> UP7 --> UP8
    AP1 --> AP2 --> AP3
    AP3 --> AP4A --> AP5A --> AP6 --> AP7 --> AP8 --> AP9
    AP3 --> AP4B --> AP5B --> AP6 --> AP7 --> AP8 --> AP9
    AP3 --> AP4C --> AP5C --> AP6 --> AP7 --> AP8 --> AP9
    AP3 --> AP4D --> AP5D --> AP6 --> AP7 --> AP8 --> AP9
```

## 6. 錯誤處理流程圖

```mermaid
flowchart TD
    subgraph Service["Service 層"]
        S1["執行業務邏輯"]
        S2{"檢查 business rules"}
        S3["返回 Result.ok(value)"]
        S4["返回 Result.err(error)"]
    end

    subgraph Handler["Handler 層"]
        H1["取得 Service 結果"]
        H2{"isOk()?"}
        H3["格式化成功訊息"]
        H4["呼叫 BotErrorHandler"]
        H5["格式化錯誤訊息"]
    end

    S1 --> S2
    S2 -->|通過| S3
    S2 -->|失敗| S4

    S3 --> H1
    S4 --> H1

    H1 --> H2
    H2 -->|是| H3
    H2 -->|否| H4
    H3 --> JDA["回覆 Discord"]
    H4 --> H5 --> JDA
```

## 7. 領域事件發布流程圖

```mermaid
flowchart LR
    subgraph Service["業務服務"]
        SVC["BalanceService\nGameTokenService\netc."]
    end

    subgraph Publisher["事件發布器"]
        PUB["DomainEventPublisher"]
    end

    subgraph Events["領域事件"]
        EV1["BalanceChangedEvent"]
        EV2["GameTokenChangedEvent"]
        EV3["ProductChangedEvent"]
    end

    subgraph Listeners["事件監聽器"]
        L1["UserPanelUpdateListener"]
        L2["AdminPanelUpdateListener"]
    end

    SVC -->|"publish(event)"| PUB
    PUB -->|同步分發| EV1
    PUB -->|同步分發| EV2
    PUB -->|同步分發| EV3
    EV1 --> L1
    EV1 --> L2
    EV2 --> L1
    EV2 --> L2
    EV3 --> L2
```

---

以上圖表涵蓋了 LTDJMS 的核心架構概念。如需更詳細的模組內部結構，請參考各模組的專屬文件。
