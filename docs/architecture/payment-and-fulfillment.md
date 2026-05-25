# Payment and Fulfillment

## Fiat Order State Machine

```
PENDING_PAYMENT
  -> markPaidIfPending (from callback or reconciliation) -> PAID
  -> markExpiredIfPending (from reconciliation)          -> EXPIRED

PAID
  -> Post-payment worker processing steps (each idempotent):
     -> notify buyer (markBuyerNotifiedIfNeeded)
     -> auto-create escort order (if configured)
     -> grant reward (markRewardGrantedIfNeeded)
     -> mark fulfilled (markFulfilledIfNeeded)

EXPIRED (terminal)
```

## Idempotency Mechanisms

### Conditional State Updates
All `mark*IfNeeded` methods use `WHERE target_column IS NULL RETURNING *`. If the step already completed, the update is a no-op.

### Claim/Release Pattern
Background workers use a `processing_at` column for lightweight row-level locking:
1. `claim*Processing` — sets `processing_at = now()` WHERE `processing_at IS NULL`
2. If claim succeeds, process the order
3. On success: steps are individually idempotent
4. On failure: `release*Processing` clears `processing_at` for retry on next tick

Three independent claim/release pairs exist: fulfillment, admin notification, and reconciliation.

### Callback Idempotency
`markPaidIfPending` only transitions from `PENDING_PAYMENT`. Duplicate callbacks are detected and return success (200 OK to ECPay) without side effects.

### Handoff Idempotency
`EscortDispatchHandoffService` checks `findBySourceIdentity` before creating a dispatch order. If one already exists for the purchase, it returns the existing order.

## Processing Pipeline (Scheduler)

**Post-payment worker** (every 10 seconds):
1. Claim fulfillment processing
2. Notify buyer of payment success
3. Record membership spend for escort-linked products (best-effort; enqueue retry on failure)
4. Auto-create escort order (if product configures it) + notify admins
5. Grant product reward (currency or tokens)
6. Mark order fulfilled
7. On any step failure: release claim for retry

**Reconciliation worker** (every 60 seconds):
1. Expire unpaid orders past their deadline
2. Query ECPay for pending orders without callbacks
3. If paid: mark as PAID (recovery for lost callbacks)
4. If not paid: schedule retry with exponential backoff (30s × attempt count, capped at 300s)

## ECPay Integration

- CVS (convenience store) payment code generation via ECPay API
- Embedded HTTP server for payment callbacks (8 worker threads)
- AES/CBC/PKCS5Padding encryption/decryption for request/response payloads
- SHA-256 CheckMacValue for trade query verification
- Trade number format: `FD{YYYYMMDDHHmmss}{3-digit sequence}` (synchronized counter)
- Stage mode blocks binding to public addresses

## Currency Purchase Flow

```
Product selection -> Balance check -> Currency deduction -> Reward grant -> Auto-refund on failure
```

Currency deduction is atomic (`balance + ? >= 0` guard). If reward grant fails after deduction, the deducted amount is automatically refunded via `BalanceAdjustmentService`.
