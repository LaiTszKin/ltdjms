/**
 * Product domain type matching Java Product record.
 * This is a minimal type used internally by the shop package.
 * The full Product CRUD belongs to the administration domain.
 */
export declare enum RewardType {
    CURRENCY = "CURRENCY",
    TOKEN = "TOKEN"
}
export interface Product {
    id: number | null;
    guildId: number;
    name: string;
    description: string | null;
    rewardType: RewardType | null;
    rewardAmount: number | null;
    currencyPrice: number | null;
    fiatPriceTwd: number | null;
    autoCreateEscortOrder: boolean;
    escortOptionCode: string | null;
    createdAt: Date;
    updatedAt: Date;
}
export declare function hasReward(product: Product): boolean;
export declare function formatReward(product: Product): string | null;
export declare function hasCurrencyPrice(product: Product): boolean;
export declare function formatCurrencyPrice(product: Product): string | null;
export declare function hasFiatPriceTwd(product: Product): boolean;
export declare function formatFiatPriceTwd(product: Product): string | null;
export declare function isFiatOnly(product: Product): boolean;
export declare function shouldAutoCreateEscortOrder(product: Product): boolean;
/**
 * Creates a Product instance. Note that full Product CRUD belongs to the
 * administration module and should be moved there when the admin package is
 * established. This factory is provided here for internal shop domain use (P2-20).
 */
export declare function createProduct(guildId: number, name: string, description: string | null, rewardType: RewardType | null, rewardAmount: number | null, currencyPrice: number | null, fiatPriceTwd: number | null, autoCreateEscortOrder?: boolean, escortOptionCode?: string | null): Product;
