import { eq } from 'drizzle-orm';
import { diceGame1Config, diceGame2Config } from '../../domain/schema.js';
/**
 * Repository for dice game configuration operations using Drizzle ORM.
 * Handles both DiceGame1 and DiceGame2 configs.
 */
export class DiceConfigRepository {
    db;
    constructor(db) {
        this.db = db;
    }
    // ============================================================
    // Dice Game 1 Config
    // ============================================================
    /**
     * Finds dice game 1 configuration by guild ID.
     * Returns null if not found.
     */
    async findDice1Config(guildId) {
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
    async upsertDice1Config(config) {
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
            },
        })
            .returning();
        return mapDice1ToDomain(result[0]);
    }
    /**
     * Deletes dice game 1 configuration.
     */
    async deleteDice1Config(guildId) {
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
    async findDice2Config(guildId) {
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
    async upsertDice2Config(config) {
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
            },
        })
            .returning();
        return mapDice2ToDomain(result[0]);
    }
    /**
     * Deletes dice game 2 configuration.
     */
    async deleteDice2Config(guildId) {
        await this.db
            .delete(diceGame2Config)
            .where(eq(diceGame2Config.guildId, guildId));
    }
}
function mapDice1ToDomain(row) {
    return {
        guildId: row.guildId,
        minTokensPerPlay: row.minTokensPerPlay,
        maxTokensPerPlay: row.maxTokensPerPlay,
        rewardPerDiceValue: row.rewardPerDiceValue,
        createdAt: row.createdAt,
        updatedAt: row.updatedAt,
    };
}
function mapDice2ToDomain(row) {
    return {
        guildId: row.guildId,
        minTokensPerPlay: row.minTokensPerPlay,
        maxTokensPerPlay: row.maxTokensPerPlay,
        straightMultiplier: row.straightMultiplier,
        baseMultiplier: row.baseMultiplier,
        tripleLowBonus: row.tripleLowBonus,
        tripleHighBonus: row.tripleHighBonus,
        createdAt: row.createdAt,
        updatedAt: row.updatedAt,
    };
}
//# sourceMappingURL=dice-config-repo.js.map