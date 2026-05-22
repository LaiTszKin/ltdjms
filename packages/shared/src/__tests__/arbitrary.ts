import * as fc from 'fast-check';

// ============================================================
// ID generators
//
// Discord snowflakes are 64-bit integers but the DB schema uses
// Drizzle bigint({ mode: 'number' }), so we generate numbers
// within safe JS integer range (< 2^53) to avoid precision loss.
// ============================================================

export const guildId = (): fc.Arbitrary<number> =>
  fc.integer({ min: 1, max: 2147483647 });

export const userId = (): fc.Arbitrary<number> =>
  fc.integer({ min: 1, max: 2147483647 });

// ============================================================
// Amount generators
// ============================================================

export const positiveAmount = (min = 1, max = 100000): fc.Arbitrary<number> =>
  fc.integer({ min, max });

export const betAmount = (): fc.Arbitrary<number> =>
  fc.integer({ min: 1, max: 1000000 });

export const multiplier = (): fc.Arbitrary<number> =>
  fc.constantFrom(0.1, 0.5, 1.0, 1.5, 2.0, 3.0, 5.0, 10.0);

// ============================================================
// String generators
// ============================================================

export const currencyName = (): fc.Arbitrary<string> =>
  fc.string({ minLength: 1, maxLength: 20 });

export const currencyIcon = (): fc.Arbitrary<string> =>
  fc.constantFrom('\u{1FA99}', '💰', '💎', '⭐', '🏆');

export const productName = (): fc.Arbitrary<string> =>
  fc.string({ minLength: 1, maxLength: 50 });

export const redemptionCode = (): fc.Arbitrary<string> =>
  fc.string({ minLength: 8, maxLength: 32 }).map(
    (s) => s.replace(/[^A-Z0-9]/gi, 'X').toUpperCase().slice(0, 32) || 'DEFAULT-CODE',
  );

// ============================================================
// Composite arbitraries
// ============================================================

export interface TransferRequest {
  guildId: number;
  senderId: number;
  receiverId: number;
  amount: number;
}

export const transferRequest = (): fc.Arbitrary<TransferRequest> =>
  fc.record({
    guildId: guildId(),
    senderId: userId(),
    receiverId: userId(),
    amount: positiveAmount(),
  });

export interface BalanceAdjustment {
  guildId: number;
  userId: number;
  amount: number;
}

export const balanceAdjustment = (): fc.Arbitrary<BalanceAdjustment> =>
  fc.record({
    guildId: guildId(),
    userId: userId(),
    amount: fc.integer({ min: -10000, max: 10000 }),
  });

export interface ProductPurchase {
  guildId: number;
  userId: number;
  productId: number;
  quantity: number;
}

export const productPurchase = (): fc.Arbitrary<ProductPurchase> =>
  fc.record({
    guildId: guildId(),
    userId: userId(),
    productId: fc.integer({ min: 1, max: 1000 }),
    quantity: fc.integer({ min: 1, max: 10 }),
  });

export interface DiceGamePlay {
  guildId: number;
  userId: number;
  betAmount: number;
}

export const diceGamePlay = (): fc.Arbitrary<DiceGamePlay> =>
  fc.record({
    guildId: guildId(),
    userId: userId(),
    betAmount: betAmount(),
  });

export interface OrderSeed {
  guildId: number;
  buyerUserId: number;
  amountTwd: number;
}

export const orderSeed = (): fc.Arbitrary<OrderSeed> =>
  fc.record({
    guildId: guildId(),
    buyerUserId: userId(),
    amountTwd: positiveAmount(1, 50000),
  });
