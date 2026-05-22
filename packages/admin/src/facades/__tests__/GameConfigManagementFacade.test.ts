import { describe, it, expect, vi, beforeEach } from 'vitest';
import { Ok, DomainError, type DomainEventPublisher } from '@ltdjms/shared';
import { GameConfigManagementFacade } from '../GameConfigManagementFacade.js';
import type { DiceConfigService } from '@ltdjms/economy';
import type { DiceGame1Config, DiceGame2Config } from '@ltdjms/economy';

describe('GameConfigManagementFacade', () => {
  let facade: GameConfigManagementFacade;
  let mockService: Partial<DiceConfigService>;
  let mockEventPublisher: Partial<DomainEventPublisher>;

  const guildId = '1';

  beforeEach(() => {
    mockService = {
      findDice1Config: vi.fn(),
      findDice2Config: vi.fn(),
      upsertDice1Config: vi.fn(),
      upsertDice2Config: vi.fn(),
    };
    mockEventPublisher = {
      publish: vi.fn(),
    };

    facade = new GameConfigManagementFacade(
      mockService as DiceConfigService,
      mockEventPublisher as DomainEventPublisher,
    );
  });

  describe('Dice Game 1', () => {
    it('should get config', async () => {
      const config: DiceGame1Config = {
        guildId: Number(guildId),
        minTokensPerPlay: 10,
        maxTokensPerPlay: 100,
        rewardPerDiceValue: 5,
        createdAt: new Date(),
        updatedAt: new Date(),
      };
      mockService.findDice1Config = vi.fn().mockResolvedValue(config);

      const result = await facade.getDiceGame1Config(guildId);
      expect(result.isOk()).toBe(true);
      expect(result.getValue().minTokensPerPlay).toBe(10);
    });

    it('should update config and delegate to service', async () => {
      const updated: DiceGame1Config = {
        guildId: Number(guildId),
        minTokensPerPlay: 20,
        maxTokensPerPlay: 200,
        rewardPerDiceValue: 10,
        createdAt: new Date(),
        updatedAt: new Date(),
      };
      mockService.findDice1Config = vi.fn().mockResolvedValue(null);
      mockService.upsertDice1Config = vi.fn().mockResolvedValue(updated);

      const result = await facade.updateDiceGame1Config(guildId, {
        minTokensPerPlay: 20,
        maxTokensPerPlay: 200,
        rewardPerDiceValue: 10,
      });

      expect(result.isOk()).toBe(true);
      expect(mockService.upsertDice1Config).toHaveBeenCalled();
    });

    it('should reject invalid min > max', async () => {
      const result = await facade.updateDiceGame1Config(guildId, {
        minTokensPerPlay: 100,
        maxTokensPerPlay: 10,
        rewardPerDiceValue: 5,
      });
      expect(result.isErr()).toBe(true);
    });
  });

  describe('Dice Game 2', () => {
    it('should get config', async () => {
      const config: DiceGame2Config = {
        guildId: Number(guildId),
        minTokensPerPlay: 10,
        maxTokensPerPlay: 100,
        straightMultiplier: 2.0,
        baseMultiplier: 1.0,
        tripleLowBonus: 3.0,
        tripleHighBonus: 5.0,
        createdAt: new Date(),
        updatedAt: new Date(),
      };
      mockService.findDice2Config = vi.fn().mockResolvedValue(config);

      const result = await facade.getDiceGame2Config(guildId);
      expect(result.isOk()).toBe(true);
    });

    it('should reject multipliers < 1.0', async () => {
      const result = await facade.updateDiceGame2Config(guildId, {
        minTokensPerPlay: 10,
        maxTokensPerPlay: 100,
        straightMultiplier: 0.5,
        baseMultiplier: 1.0,
        tripleLowBonus: 1.0,
        tripleHighBonus: 1.0,
      });
      expect(result.isErr()).toBe(true);
    });
  });
});
