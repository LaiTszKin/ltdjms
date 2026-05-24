# Contract: shop-java-parity

- Date: 2026-05-24
- Feature: shop-java-parity
- Change Name: shop-java-parity

## Scope

- **External deps in this doc:** 2 (test-only, adopted by sibling spec)

## Dependencies

### Vitest JSON snapshot (existing)

#### Evidence

| Primary docs URL(s) | Sections |
| --- | --- |
| https://vitest.dev/guide/snapshot.html | toMatchJsonSnapshot |

**Version revision assumed:** `^4.1.7` (already installed)

#### Integration anchors

| ID | Boundary | Non-negotiables | Forbidden assumptions |
| --- | --- | --- | --- |
| `EXT-SHOP-001` | `toMatchJsonSnapshot()` | Normalize embed before compare | Pixel-perfect Discord rendering |

### @robojs/mock (dev, from external-deps-adoption)

#### Evidence

| Primary docs URL(s) | Sections |
| --- | --- |
| https://www.npmjs.com/package/@robojs/mock | interaction mocking |

**Version revision assumed:** `0.1.1-next.1`

#### Integration anchors

| ID | Boundary | Non-negotiables | Forbidden assumptions |
| --- | --- | --- | --- |
| `EXT-SHOP-002` | Slash/button mock | Fallback to hand mock if unstable | Required for all UT-306–308 |

#### Trace hooks

- Spec IDs: R1–R6
- Unknown / TBD: None

**None** for additional runtime external APIs beyond existing ECPay/Discord (unchanged in this spec).
