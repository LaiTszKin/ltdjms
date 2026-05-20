import { describe, it, expect, vi, beforeEach } from 'vitest';
import { Ok, } from '@ltdjms/shared';
import { GameTokenManagementFacade } from '../GameTokenManagementFacade.js';
describe('GameTokenManagementFacade', () => {
    let facade;
    let mockTokenService;
    let mockTxService;
    const guildId = '1';
    const userId = '100';
    beforeEach(() => {
        mockTokenService = {
            getBalance: vi.fn(),
            tryAdjustTokens: vi.fn(),
        };
        mockTxService = {
            getTransactionPage: vi.fn(),
        };
        facade = new GameTokenManagementFacade(mockTokenService, mockTxService);
    });
    describe('getTokens', () => {
        it('should return token balance', async () => {
            mockTokenService.getBalance = vi.fn().mockResolvedValue(500);
            const result = await facade.getTokens(guildId, userId);
            expect(result.isOk()).toBe(true);
            expect(result.getValue()).toBe(500);
        });
        it('should return error when service fails', async () => {
            mockTokenService.getBalance = vi.fn().mockRejectedValue(new Error('DB error'));
            const result = await facade.getTokens(guildId, userId);
            expect(result.isErr()).toBe(true);
        });
    });
    describe('adjustTokens', () => {
        it('should adjust tokens successfully', async () => {
            const adjustResult = {
                guildId: Number(guildId),
                userId: Number(userId),
                previousTokens: 100,
                newTokens: 200,
                adjustment: 100,
            };
            mockTokenService.tryAdjustTokens = vi.fn().mockResolvedValue(new Ok(adjustResult));
            const result = await facade.adjustTokens(guildId, userId, 100, 'test', '999');
            expect(result.isOk()).toBe(true);
            expect(result.getValue().newTokens).toBe(200);
        });
        it('should reject invalid amounts', async () => {
            const result = await facade.adjustTokens(guildId, userId, NaN, 'test', '999');
            expect(result.isErr()).toBe(true);
        });
    });
});
//# sourceMappingURL=GameTokenManagementFacade.test.js.map