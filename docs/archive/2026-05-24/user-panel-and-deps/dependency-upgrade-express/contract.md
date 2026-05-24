# Contract: dependency-upgrade-express

## Version targets

| Package | Target |
| ------- | ------ |
| express | ^5.2.1 |
| @types/express | ^5.0.6 |

## HTTP contract invariants

- ECPay callback POST endpoint path 不變
- Signature verification 輸入（raw body + headers）不變
- 成功/失敗 HTTP status code 不變
- Health check endpoint 行為不變
