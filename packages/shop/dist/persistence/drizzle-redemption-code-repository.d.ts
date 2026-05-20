import type { NodePgDatabase } from 'drizzle-orm/node-postgres';
import { type RedemptionCodeRepository, type CodeStats } from '../domain/redemption-code-repository.js';
import { type RedemptionCode } from '../domain/redemption-code.js';
import pino from 'pino';
export declare class DrizzleRedemptionCodeRepository implements RedemptionCodeRepository {
    private readonly db;
    private readonly log;
    constructor(db: NodePgDatabase, logger?: pino.Logger);
    save(code: RedemptionCode): Promise<RedemptionCode>;
    saveAll(codes: RedemptionCode[]): Promise<RedemptionCode[]>;
    update(code: RedemptionCode): Promise<RedemptionCode>;
    markAsRedeemedIfAvailable(codeId: number, userId: number, redeemedAt: Date): Promise<boolean>;
    clearRedeemedIfMatches(codeId: number, userId: number, redeemedAt: Date): Promise<boolean>;
    findByCode(code: string): Promise<RedemptionCode | null>;
    findById(id: number): Promise<RedemptionCode | null>;
    existsByCode(code: string): Promise<boolean>;
    findByProductId(productId: number, limit: number, offset: number): Promise<RedemptionCode[]>;
    countByProductId(productId: number): Promise<number>;
    countRedeemedByProductId(productId: number): Promise<number>;
    countUnusedByProductId(productId: number): Promise<number>;
    deleteUnusedByProductId(productId: number): Promise<number>;
    getStatsByProductId(productId: number): Promise<CodeStats>;
    invalidateByProductId(productId: number): Promise<number>;
    findInvalidatedByProductId(productId: number): Promise<RedemptionCode[]>;
}
