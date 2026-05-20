import { Result, DomainError } from '@ltdjms/shared';
import { type Product } from '../domain/product-types.js';
import pino from 'pino';
export interface PurchaseResult {
    product: Product;
    previousBalance: number;
    newBalance: number;
    price: number;
    rewardMessage: string;
}
export declare function formatPurchaseSuccessMessage(result: PurchaseResult): string;
export declare class CurrencyPurchaseService {
    private readonly productService;
    private readonly balanceService;
    private readonly balanceAdjustmentService;
    private readonly transactionService;
    private readonly productRewardService;
    private readonly log;
    constructor(productService: {
        findById(productId: number): Promise<Product | null>;
    }, balanceService: {
        tryGetBalance(guildId: number, userId: number): Promise<Result<{
            balance: number;
        }, DomainError>>;
    }, balanceAdjustmentService: {
        tryAdjustBalance(guildId: number, userId: number, amount: number): Promise<Result<{
            newBalance: number;
        }, DomainError>>;
    }, transactionService: {
        recordTransaction(guildId: number, userId: number, amount: number, balance: number, source: string, description: string): Promise<void>;
    }, productRewardService: {
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
    }, logger?: pino.Logger);
    purchaseProduct(guildId: number, userId: number, productId: number): Promise<Result<PurchaseResult, DomainError>>;
    private refundPurchaseAfterRewardFailure;
}
