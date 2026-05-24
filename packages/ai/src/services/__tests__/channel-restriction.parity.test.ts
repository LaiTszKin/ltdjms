import 'reflect-metadata';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  DefaultAIChannelRestrictionService,
  type AIChannelRestrictionRepository,
} from '../routing/channel-restriction-service.js';
import { DomainErrorCategory } from '@ltdjms/shared';
import type { AllowedChannel, AllowedCategory } from '../ai-chat-service.js';

/** UT-AIC-007 — DefaultAIChannelRestrictionServiceTest.java parity */
describe('UT-AIC-007 channel-restriction parity', () => {
  let repository: AIChannelRestrictionRepository;
  let service: DefaultAIChannelRestrictionService;

  beforeEach(() => {
    repository = {
      findChannel: vi.fn(),
      findByGuildId: vi.fn(),
      findRestrictionByGuildId: vi.fn(),
      findAllowedCategories: vi.fn(),
      addChannel: vi.fn(),
      addCategory: vi.fn(),
      removeChannel: vi.fn(),
      removeCategory: vi.fn(),
      deleteRemovedChannels: vi.fn(),
    };
    service = new DefaultAIChannelRestrictionService(repository);
  });

  it('shouldReturnFalseWhenEmpty', async () => {
    vi.mocked(repository.findChannel).mockResolvedValue(null);
    vi.mocked(repository.findAllowedCategories).mockResolvedValue([]);
    expect(await service.isChannelAllowed('123', '1001')).toBe(false);
  });

  it('shouldReturnTrueWhenInList', async () => {
    vi.mocked(repository.findChannel).mockResolvedValue({
      guildId: '123',
      channelId: '1001',
      channelName: 'general',
    } as AllowedChannel);
    expect(await service.isChannelAllowed('123', '1001')).toBe(true);
  });

  it('shouldReturnTrueWhenCategoryAllowed', async () => {
    vi.mocked(repository.findChannel).mockResolvedValue(null);
    vi.mocked(repository.findAllowedCategories).mockResolvedValue([
      { guildId: '123', categoryId: '2001', categoryName: 'cat' } as AllowedCategory,
    ]);
    expect(await service.isChannelAllowed('123', '9999', '2001')).toBe(true);
  });

  it('shouldReturnFalseWhenQueryFails', async () => {
    vi.mocked(repository.findChannel).mockRejectedValue(new Error('DB error'));
    expect(await service.isChannelAllowed('123', '1001')).toBe(false);
  });

  it('shouldReturnErrorWhenDuplicate', async () => {
    const channel = { channelId: '1001', channelName: 'general' };
    vi.mocked(repository.addChannel).mockResolvedValue({
      isOk: () => false,
      isErr: () => true,
      getError: () => ({
        category: DomainErrorCategory.DUPLICATE_CHANNEL,
        message: 'duplicate',
      }),
    } as never);
    const result = await service.addAllowedChannel('123', channel);
    expect(result.isErr()).toBe(true);
  });
});
