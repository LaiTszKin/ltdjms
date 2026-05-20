import type { FiatOrderRepository } from '../domain/fiat-order-repository.js';
import type { FiatOrder } from '../domain/fiat-order.js';
import pino from 'pino';
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
    }, escortDispatchHandoffService: {
        handoffFromFiatPayment(guildId: number, buyerUserId: number, fulfillmentProduct: any, orderNumber: string): Promise<{
            isOk: () => boolean;
            getError: () => {
                message: string;
            };
            getValue: () => any;
        }>;
    }, escortOrderBuyerNotificationService: {
        notifyEscortOrderCreated(dispatchOrder: any): void;
    }, adminNotificationService: {
        notifyAdminsOrderCreated(guildId: number, buyerUserId: number, dispatchOrder: any): void;
    }, productRewardService: {
        grantReward(request: any): Promise<{
            isErr: () => boolean;
            getError: () => {
                message: string;
            };
        }>;
    }, logger?: pino.Logger);
    processPendingOrders(): Promise<void>;
    processSingleOrder(order: FiatOrder): Promise<void>;
}
