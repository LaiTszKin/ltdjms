import type { Result } from '@ltdjms/shared';
import { DomainError } from '@ltdjms/shared';
import type { EscortDispatchOrderRepo } from '../repo/escort-dispatch-order.repo.js';
import { EscortDispatchOrderNumberGenerator } from '../domain/order-number-generator.js';
import { type EscortDispatchOrder } from '../domain/index.js';
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
export declare class EscortDispatchHandoffService {
    private readonly repository;
    private readonly orderNumberGenerator?;
    constructor(repository: EscortDispatchOrderRepo, orderNumberGenerator?: EscortDispatchOrderNumberGenerator | undefined);
    /** Handoff from currency purchase (idempotent). */
    handoffFromCurrencyPurchase(guildId: number, buyerUserId: number, product: HandoffProductSnapshot | null, sourceReference: string): Promise<Result<EscortDispatchOrder, DomainError>>;
    /** Handoff from fiat payment (idempotent). */
    handoffFromFiatPayment(guildId: number, buyerUserId: number, product: HandoffProductSnapshot | null, sourceReference: string): Promise<Result<EscortDispatchOrder, DomainError>>;
    private handoff;
    private generateUniqueOrderNumber;
}
