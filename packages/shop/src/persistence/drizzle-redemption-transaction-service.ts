import type { NodePgDatabase } from 'drizzle-orm/node-postgres';
import { productRedemptionTransaction as txTable } from './schema.js';
import type { RedemptionTransactionService } from '../di/shop-module.js';
import type { Product } from '../domain/product-types.js';

/**
 * Records product redemption transactions in the database.
 * Implements the RedemptionTransactionService interface expected by RedemptionService.
 */
export class DrizzleRedemptionTransactionService implements RedemptionTransactionService {
  constructor(private readonly db: NodePgDatabase) {}

  async recordTransaction(
    guildId: number,
    userId: number,
    product: Product,
    code: { code: string; id?: number | null },
  ): Promise<unknown> {
    return await this.db.insert(txTable).values({
      guildId: guildId,
      userId: userId,
      productId: product.id as number,
      productName: product.name,
      redemptionCodeId: code.id ?? 0,
      code: code.code,
    });
  }
}
