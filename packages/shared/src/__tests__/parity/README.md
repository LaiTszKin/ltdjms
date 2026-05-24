# Java fixture → Vitest JSON parity workflow

Parity specs compare TypeScript output against Java test oracles exported as JSON fixtures.

## Workflow

1. **Extract oracle** — Copy structural expectations from Java tests into `docs/plans/.../fixtures/*.json` (see `user-panel-java-parity` and `shop-java-parity` for examples).
2. **Load fixture** — In Vitest, `readFileSync` + `JSON.parse` the oracle file next to the test (or from batch fixtures path).
3. **Build actual** — Invoke the TypeScript unit under test (handler, view builder, tool schema, etc.).
4. **Normalize** — For embeds/components, call `normalizeEmbedForSnapshot()`; for generic JSON, use `normalizeValue()`.
5. **Assert** — Call `assertJsonParity(actual, oracle)` or `expect(normalized).toMatchSnapshot()` when capturing TS baselines.

## Helpers

| Export | Use when |
| --- | --- |
| `assertJsonParity(actual, oracle)` | Generic structural parity vs Java fixture |
| `assertEmbedParity(actual, oracle)` | Discord embed fields (strips timestamp/url/thumbnail) |
| `normalizeEmbedForSnapshot(embed)` | Pre-process embed before snapshot or manual `expect` |
| `normalizeValue(value)` | Sort keys / deep-normalize arbitrary JSON |

## Example

```typescript
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { assertJsonParity, normalizeEmbedForSnapshot } from './json-snapshot.js';

const oracle = JSON.parse(
  readFileSync(join(dirname(fileURLToPath(import.meta.url)), 'fixtures/java-shop-view-oracle.json'), 'utf8'),
);

const actual = buildShopEmbed(products, page, totalPages);
assertJsonParity(normalizeEmbedForSnapshot(actual), oracle.scenarios.browseSingleProduct);
```

## References

- Vitest snapshots: https://vitest.dev/guide/snapshot.html
- Batch mapping: `docs/plans/2026-05-24/java-parity-shop-ai/ai-agent-java-parity/fixtures/java-test-mapping.md`
