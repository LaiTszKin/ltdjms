import type { Result, DomainError } from '@ltdjms/shared';
import type { NodePgDatabase } from 'drizzle-orm/node-postgres';
import type pino from 'pino';
import type { Product } from '../domain/product-types.js';
import { type EscortDispatchHandoffService } from '../services/fiat-order-post-payment-worker.js';
/** Product repository interface as used by shop services. */
export interface ProductRepository {
    findById(id: number): Promise<Product | null>;
    countByGuildId(guildId: number): Promise<number>;
    findByGuildIdPaginated(guildId: number, page: number, size: number): Promise<Product[]>;
    countByGuildIdAndNameContaining(guildId: number, keyword: string): Promise<number>;
    findByGuildIdAndNameContaining(guildId: number, keyword: string, page: number, size: number): Promise<Product[]>;
}
/** Product reward service interface as used by shop services. */
export interface ProductRewardService {
    grantReward(request: {
        guildId: number;
        userId: number;
        product: Product;
        amount: number;
        description: string;
    }): Promise<Result<{
        amount: number;
        currencyBalanceAfter: number | null;
        formatReward(product: Product): string;
    }, DomainError>>;
}
/** Balance service interface as used by shop services. */
export interface BalanceService {
    tryGetBalance(guildId: number, userId: number): Promise<Result<{
        balance: number;
    }, DomainError>>;
}
/** Balance adjustment service interface as used by shop services. */
export interface BalanceAdjustmentService {
    tryAdjustBalance(guildId: number, userId: number, amount: number): Promise<Result<{
        newBalance: number;
    }, DomainError>>;
}
/** Currency transaction service interface as used by shop services. */
export interface CurrencyTransactionService {
    recordTransaction(guildId: number, userId: number, amount: number, balance: number, source: string, description: string): Promise<void>;
}
/** Redemption transaction service interface as used by shop services. */
export interface RedemptionTransactionService {
    recordTransaction(guildId: number, userId: number, product: Product, code: {
        code: string;
    }): Promise<unknown>;
}
/** Configuration options for the shop module container. */
export interface ShopModuleOptions {
    db: NodePgDatabase;
    productRepository: ProductRepository;
    productRewardService: ProductRewardService;
    escortDispatchHandoffService: EscortDispatchHandoffService;
    balanceService: BalanceService;
    balanceAdjustmentService: BalanceAdjustmentService;
    currencyTransactionService: CurrencyTransactionService;
    redemptionTransactionService: RedemptionTransactionService;
    logger?: pino.Logger;
}
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
    ShopCommandHandler: symbol;
    RedemptionCodeGenerator: symbol;
    RedemptionService: symbol;
    FiatOrderBuyerNotificationService: symbol;
    EscortOrderBuyerNotificationService: symbol;
    ShopAdminNotificationService: symbol;
    EcpayCallbackHttpServer: symbol;
};
export declare function configureContainer(options: ShopModuleOptions): void;
