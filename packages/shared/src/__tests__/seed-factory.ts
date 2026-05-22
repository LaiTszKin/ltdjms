import type { NodePgDatabase } from 'drizzle-orm/node-postgres';
import { sql } from 'drizzle-orm';

// ============================================================
// Guild seed
// ============================================================

export interface GuildSeed {
  guildId: number;
  currencyName: string;
  currencyIcon: string;
}

const DEFAULT_GUILD: GuildSeed = {
  guildId: 1,
  currencyName: 'Coins',
  currencyIcon: '\u{1FA99}',
};

export async function seedGuild(
  db: NodePgDatabase,
  overrides?: Partial<GuildSeed>,
): Promise<GuildSeed> {
  const data = { ...DEFAULT_GUILD, ...overrides };
  await db.execute(sql`
    INSERT INTO guild_currency_config (guild_id, currency_name, currency_icon)
    VALUES (${data.guildId}, ${data.currencyName}, ${data.currencyIcon})
    ON CONFLICT (guild_id) DO UPDATE SET
      currency_name = EXCLUDED.currency_name,
      currency_icon = EXCLUDED.currency_icon
  `);
  return data;
}

// ============================================================
// User account seed
// ============================================================

export interface UserAccountSeed {
  guildId: number;
  userId: number;
  balance: number;
  tokenBalance: number;
}

const DEFAULT_USER_ACCOUNT: UserAccountSeed = {
  guildId: 1,
  userId: 100,
  balance: 10000,
  tokenBalance: 100,
};

export async function seedUserAccount(
  db: NodePgDatabase,
  overrides?: Partial<UserAccountSeed>,
): Promise<UserAccountSeed> {
  const data = { ...DEFAULT_USER_ACCOUNT, ...overrides };
  await db.execute(sql`
    INSERT INTO member_currency_account (guild_id, user_id, balance)
    VALUES (${data.guildId}, ${data.userId}, ${data.balance})
    ON CONFLICT (guild_id, user_id) DO UPDATE SET
      balance = EXCLUDED.balance
  `);
  await db.execute(sql`
    INSERT INTO game_token_account (guild_id, user_id, tokens)
    VALUES (${data.guildId}, ${data.userId}, ${data.tokenBalance})
    ON CONFLICT (guild_id, user_id) DO UPDATE SET
      tokens = EXCLUDED.tokens
  `);
  return data;
}

// ============================================================
// Product seed
// ============================================================

export interface ProductSeed {
  guildId: number;
  name: string;
  description: string | null;
  rewardType: string | null;
  rewardAmount: number | null;
  currencyPrice: number | null;
  fiatPriceTwd: number | null;
  autoCreateEscortOrder: boolean;
  escortOptionCode: string | null;
}

const DEFAULT_PRODUCT: ProductSeed = {
  guildId: 1,
  name: 'Test Product',
  description: null,
  rewardType: 'CURRENCY',
  rewardAmount: 1000,
  currencyPrice: 500,
  fiatPriceTwd: null,
  autoCreateEscortOrder: false,
  escortOptionCode: null,
};

export async function seedProduct(
  db: NodePgDatabase,
  overrides?: Partial<ProductSeed>,
): Promise<Required<{ id: number }> & ProductSeed> {
  const data = { ...DEFAULT_PRODUCT, ...overrides };
  const result = await db.execute(sql`
    INSERT INTO product (guild_id, name, description, reward_type, reward_amount, currency_price, fiat_price_twd, auto_create_escort_order, escort_option_code)
    VALUES (${data.guildId}, ${data.name}, ${data.description}, ${data.rewardType}, ${data.rewardAmount}, ${data.currencyPrice}, ${data.fiatPriceTwd}, ${data.autoCreateEscortOrder}, ${data.escortOptionCode})
    RETURNING id
  `);
  const id = Number(result.rows?.[0]?.id ?? 0);
  return { ...data, id };
}

// ============================================================
// Redemption code seed
// ============================================================

export interface RedemptionCodeSeed {
  guildId: number;
  productId: number | null;
  code: string;
  quantity: number;
  expiresAt: Date | null;
}

const DEFAULT_REDEMPTION_CODE: RedemptionCodeSeed = {
  guildId: 1,
  productId: null,
  code: 'TEST-CODE-001',
  quantity: 1,
  expiresAt: null,
};

export async function seedRedemptionCode(
  db: NodePgDatabase,
  overrides?: Partial<RedemptionCodeSeed>,
): Promise<Required<{ id: number }> & RedemptionCodeSeed> {
  const data = { ...DEFAULT_REDEMPTION_CODE, ...overrides };
  const result = await db.execute(sql`
    INSERT INTO redemption_code (guild_id, product_id, code, quantity, expires_at)
    VALUES (${data.guildId}, ${data.productId}, ${data.code}, ${data.quantity}, ${data.expiresAt})
    RETURNING id
  `);
  const id = Number(result.rows?.[0]?.id ?? 0);
  return { ...data, id };
}

// ============================================================
// Dice game config seeds
// ============================================================

export interface DiceGame1ConfigSeed {
  guildId: number;
  minTokensPerPlay: number;
  maxTokensPerPlay: number;
  rewardPerDiceValue: number;
}

const DEFAULT_DICE_GAME1: DiceGame1ConfigSeed = {
  guildId: 1,
  minTokensPerPlay: 1,
  maxTokensPerPlay: 10,
  rewardPerDiceValue: 250000,
};

export async function seedDiceGame1Config(
  db: NodePgDatabase,
  overrides?: Partial<DiceGame1ConfigSeed>,
): Promise<DiceGame1ConfigSeed> {
  const data = { ...DEFAULT_DICE_GAME1, ...overrides };
  await db.execute(sql`
    INSERT INTO dice_game1_config (guild_id, min_tokens_per_play, max_tokens_per_play, reward_per_dice_value)
    VALUES (${data.guildId}, ${data.minTokensPerPlay}, ${data.maxTokensPerPlay}, ${data.rewardPerDiceValue})
    ON CONFLICT (guild_id) DO UPDATE SET
      min_tokens_per_play = EXCLUDED.min_tokens_per_play,
      max_tokens_per_play = EXCLUDED.max_tokens_per_play,
      reward_per_dice_value = EXCLUDED.reward_per_dice_value
  `);
  return data;
}

export interface DiceGame2ConfigSeed {
  guildId: number;
  minTokensPerPlay: number;
  maxTokensPerPlay: number;
  straightMultiplier: number;
  baseMultiplier: number;
  tripleLowBonus: number;
  tripleHighBonus: number;
}

const DEFAULT_DICE_GAME2: DiceGame2ConfigSeed = {
  guildId: 1,
  minTokensPerPlay: 5,
  maxTokensPerPlay: 50,
  straightMultiplier: 100000,
  baseMultiplier: 20000,
  tripleLowBonus: 1500000,
  tripleHighBonus: 2500000,
};

export async function seedDiceGame2Config(
  db: NodePgDatabase,
  overrides?: Partial<DiceGame2ConfigSeed>,
): Promise<DiceGame2ConfigSeed> {
  const data = { ...DEFAULT_DICE_GAME2, ...overrides };
  await db.execute(sql`
    INSERT INTO dice_game2_config (guild_id, min_tokens_per_play, max_tokens_per_play, straight_multiplier, base_multiplier, triple_low_bonus, triple_high_bonus)
    VALUES (${data.guildId}, ${data.minTokensPerPlay}, ${data.maxTokensPerPlay}, ${data.straightMultiplier}, ${data.baseMultiplier}, ${data.tripleLowBonus}, ${data.tripleHighBonus})
    ON CONFLICT (guild_id) DO UPDATE SET
      min_tokens_per_play = EXCLUDED.min_tokens_per_play,
      max_tokens_per_play = EXCLUDED.max_tokens_per_play,
      straight_multiplier = EXCLUDED.straight_multiplier,
      base_multiplier = EXCLUDED.base_multiplier,
      triple_low_bonus = EXCLUDED.triple_low_bonus,
      triple_high_bonus = EXCLUDED.triple_high_bonus
  `);
  return data;
}

// ============================================================
// Fiat order seed
// ============================================================

export interface FiatOrderSeed {
  guildId: number;
  buyerUserId: number;
  productId: number;
  productName: string;
  orderNumber: string;
  paymentNo: string;
  amountTwd: number;
  status: string;
  expireAt: Date;
}

const DEFAULT_FIAT_ORDER: FiatOrderSeed = {
  guildId: 1,
  buyerUserId: 100,
  productId: 1,
  productName: 'Test Product',
  orderNumber: 'TEST-ORDER-001',
  paymentNo: 'TEST-PAY-001',
  amountTwd: 1000,
  status: 'PENDING_PAYMENT',
  expireAt: new Date(Date.now() + 86400000),
};

export async function seedFiatOrder(
  db: NodePgDatabase,
  overrides?: Partial<FiatOrderSeed>,
): Promise<Required<{ id: number }> & FiatOrderSeed> {
  const data = { ...DEFAULT_FIAT_ORDER, ...overrides };
  const result = await db.execute(sql`
    INSERT INTO fiat_order (guild_id, buyer_user_id, product_id, product_name, order_number, payment_no, amount_twd, status, expire_at)
    VALUES (${data.guildId}, ${data.buyerUserId}, ${data.productId}, ${data.productName}, ${data.orderNumber}, ${data.paymentNo}, ${data.amountTwd}, ${data.status}, ${data.expireAt})
    RETURNING id
  `);
  const id = Number(result.rows?.[0]?.id ?? 0);
  return { ...data, id };
}
