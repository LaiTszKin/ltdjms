/**
 * Repository for guild-specific escort option pricing overrides.
 */
export interface EscortOptionPriceRepo {
  /**
   * Finds all configured price overrides in a guild.
   * @returns map of optionCode -> priceTwd
   */
  findAllByGuildId(guildId: number): Promise<Map<string, number>>;

  /** Finds a guild-level price override for one option code. */
  findByGuildIdAndOptionCode(guildId: number, optionCode: string): Promise<number | null>;

  /** Upserts a guild-level price override. */
  upsert(
    guildId: number,
    optionCode: string,
    priceTwd: number,
    updatedByUserId: number | null,
  ): Promise<void>;

  /** Deletes a guild-level price override. */
  delete(guildId: number, optionCode: string): Promise<boolean>;

  /** Counts how many guilds have price overrides for the given option code. */
  countByOptionCode(optionCode: string): Promise<number>;

  /** Returns the guild IDs that have price overrides for the given option code. */
  findGuildIdsByOptionCode(optionCode: string): Promise<number[]>;
}
