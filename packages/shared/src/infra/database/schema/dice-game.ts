import { pgTable, bigint, timestamp } from 'drizzle-orm/pg-core';

/**
 * Dice Game 1 configuration per guild.
 * Maps to: dice_game1_config
 * Note: default_tokens_per_play was removed by migration V003.
 */
export const diceGame1Config = pgTable('dice_game1_config', {
  guildId: bigint('guild_id', { mode: 'bigint' }).primaryKey(),
  minTokensPerPlay: bigint('min_tokens_per_play', { mode: 'bigint' }).notNull().default(1n),
  maxTokensPerPlay: bigint('max_tokens_per_play', { mode: 'bigint' }).notNull().default(10n),
  rewardPerDiceValue: bigint('reward_per_dice_value', { mode: 'bigint' }).notNull().default(250000n),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
});

/**
 * Dice Game 2 configuration per guild.
 * Maps to: dice_game2_config
 * Note: default_tokens_per_play was removed by migration V003.
 */
export const diceGame2Config = pgTable('dice_game2_config', {
  guildId: bigint('guild_id', { mode: 'bigint' }).primaryKey(),
  minTokensPerPlay: bigint('min_tokens_per_play', { mode: 'bigint' }).notNull().default(5n),
  maxTokensPerPlay: bigint('max_tokens_per_play', { mode: 'bigint' }).notNull().default(50n),
  straightMultiplier: bigint('straight_multiplier', { mode: 'bigint' }).notNull().default(100000n),
  baseMultiplier: bigint('base_multiplier', { mode: 'bigint' }).notNull().default(20000n),
  tripleLowBonus: bigint('triple_low_bonus', { mode: 'bigint' }).notNull().default(1500000n),
  tripleHighBonus: bigint('triple_high_bonus', { mode: 'bigint' }).notNull().default(2500000n),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
});
