import { describe, it, expect, vi, beforeEach } from 'vitest';
import { Ok } from '@ltdjms/shared';
import { MemberInfoFacade } from '../MemberInfoFacade.js';
describe('MemberInfoFacade', () => {
    let facade;
    let mockBalanceService;
    let mockTokenService;
    let mockCurrencyTxService;
    let mockTokenTxService;
    let mockRedemptionService;
    const guildId = 1;
    const userId = 100;
    beforeEach(() => {
        mockBalanceService = {
            getBalance: vi.fn(),
        };
        mockTokenService = {
            getBalance: vi.fn(),
        };
        mockCurrencyTxService = {
            getTransactionPage: vi.fn(),
        };
        mockTokenTxService = {
            getTransactionPage: vi.fn(),
        };
        mockRedemptionService = {
            redeemCode: vi.fn(),
        };
        facade = new MemberInfoFacade(mockBalanceService, mockTokenService, mockCurrencyTxService, mockTokenTxService, mockRedemptionService);
    });
    describe('getUserPanelView', () => {
        it('should return combined balance and token info', async () => {
            const balanceView = {
                guildId,
                userId,
                balance: 500,
                currencyName: 'Coins',
                currencyIcon: '🪙',
            };
            mockBalanceService.getBalance = vi.fn().mockResolvedValue(balanceView);
            mockTokenService.getBalance = vi.fn().mockResolvedValue(250);
            const result = await facade.getUserPanelView(guildId, userId);
            expect(result.isOk()).toBe(true);
            const view = result.getValue();
            expect(view.balance).toBe(500);
            expect(view.tokens).toBe(250);
            expect(view.currencyName).toBe('Coins');
        });
        it('should return error on failure', async () => {
            mockBalanceService.getBalance = vi.fn().mockRejectedValue(new Error('DB error'));
            const result = await facade.getUserPanelView(guildId, userId);
            expect(result.isErr()).toBe(true);
        });
    });
    describe('redeemCode', () => {
        it('should delegate to redemption service', async () => {
            const successResult = { code: {}, product: {}, rewardedAmount: null };
            mockRedemptionService.redeemCode = vi.fn().mockResolvedValue(new Ok(successResult));
            const result = await facade.redeemCode(guildId, userId, 'TESTCODE12345678');
            expect(result.isOk()).toBe(true);
            expect(mockRedemptionService.redeemCode).toHaveBeenCalledWith('TESTCODE12345678', guildId, userId);
        });
    });
});
//# sourceMappingURL=MemberInfoFacade.test.js.map