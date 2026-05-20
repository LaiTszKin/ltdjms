import type { FiatOrderRepository } from '../domain/fiat-order-repository.js';
import { EcpayTradeQueryService } from './ecpay-trade-query.service.js';
import pino from 'pino';
export declare class FiatPaymentReconciliationService {
    private readonly fiatOrderRepository;
    private readonly ecpayTradeQueryService;
    private readonly log;
    constructor(fiatOrderRepository: FiatOrderRepository, ecpayTradeQueryService: EcpayTradeQueryService, logger?: pino.Logger);
    reconcilePendingOrders(): Promise<void>;
    private expirePendingOrders;
    private reconcileSingleOrder;
    private scheduleRetry;
    private buildSyntheticPayload;
}
