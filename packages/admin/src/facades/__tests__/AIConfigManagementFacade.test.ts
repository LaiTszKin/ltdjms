import { describe, it, expect, vi, beforeEach } from 'vitest';
import { Ok, Err, DomainError, DomainErrorCategory } from '@ltdjms/shared';
import { AIConfigManagementFacade } from '../AIConfigManagementFacade.js';
import type {
  AIChannelRestrictionService,
  AIAgentChannelConfigService,
  AllowedChannel,
  AllowedCategory,
} from '@ltdjms/ai';

describe('AIConfigManagementFacade', () => {
  let facade: AIConfigManagementFacade;
  let mockChannelService: Partial<AIChannelRestrictionService>;
  let mockAgentService: Partial<AIAgentChannelConfigService>;

  const guildId = '123';
  const channelId = '456';

  beforeEach(() => {
    mockChannelService = {
      getAllowedChannels: vi.fn(),
      getAllowedCategories: vi.fn(),
      addAllowedChannel: vi.fn(),
      removeAllowedChannel: vi.fn(),
      addAllowedCategory: vi.fn(),
      removeAllowedCategory: vi.fn(),
    };
    mockAgentService = {
      getEnabledChannels: vi.fn(),
      setAgentEnabled: vi.fn(),
      removeChannel: vi.fn(),
    };

    facade = new AIConfigManagementFacade(
      mockChannelService as AIChannelRestrictionService,
      mockAgentService as AIAgentChannelConfigService,
    );
  });

  describe('channels', () => {
    it('should list allowed channels', async () => {
      const channels: AllowedChannel[] = [
        { guildId: '123', channelId: '456', channelName: 'general' },
      ];
      mockChannelService.getAllowedChannels = vi.fn().mockResolvedValue(new Ok(channels));

      const result = await facade.getAllowedChannels(guildId);
      expect(result.isOk()).toBe(true);
      expect(result.getValue()).toHaveLength(1);
    });

    it('should add allowed channel', async () => {
      const channel: AllowedChannel = { guildId, channelId, channelName: 'general' };
      mockChannelService.addAllowedChannel = vi.fn().mockResolvedValue(new Ok(channel));

      const result = await facade.addAllowedChannel(guildId, channelId, 'general');
      expect(result.isOk()).toBe(true);
    });
  });

  describe('agent', () => {
    it('should list agent channels', async () => {
      mockAgentService.getEnabledChannels = vi.fn().mockResolvedValue(new Ok(['456']));

      const result = await facade.getAgentConfigs(guildId);
      expect(result.isOk()).toBe(true);
      expect(result.getValue()).toEqual(['456']);
    });
  });
});
