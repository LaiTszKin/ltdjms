# Administration

## Admin Panel

### Access Admin Panel
Given the user has `ADMINISTRATOR` permission or is the guild owner  
When they use `/admin-panel`  
Then they see the main admin panel with buttons for each management area

### Session Management
Given the user is using the admin panel  
When they navigate between sub-panels  
Then their session state is preserved across interactions

### Real-Time Panel Updates
Given an admin panel is open  
When a relevant event occurs (balance change, currency config change, product change)  
Then the panel is automatically refreshed

## User Panel

個人面板已抽成獨立 TypeScript package `@ltdjms/user-panel`（`packages/user-panel/`），與 Java bot 1:1 對齊 `user_panel_*` customId 與 embed 結構。Handler 在 `apps/bot/src/main.ts` 註冊；slash 定義由 app 層 composer 合併後向 Discord API 註冊。

### Access User Panel
Given the user is a guild member  
When they use `/user-panel`  
Then they see their balance, game tokens, and action buttons for history and redemption

### Real-Time User Panel Updates
Given a user panel is open  
When a balance or token change occurs  
Then the panel is automatically updated (per-user debounce + serialized edits)

### Session Expiry
Given a user panel session has expired  
When the user clicks a panel button  
Then they receive an ephemeral message to re-run `/user-panel`

## Dispatch Panel

### Access Dispatch Panel (Admin)
Given the user has `ADMINISTRATOR` permission or is the guild owner  
When they use `/dispatch-panel`  
Then they see options to create and assign dispatch orders

## AI Configuration (Admin)

### Configure AI Allowed Channels
Given the user is a guild administrator  
When they add or remove channels or categories from the AI allowlist in the admin panel  
Then the AI chat restrictions are updated

### Configure AI Agent Channels
Given the user is a guild administrator  
When they enable or disable agent mode for channels in the admin panel  
Then the agent configuration is updated globally

## Game Configuration (Admin)

### Configure Dice Game 1
Given the user is a guild administrator  
When they adjust the minimum tokens, maximum tokens, or reward per dice value for Dice Game 1  
Then the game settings are updated immediately

### Configure Dice Game 2
Given the user is a guild administrator  
When they adjust the game parameters for Dice Game 2  
Then the game settings are updated immediately
