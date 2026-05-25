# Shop and Payment

## Product Management

### Create Product (Admin)
Given the user is a guild administrator  
When they create a product with a name, description, reward type, and pricing  
Then the product is available in the shop  
And a product changed event is published

### Edit Product (Admin)
Given the user is a guild administrator  
When they edit an existing product's details  
Then the product is updated  
And associated redemption codes are invalidated if the product is deleted

### Browse Shop
Given the user is a member of a guild  
When they use `/shop`  
Then they see a paginated list of available products with prices and descriptions  
And products are sorted alphabetically by name  
And there is a unified "Buy" button for all purchasable products

### Search Shop
Given the user is a member of a guild  
When they click the search button in the shop and enter a keyword  
Then matching products are shown by name similarity  
And results are paginated when exceeding 5 items  

### Buy from Search Results
Given the user is viewing search results  
When they select a product from the buy menu on the search results page  
Then the purchase confirmation flow proceeds as if the product were selected from the main shop

### Unified Purchase Flow
Given the user selects a product in the shop  
When the product has both currency price and fiat price  
Then they are prompted to choose between currency purchase or fiat order  
When the product has only one payment method  
Then the purchase flow proceeds directly without a payment choice step

### Member Pricing in Shop List
Given the user has a global membership tier above NONE  
When they browse the shop with `/shop`, pagination, or search  
Then escort-linked products show strikethrough list price and discounted member price in the embed  
And buy menu option descriptions show compact discounted prices without markdown (≤ 100 characters)

### Member Pricing on Escort Products
Given the user has a global membership tier above NONE  
When they select an escort-linked product  
Then the shop shows the discounted member price on the confirmation step  
And currency or fiat charges use the discounted amount while spend ledger records catalog list price M

### Fiat-Only Confirmation
Given the user selects a fiat-only escort product  
When they proceed to purchase  
Then they see a confirmation embed with member pricing before the order is created

## Currency Purchase

### Purchase with Guild Currency
Given the user has sufficient guild currency  
When they select a currency-priced product in the shop and confirm the purchase  
Then the currency is deducted  
And if the product has a reward, it is granted  
And a purchase transaction is recorded

### Auto-Refund on Reward Failure
Given the user purchased a product with guild currency  
And the reward grant fails after currency deduction  
Then the purchase amount is automatically refunded  
And the user is notified of the refund

## Fiat Payment

### Create Fiat Order
Given the user selects a fiat-only product in the shop  
When they confirm the order  
Then an ECPay CVS payment code is generated  
And the user receives a direct message with the order number, payment code, and payment deadline

### Payment Callback
Given a fiat order is in `PENDING_PAYMENT` status  
When ECPay sends a payment callback  
Then the callback payload is verified and decrypted  
And if payment is confirmed, the order status transitions to `PAID`  
And duplicate callbacks are handled idempotently

### Payment Expiry
Given a fiat order has passed its payment deadline  
When the reconciliation scheduler runs  
Then the order is marked as `EXPIRED`  
And the user can still query the expired status

### Post-Payment Fulfillment
Given a fiat order has been marked as `PAID`  
When the background worker processes the order  
Then the buyer receives a payment success notification  
And if the product is escort-linked, catalog list price M is recorded for membership spend (best-effort with retry on failure)  
And if the product has a reward, it is granted  
And the order is marked as fulfilled  
And if the product enables escort auto-creation, a dispatch order is created

### Payment Reconciliation
Given a fiat order remains in `PENDING_PAYMENT` past its deadline  
When the reconciliation scheduler runs  
Then the order is queried against ECPay's trade status API  
And if payment was received (missed callback), the order transitions to `PAID`  
And if not paid, the order is marked as `EXPIRED` or retried with backoff

### Fiat Order Inflight Dedup
The shop handler tracks in-flight fiat orders in an in-process `Set` keyed by `guildId:userId:productId`.  
This prevents duplicate fiat order creation within a single bot instance when the user double-clicks buy.  
Multi-instance deployments do not share this guard; use a single bot instance or add a distributed lock (e.g. Redis) if cross-process dedup is required.

## Redemption Codes

### Generate Redemption Codes (Admin)
Given the user is a guild administrator  
When they generate redemption codes for a product with a count and expiration  
Then unique 16-character codes are created in the database  
And a code generation event is published

### Redeem a Code
Given the user has a valid redemption code for a product in their guild  
When they enter the code in the user panel  
Then the code is atomically marked as redeemed  
And if the product has a reward, it is granted  
And a redemption transaction is recorded

### Invalid or Expired Code
Given the user enters an invalid, already redeemed, or expired redemption code  
When they attempt to redeem it  
Then they receive an appropriate error message  
And no reward is granted

### Admin Code Management
Given the user is a guild administrator  
When they view code statistics in the admin panel  
Then they see total, redeemed, unused, and expired counts for each product's codes
