import { NodePgDatabase } from 'drizzle-orm/node-postgres';
import { eq, sql } from 'drizzle-orm';
import { diceGame1Config, diceGame2Config } from '../../domain/schema.js';
import type { DiceGame1Config, DiceGame2Config } from '../../domain/types.js';

/**
 * Repository for dice game configuration operations using Drizzle ORM.
 * Handles both DiceGame1 and DiceGame2 configs.
 */
export class DiceConfigRepository {
  constructor(private readonly db: NodePgDatabase) {}

  // ============================================================
  // Dice Game 1 Config
  // ============================================================

  /**
   * Finds dice game 1 configuration by guild ID.
   * Returns null if not found.
   */
  async findDice1Config(guildId: number): Promise<DiceGame1Config | null> {
    const rows = await this.db
      .select()
      .from(diceGame1Config)
      .where(eq(diceGame1Config.guildId, guildId))
      .limit(1);

    return rows.length > 0 ? mapDice1ToDomain(rows[0]) : null;
  }

  /**
   * Upserts dice game 1 configuration.
   */
  async upsertDice1Config(config: DiceGame1Config): Promise<DiceGame1Config> {
    const result = await this.db
      .insert(diceGame1Config)
      .values({
        guildId: config.guildId,
        minTokensPerPlay: config.minTokensPerPlay,
        maxTokensPerPlay: config.maxTokensPerPlay,
        rewardPerDiceValue: config.rewardPerDiceValue,
      })
      .onConflictDoUpdate({
        target: diceGame1Config.guildId,
        set: {
          minTokensPerPlay: config.minTokensPerPlay,
          maxTokensPerPlay: config.maxTokensPerPlay,
          rewardPerDiceValue: config.rewardPerDiceValue,
          updatedAt: sql`NOW()`,
        },
      })
      .returning();

    return mapDice1ToDomain(result[0]);
  }

  /**
   * Deletes dice game 1 configuration.
   */
  async deleteDice1Config(guildId: number): Promise<void> {
    await this.db
      .delete(diceGame1Config)
      .where(eq(diceGame1Config.guildId, guildId));
  }

  // ============================================================
  // Dice Game 2 Config
  // ============================================================

  /**
   * Finds dice game 2 configuration by guild ID.
   * Returns null if not found.
   */
  async findDice2Config(guildId: number): Promise<DiceGame2Config | null> {
    const rows = await this.db
      .select()
      .from(diceGame2Config)
      .where(eq(diceGame2Config.guildId, guildId))
      .limit(1);

    return rows.length > 0 ? mapDice2ToDomain(rows[0]) : null;
  }

  /**
   * Upserts dice game 2 configuration.
   */
  async upsertDice2Config(config: DiceGame2Config): Promise<DiceGame2Config> {
    const result = await this.db
      .insert(diceGame2Config)
      .values({
        guildId: config.guildId,
        minTokensPerPlay: config.minTokensPerPlay,
        maxTokensPerPlay: config.maxTokensPerPlay,
        straightMultiplier: config.straightMultiplier,
        baseMultiplier: config.baseMultiplier,
        tripleLowBonus: config.tripleLowBonus,
        tripleHighBonus: config.tripleHighBonus,
      })
      .onConflictDoUpdate({
        target: diceGame2Config.guildId,
        set: {
          minTokensPerPlay: config.minTokensPerPlay,
          maxTokensPerPlay: config.maxTokensPerPlay,
          straightMultiplier: config.straightMultiplier,
          baseMultiplier: config.baseMultiplier,
          tripleLowBonus: config.tripleLowBonus,
          tripleHighBonus: config.tripleHighBonus,
          updatedAt: sql`NOW()`,
        },
      })
      .returning();

    return mapDice2ToDomain(result[0]);
  }

  /**
   * Deletes dice game 2 configuration.
   */
  async deleteDice2Config(guildId: number): Promise<void> {
    await this.db
      .delete(diceGame2Config)
      .where(eq(diceGame2Config.guildId, guildId));
  }

  /**
   * Finds dice game 1 configuration by guild ID.
   * Creates a default config if none exists.
   */
  async findOrCreateDefaultDice1(guildId: number): Promise<DiceGame1Config> {
    const existing = await this.findDice1Config(guildId);
    if (existing) return existing;

    return this.upsertDice1Config({
      guildId,
      minTokensPerPlay: 1,
      maxTokensPerPlay: 10,
      rewardPerDiceValue: 250000,
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  }

  /**
   * Finds dice game 2 configuration by guild ID.
   * Creates a default config if none exists.
   */
  async findOrCreateDefaultDice2(guildId: number): Promise<DiceGame2Config> {
    const existing = await this.findDice2Config(guildId);
    if (existing) return existing;

    return this.upsertDice2Config({
      guildId,
      minTokensPerPlay: 5,
      maxTokensPerPlay: 50,
      straightMultiplier: 100000,
      baseMultiplier: 20000,
      tripleLowBonus: 1500000,
      tripleHighBonus: 2500000,
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  }
}

function mapDice1ToDomain(row: Record<string, unknown>): DiceGame1Config {
  return {
    guildId: row.guildId as number,
    minTokensPerPlay: row.minTokensPerPlay as number,
    maxTokensPerPlay: row.maxTokensPerPlay as number,
    rewardPerDiceValue: row.rewardPerDiceValue as number,
    createdAt: row.createdAt as Date,
    updatedAt: row.updatedAt as Date,
  };
}

function mapDice2ToDomain(row: Record<string, unknown>): DiceGame2Config {
  return {
    guildId: row.guildId as number,
    minTokensPerPlay: row.minTokensPerPlay as number,
    maxTokensPerPlay: row.maxTokensPerPlay as number,
    straightMultiplier: row.straightMultiplier as number,
    baseMultiplier: row.baseMultiplier as number,
    tripleLowBonus: row.tripleLowBonus as number,
    tripleHighBonus: row.tripleHighBonus as number,
    createdAt: row.createdAt as Date,
    updatedAt: row.updatedAt as Date,
  };
}
