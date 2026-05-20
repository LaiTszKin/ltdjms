import type { FiatOrderRepository } from '../domain/fiat-order-repository.js';
import type { FiatOrder } from '../domain/fiat-order.js';
import type { Product } from '../domain/product-types.js';
import pino from 'pino';
/** Snapshot of a dispatch order used for notification callbacks. */
export interface DispatchOrderSnapshot {
    guildId: number;
    customerUserId: number;
    orderNumber: string;
    sourceProductName?: string | null;
    sourceType?: string | null;
    sourceEscortOptionCode?: string | null;
    sourceCurrencyPrice?: number | null;
    sourceFiatPriceTwd?: number | null;
    sourceReference?: string | null;
}
/** Service interface for auto-creating escort orders from fiat payments. */
export interface EscortDispatchHandoffService {
    handoffFromFiatPayment(guildId: number, buyerUserId: number, product: Product | null, sourceReference: string): Promise<{
        isOk: () => boolean;
        getError: () => {
            message: string;
        };
        getValue: () => DispatchOrderSnapshot;
    }>;
}
/**
 * Service interface for notifying buyers of escort order creation.
 * The parameter is `any` because the notification services accept domain objects
 * whose declared types use incompatible parameter counts across packages.
 */
export type EscortOrderBuyerNotifier = {
    notifyEscortOrderCreated(dispatchOrder: any): void;
};
/**
 * Service interface for notifying admins of new orders.
 * See EscortOrderBuyerNotifier for why dispatchOrder is `any`.
 */
export type AdminOrderNotifier = {
    notifyAdminsOrderCreated(guildId: number, buyerUserId: number, dispatchOrder: any): void;
};
/** Reward grant request shape. */
export interface GrantRewardRequest {
    guildId: number;
    userId: number;
    product: Product;
    amount: number;
    description: string;
}
/** Service interface for granting product rewards. */
export interface ProductRewardGranter {
    grantReward(request: GrantRewardRequest): Promise<{
        isErr: () => boolean;
        getError: () => {
            message: string;
        };
    }>;
}
export declare class FiatOrderPostPaymentWorker {
    private readonly fiatOrderRepository;
    private readonly buyerNotificationService;
    private readonly escortDispatchHandoffService;
    private readonly escortOrderBuyerNotificationService;
    private readonly adminNotificationService;
    private readonly productRewardService;
    private readonly log;
    constructor(fiatOrderRepository: FiatOrderRepository, buyerNotificationService: {
        notifyPaymentSucceeded(order: FiatOrder): void;
    }, escortDispatchHandoffService: EscortDispatchHandoffService, escortOrderBuyerNotificationService: EscortOrderBuyerNotifier, adminNotificationService: AdminOrderNotifier, productRewardService: ProductRewardGranter, logger?: pino.Logger);
    processPendingOrders(): Promise<void>;
    processSingleOrder(order: FiatOrder): Promise<void>;
}
