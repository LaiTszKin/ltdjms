import { describe, it, expect, vi, beforeEach } from 'vitest';
import * as fc from 'fast-check';
import { DomainErrorCategory, type DomainEventPublisher } from '@ltdjms/shared';
import { GameConfigManagementFacade } from '@ltdjms/games';
import type { DiceConfigService, DiceGame1Config, DiceGame2Config } from '@ltdjms/games';

const guildId = (): fc.Arbitrary<number> => fc.integer({ min: 1, max: 2147483647 });

describe('GameConfigManagementFacade PBT', () => {
  let facade: GameConfigManagementFacade;
  let mock: Partial<DiceConfigService>;
  let mockEvt: Partial<DomainEventPublisher>;

  beforeEach(() => {
    mock = {
      findDice1Config: vi.fn(),
      findDice2Config: vi.fn(),
      upsertDice1Config: vi.fn(),
      upsertDice2Config: vi.fn(),
    };
    mockEvt = { publish: vi.fn() };
    facade = new GameConfigManagementFacade(
      mock as DiceConfigService,
      mockEvt as DomainEventPublisher,
    );
  });

  describe('Dice Game 1 有效', () => {
    it('所有數值 > 0 且 min < max 應通過', async () => {
      await fc.assert(
        fc.asyncProperty(
          guildId(),
          fc.integer({ min: 1, max: 1000 }),
          fc.integer({ min: 2, max: 10000 }),
          fc.integer({ min: 1, max: 1e5 }),
          async (gId, a, b, r) => {
            const min = Math.min(a, b - 1);
            const max = Math.max(min + 1, b);
            mock.findDice1Config = vi.fn().mockResolvedValue(null);
            mock.upsertDice1Config = vi.fn().mockResolvedValue({
              guildId: Number(gId),
              minTokensPerPlay: min,
              maxTokensPerPlay: max,
              rewardPerDiceValue: r,
              createdAt: new Date(),
              updatedAt: new Date(),
            });
            expect(
              (
                await facade.updateDiceGame1Config(String(gId), {
                  minTokensPerPlay: min,
                  maxTokensPerPlay: max,
                  rewardPerDiceValue: r,
                })
              ).isOk(),
            ).toBe(true);
            expect(mock.upsertDice1Config).toHaveBeenCalled();
            return true;
          },
        ),
      );
    });
    it('getDiceGame1Config 回傳 Ok', async () => {
      await fc.assert(
        fc.asyncProperty(guildId(), async (gId) => {
          const cfg: DiceGame1Config = {
            guildId: Number(gId),
            minTokensPerPlay: 10,
            maxTokensPerPlay: 100,
            rewardPerDiceValue: 5,
            createdAt: new Date(),
            updatedAt: new Date(),
          };
          mock.findDice1Config = vi.fn().mockResolvedValue(cfg);
          const r = await facade.getDiceGame1Config(String(gId));
          expect(r.isOk()).toBe(true);
          expect(r.getValue()).toEqual(cfg);
          return true;
        }),
      );
    });
  });

  describe('Dice Game 1 無效', () => {
    it('數值 <= 0 拒絕', async () => {
      await fc.assert(
        fc.asyncProperty(
          fc.constantFrom<'min' | 'max' | 'reward'>('min', 'max', 'reward'),
          fc.integer({ max: 0 }),
          async (t, v) => {
            const base = { minTokensPerPlay: 10, maxTokensPerPlay: 100, rewardPerDiceValue: 5 };
            if (t === 'min') base.minTokensPerPlay = v;
            else if (t === 'max') base.maxTokensPerPlay = v;
            else base.rewardPerDiceValue = v;
            const r = await facade.updateDiceGame1Config('1', base);
            expect(r.isErr()).toBe(true);
            expect(r.getError().category).toBe(DomainErrorCategory.INVALID_INPUT);
            expect(mock.upsertDice1Config).not.toHaveBeenCalled();
            return true;
          },
        ),
      );
    });
    it('min >= max 拒絕', async () => {
      await fc.assert(
        fc.asyncProperty(
          fc.integer({ min: 1, max: 1000 }),
          fc.integer({ min: 0, max: 999 }),
          fc.integer({ min: 1, max: 1e5 }),
          async (a, b, r) => {
            const r2 = await facade.updateDiceGame1Config('1', {
              minTokensPerPlay: Math.max(a, b + 1),
              maxTokensPerPlay: Math.min(a, b),
              rewardPerDiceValue: r,
            });
            expect(r2.isErr()).toBe(true);
            expect(r2.getError().category).toBe(DomainErrorCategory.INVALID_INPUT);
            return true;
          },
        ),
      );
    });
  });

  describe('Dice Game 2 有效', () => {
    it('所有參數有效應通過', async () => {
      await fc.assert(
        fc.asyncProperty(
          guildId(),
          fc.integer({ min: 1, max: 1000 }),
          fc.integer({ min: 2, max: 10000 }),
          fc.double({ min: 1, max: 100 }),
          fc.double({ min: 1, max: 100 }),
          fc.double({ min: 1, max: 100 }),
          fc.double({ min: 1, max: 100 }),
          async (gId, a, b, s, base, low, high) => {
            const min = Math.min(a, b - 1);
            const max = Math.max(min + 1, b);
            mock.findDice2Config = vi.fn().mockResolvedValue(null);
            mock.upsertDice2Config = vi.fn().mockResolvedValue({
              guildId: Number(gId),
              minTokensPerPlay: min,
              maxTokensPerPlay: max,
              straightMultiplier: s,
              baseMultiplier: base,
              tripleLowBonus: low,
              tripleHighBonus: high,
              createdAt: new Date(),
              updatedAt: new Date(),
            });
            expect(
              (
                await facade.updateDiceGame2Config(String(gId), {
                  minTokensPerPlay: min,
                  maxTokensPerPlay: max,
                  straightMultiplier: s,
                  baseMultiplier: base,
                  tripleLowBonus: low,
                  tripleHighBonus: high,
                })
              ).isOk(),
            ).toBe(true);
            return true;
          },
        ),
      );
    });
  });

  describe('Dice Game 2 無效', () => {
    it('倍率 < 1.0 拒絕', async () => {
      await fc.assert(
        fc.asyncProperty(
          fc.constantFrom<'st' | 'ba' | 'lo' | 'hi'>('st', 'ba', 'lo', 'hi'),
          fc.double({ min: 0, max: 0.9999 }).filter((v) => Number.isFinite(v)),
          async (t, v) => {
            const base = {
              minTokensPerPlay: 10,
              maxTokensPerPlay: 100,
              straightMultiplier: 2,
              baseMultiplier: 1.5,
              tripleLowBonus: 3,
              tripleHighBonus: 5,
            };
            if (t === 'st') base.straightMultiplier = v;
            else if (t === 'ba') base.baseMultiplier = v;
            else if (t === 'lo') base.tripleLowBonus = v;
            else base.tripleHighBonus = v;
            const r = await facade.updateDiceGame2Config('1', base);
            expect(r.isErr()).toBe(true);
            expect(r.getError().category).toBe(DomainErrorCategory.INVALID_INPUT);
            expect(mock.upsertDice2Config).not.toHaveBeenCalled();
            return true;
          },
        ),
      );
    });
    it('min >= max 拒絕', async () => {
      await fc.assert(
        fc.asyncProperty(
          fc.integer({ min: 1, max: 1000 }),
          fc.integer({ min: 0, max: 999 }),
          async (a, b) => {
            const r = await facade.updateDiceGame2Config('1', {
              minTokensPerPlay: Math.max(a, b + 1),
              maxTokensPerPlay: Math.min(a, b),
              straightMultiplier: 2,
              baseMultiplier: 1.5,
              tripleLowBonus: 3,
              tripleHighBonus: 5,
            });
            expect(r.isErr()).toBe(true);
            return true;
          },
        ),
      );
    });
    it('代幣 <= 0 拒絕', async () => {
      await fc.assert(
        fc.asyncProperty(
          fc.constantFrom<'min' | 'max'>('min', 'max'),
          fc.integer({ max: 0 }),
          async (t, v) => {
            const base = {
              minTokensPerPlay: 10,
              maxTokensPerPlay: 100,
              straightMultiplier: 2,
              baseMultiplier: 1.5,
              tripleLowBonus: 3,
              tripleHighBonus: 5,
            };
            if (t === 'min') base.minTokensPerPlay = v;
            else base.maxTokensPerPlay = v;
            const r = await facade.updateDiceGame2Config('1', base);
            expect(r.isErr()).toBe(true);
            return true;
          },
        ),
      );
    });
  });
});
