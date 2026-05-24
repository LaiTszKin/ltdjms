# Contract: user-panel-java-parity

## UI contract (must match Java exactly)

### Embed (main panel)

| Field | Expected |
| ----- | -------- |
| title | `個人面板` |
| description | `{userMention} 的帳戶資訊` |
| fields[0].name | `{currencyName}餘額` |
| fields[1].name | `遊戲代幣餘額` |
| color | `5793266` (0x5865F2) |
| footer (initial) | `點擊下方按鈕查看流水紀錄或兌換碼` |

### Buttons (main panel)

| Row | customId | label | style |
| --- | -------- | ----- | ----- |
| 1 | `user_panel_currency_history` | `{icon} 查看貨幣流水` | SECONDARY |
| 1 | `user_panel_token_history` | `📜 查看遊戲代幣流水` | SECONDARY |
| 1 | `user_panel_product_redemption_history` | `🛒 查看商品流水` | SECONDARY |
| 2 | `user_panel_redeem` | `🎫 兌換碼` | SUCCESS |

### Pagination

| Button | customId pattern |
| ------ | ---------------- |
| Back | `user_panel_back` |
| Prev | `user_panel_{type}_page_{n-1}` |
| Next | `user_panel_{type}_page_{n+1}` |

### Modal

| Field | Value |
| ----- | ----- |
| modal id | `user_panel_modal_redeem` |
| text input id | `code` |
| min length | 16 |
| max length | 20 |

## Behavioral contract

- PAGE_SIZE = 10, 1-based page, clamp to valid range
- Session TTL = 15 minutes
- Push update: embed only, no button refresh
- Redemption: ephemeral reply, no auto panel refresh (rewards may trigger balance events)

## Acceptance test command

```bash
pnpm vitest run --project @ltdjms/user-panel
make verify
```
