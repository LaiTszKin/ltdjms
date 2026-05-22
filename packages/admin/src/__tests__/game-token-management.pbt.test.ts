import { describe, it, expect, vi, beforeEach } from 'vitest';
import * as fc from 'fast-check';
import { Ok, Err, DomainError, DomainErrorCategory } from '@ltdjms/shared';
import { GameTokenManagementFacade } from '../facades/GameTokenManagementFacade.js';
import type {
  GameTokenService,
  GameTokenTransactionService,
  TokenAdjustmentResult,
} from '@ltdjms/economy';

const guildId = (): fc.Arbitrary<number> => fc.integer({ min: 1, max: 2147483647 });
const userId = (): fc.Arbitrary<number> => fc.integer({ min: 1, max: 2147483647 });

describe('GameTokenManagementFacade PBT', () => {
  let facade: GameTokenManagementFacade;
  let mockSvc: Partial<GameTokenService>;
  let mockTx: Partial<GameTokenTransactionService>;
  const reason = '管理員調整';
  const actorId = '999';

  beforeEach(() => {
    mockSvc = { getBalance: vi.fn(), tryAdjustTokens: vi.fn() };
    mockTx = { getTransactionPage: vi.fn() };
    facade = new GameTokenManagementFacade(
      mockSvc as GameTokenService,
      mockTx as GameTokenTransactionService,
    );
  });

  describe('getTokens', () => {
    it('delegate 到 tokenService', async () => {
      await fc.assert(
        fc.asyncProperty(
          guildId(),
          userId(),
          fc.integer({ min: 0, max: 1e5 }),
          async (gId, uId, bal) => {
            mockSvc.getBalance = vi.fn().mockResolvedValue(bal);
            const r = await facade.getTokens(String(gId), String(uId));
            expect(r.isOk()).toBe(true);
            expect(r.getValue()).toBe(bal);
            expect(mockSvc.getBalance).toHaveBeenLastCalledWith(gId, String(uId));
            return true;
          },
        ),
      );
    });
  });

  describe('adjustTokens', () => {
    it('有效金額被接受', async () => {
      await fc.assert(
        fc.asyncProperty(
          guildId(),
          userId(),
          fc.integer({ min: -1e5, max: 1e5 }),
          async (gId, uId, amt) => {
            const adj: TokenAdjustmentResult = {
              guildId: gId,
              userId: String(uId),
              previousTokens: 100,
              newTokens: 100 + amt,
              adjustment: amt,
            };
            mockSvc.tryAdjustTokens = vi.fn().mockResolvedValue(new Ok(adj));
            const r = await facade.adjustTokens(String(gId), String(uId), amt, reason, actorId);
            expect(r.isOk()).toBe(true);
            expect(mockSvc.tryAdjustTokens).toHaveBeenLastCalledWith(
              gId,
              String(uId),
              amt,
              expect.anything(),
              reason,
            );
            return true;
          },
        ),
      );
    });
    it('NaN/Infinity 被拒絕', async () => {
      await fc.assert(
        fc.asyncProperty(
          guildId(),
          userId(),
          fc.constantFrom(NaN, Infinity, -Infinity),
          async (gId, uId, amt) => {
            const r = await facade.adjustTokens(String(gId), String(uId), amt, reason, actorId);
            expect(r.isErr()).toBe(true);
            expect(r.getError().category).toBe(DomainErrorCategory.INVALID_INPUT);
            expect(mockSvc.tryAdjustTokens).not.toHaveBeenCalled();
            return true;
          },
        ),
      );
    });
  });

  describe('setTokens', () => {
    it('非負金額成功設定', async () => {
      await fc.assert(
        fc.asyncProperty(
          guildId(),
          userId(),
          fc.integer({ min: 0, max: 1e5 }),
          fc.integer({ min: 0, max: 1e5 }),
          async (gId, uId, cur, tgt) => {
            mockSvc.getBalance = vi.fn().mockResolvedValue(cur);
            const delta = tgt - cur;
            mockSvc.tryAdjustTokens = vi
              .fn()
              .mockResolvedValue(
                new Ok({
                  guildId: gId,
                  userId: String(uId),
                  previousTokens: cur,
                  newTokens: tgt,
                  adjustment: delta,
                }),
              );
            const r = await facade.setTokens(String(gId), String(uId), tgt, reason, actorId);
            expect(r.isOk()).toBe(true);
            if (delta === 0) {
              expect(mockSvc.tryAdjustTokens).not.toHaveBeenCalled();
              expect(r.getValue().adjustment).toBe(0);
            } else {
              expect(mockSvc.tryAdjustTokens).toHaveBeenLastCalledWith(gId, String(uId), delta);
            }
            return true;
          },
        ),
      );
    });
    it('負數金額被拒絕', async () => {
      await fc.assert(
        fc.asyncProperty(guildId(), userId(), fc.integer({ max: -1 }), async (gId, uId, amt) => {
          const r = await facade.setTokens(String(gId), String(uId), amt, reason, actorId);
          expect(r.isErr()).toBe(true);
          expect(r.getError().category).toBe(DomainErrorCategory.INVALID_INPUT);
          return true;
        }),
      );
    });
  });

  describe('getTokenTransactionPage', () => {
    it('delegate 到 transactionService', async () => {
      await fc.assert(
        fc.asyncProperty(
          guildId(),
          userId(),
          fc.integer({ min: 1, max: 10 }),
          fc.integer({ min: 5, max: 50 }),
          async (gId, uId, p, ps) => {
            const txPage = { items: [], total: 0, page: p, pageSize: ps };
            mockTx.getTransactionPage = vi.fn().mockResolvedValue(txPage);
            const r = await facade.getTokenTransactionPage(String(gId), String(uId), p, ps);
            expect(r.isOk()).toBe(true);
            expect(mockTx.getTransactionPage).toHaveBeenLastCalledWith(gId, String(uId), p, ps);
            return true;
          },
        ),
      );
    });
  });
});
