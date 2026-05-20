import { Result, DomainError } from '@ltdjms/shared';
import type { FiatOrderRepository } from '../domain/fiat-order-repository.js';
import { EcpayCvsPaymentService } from './ecpay-cvs-payment.service.js';
import { type Product } from '../domain/product-types.js';
import pino from 'pino';
export interface FiatOrderResult {
    product: Product;
    orderNumber: string;
    paymentNo: string;
    expireDate: string | null;
    paymentUrl: string | null;
    fulfillmentWarning: string | null;
}
export declare function formatFiatOrderDMMessage(result: FiatOrderResult): string;
export declare class FiatOrderService {
    private readonly productService;
    private readonly ecpayCvsPaymentService;
    private readonly fiatOrderRepository;
    private readonly log;
    constructor(productService: {
        getProduct(productId: number): Promise<Product | null>;
    }, ecpayCvsPaymentService: EcpayCvsPaymentService, fiatOrderRepository: FiatOrderRepository, logger?: pino.Logger);
    createFiatOnlyOrder(guildId: number, userId: number, productId: number, tradeDesc?: string): Promise<Result<FiatOrderResult, DomainError>>;
}
