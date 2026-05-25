# Membership Tiers

Global membership tiers apply across all guilds. Tier is driven by catalog list-price spend (M) on escort-linked shop purchases, evaluated on each member's personal settlement date.

## Tier Levels and Benefits

### Tier Ladder
Given the coordination-approved tier table is in effect  
When a member's period average list price M is calculated at settlement  
Then tier is resolved from NONE through BRONZE, SILVER, GOLD, PLATINUM, DIAMOND, to BLACK by threshold  
And members with a qualifying bronze order never fall below BRONZE after settlement

### Member Discount on Escort Purchases
Given the member has an active tier above NONE  
When they purchase an escort-linked product with currency or fiat  
Then the list price is discounted by the tier rate before charge  
And the shop confirmation UI shows the member price before order creation

### Monthly Token Grant
Given settlement completes for a period  
When the member's tier grants monthly game tokens  
Then tokens are credited idempotently for that settlement period  
And grant retries recover from transient failures without double-crediting

## Join and Settlement Anchor

### Record Earliest Guild Join
Given a member joins a guild for the first time in the system  
When the join listener runs  
Then the earliest guild join timestamp is stored  
And settlement day-of-month is derived from the join date (days 29–31 clamp to 28)  
And the next settlement anchor is initialized when not yet settled

### Personal Settlement Cycle
Given a member's settlement date is due  
When the settlement scheduler runs  
Then period spend is summed for `[last_settlement_at, next_settlement_at)`  
And tier is recalculated and the next settlement anchor advances one month  
And a tier-changed event is published when the effective tier changes

## Spend Ledger

### Record Escort Fiat Payment
Given a paid fiat order is for an escort-linked product  
When post-payment fulfillment runs  
Then catalog list price M is recorded in the membership spend ledger  
And duplicate recordings for the same order are ignored

### Qualifying Bronze Order
Given a single escort payment's catalog list price M meets the bronze threshold  
When spend is recorded  
Then the member qualifies for permanent bronze floor on future settlements

### Best-Effort Spend Recording
Given membership spend recording fails during fiat fulfillment  
When the failure is detected  
Then fulfillment continues and the order is enqueued for background retry  
And permanently failing retries are dead-lettered after max attempts

## User Panel

### View Membership Progress
Given the member opens `/user-panel`  
When membership data exists  
Then they see join date (Discord `<t:epoch:D>` or「尚未記錄」), current tier, current benefits (escort discount and monthly token grant), period spend M, remaining M to the next tier, progress toward the next threshold, and next settlement date

### Panel Updates on Tier Change
Given the member has an open user panel  
When membership tier changes after settlement, bronze promotion, or admin tier adjustment  
Then the panel refreshes to show the updated tier, join date, benefits, remaining M, and progress

### Panel Updates on Period Spend Change
Given the member has an open user panel  
When an administrator adjusts the member's period spend M via `ADMIN_ADJUST`  
Then `MembershipPeriodSpendChangedEvent` is published  
And the panel refreshes to show the updated period spend M, remaining M, and progress
