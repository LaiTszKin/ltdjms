# Guild Economy

## Currency

### View Balance
Given the user is a member of a guild  
When they open the user panel  
Then they see their current currency balance with the guild's currency name and icon

### Adjust Balance (Admin)
Given the user is a guild administrator  
When they use the admin panel to adjust a member's balance  
Then the member's balance is updated  
And a transaction record is created with the source `ADMIN_ADJUSTMENT`

### Configure Currency (Admin)
Given the user is a guild administrator  
When they use `/currency-config` with a name and icon  
Then the guild's currency name and icon are updated globally

## Game Tokens

### View Token Balance
Given the user is a member of a guild  
When they open the user panel  
Then they see their game token balance

### Play Dice Game 1
Given the user has sufficient game tokens  
When they use `/dice-game-1` with a token wager within the allowed range  
Then the tokens are deducted  
And dice are rolled  
And the user receives a currency reward equal to the sum of dice values multiplied by the configured reward rate

### Play Dice Game 2
Given the user has sufficient game tokens  
When they use `/dice-game-2` with a token wager within the allowed range  
Then three dice per token are rolled  
And the reward is calculated based on straights, triples, and remaining dice values

### Insufficient Tokens
Given the user has insufficient game tokens  
When they play a dice game  
Then they receive an insufficient tokens error  
And no tokens are deducted

### Adjust Token Balance (Admin)
Given the user is a guild administrator  
When they use the admin panel to adjust a member's game tokens  
Then the member's token balance is updated  
And a transaction record is created

### Membership Settlement Token Grant
Given a member completes a membership settlement period at SILVER or above  
When token grant processing runs  
Then monthly tier game tokens are credited to a primary guild account for that period  
And duplicate grants for the same settlement period are prevented

## Transaction History

### View Currency History
Given the user is a member of a guild  
When they view their transaction history in the user panel  
Then they see paginated currency transaction records with amounts, sources, and timestamps

### View Token History
Given the user is a member of a guild  
When they view their token history in the user panel  
Then they see paginated game token transaction records
