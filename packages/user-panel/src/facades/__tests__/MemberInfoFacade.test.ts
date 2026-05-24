import { describe, it, expect, vi, beforeEach } from 'vitest';
import { Ok, Err, DomainError } from '@ltdjms/shared';
import { MemberInfoFacade } from '../MemberInfoFacade.js';
import type { BalanceService, CurrencyTransactionService, BalanceView } from '@ltdjms/economy';
import type { GameTokenService, GameTokenTransactionService } from '@ltdjms/games';
import type { RedemptionService } from '@ltdjms/shop';

describe('MemberInfoFacade', () => {
  let facade: MemberInfoFacade;
  let mockBalanceService: Partial<BalanceService>;
  let mockTokenService: Partial<GameTokenService>;
  let mockCurrencyTxService: Partial<CurrencyTransactionService>;
  let mockTokenTxService: Partial<GameTokenTransactionService>;
  let mockRedemptionService: Partial<RedemptionService>;

  const guildId = '1';
  const userId = '100';

  beforeEach(() => {
    mockBalanceService = {
      getBalanceUnchecked: vi.fn(),
    } as Partial<BalanceService>;
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

    facade = new MemberInfoFacade(
      mockBalanceService as BalanceService,
      mockTokenService as GameTokenService,
      mockCurrencyTxService as CurrencyTransactionService,
      mockTokenTxService as GameTokenTransactionService,
      mockRedemptionService as RedemptionService,
    );
  });

  describe('getUserPanelView', () => {
    it('should return combined balance and token info', async () => {
      const balanceView: BalanceView = {
        guildId: Number(guildId),
        userId,
        balance: 500,
        currencyName: 'Coins',
        currencyIcon: '🪙',
      };
      mockBalanceService.getBalanceUnchecked = vi.fn().mockResolvedValue(balanceView);
      mockTokenService.getBalance = vi.fn().mockResolvedValue(250);

      const result = await facade.getUserPanelView(guildId, userId);
      expect(result.isOk()).toBe(true);
      const view = result.getValue();
      expect(view.balance).toBe(500);
      expect(view.tokens).toBe(250);
      expect(view.currencyName).toBe('Coins');
    });

    it('should return error on failure', async () => {
      mockBalanceService.getBalanceUnchecked = vi.fn().mockRejectedValue(new Error('DB error'));

      const result = await facade.getUserPanelView(guildId, userId);
      expect(result.isErr()).toBe(true);
    });
  });

  describe('redeemCode', () => {
    it('should delegate to redemption service', async () => {
      const successResult = { code: {} as any, product: {} as any, rewardAmount: null };
      mockRedemptionService.redeemCode = vi.fn().mockResolvedValue(new Ok(successResult));

      const result = await facade.redeemCode(guildId, userId, 'TESTCODE12345678');
      expect(result.isOk()).toBe(true);
      expect(mockRedemptionService.redeemCode).toHaveBeenCalledWith('TESTCODE12345678', 1, '100');
    });
  });
});
