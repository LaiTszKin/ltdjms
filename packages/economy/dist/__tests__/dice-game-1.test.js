import { describe, it, expect, vi } from 'vitest';
import { DiceGame1Service, DefaultRandom } from '../dice/services/dice-game-1-service.js';
describe('DiceGame1Service', () => {
    describe('calculateTotalReward', () => {
        const service = new DiceGame1Service({}, {}, {}, {}, DefaultRandom);
        it('should calculate reward as sum of dice times rewardPerDiceValue', () => {
            // [1, 2, 3] sum = 6, reward = 6 * 250000 = 1500000
            const reward = service.calculateTotalReward([1, 2, 3], 250000);
            expect(reward).toBe(1500000);
        });
        it('should handle empty dice list', () => {
            const reward = service.calculateTotalReward([], 250000);
            expect(reward).toBe(0);
        });
        it('should handle single die', () => {
            const reward = service.calculateTotalReward([6], 250000);
            expect(reward).toBe(1500000);
        });
        it('should handle maximum dice values', () => {
            // [6, 6, 6, 6, 6] sum = 30, reward = 30 * 250000 = 7500000
            const reward = service.calculateTotalReward([6, 6, 6, 6, 6], 250000);
            expect(reward).toBe(7500000);
        });
        it('should use configured rewardPerDiceValue', () => {
            const reward = service.calculateTotalReward([1, 2, 3], 100000);
            expect(reward).toBe(600000);
        });
    });
    describe('rollDice with seeded random', () => {
        it('should produce deterministic results', () => {
            const random = {
                nextInt(bound) {
                    // Always return 0, so dice always lands on 1
                    return 0;
                },
            };
            const service = new DiceGame1Service({}, {}, {}, {}, random);
            const rolls = service.rollDice(3);
            expect(rolls).toEqual([1, 1, 1]);
        });
        it('should return correct number of dice', () => {
            const random = {
                nextInt(_bound) {
                    return 3; // Returns 3, so dice = 4
                },
            };
            const service = new DiceGame1Service({}, {}, {}, {}, random);
            const rolls = service.rollDice(5);
            expect(rolls).toHaveLength(5);
            expect(rolls).toEqual([4, 4, 4, 4, 4]);
        });
    });
    describe('integration with GameRewardService', () => {
        it('should use GameRewardService.creditReward to apply reward', async () => {
            // This test validates that creditReward is called with correct parameters
            const random = {
                nextInt(_bound) {
                    return 2; // dice = 3
                },
            };
            const mockDiceConfigRepo = {
                findDice1Config: vi.fn().mockResolvedValue({
                    guildId: 1,
                    minTokensPerPlay: 1,
                    maxTokensPerPlay: 10,
                    rewardPerDiceValue: 250000,
                    createdAt: new Date(),
                    updatedAt: new Date(),
                }),
            };
            const mockGameTokenService = {
                tryDeductTokens: vi.fn().mockResolvedValue({
                    isOk: () => true,
                    isErr: () => false,
                    getValue: () => ({ guildId: 1, userId: 1, tokens: 7 }),
                }),
            };
            const mockTokenTxService = {
                recordTransaction: vi.fn().mockResolvedValue({}),
            };
            const mockGameRewardService = {
                creditReward: vi.fn().mockResolvedValue(1500000),
            };
            const service = new DiceGame1Service(mockDiceConfigRepo, mockGameTokenService, mockTokenTxService, mockGameRewardService, random);
            const result = await service.play(1, 1, 2);
            expect(result.isOk()).toBe(true);
            if (result.isOk()) {
                expect(result.getValue().diceRolls).toHaveLength(2);
                // Both dice = 3, sum = 6, reward = 6 * 250000 = 1500000
                expect(result.getValue().diceRolls).toEqual([3, 3]);
                expect(result.getValue().totalReward).toBe(1500000);
            }
            // Verify creditReward was called twice (once for 0 balance query, once for actual reward)
            expect(mockGameRewardService.creditReward).toHaveBeenCalledTimes(2);
        });
        it('should fail when tokens are insufficient', async () => {
            const mockDiceConfigRepo = {
                findDice1Config: vi.fn().mockResolvedValue({
                    guildId: 1,
                    minTokensPerPlay: 1,
                    maxTokensPerPlay: 10,
                    rewardPerDiceValue: 250000,
                    createdAt: new Date(),
                    updatedAt: new Date(),
                }),
            };
            const mockGameTokenService = {
                tryDeductTokens: vi.fn().mockResolvedValue({
                    isOk: () => false,
                    isErr: () => true,
                    getError: () => ({
                        category: 'INSUFFICIENT_TOKENS',
                        message: 'Insufficient tokens',
                    }),
                }),
            };
            const service = new DiceGame1Service(mockDiceConfigRepo, mockGameTokenService, {}, {}, DefaultRandom);
            const result = await service.play(1, 1, 5);
            expect(result.isErr()).toBe(true);
        });
        it('should fail when token count is below minimum', async () => {
            const mockDiceConfigRepo = {
                findDice1Config: vi.fn().mockResolvedValue({
                    guildId: 1,
                    minTokensPerPlay: 3,
                    maxTokensPerPlay: 10,
                    rewardPerDiceValue: 250000,
                    createdAt: new Date(),
                    updatedAt: new Date(),
                }),
            };
            const service = new DiceGame1Service(mockDiceConfigRepo, {}, {}, {}, DefaultRandom);
            const result = await service.play(1, 1, 1);
            expect(result.isErr()).toBe(true);
        });
        it('should fail when token count exceeds maximum', async () => {
            const mockDiceConfigRepo = {
                findDice1Config: vi.fn().mockResolvedValue({
                    guildId: 1,
                    minTokensPerPlay: 1,
                    maxTokensPerPlay: 5,
                    rewardPerDiceValue: 250000,
                    createdAt: new Date(),
                    updatedAt: new Date(),
                }),
            };
            const service = new DiceGame1Service(mockDiceConfigRepo, {}, {}, {}, DefaultRandom);
            const result = await service.play(1, 1, 10);
            expect(result.isErr()).toBe(true);
        });
    });
});
//# sourceMappingURL=dice-game-1.test.js.map