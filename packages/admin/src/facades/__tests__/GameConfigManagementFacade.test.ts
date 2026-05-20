import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  Ok,
  Err,
  DomainError,
  DomainErrorCategory,
  DomainEventPublisher,
  GameType,
} from '@ltdjms/shared';
import { GameConfigManagementFacade } from '../GameConfigManagementFacade.js';
import type { DiceGame1Config, DiceGame2Config } from '@ltdjms/economy';
import { DiceConfigRepository } from '@ltdjms/economy';

describe('GameConfigManagementFacade', () => {
  let facade: GameConfigManagementFacade;
  let mockRepo: Partial<DiceConfigRepository>;
  let eventPublisher: DomainEventPublisher;

  const guildId = '1';

  beforeEach(() => {
    mockRepo = {
      findDice1Config: vi.fn(),
      findDice2Config: vi.fn(),
      upsertDice1Config: vi.fn(),
      upsertDice2Config: vi.fn(),
    };

    eventPublisher = new DomainEventPublisher();
    vi.spyOn(eventPublisher, 'publish');

    facade = new GameConfigManagementFacade(
      mockRepo as DiceConfigRepository,
      eventPublisher,
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
      mockRepo.findDice1Config = vi.fn().mockResolvedValue(config);

      const result = await facade.getDiceGame1Config(guildId);
      expect(result.isOk()).toBe(true);
      expect(result.getValue().minTokensPerPlay).toBe(10);
    });

    it('should update config and publish event', async () => {
      const updated: DiceGame1Config = {
        guildId: Number(guildId),
        minTokensPerPlay: 20,
        maxTokensPerPlay: 200,
        rewardPerDiceValue: 10,
        createdAt: new Date(),
        updatedAt: new Date(),
      };
      mockRepo.findDice1Config = vi.fn().mockResolvedValue(null);
      mockRepo.upsertDice1Config = vi.fn().mockResolvedValue(updated);

      const result = await facade.updateDiceGame1Config(guildId, {
        minTokensPerPlay: 20,
        maxTokensPerPlay: 200,
        rewardPerDiceValue: 10,
      });

      expect(result.isOk()).toBe(true);
      expect(eventPublisher.publish).toHaveBeenCalledWith(
        expect.objectContaining({
          guildId: guildId,
          gameType: GameType.DICE_GAME_1,
        }),
      );
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
      mockRepo.findDice2Config = vi.fn().mockResolvedValue(config);

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
