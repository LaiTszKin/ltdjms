import type { NodePgDatabase } from 'drizzle-orm/node-postgres';
import { type Product } from '../domain/product-types.js';
/**
 * Drizzle-based product repository used by shop services.
 * Provides the ProductRepository interface expected by ShopService, RedemptionService, etc.
 */
export declare class DrizzleProductRepository {
    private readonly db;
    constructor(db: NodePgDatabase);
    findById(id: number): Promise<Product | null>;
    countByGuildId(guildId: number): Promise<number>;
    findByGuildIdPaginated(guildId: number, page: number, size: number): Promise<Product[]>;
    countByGuildIdAndNameContaining(guildId: number, keyword: string): Promise<number>;
    findByGuildIdAndNameContaining(guildId: number, keyword: string, page: number, size: number): Promise<Product[]>;
    private mapRow;
}
