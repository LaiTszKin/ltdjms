import { Ok, Err, DomainError } from '@ltdjms/shared';
import { EscortDispatchOrderNumberGenerator } from '../domain/order-number-generator.js';
import { SourceType, createAutoHandoff } from '../domain/index.js';
const MAX_ORDER_NUMBER_RETRIES = 20;
/**
 * Durable handoff boundary for auto-created escort dispatch work items.
 * Matches Java EscortDispatchHandoffService exactly.
 */
export class EscortDispatchHandoffService {
    repository;
    orderNumberGenerator;
    constructor(repository, orderNumberGenerator) {
        this.repository = repository;
        this.orderNumberGenerator = orderNumberGenerator;
        this.orderNumberGenerator = orderNumberGenerator ?? new EscortDispatchOrderNumberGenerator();
    }
    /** Handoff from currency purchase (idempotent). */
    async handoffFromCurrencyPurchase(guildId, buyerUserId, product, sourceReference) {
        return this.handoff(guildId, buyerUserId, product, sourceReference, SourceType.CURRENCY_PURCHASE);
    }
    /** Handoff from fiat payment (idempotent). */
    async handoffFromFiatPayment(guildId, buyerUserId, product, sourceReference) {
        return this.handoff(guildId, buyerUserId, product, sourceReference, SourceType.FIAT_PAYMENT);
    }
    async handoff(guildId, buyerUserId, product, sourceReference, sourceType) {
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
            if (!product.escortOptionCode || product.escortOptionCode.trim().length === 0) {
                return new Err(DomainError.invalidInput('護航選項代碼無效'));
            }
            // Idempotency check
            const existing = await this.repository.findBySourceIdentity(sourceType, sourceReference);
            if (existing != null) {
                return new Ok(existing);
            }
            const orderNumber = await this.generateUniqueOrderNumber();
            const order = createAutoHandoff(orderNumber, guildId, 0, // assignedByUserId=0 (auto)
            0, // escortUserId=0 (unassigned)
            buyerUserId, sourceType, sourceReference, product.id, product.name, product.currencyPrice, product.fiatPriceTwd, product.escortOptionCode);
            const saved = await this.repository.save(order);
            return new Ok(saved);
        }
        catch (e) {
            const err = e instanceof Error ? e : new Error(String(e));
            // Fallback: re-check for race condition (duplicate handoff from another transaction)
            try {
                const fallback = await this.repository.findBySourceIdentity(sourceType, sourceReference);
                if (fallback != null) {
                    return new Ok(fallback);
                }
            }
            catch {
                // fallback query also failed
            }
            return new Err(DomainError.persistenceFailure('建立護航交接失敗', err));
        }
    }
    async generateUniqueOrderNumber() {
        const gen = this.orderNumberGenerator;
        for (let attempt = 1; attempt <= MAX_ORDER_NUMBER_RETRIES; attempt++) {
            const orderNumber = gen.generate();
            const exists = await this.repository.existsByOrderNumber(orderNumber);
            if (!exists) {
                return orderNumber;
            }
        }
        throw new Error('無法產生唯一的護航訂單編號');
    }
}
//# sourceMappingURL=escort-dispatch-handoff.service.js.map