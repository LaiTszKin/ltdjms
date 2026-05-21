import { type RedemptionCode } from './redemption-code.js';

/** Statistics about a product's redemption codes. */
export interface CodeStats {
  totalCount: number;
  redeemedCount: number;
  unusedCount: number;
  expiredCount: number;
}

export function createCodeStatsZero(): CodeStats {
  return { totalCount: 0, redeemedCount: 0, unusedCount: 0, expiredCount: 0 };
}

/**
 * Repository interface for redemption code persistence.
 * Matches Java RedemptionCodeRepository.
 */
export interface RedemptionCodeRepository {
  save(code: RedemptionCode): Promise<RedemptionCode>;

  saveAll(codes: RedemptionCode[]): Promise<RedemptionCode[]>;

  // ADMIN: used by admin panel
  update(code: RedemptionCode): Promise<RedemptionCode>;

  markAsRedeemedIfAvailable(
    codeId: number,
    userId: string,
    redeemedAt: Date,
  ): Promise<boolean>;

  clearRedeemedIfMatches(
    codeId: number,
    userId: string,
    redeemedAt: Date,
  ): Promise<boolean>;

  findByCode(code: string): Promise<RedemptionCode | null>;

  // ADMIN: used by admin panel
  findById(id: number): Promise<RedemptionCode | null>;

  existsByCode(code: string): Promise<boolean>;

  findByProductId(productId: number, limit: number, offset: number): Promise<RedemptionCode[]>;

  countByProductId(productId: number): Promise<number>;

  // ADMIN: used by admin panel
  countRedeemedByProductId(productId: number): Promise<number>;

  // ADMIN: used by admin panel
  countUnusedByProductId(productId: number): Promise<number>;

  // ADMIN: used by admin panel
  deleteUnusedByProductId(productId: number): Promise<number>;

  getStatsByProductId(productId: number): Promise<CodeStats>;

  // ADMIN: used by admin panel
  invalidateByProductId(productId: number): Promise<number>;

  // ADMIN: used by admin panel
  findInvalidatedByProductId(productId: number): Promise<RedemptionCode[]>;
}
