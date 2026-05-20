import { Result, DomainError } from '@ltdjms/shared';
import type { RedemptionCodeRepository, CodeStats } from '../domain/redemption-code-repository.js';
import { type RedemptionCode } from '../domain/redemption-code.js';
import { type Product } from '../domain/product-types.js';
import { RedemptionCodeGenerator } from './redemption-code-generator.js';
import pino from 'pino';
export interface RedemptionResult {
    code: RedemptionCode;
    product: Product;
    rewardedAmount: number | null;
}
export declare function formatRedemptionSuccessMessage(result: RedemptionResult): string;
export interface CodePage {
    codes: RedemptionCode[];
    currentPage: number;
    totalPages: number;
    totalCount: number;
    pageSize: number;
}
export declare class RedemptionService {
    private readonly codeRepository;
    private readonly productRepository;
    private readonly codeGenerator;
    private readonly productRewardService;
    private readonly transactionService;
    private readonly eventPublisher;
    private readonly log;
    constructor(codeRepository: RedemptionCodeRepository, productRepository: {
        findById(productId: number): Promise<Product | null>;
    }, codeGenerator: RedemptionCodeGenerator, productRewardService: {
        grantReward(request: {
            guildId: number;
            userId: number;
            product: Product;
            amount: number;
            description: string;
        }): Promise<Result<{
            amount: number;
        }, DomainError>>;
    }, transactionService: {
        recordTransaction(guildId: number, userId: number, product: Product, code: RedemptionCode): Promise<any>;
    }, eventPublisher: {
        publish(event: any): void;
    }, logger?: pino.Logger);
    generateCodes(productId: number, count: number, expiresAt: Date | null, quantity?: number): Promise<Result<RedemptionCode[], DomainError>>;
    redeemCode(codeStr: string, guildId: number, userId: number): Promise<Result<RedemptionResult, DomainError>>;
    findByCode(codeStr: string): Promise<RedemptionCode | null>;
    getCodePage(productId: number, page: number, pageSize: number): Promise<CodePage>;
    getCodeStats(productId: number): Promise<CodeStats>;
    private generateUniqueCode;
    private rollbackRedeemedCodeAfterRewardFailure;
    private calculateTotalRewardAmount;
}
