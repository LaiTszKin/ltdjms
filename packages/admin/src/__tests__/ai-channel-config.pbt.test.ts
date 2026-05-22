import { describe, it, expect, vi, beforeEach } from 'vitest';
import * as fc from 'fast-check';
import {
  Ok,
  Err,
  DomainError,
  DomainErrorCategory,
  okVoid,
  type DomainEventPublisher,
} from '@ltdjms/shared';
import { AIConfigManagementFacade } from '../facades/AIConfigManagementFacade.js';
import { AgentMode } from '../facades/agent-mode.js';
import type {
  AIChannelRestrictionService,
  AIAgentChannelConfigService,
  AllowedChannel,
  AllowedCategory,
} from '@ltdjms/ai';

/** Generates a short alphanumeric ID token (no whitespace). */
const idToken = (): fc.Arbitrary<string> => fc.stringMatching(/^[a-z0-9]{1,12}$/);

describe('AIConfigManagementFacade PBT', () => {
  let facade: AIConfigManagementFacade;
  let mockChannelService: Partial<AIChannelRestrictionService>;
  let mockAgentService: Partial<AIAgentChannelConfigService>;
  let mockEventPublisher: Partial<DomainEventPublisher>;

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
    mockEventPublisher = { publish: vi.fn() };

    facade = new AIConfigManagementFacade(
      mockChannelService as AIChannelRestrictionService,
      mockAgentService as AIAgentChannelConfigService,
      mockEventPublisher as DomainEventPublisher,
    );
  });

  // ================================================================
  // AI 頻道白名單管理
  // ================================================================
  describe('AI 頻道白名單管理', () => {
    it('getAllowedChannels：對任何 guildId 委派到 channelRestrictionService', async () => {
      await fc.assert(
        fc.asyncProperty(idToken(), idToken(), async (guildIdStr, channelIdStr) => {
          const channels: AllowedChannel[] = [
            { guildId: guildIdStr, channelId: channelIdStr, channelName: 'general' },
          ];
          mockChannelService.getAllowedChannels = vi.fn().mockResolvedValue(new Ok(channels));
          const result = await facade.getAllowedChannels(guildIdStr);
          expect(result.isOk()).toBe(true);
          expect(result.getValue()).toHaveLength(1);
          expect(result.getValue()[0]).toEqual(channels[0]);
          expect(mockChannelService.getAllowedChannels).toHaveBeenLastCalledWith(guildIdStr);
          return true;
        }),
      );
    });

    it('addAllowedChannel：成功時發布事件', async () => {
      await fc.assert(
        fc.asyncProperty(
          idToken(),
          idToken(),
          idToken(),
          async (guildIdStr, channelIdStr, channelName) => {
            const channel: AllowedChannel = {
              guildId: guildIdStr,
              channelId: channelIdStr,
              channelName,
            };
            mockChannelService.addAllowedChannel = vi.fn().mockResolvedValue(new Ok(channel));
            const result = await facade.addAllowedChannel(guildIdStr, channelIdStr, channelName);
            expect(result.isOk()).toBe(true);
            expect(result.getValue()).toEqual(channel);
            expect(mockChannelService.addAllowedChannel).toHaveBeenLastCalledWith(guildIdStr, {
              channelId: channelIdStr,
              channelName,
            });
            expect(mockEventPublisher.publish).toHaveBeenLastCalledWith(
              expect.objectContaining({
                eventType: 'ai_channel_config_changed',
                guildId: guildIdStr,
                changeType: 'channel_added',
                targetId: channelIdStr,
              }),
            );
            return true;
          },
        ),
      );
    });

    it('addAllowedChannel：失敗時不發布事件', async () => {
      await fc.assert(
        fc.asyncProperty(idToken(), idToken(), async (guildIdStr, channelIdStr) => {
          const err = DomainError.persistenceFailure('DB error');
          mockChannelService.addAllowedChannel = vi.fn().mockResolvedValue(new Err(err));
          const result = await facade.addAllowedChannel(guildIdStr, channelIdStr, 'general');
          expect(result.isErr()).toBe(true);
          expect(mockEventPublisher.publish).not.toHaveBeenCalled();
          return true;
        }),
      );
    });

    it('removeAllowedChannel：成功時發布事件', async () => {
      await fc.assert(
        fc.asyncProperty(idToken(), idToken(), async (guildIdStr, channelIdStr) => {
          mockChannelService.removeAllowedChannel = vi.fn().mockResolvedValue(okVoid());
          const result = await facade.removeAllowedChannel(guildIdStr, channelIdStr);
          expect(result.isOk()).toBe(true);
          expect(mockChannelService.removeAllowedChannel).toHaveBeenLastCalledWith(
            guildIdStr,
            channelIdStr,
          );
          expect(mockEventPublisher.publish).toHaveBeenLastCalledWith(
            expect.objectContaining({
              eventType: 'ai_channel_config_changed',
              guildId: guildIdStr,
              changeType: 'channel_removed',
              targetId: channelIdStr,
            }),
          );
          return true;
        }),
      );
    });
  });

  // ================================================================
  // AI 分類白名單管理
  // ================================================================
  describe('AI 分類白名單管理', () => {
    it('getAllowedCategories：對任何 guildId 委派', async () => {
      await fc.assert(
        fc.asyncProperty(idToken(), async (guildIdStr) => {
          const categories: AllowedCategory[] = [];
          mockChannelService.getAllowedCategories = vi.fn().mockResolvedValue(new Ok(categories));
          const result = await facade.getAllowedCategories(guildIdStr);
          expect(result.isOk()).toBe(true);
          expect(mockChannelService.getAllowedCategories).toHaveBeenLastCalledWith(guildIdStr);
          return true;
        }),
      );
    });

    it('addAllowedCategory：成功時發布事件', async () => {
      await fc.assert(
        fc.asyncProperty(
          idToken(),
          idToken(),
          idToken(),
          async (guildIdStr, categoryIdStr, categoryName) => {
            const category: AllowedCategory = {
              guildId: guildIdStr,
              categoryId: categoryIdStr,
              categoryName,
            };
            mockChannelService.addAllowedCategory = vi.fn().mockResolvedValue(new Ok(category));
            const result = await facade.addAllowedCategory(guildIdStr, categoryIdStr, categoryName);
            expect(result.isOk()).toBe(true);
            expect(mockChannelService.addAllowedCategory).toHaveBeenLastCalledWith(guildIdStr, {
              categoryId: categoryIdStr,
              categoryName,
            });
            expect(mockEventPublisher.publish).toHaveBeenLastCalledWith(
              expect.objectContaining({
                eventType: 'ai_channel_config_changed',
                guildId: guildIdStr,
                changeType: 'category_added',
                targetId: categoryIdStr,
              }),
            );
            return true;
          },
        ),
      );
    });

    it('removeAllowedCategory：成功時發布事件', async () => {
      await fc.assert(
        fc.asyncProperty(idToken(), idToken(), async (guildIdStr, categoryIdStr) => {
          mockChannelService.removeAllowedCategory = vi.fn().mockResolvedValue(okVoid());
          const result = await facade.removeAllowedCategory(guildIdStr, categoryIdStr);
          expect(result.isOk()).toBe(true);
          expect(mockChannelService.removeAllowedCategory).toHaveBeenLastCalledWith(
            guildIdStr,
            categoryIdStr,
          );
          expect(mockEventPublisher.publish).toHaveBeenLastCalledWith(
            expect.objectContaining({
              eventType: 'ai_channel_config_changed',
              guildId: guildIdStr,
              changeType: 'category_removed',
              targetId: categoryIdStr,
            }),
          );
          return true;
        }),
      );
    });
  });

  // ================================================================
  // Agent 模式設定
  // ================================================================
  describe('Agent 模式設定', () => {
    it('enableAgent：對任何 guildId/channelId 委派到 agentConfigService', async () => {
      await fc.assert(
        fc.asyncProperty(idToken(), idToken(), async (guildIdStr, channelIdStr) => {
          mockAgentService.setAgentEnabled = vi.fn().mockResolvedValue(okVoid());
          const result = await facade.enableAgent(guildIdStr, channelIdStr, AgentMode.AGENT);
          expect(result.isOk()).toBe(true);
          expect(mockAgentService.setAgentEnabled).toHaveBeenLastCalledWith(
            guildIdStr,
            channelIdStr,
            true,
          );
          expect(mockEventPublisher.publish).toHaveBeenLastCalledWith(
            expect.objectContaining({
              eventType: 'ai_agent_channel_config_changed',
              guildId: guildIdStr,
              channelId: channelIdStr,
              agentEnabled: true,
            }),
          );
          return true;
        }),
      );
    });

    it('disableAgent：對任何 guildId/channelId 委派到 agentConfigService', async () => {
      await fc.assert(
        fc.asyncProperty(idToken(), idToken(), async (guildIdStr, channelIdStr) => {
          mockAgentService.setAgentEnabled = vi.fn().mockResolvedValue(okVoid());
          const result = await facade.disableAgent(guildIdStr, channelIdStr);
          expect(result.isOk()).toBe(true);
          expect(mockAgentService.setAgentEnabled).toHaveBeenLastCalledWith(
            guildIdStr,
            channelIdStr,
            false,
          );
          expect(mockEventPublisher.publish).toHaveBeenLastCalledWith(
            expect.objectContaining({
              eventType: 'ai_agent_channel_config_changed',
              guildId: guildIdStr,
              channelId: channelIdStr,
              agentEnabled: false,
            }),
          );
          return true;
        }),
      );
    });

    it('getAgentConfigs：對任何 guildId 委派並回傳設定清單', async () => {
      await fc.assert(
        fc.asyncProperty(
          idToken(),
          fc.array(idToken(), { minLength: 0, maxLength: 5 }),
          async (guildIdStr, channelIds) => {
            mockAgentService.getEnabledChannels = vi.fn().mockResolvedValue(new Ok(channelIds));
            const result = await facade.getAgentConfigs(guildIdStr);
            expect(result.isOk()).toBe(true);
            const configs = result.getValue();
            expect(configs).toHaveLength(channelIds.length);
            for (let i = 0; i < channelIds.length; i++) {
              expect(configs[i].channelId).toBe(channelIds[i]);
              expect(configs[i].mode).toBe('agent');
            }
            expect(mockAgentService.getEnabledChannels).toHaveBeenLastCalledWith(guildIdStr);
            return true;
          },
        ),
      );
    });

    it('setAgentEnabled(true) 委派到 enableAgent', async () => {
      await fc.assert(
        fc.asyncProperty(idToken(), idToken(), async (guildIdStr, channelIdStr) => {
          mockAgentService.setAgentEnabled = vi.fn().mockResolvedValue(okVoid());
          const result = await facade.setAgentEnabled(guildIdStr, channelIdStr, true);
          expect(result.isOk()).toBe(true);
          expect(mockAgentService.setAgentEnabled).toHaveBeenLastCalledWith(
            guildIdStr,
            channelIdStr,
            true,
          );
          return true;
        }),
      );
    });

    it('setAgentEnabled(false) 委派到 disableAgent', async () => {
      await fc.assert(
        fc.asyncProperty(idToken(), idToken(), async (guildIdStr, channelIdStr) => {
          mockAgentService.setAgentEnabled = vi.fn().mockResolvedValue(okVoid());
          const result = await facade.setAgentEnabled(guildIdStr, channelIdStr, false);
          expect(result.isOk()).toBe(true);
          expect(mockAgentService.setAgentEnabled).toHaveBeenLastCalledWith(
            guildIdStr,
            channelIdStr,
            false,
          );
          return true;
        }),
      );
    });
  });
});
