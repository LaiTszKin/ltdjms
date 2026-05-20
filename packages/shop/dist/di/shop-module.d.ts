import type { NodePgDatabase } from 'drizzle-orm/node-postgres';
import type pino from 'pino';
/** Tokens for shop module dependencies. */
export declare const SHOP_TOKENS: {
    FiatOrderRepository: symbol;
    RedemptionCodeRepository: symbol;
    EcpayCvsPaymentService: symbol;
    EcpayTradeQueryService: symbol;
    FiatPaymentCallbackService: symbol;
    FiatOrderService: symbol;
    FiatOrderPostPaymentWorker: symbol;
    FiatPaymentReconciliationService: symbol;
    FiatOrderProcessingScheduler: symbol;
    CurrencyPurchaseService: symbol;
    ShopService: symbol;
    RedemptionCodeGenerator: symbol;
    RedemptionService: symbol;
    FiatOrderBuyerNotificationService: symbol;
    EscortOrderBuyerNotificationService: symbol;
    ShopAdminNotificationService: symbol;
    EcpayCallbackHttpServer: symbol;
};
export declare function configureContainer(options: {
    db: NodePgDatabase;
    productRepository: any;
    productRewardService: any;
    escortDispatchHandoffService: any;
    balanceService: any;
    balanceAdjustmentService: any;
    currencyTransactionService: any;
    redemptionTransactionService: any;
    logger?: pino.Logger;
}): void;
