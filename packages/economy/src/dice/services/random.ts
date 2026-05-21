/**
 * Injectable random number generator interface for testing.
 * Matches Java's java.util.Random nextInt(1, 7) behavior.
 */
export interface Random {
  /** Returns a random integer in [0, bound). */
  nextInt(bound: number): number;
}

/**
 * Default random implementation using Math.random.
 */
export const DefaultRandom: Random = {
  nextInt(bound: number): number {
    return Math.floor(Math.random() * bound);
  },
};

/**
 * Seeded random implementation for deterministic testing.
 */
export class SeededRandom implements Random {
  private state: number;

  constructor(seed: number) {
    this.state = seed;
  }

  /**
   * Linear Congruential Generator matching Java's java.util.Random.nextInt(int).
   * Uses Java's LCG parameters (multiplier=25214903917, addend=11, 48-bit mask).
   * nextInt(6) + 1 gives dice values 1-6.
   *
   * Uses BigInt internally to avoid Number overflow (multiplier * state exceeds MAX_SAFE_INTEGER).
   */
  nextInt(bound: number): number {
    const multiplier = 25214903917n;
    const addend = 11n;
    const mask = (1n << 48n) - 1n;
    this.state = Number((BigInt(this.state) * multiplier + addend) & mask);
    return (this.state >>> 16) % bound;
  }
}
