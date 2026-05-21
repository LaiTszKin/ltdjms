import type { Result } from '@ltdjms/shared';
import { Ok, Err, DomainError } from '@ltdjms/shared';

import type { EscortDispatchOrderRepo } from '../repo/escort-dispatch-order.repo.js';
import { EscortDispatchOrderNumberGenerator, generateUniqueOrderNumber } from '../domain/order-number-generator.js';
import { type EscortDispatchOrder, SourceType, createAutoHandoff } from '../domain/index.js';

const MAX_ORDER_NUMBER_RETRIES = 20;

/**
 * Product snapshot used for handoff from shop purchases.
 * This provides a minimal type for the handoff service without depending on a full Product module.
 */
export interface HandoffProductSnapshot {
  readonly id: number;
  readonly name: string;
  readonly currencyPrice: number | null;
  readonly fiatPriceTwd: number | null;
  readonly escortOptionCode: string;
  readonly shouldAutoCreateEscortOrder: boolean;
}

/**
 * Durable handoff boundary for auto-created escort dispatch work items.
 * Matches Java EscortDispatchHandoffService exactly.
 */
export class EscortDispatchHandoffService {
  constructor(
    private readonly repository: EscortDispatchOrderRepo,
    private readonly orderNumberGenerator?: EscortDispatchOrderNumberGenerator,
  ) {
    this.orderNumberGenerator = orderNumberGenerator ?? new EscortDispatchOrderNumberGenerator();
  }

  /** Handoff from currency purchase (idempotent). */
  async handoffFromCurrencyPurchase(
    guildId: number,
    buyerUserId: number,
    product: HandoffProductSnapshot | null,
    sourceReference: string,
  ): Promise<Result<EscortDispatchOrder, DomainError>> {
    return this.handoff(guildId, buyerUserId, product, sourceReference, SourceType.CURRENCY_PURCHASE);
  }

  /** Handoff from fiat payment (idempotent). */
  async handoffFromFiatPayment(
    guildId: number,
    buyerUserId: number,
    product: HandoffProductSnapshot | null,
    sourceReference: string,
  ): Promise<Result<EscortDispatchOrder, DomainError>> {
    return this.handoff(guildId, buyerUserId, product, sourceReference, SourceType.FIAT_PAYMENT);
  }

  private async handoff(
    guildId: number,
    buyerUserId: number,
    product: HandoffProductSnapshot | null,
    sourceReference: string,
    sourceType: SourceType,
  ): Promise<Result<EscortDispatchOrder, DomainError>> {
    try {
      if (product == null) {
        return new Err(DomainError.invalidInput('找不到該商品'));
      }
      if (!product.shouldAutoCreateEscortOrder) {
        return new Err(DomainError.invalidInput('此商品尚未啟用自動護航開單'));
      }
      if (!sourceReference || sourceReference.trim().length === 0) {
        return new Err(DomainError.invalidInput('來源參考無效'));
      }
      // Defensive check: product.escortOptionCode should be validated by the caller,
      // but we verify here to prevent creating an order without a valid option code.
      if (!product.escortOptionCode || product.escortOptionCode.trim().length === 0) {
        return new Err(DomainError.invalidInput('護航選項代碼無效'));
      }

      // Defensive check: product.name must not be blank for auto-sourced orders.
      if (!product.name || product.name.trim().length === 0) {
        return new Err(DomainError.invalidInput('商品名稱為空白'));
      }

      // Idempotency check
      const existing = await this.repository.findBySourceIdentity(sourceType, sourceReference);
      if (existing != null) {
        return new Ok(existing);
      }

      const orderNumber = await this.generateUniqueOrderNumber();
      const orderResult = createAutoHandoff(
        orderNumber,
        guildId,
        0, // assignedByUserId=0 (auto)
        0, // escortUserId=0 (unassigned)
        buyerUserId,
        sourceType,
        sourceReference,
        product.id,
        product.name,
        product.currencyPrice,
        product.fiatPriceTwd,
        product.escortOptionCode,
      );
      if (orderResult.isErr()) {
        return orderResult;
      }

      const saved = await this.repository.save(orderResult.getValue());
      return new Ok(saved);
    } catch (e) {
      const err = e instanceof Error ? e : new Error(String(e));

      // Fallback: re-check for race condition (duplicate handoff from another transaction)
      try {
        const fallback = await this.repository.findBySourceIdentity(sourceType, sourceReference);
        if (fallback != null) {
          return new Ok(fallback);
        }
      } catch {
        // fallback query also failed
      }

      return new Err(DomainError.persistenceFailure('建立護航交接失敗', err));
    }
  }

  private async generateUniqueOrderNumber(): Promise<string> {
    return generateUniqueOrderNumber(
      this.orderNumberGenerator!,
      (orderNumber) => this.repository.existsByOrderNumber(orderNumber),
      MAX_ORDER_NUMBER_RETRIES,
    );
  }
}
