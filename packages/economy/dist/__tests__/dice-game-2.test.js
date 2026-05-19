import { describe, it, expect } from 'vitest';
import { DiceGame2Service } from '../dice/services/dice-game-2-service.js';
const defaultConfig = {
    guildId: 1,
    minTokensPerPlay: 5,
    maxTokensPerPlay: 50,
    straightMultiplier: 100000,
    baseMultiplier: 20000,
    tripleLowBonus: 1500000,
    tripleHighBonus: 2500000,
    createdAt: new Date(),
    updatedAt: new Date(),
};
describe('DiceGame2Service - analyzeRolls', () => {
    const noopRandom = { nextInt: () => 0 };
    const service = new DiceGame2Service({}, {}, {}, {}, noopRandom);
    describe('straight detection', () => {
        it('should detect a straight of length 3', () => {
            // [1, 2, 3] is a straight
            const rolls = [1, 2, 3];
            const analysis = service.analyzeRolls(rolls, defaultConfig);
            expect(analysis.straightSegments).toHaveLength(1);
            expect(analysis.straightSegments[0]).toEqual([1, 2, 3]);
        });
        it('should detect a straight of length 4', () => {
            const rolls = [2, 3, 4, 5];
            const analysis = service.analyzeRolls(rolls, defaultConfig);
            expect(analysis.straightSegments).toHaveLength(1);
            expect(analysis.straightSegments[0]).toEqual([2, 3, 4, 5]);
        });
        it('should not detect length-2 increasing as straight', () => {
            const rolls = [1, 2, 4, 6];
            const analysis = service.analyzeRolls(rolls, defaultConfig);
            expect(analysis.straightSegments).toHaveLength(0);
        });
        it('should detect multiple straight segments', () => {
            // [1, 2, 3, 0, 4, 5, 6]
            const rolls = [1, 2, 3, 2, 4, 5, 6];
            const analysis = service.analyzeRolls(rolls, defaultConfig);
            expect(analysis.straightSegments).toHaveLength(2);
            expect(analysis.straightSegments[0]).toEqual([1, 2, 3]);
            expect(analysis.straightSegments[1]).toEqual([4, 5, 6]);
        });
    });
    describe('triple detection', () => {
        it('should detect exactly 3 same values as triple', () => {
            const rolls = [2, 4, 4, 4, 6];
            const analysis = service.analyzeRolls(rolls, defaultConfig);
            expect(analysis.tripleSegments).toHaveLength(1);
            expect(analysis.tripleSegments[0]).toEqual([4, 4, 4]);
        });
        it('should not count 4+ same values as triple', () => {
            // Four 5s is NOT a triple
            const rolls = [5, 5, 5, 5];
            const analysis = service.analyzeRolls(rolls, defaultConfig);
            expect(analysis.tripleSegments).toHaveLength(0);
        });
        it('should not count 2 same values as triple', () => {
            const rolls = [1, 3, 3, 6];
            const analysis = service.analyzeRolls(rolls, defaultConfig);
            expect(analysis.tripleSegments).toHaveLength(0);
        });
        it('should detect multiple triple segments', () => {
            // Two separate triples: [2, 2, 2] and [5, 5, 5]
            const rolls = [2, 2, 2, 5, 5, 5];
            const analysis = service.analyzeRolls(rolls, defaultConfig);
            expect(analysis.tripleSegments).toHaveLength(2);
            expect(analysis.tripleSegments[0]).toEqual([2, 2, 2]);
            expect(analysis.tripleSegments[1]).toEqual([5, 5, 5]);
        });
    });
    describe('straight and triple interaction', () => {
        it('should prioritize straights over overlapping triples', () => {
            // [1, 2, 3] is both a straight and could be considered a triple of different values
            // But triples need same values, so no overlap here
            const rolls = [1, 2, 3, 4, 5, 6];
            const analysis = service.analyzeRolls(rolls, defaultConfig);
            // Should be detected as a single straight of length 6
            expect(analysis.straightSegments).toHaveLength(1);
            expect(analysis.straightSegments[0]).toEqual([1, 2, 3, 4, 5, 6]);
            expect(analysis.tripleSegments).toHaveLength(0);
        });
        it('should find triples from dice not used in straights', () => {
            // [1, 2, 3, 6, 6, 6]
            // First 3 = straight [1,2,3], remaining triple = [6,6,6]
            const rolls = [1, 2, 3, 6, 6, 6];
            const analysis = service.analyzeRolls(rolls, defaultConfig);
            expect(analysis.straightSegments).toHaveLength(1);
            expect(analysis.straightSegments[0]).toEqual([1, 2, 3]);
            expect(analysis.tripleSegments).toHaveLength(1);
            expect(analysis.tripleSegments[0]).toEqual([6, 6, 6]);
        });
        it('should not use straight-marked dice in triples', () => {
            // The triple at positions 2,3,4 overlaps with the start of a straight
            // [5, 5, 5, 2, 3, 4]
            // Wait, 5,5,5 is a triple (positions 0,1,2)
            // Then 2,3,4 is a straight (positions 3,4,5)
            // No overlap
            const rolls = [5, 5, 5, 2, 3, 4];
            const analysis = service.analyzeRolls(rolls, defaultConfig);
            expect(analysis.tripleSegments).toHaveLength(1);
            expect(analysis.straightSegments).toHaveLength(1);
            expect(analysis.tripleSegments[0]).toEqual([5, 5, 5]);
            expect(analysis.straightSegments[0]).toEqual([2, 3, 4]);
        });
    });
    describe('reward calculation', () => {
        it('should calculate straight reward correctly', () => {
            // Straight [1, 2, 3] sum = 6, reward = 6 * 100000 = 600000
            const rolls = [1, 2, 3];
            const analysis = service.analyzeRolls(rolls, defaultConfig);
            expect(analysis.straightReward).toBe(600000);
            expect(analysis.totalReward).toBe(600000); // no other dice
        });
        it('should calculate triple reward correctly (low bonus)', () => {
            // Triple [1, 1, 1] sum = 3 < 10 => low bonus
            const rolls = [1, 1, 1];
            const analysis = service.analyzeRolls(rolls, defaultConfig);
            expect(analysis.tripleReward).toBe(1500000);
            expect(analysis.totalReward).toBe(1500000);
        });
        it('should calculate triple reward correctly (high bonus)', () => {
            // Triple [5, 5, 5] sum = 15 >= 10 => high bonus
            const rolls = [5, 5, 5];
            const analysis = service.analyzeRolls(rolls, defaultConfig);
            expect(analysis.tripleReward).toBe(2500000);
            expect(analysis.totalReward).toBe(2500000);
        });
        it('should calculate base reward for non-straight/non-triple dice', () => {
            // Dice: [1, 2, 4, 6, 3]
            // No straights (max consecutive is 1,2 then breaks)
            // No triples (no 3 same values)
            // Non-straight sum = 1+2+4+6+3 = 16, reward = 16 * 20000 = 320000
            const rolls = [1, 2, 4, 6, 3];
            const analysis = service.analyzeRolls(rolls, defaultConfig);
            expect(analysis.straightSegments).toHaveLength(0);
            expect(analysis.tripleSegments).toHaveLength(0);
            expect(analysis.nonStraightReward).toBe(320000);
            expect(analysis.totalReward).toBe(320000);
        });
        it('should calculate combined reward correctly', () => {
            // [1, 2, 3] = straight sum 6 * 100000 = 600000
            // [6] = non-straight sum 6 * 20000 = 120000
            // Total = 720000
            const rolls = [1, 2, 3, 6];
            const analysis = service.analyzeRolls(rolls, defaultConfig);
            expect(analysis.straightReward).toBe(600000);
            expect(analysis.nonStraightReward).toBe(120000);
            expect(analysis.totalReward).toBe(720000);
        });
        it('should calculate straight + triple + base correctly', () => {
            // [1, 2, 3, 6, 5, 5, 5]
            // Straight [1, 2, 3] sum=6 * 100000 = 600000
            // Triple [5, 5, 5] sum=15 >= 10 => 2500000
            // Non-straight [6] sum=6 * 20000 = 120000
            // Total = 600000 + 2500000 + 120000 = 3220000
            const rolls = [1, 2, 3, 6, 5, 5, 5];
            const analysis = service.analyzeRolls(rolls, defaultConfig);
            expect(analysis.straightReward).toBe(600000);
            expect(analysis.tripleReward).toBe(2500000);
            expect(analysis.nonStraightReward).toBe(120000);
            expect(analysis.totalReward).toBe(3220000);
        });
    });
});
//# sourceMappingURL=dice-game-2.test.js.map