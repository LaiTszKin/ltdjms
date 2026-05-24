# Contract: user-panel-package-extraction

## Public API (`@ltdjms/user-panel`)

```typescript
// packages/user-panel/src/index.ts
export { configureUserPanelContainer, USER_PANEL_TOKENS } from './di/user-panel-module.js';
export type { MemberPanelView } from './facades/MemberInfoFacade.js'; // if needed externally
```

## Internal contracts (unchanged during extraction)

- MemberInfoFacade method signatures 不變
- SlashCommandListener registration pattern 不變
- Session key prefix `user_panel:` 不變
- Domain events: `balance_changed`, `game_token_changed`, `currency_config_changed`

## Package dependencies

| Dependency | Purpose |
| ---------- | ------- |
| @ltdjms/shared | DiscordInteraction, EmbedView, events, cache |
| @ltdjms/economy | BalanceService, CurrencyTransactionService |
| @ltdjms/games | GameTokenService, GameTokenTransactionService |
| @ltdjms/shop | RedemptionService, ProductRedemptionTransactionService |
| discord.js | UI components |

## Out of scope for this contract

- Java customId parity（parity spec）
- Embed field layout parity（parity spec）
