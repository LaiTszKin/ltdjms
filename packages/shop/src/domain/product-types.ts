/**
 * Product domain type matching Java Product record.
 * This is a minimal type used internally by the shop package.
 * The full Product CRUD belongs to the administration domain.
 */

export enum RewardType {
  CURRENCY = 'CURRENCY',
  TOKEN = 'TOKEN',
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

export function hasReward(product: Product): boolean {
  return product.rewardType !== null && product.rewardAmount !== null;
}

export function formatReward(product: Product): string | null {
  if (!hasReward(product)) return null;
  switch (product.rewardType) {
    case RewardType.CURRENCY:
      return `${product.rewardAmount!.toLocaleString()} 货币`;
    case RewardType.TOKEN:
      return `${product.rewardAmount!.toLocaleString()} 代币`;
    default:
      return null;
  }
}

export function hasCurrencyPrice(product: Product): boolean {
  return product.currencyPrice !== null && product.currencyPrice > 0;
}

export function formatCurrencyPrice(product: Product): string | null {
  if (!hasCurrencyPrice(product)) return null;
  return `${product.currencyPrice!.toLocaleString()} 货币`;
}

export function hasFiatPriceTwd(product: Product): boolean {
  return product.fiatPriceTwd !== null && product.fiatPriceTwd > 0;
}

export function formatFiatPriceTwd(product: Product): string | null {
  if (!hasFiatPriceTwd(product)) return null;
  return `NT$${product.fiatPriceTwd!.toLocaleString()}`;
}

export function isFiatOnly(product: Product): boolean {
  return hasFiatPriceTwd(product) && !hasCurrencyPrice(product);
}

export function shouldAutoCreateEscortOrder(product: Product): boolean {
  return product.autoCreateEscortOrder
    && product.escortOptionCode !== null
    && product.escortOptionCode.trim().length > 0;
}

/**
 * Product repository interface as used by shop services.
 * Defined in the domain layer so that persistence and DI modules
 * depend on domain, not the other way around.
 */
export interface ProductRepository {
  findById(id: number): Promise<Product | null>;
  countByGuildId(guildId: number): Promise<number>;
  findByGuildIdPaginated(guildId: number, page: number, size: number): Promise<Product[]>;
  countByGuildIdAndNameContaining(guildId: number, keyword: string): Promise<number>;
  findByGuildIdAndNameContaining(guildId: number, keyword: string, page: number, size: number): Promise<Product[]>;
  create(data: Omit<Product, 'id' | 'createdAt' | 'updatedAt'>): Promise<Product>;
  update(id: number, data: Partial<Omit<Product, 'id' | 'createdAt' | 'updatedAt'>>): Promise<Product | null>;
  delete(id: number): Promise<boolean>;
}

/**
 * Creates a Product instance. Note that full Product CRUD belongs to the
 * administration module and should be moved there when the admin package is
 * established. This factory is provided here for internal shop domain use (P2-20).
 */
export function createProduct(
  guildId: number,
  name: string,
  description: string | null,
  rewardType: RewardType | null,
  rewardAmount: number | null,
  currencyPrice: number | null,
  fiatPriceTwd: number | null,
  autoCreateEscortOrder = false,
  escortOptionCode: string | null = null,
): Product {
  const now = new Date();
  return {
    id: null,
    guildId,
    name,
    description,
    rewardType,
    rewardAmount,
    currencyPrice,
    fiatPriceTwd,
    autoCreateEscortOrder,
    escortOptionCode,
    createdAt: now,
    updatedAt: now,
  };
}
