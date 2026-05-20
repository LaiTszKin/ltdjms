import { describe, it, expect, vi, beforeEach } from 'vitest';
import { Ok } from '@ltdjms/shared';
import { AIConfigManagementFacade } from '../AIConfigManagementFacade.js';
describe('AIConfigManagementFacade', () => {
    let facade;
    let mockChannelService;
    let mockAgentService;
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
        facade = new AIConfigManagementFacade(mockChannelService, mockAgentService);
    });
    describe('channels', () => {
        it('should list allowed channels', async () => {
            const channels = [
                { guildId: '123', channelId: '456', channelName: 'general' },
            ];
            mockChannelService.getAllowedChannels = vi.fn().mockResolvedValue(new Ok(channels));
            const result = await facade.listAllowedChannels(guildId);
            expect(result.isOk()).toBe(true);
            expect(result.getValue()).toHaveLength(1);
        });
        it('should add allowed channel', async () => {
            const channel = { guildId, channelId, channelName: 'general' };
            mockChannelService.addAllowedChannel = vi.fn().mockResolvedValue(new Ok(channel));
            const result = await facade.addAllowedChannel(guildId, channelId, 'general');
            expect(result.isOk()).toBe(true);
        });
    });
    describe('agent', () => {
        it('should list agent channels', async () => {
            mockAgentService.getEnabledChannels = vi.fn().mockResolvedValue(new Ok(['456']));
            const result = await facade.listAgentChannels(guildId);
            expect(result.isOk()).toBe(true);
            expect(result.getValue()).toEqual(['456']);
        });
    });
});
//# sourceMappingURL=AIConfigManagementFacade.test.js.map