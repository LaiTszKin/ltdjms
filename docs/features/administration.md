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

### Access User Panel
Given the user is a guild member  
When they use `/user-panel`  
Then they see their balance, game tokens, membership tier and period progress, and action buttons for history and redemption

### Real-Time User Panel Updates
Given a user panel is open  
When a balance, token, or membership tier change occurs  
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

## Membership Management (Admin)

### View Member Membership Detail
Given the user is a guild administrator  
When they open **🏅 會員等級管理** in `/admin-panel` and select a member  
Then the panel shows tier, join date, current period spend M, remaining to next tier, next settlement date, and bronze qualifying flag

### Adjust Period Spend M
Given an administrator has selected a member  
When they choose add, deduct, or set mode and submit a non-negative integer M amount  
Then an `ADMIN_ADJUST` row is appended to `membership_spend_entry` with a signed delta  
And the admin detail embed refreshes with the updated period sum  
And `MembershipPeriodSpendChangedEvent` refreshes the member's open user panel (period spend and progress)  
And tier is not recalculated immediately (settlement still applies on schedule)

### Set Membership Tier
Given an administrator has selected a member  
When they choose a target tier and confirm  
Then `current_tier` is updated immediately  
And `has_qualifying_bronze_order` is set when tier is BRONZE or higher, cleared for NONE  
And `MembershipTierChangedEvent` is published when the effective tier changes (refreshing open user panels and shop discounts)

### Settlement Override Notice
Manual tier changes take effect immediately but may be overwritten on the next scheduled settlement based on ledger spend. The admin membership embed footer reminds operators of this behavior.
