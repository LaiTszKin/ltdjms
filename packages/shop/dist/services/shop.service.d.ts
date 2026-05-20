import { type Product } from '../domain/product-types.js';
import pino from 'pino';
export interface ShopPage {
    products: Product[];
    currentPage: number;
    totalPages: number;
}
export declare const ShopPageHelper: {
    isEmpty(page: ShopPage): boolean;
    hasPreviousPage(page: ShopPage): boolean;
    hasNextPage(page: ShopPage): boolean;
    formatPageIndicator(page: ShopPage): string;
};
export declare const PAGE_SIZE = 5;
export declare class ShopService {
    private readonly productRepository;
    private readonly log;
    constructor(productRepository: {
        countByGuildId(guildId: number): Promise<number>;
        findByGuildIdPaginated(guildId: number, page: number, size: number): Promise<Product[]>;
        countByGuildIdAndNameContaining(guildId: number, keyword: string): Promise<number>;
        findByGuildIdAndNameContaining(guildId: number, keyword: string, page: number, size: number): Promise<Product[]>;
    }, logger?: pino.Logger);
    getShopPage(guildId: number, page: number): Promise<ShopPage>;
    searchProducts(guildId: number, keyword: string, page: number): Promise<ShopPage>;
    getProductCount(guildId: number): Promise<number>;
    hasProducts(guildId: number): Promise<boolean>;
}
