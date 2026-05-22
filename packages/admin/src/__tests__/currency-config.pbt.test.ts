import { describe, it, expect, vi, beforeEach } from 'vitest';
import * as fc from 'fast-check';
import { Ok, Err, DomainError, DomainErrorCategory } from '@ltdjms/shared';
import { CurrencyManagementFacade } from '../facades/CurrencyManagementFacade.js';
import type {
  BalanceService,
  BalanceAdjustmentService,
  CurrencyConfigService,
  GuildCurrencyConfig,
  BalanceView,
  BalanceAdjustmentResult,
} from '@ltdjms/economy';
import { CurrencyTransactionSource } from '@ltdjms/economy';

/** Inline guildId generator (1..2147483647) matching shared's arbitrary.ts */
const guildId = (): fc.Arbitrary<number> => fc.integer({ min: 1, max: 2147483647 });
/** Inline userId generator matching shared's arbitrary.ts */
const userId = (): fc.Arbitrary<number> => fc.integer({ min: 1, max: 2147483647 });

/** Minimal valid BalanceAdjustmentResult used by mocks. */
function makeAdjustResult(
  gId: number,
  uId: string,
  adjustment: number,
  prev: number,
): BalanceAdjustmentResult {
  return {
    guildId: gId,
    userId: uId,
    previousBalance: prev,
    newBalance: prev + adjustment,
    adjustment,
    currencyName: 'Coins',
    currencyIcon: '\u{1FA99}',
  };
}

describe('CurrencyManagementFacade PBT', () => {
  let facade: CurrencyManagementFacade;
  let mockBalanceService: Partial<BalanceService>;
  let mockAdjustService: Partial<BalanceAdjustmentService>;
  let mockConfigService: Partial<CurrencyConfigService>;
  const reason = '管理員調整';
  const actorId = '999';

  beforeEach(() => {
    mockBalanceService = { getBalance: vi.fn() };
    mockAdjustService = { tryAdjustBalance: vi.fn() };
    mockConfigService = { tryGetConfig: vi.fn() };
    facade = new CurrencyManagementFacade(
      mockBalanceService as BalanceService,
      mockAdjustService as BalanceAdjustmentService,
      mockConfigService as CurrencyConfigService,
    );
  });

  describe('getConfig', () => {
    it('對於任何 guildId 回傳 config', async () => {
      await fc.assert(
        fc.asyncProperty(guildId(), async (gId) => {
          const config: GuildCurrencyConfig = {
            guildId: gId,
            currencyName: 'Coins',
            currencyIcon: '\u{1FA99}',
            createdAt: new Date(),
            updatedAt: new Date(),
          };
          mockConfigService.tryGetConfig = vi.fn().mockResolvedValue(new Ok(config));
          const result = await facade.getConfig(String(gId));
          expect(result.isOk()).toBe(true);
          expect(result.getValue()).toEqual(config);
          expect(mockConfigService.tryGetConfig).toHaveBeenLastCalledWith(gId);
          return true;
        }),
      );
    });
    it('service 錯誤時傳遞錯誤', async () => {
      await fc.assert(
        fc.asyncProperty(guildId(), async (gId) => {
          const error = DomainError.persistenceFailure('DB error');
          mockConfigService.tryGetConfig = vi.fn().mockResolvedValue(new Err(error));
          const result = await facade.getConfig(String(gId));
          expect(result.isErr()).toBe(true);
          expect(result.getError().category).toBe(DomainErrorCategory.PERSISTENCE_FAILURE);
          return true;
        }),
      );
    });
  });

  describe('getBalance', () => {
    it('對任何 guildId/userId delegate 到 balanceService', async () => {
      await fc.assert(
        fc.asyncProperty(guildId(), userId(), async (gId, uId) => {
          const balance: BalanceView = {
            guildId: gId,
            userId: String(uId),
            balance: 500,
            currencyName: 'Coins',
            currencyIcon: '\u{1FA99}',
          };
          mockBalanceService.getBalance = vi.fn().mockResolvedValue(new Ok(balance));
          const result = await facade.getBalance(String(gId), String(uId));
          expect(result.isOk()).toBe(true);
          expect(result.getValue().balance).toBe(500);
          expect(mockBalanceService.getBalance).toHaveBeenLastCalledWith(gId, String(uId));
          return true;
        }),
      );
    });
  });

  describe('adjustBalance（有效正整數）', () => {
    it('任何正整數轉發到 balanceAdjustmentService', async () => {
      await fc.assert(
        fc.asyncProperty(
          guildId(),
          userId(),
          fc.integer({ min: 1, max: 100000 }),
          async (gId, uId, amount) => {
            const resultData = makeAdjustResult(gId, String(uId), amount, 0);
            mockAdjustService.tryAdjustBalance = vi.fn().mockResolvedValue(new Ok(resultData));
            const result = await facade.adjustBalance(
              String(gId),
              String(uId),
              amount,
              reason,
              actorId,
            );
            expect(result.isOk()).toBe(true);
            expect(result.getValue().newBalance).toBe(amount);
            expect(mockAdjustService.tryAdjustBalance).toHaveBeenLastCalledWith(
              gId,
              String(uId),
              amount,
              CurrencyTransactionSource.ADMIN_ADJUSTMENT,
              expect.stringContaining(reason),
            );
            return true;
          },
        ),
      );
    });
  });

  describe('adjustBalance（無效金額）', () => {
    it('<= 0 的金額被拒絕', async () => {
      await fc.assert(
        fc.asyncProperty(guildId(), userId(), fc.integer({ max: 0 }), async (gId, uId, amount) => {
          const result = await facade.adjustBalance(
            String(gId),
            String(uId),
            amount,
            reason,
            actorId,
          );
          expect(result.isErr()).toBe(true);
          expect(result.getError().category).toBe(DomainErrorCategory.INVALID_INPUT);
          expect(mockAdjustService.tryAdjustBalance).not.toHaveBeenCalled();
          return true;
        }),
      );
    });
    it('NaN, Infinity, -Infinity 被拒絕', async () => {
      await fc.assert(
        fc.asyncProperty(
          guildId(),
          userId(),
          fc.constantFrom(NaN, Infinity, -Infinity),
          async (gId, uId, amount) => {
            const result = await facade.adjustBalance(
              String(gId),
              String(uId),
              amount,
              reason,
              actorId,
            );
            expect(result.isErr()).toBe(true);
            expect(result.getError().category).toBe(DomainErrorCategory.INVALID_INPUT);
            return true;
          },
        ),
      );
    });
  });

  describe('setBalance', () => {
    it('非負金額計算 delta', async () => {
      await fc.assert(
        fc.asyncProperty(
          guildId(),
          userId(),
          fc.integer({ min: 0, max: 100000 }),
          async (gId, uId, amount) => {
            const currentBalance: BalanceView = {
              guildId: gId,
              userId: String(uId),
              balance: 100,
              currencyName: 'Coins',
              currencyIcon: '\u{1FA99}',
            };
            mockBalanceService.getBalance = vi.fn().mockResolvedValue(new Ok(currentBalance));
            const delta = amount - currentBalance.balance;
            const resultData = makeAdjustResult(gId, String(uId), delta, currentBalance.balance);
            mockAdjustService.tryAdjustBalance = vi.fn().mockResolvedValue(new Ok(resultData));
            const result = await facade.setBalance(
              String(gId),
              String(uId),
              amount,
              reason,
              actorId,
            );
            expect(result.isOk()).toBe(true);
            expect(mockAdjustService.tryAdjustBalance).toHaveBeenLastCalledWith(
              gId,
              String(uId),
              delta,
              CurrencyTransactionSource.ADMIN_ADJUSTMENT,
              expect.stringContaining(reason),
            );
            return true;
          },
        ),
      );
    });
    it('負數金額被拒絕', async () => {
      await fc.assert(
        fc.asyncProperty(guildId(), userId(), fc.integer({ max: -1 }), async (gId, uId, amount) => {
          const result = await facade.setBalance(String(gId), String(uId), amount, reason, actorId);
          expect(result.isErr()).toBe(true);
          expect(result.getError().category).toBe(DomainErrorCategory.INVALID_INPUT);
          return true;
        }),
      );
    });
    it('amount 等於當前餘額時 delta=0', async () => {
      await fc.assert(
        fc.asyncProperty(
          guildId(),
          userId(),
          fc.integer({ min: 0, max: 100000 }),
          async (gId, uId, amount) => {
            const currentBalance: BalanceView = {
              guildId: gId,
              userId: String(uId),
              balance: amount,
              currencyName: 'Coins',
              currencyIcon: '\u{1FA99}',
            };
            mockBalanceService.getBalance = vi.fn().mockResolvedValue(new Ok(currentBalance));
            const resultData = makeAdjustResult(gId, String(uId), 0, amount);
            mockAdjustService.tryAdjustBalance = vi.fn().mockResolvedValue(new Ok(resultData));
            const result = await facade.setBalance(
              String(gId),
              String(uId),
              amount,
              reason,
              actorId,
            );
            expect(result.isOk()).toBe(true);
            expect(mockAdjustService.tryAdjustBalance).toHaveBeenLastCalledWith(
              gId,
              String(uId),
              0,
              CurrencyTransactionSource.ADMIN_ADJUSTMENT,
              expect.any(String),
            );
            return true;
          },
        ),
      );
    });
  });

  describe('設定變更不影響既有餘額', () => {
    it('setBalance 前後 getBalance 回傳獨立查詢結果', async () => {
      await fc.assert(
        fc.asyncProperty(
          guildId(),
          userId(),
          fc.integer({ min: 0, max: 100000 }),
          async (gId, uId, targetAmount) => {
            const originalBalance: BalanceView = {
              guildId: gId,
              userId: String(uId),
              balance: 500,
              currencyName: 'Coins',
              currencyIcon: '\u{1FA99}',
            };
            mockBalanceService.getBalance = vi.fn().mockResolvedValue(new Ok(originalBalance));
            expect((await facade.getBalance(String(gId), String(uId))).getValue().balance).toBe(
              500,
            );
            const delta = targetAmount - 500;
            mockAdjustService.tryAdjustBalance = vi
              .fn()
              .mockResolvedValue(new Ok(makeAdjustResult(gId, String(uId), delta, 500)));
            expect(
              (
                await facade.setBalance(String(gId), String(uId), targetAmount, reason, actorId)
              ).isOk(),
            ).toBe(true);
            mockBalanceService.getBalance = vi
              .fn()
              .mockResolvedValue(new Ok({ ...originalBalance, balance: targetAmount }));
            expect((await facade.getBalance(String(gId), String(uId))).getValue().balance).toBe(
              targetAmount,
            );
            return true;
          },
        ),
      );
    });
  });
});
